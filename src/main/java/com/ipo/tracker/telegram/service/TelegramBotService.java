package com.ipo.tracker.telegram.service;

import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.service.IpoService;
import com.ipo.tracker.telegram.TelegramBotProperties;
import com.ipo.tracker.telegram.dto.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final TelegramBotProperties properties;
    private final IpoService ipoService;
    private final TelegramMessageFormatter formatter;
    private final RestClient restClient;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService pollerExecutor;
    private long lastUpdateId = 0;

    public TelegramBotService(
            TelegramBotProperties properties,
            IpoService ipoService,
            TelegramMessageFormatter formatter) {
        this.properties = properties;
        this.ipoService = ipoService;
        this.formatter = formatter;
        this.restClient = RestClient.builder().build();
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("Telegram Bot integration is disabled via configuration.");
            return;
        }

        if (!properties.hasValidToken()) {
            log.warn("Telegram Bot token is not configured. Set 'telegram.bot.token' or TELEGRAM_BOT_TOKEN environment variable to activate Telegram polling.");
            return;
        }

        running.set(true);
        pollerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "telegram-bot-poller");
            t.setDaemon(true);
            return t;
        });

        pollerExecutor.submit(this::pollUpdatesLoop);
        log.info("Telegram Bot [@{}] polling started successfully.", properties.getUsername());
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (pollerExecutor != null) {
            pollerExecutor.shutdownNow();
        }
        log.info("Telegram Bot stopped.");
    }

    private void pollUpdatesLoop() {
        String urlTemplate = TELEGRAM_API_BASE + properties.getToken() + "/getUpdates?offset=%d&timeout=25";

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                String url = String.format(urlTemplate, lastUpdateId + 1);
                TelegramUpdatesResponse response = restClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(TelegramUpdatesResponse.class);

                if (response != null && Boolean.TRUE.equals(response.ok()) && response.result() != null) {
                    for (TelegramUpdate update : response.result()) {
                        if (update.updateId() != null) {
                            lastUpdateId = Math.max(lastUpdateId, update.updateId());
                        }
                        if (update.message() != null && update.message().text() != null) {
                            handleIncomingMessage(update.message());
                        } else if (update.callbackQuery() != null) {
                            handleCallbackQuery(update.callbackQuery());
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.warn("Error in Telegram polling loop: {}. Retrying in 5 seconds...", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public void handleIncomingMessage(TelegramMessage message) {
        Long chatId = message.chat().id();
        String rawText = message.text().trim();
        String text = rawText.toLowerCase();
        String senderName = message.from() != null ? message.from().firstName() : "";

        log.info("Received Telegram message from chatId {}: '{}'", chatId, rawText);

        try {
            if (text.startsWith("/start") || text.equals("hi") || text.equals("hello")) {
                sendReply(chatId, formatter.formatWelcome(senderName), ReplyKeyboardMarkup.defaultMenu());
            } else if (text.startsWith("/active") || text.startsWith("/open") || text.contains("active ipo")) {
                List<Ipo> active = ipoService.getActiveIpos();
                sendReply(chatId, formatter.formatActiveIpos(active), formatter.createActiveIposKeyboard(active));
            } else if (text.startsWith("/upcoming") || text.contains("upcoming ipo")) {
                List<Ipo> upcoming = ipoService.getUpcomingIpos();
                sendReply(chatId, formatter.formatUpcomingIpos(upcoming), formatter.createUpcomingIposKeyboard(upcoming));
            } else if (text.startsWith("/closed") || text.contains("closed ipo")) {
                List<Ipo> closed = ipoService.getClosedIpos();
                sendReply(chatId, formatter.formatClosedIpos(closed), formatter.createClosedIposKeyboard());
            } else if (text.startsWith("/help") || text.equals("❓ help")) {
                sendReply(chatId, formatter.formatHelp(), ReplyKeyboardMarkup.defaultMenu());
            } else if (text.startsWith("/ipo ") || text.startsWith("/search ")) {
                String query = rawText.substring(rawText.indexOf(' ') + 1).trim();
                handleSearch(chatId, query);
            } else {
                // Check if user typed a symbol or company name directly
                Optional<Ipo> match = ipoService.getIpoById(rawText);
                if (match.isPresent()) {
                    sendReply(chatId, formatter.formatSingleIpo(match.get()), formatter.createSingleIpoKeyboard(match.get()));
                } else {
                    List<Ipo> matches = ipoService.getAllIpos(null, null, rawText);
                    if (!matches.isEmpty()) {
                        sendReply(chatId, formatter.formatActiveIpos(matches), formatter.createActiveIposKeyboard(matches));
                    } else {
                        String notFound = "🔍 No IPO found matching '<b>" + escapeHtml(rawText) + "</b>'.\n\n" +
                                "Tap 🟢 <b>Active IPOs</b> or 📅 <b>Upcoming IPOs</b> below:";
                        sendReply(chatId, notFound, ReplyKeyboardMarkup.defaultMenu());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process message from chatId {}: {}", chatId, e.getMessage(), e);
            sendReply(chatId, "⚠️ An error occurred while retrieving IPO details. Please try again.", null);
        }
    }

    public void handleCallbackQuery(TelegramCallbackQuery callbackQuery) {
        String queryId = callbackQuery.id();
        String data = callbackQuery.data();
        Long chatId = (callbackQuery.message() != null && callbackQuery.message().chat() != null)
                ? callbackQuery.message().chat().id()
                : null;

        if (chatId == null || data == null) {
            answerCallbackQuery(queryId, null, false);
            return;
        }

        log.info("Received callback query from chatId {}: '{}'", chatId, data);

        try {
            if (data.startsWith("calc:")) {
                answerCallbackQuery(queryId, "Calculating listing profit...", false);
                String symbol = data.substring(5).trim();
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    sendReply(chatId, formatter.formatProfitCalculation(ipo.get()), formatter.createProfitCalcKeyboard(ipo.get()));
                } else {
                    sendReply(chatId, "⚠️ IPO details not found for symbol: " + symbol, null);
                }
            } else if (data.startsWith("score:")) {
                answerCallbackQuery(queryId, "Evaluating Smart Score...", false);
                String symbol = data.substring(6).trim();
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    sendReply(chatId, formatter.formatApplyScore(ipo.get()), formatter.createApplyScoreKeyboard(ipo.get()));
                } else {
                    sendReply(chatId, "⚠️ IPO details not found for symbol: " + symbol, null);
                }
            } else if (data.startsWith("sub:")) {
                answerCallbackQuery(queryId, "Fetching subscription...", false);
                String symbol = data.substring(4).trim();
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    sendReply(chatId, formatter.formatSubscriptionBreakdown(ipo.get()), formatter.createSubscriptionKeyboard(ipo.get()));
                } else {
                    sendReply(chatId, "⚠️ IPO details not found for symbol: " + symbol, null);
                }
            } else if (data.startsWith("reg:")) {
                answerCallbackQuery(queryId, "Fetching registrar portal...", false);
                String symbol = data.substring(4).trim();
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    sendReply(chatId, formatter.formatRegistrarDetails(ipo.get()), formatter.createRegistrarKeyboard(ipo.get()));
                } else {
                    sendReply(chatId, "⚠️ IPO details not found for symbol: " + symbol, null);
                }
            } else if (data.startsWith("ipo:")) {
                answerCallbackQuery(queryId, "Loading IPO...", false);
                String symbol = data.substring(4).trim();
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    sendReply(chatId, formatter.formatSingleIpo(ipo.get()), formatter.createSingleIpoKeyboard(ipo.get()));
                } else {
                    sendReply(chatId, "⚠️ IPO details not found for symbol: " + symbol, null);
                }
            } else if (data.startsWith("refresh:")) {
                String symbol = data.substring(8).trim();
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    answerCallbackQuery(queryId, "Refreshed live data for " + symbol + "!", false);
                    sendReply(chatId, formatter.formatSingleIpo(ipo.get()), formatter.createSingleIpoKeyboard(ipo.get()));
                } else {
                    answerCallbackQuery(queryId, "IPO not found", false);
                }
            } else if (data.equals("nav:active")) {
                answerCallbackQuery(queryId, "Loading active IPOs...", false);
                List<Ipo> active = ipoService.getActiveIpos();
                sendReply(chatId, formatter.formatActiveIpos(active), formatter.createActiveIposKeyboard(active));
            } else if (data.equals("nav:upcoming")) {
                answerCallbackQuery(queryId, "Loading upcoming IPOs...", false);
                List<Ipo> upcoming = ipoService.getUpcomingIpos();
                sendReply(chatId, formatter.formatUpcomingIpos(upcoming), formatter.createUpcomingIposKeyboard(upcoming));
            } else if (data.equals("nav:closed")) {
                answerCallbackQuery(queryId, "Loading closed IPOs...", false);
                List<Ipo> closed = ipoService.getClosedIpos();
                sendReply(chatId, formatter.formatClosedIpos(closed), formatter.createClosedIposKeyboard());
            } else {
                answerCallbackQuery(queryId, null, false);
            }
        } catch (Exception e) {
            log.error("Error handling callback query {}: {}", data, e.getMessage(), e);
            answerCallbackQuery(queryId, "Error processing action", false);
        }
    }

    public void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
        if (!properties.hasValidToken() || callbackQueryId == null) {
            return;
        }
        String url = TELEGRAM_API_BASE + properties.getToken() + "/answerCallbackQuery";
        TelegramAnswerCallbackRequest request = new TelegramAnswerCallbackRequest(callbackQueryId, text, showAlert);
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.debug("Failed to answer callback query {}: {}", callbackQueryId, e.getMessage());
        }
    }

    private void handleSearch(Long chatId, String query) {
        if (query.isBlank()) {
            sendReply(chatId, "Please specify an IPO symbol or company name, e.g. <code>/ipo swiggy</code>", null);
            return;
        }

        Optional<Ipo> exactMatch = ipoService.getIpoById(query);
        if (exactMatch.isPresent()) {
            sendReply(chatId, formatter.formatSingleIpo(exactMatch.get()), formatter.createSingleIpoKeyboard(exactMatch.get()));
            return;
        }

        List<Ipo> matches = ipoService.getAllIpos(null, null, query);
        if (!matches.isEmpty()) {
            sendReply(chatId, formatter.formatActiveIpos(matches), formatter.createActiveIposKeyboard(matches));
        } else {
            sendReply(chatId, "🔍 No IPO found matching '<b>" + escapeHtml(query) + "</b>'.", ReplyKeyboardMarkup.defaultMenu());
        }
    }

    public void sendReply(Long chatId, String htmlText, Object replyMarkup) {
        if (!properties.hasValidToken()) {
            log.warn("Cannot send Telegram message: Bot token is not configured.");
            return;
        }

        if (htmlText == null || htmlText.isBlank()) return;

        // Telegram message limit is 4096 chars. If message is longer, safely split into chunks
        if (htmlText.length() <= 3800) {
            executeSendMessage(chatId, htmlText, replyMarkup);
        } else {
            List<String> chunks = splitHtmlMessage(htmlText, 3800);
            for (int i = 0; i < chunks.size(); i++) {
                Object markup = (i == chunks.size() - 1) ? replyMarkup : null;
                executeSendMessage(chatId, chunks.get(i), markup);
            }
        }
    }

    private void executeSendMessage(Long chatId, String htmlText, Object replyMarkup) {
        String url = TELEGRAM_API_BASE + properties.getToken() + "/sendMessage";
        SendMessageRequest request = SendMessageRequest.html(chatId, htmlText, replyMarkup);

        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send Telegram message to chatId {}: {}", chatId, e.getMessage());
        }
    }

    private List<String> splitHtmlMessage(String text, int maxLength) {
        List<String> chunks = new java.util.ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + maxLength, length);
            if (end < length) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start) {
                    end = lastNewline;
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end;
        }
        return chunks;
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
