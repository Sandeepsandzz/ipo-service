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
                sendReply(chatId, formatter.formatActiveIpos(active), ReplyKeyboardMarkup.defaultMenu());
            } else if (text.startsWith("/upcoming") || text.contains("upcoming ipo")) {
                List<Ipo> upcoming = ipoService.getUpcomingIpos();
                sendReply(chatId, formatter.formatUpcomingIpos(upcoming), ReplyKeyboardMarkup.defaultMenu());
            } else if (text.startsWith("/closed") || text.contains("closed ipo")) {
                List<Ipo> closed = ipoService.getClosedIpos();
                sendReply(chatId, "📊 <b>Recently Closed IPOs:</b>\n\n" + formatter.formatActiveIpos(closed), ReplyKeyboardMarkup.defaultMenu());
            } else if (text.startsWith("/help") || text.equals("❓ help")) {
                sendReply(chatId, formatter.formatHelp(), ReplyKeyboardMarkup.defaultMenu());
            } else if (text.startsWith("/ipo ") || text.startsWith("/search ")) {
                String query = rawText.substring(rawText.indexOf(' ') + 1).trim();
                handleSearch(chatId, query);
            } else {
                // Check if user typed a symbol or company name directly
                Optional<Ipo> match = ipoService.getIpoById(rawText);
                if (match.isPresent()) {
                    sendReply(chatId, formatter.formatSingleIpo(match.get()), ReplyKeyboardMarkup.defaultMenu());
                } else {
                    List<Ipo> matches = ipoService.getAllIpos(null, null, rawText);
                    if (!matches.isEmpty()) {
                        sendReply(chatId, formatter.formatActiveIpos(matches), ReplyKeyboardMarkup.defaultMenu());
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

    private void handleSearch(Long chatId, String query) {
        if (query.isBlank()) {
            sendReply(chatId, "Please specify an IPO symbol or company name, e.g. <code>/ipo swiggy</code>", null);
            return;
        }

        Optional<Ipo> exactMatch = ipoService.getIpoById(query);
        if (exactMatch.isPresent()) {
            sendReply(chatId, formatter.formatSingleIpo(exactMatch.get()), ReplyKeyboardMarkup.defaultMenu());
            return;
        }

        List<Ipo> matches = ipoService.getAllIpos(null, null, query);
        if (!matches.isEmpty()) {
            sendReply(chatId, formatter.formatActiveIpos(matches), ReplyKeyboardMarkup.defaultMenu());
        } else {
            sendReply(chatId, "🔍 No IPO found matching '<b>" + escapeHtml(query) + "</b>'.", ReplyKeyboardMarkup.defaultMenu());
        }
    }

    public void sendReply(Long chatId, String htmlText, Object replyMarkup) {
        if (!properties.hasValidToken()) {
            log.warn("Cannot send Telegram message: Bot token is not configured.");
            return;
        }

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

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
