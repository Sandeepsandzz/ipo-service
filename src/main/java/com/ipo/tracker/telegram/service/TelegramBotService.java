package com.ipo.tracker.telegram.service;

import com.ipo.tracker.model.AllotmentResult;
import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.SavedPan;
import com.ipo.tracker.model.Subscriber;
import com.ipo.tracker.repository.SubscriberRepository;
import com.ipo.tracker.service.IpoAllotmentService;
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

import java.util.ArrayList;
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
    private final SubscriberRepository subscriberRepository;
    private final IpoAllotmentService allotmentService;
    private final RestClient restClient;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService pollerExecutor;
    private long lastUpdateId = 0;

    public TelegramBotService(
            TelegramBotProperties properties,
            IpoService ipoService,
            TelegramMessageFormatter formatter,
            SubscriberRepository subscriberRepository,
            IpoAllotmentService allotmentService) {
        this.properties = properties;
        this.ipoService = ipoService;
        this.formatter = formatter;
        this.subscriberRepository = subscriberRepository;
        this.allotmentService = allotmentService;
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
        String username = message.from() != null ? message.from().username() : null;

        log.info("Received Telegram message from chatId {}: '{}'", chatId, rawText);

        // Auto-register or refresh subscriber profile
        subscriberRepository.registerOrUpdate(chatId, username, senderName);

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
            } else if (text.startsWith("/odds") || text.equals("🎲 allotment odds")) {
                handleOddsCommand(chatId, rawText);
            } else if (text.startsWith("/calc") || text.equals("🧮 profit calculator")) {
                handleCalcCommand(chatId, rawText);
            } else if (text.startsWith("/status") || text.equals("🔍 check allotment")) {
                handleStatusCommand(chatId, rawText);
            } else if (text.startsWith("/savepan")) {
                handleSavePanCommand(chatId, rawText);
            } else if (text.startsWith("/mypans")) {
                handleMyPansCommand(chatId);
            } else if (text.startsWith("/delpan")) {
                handleDeletePanCommand(chatId, rawText);
            } else if (text.startsWith("/alerts") || text.equals("🔔 alerts & watchlist")) {
                handleAlertsCommand(chatId, username, senderName);
            } else if (text.startsWith("/subscribe")) {
                subscriberRepository.updateSettings(chatId, true, true, true);
                Subscriber sub = subscriberRepository.getSubscriber(chatId).orElseGet(() -> Subscriber.createDefault(chatId, username, senderName));
                sendReply(chatId, "🔔 <b>All Push Alerts Activated!</b>\n\nYou will receive:\n• 9:00 AM Daily Morning Digest\n• 2:00 PM Closing Day Reminders\n• Real-time GMP Surge Alerts\n\nManage preferences anytime with <code>/alerts</code>.", formatter.createAlertsSettingsKeyboard(sub));
            } else if (text.startsWith("/unsubscribe")) {
                subscriberRepository.unsubscribe(chatId);
                sendReply(chatId, "🔕 <b>All Alerts Paused</b>\n\nYou have turned off automated notifications. Turn back on anytime with <code>/subscribe</code> or <code>/alerts</code>.", ReplyKeyboardMarkup.defaultMenu());
            } else if (text.startsWith("/track")) {
                handleTrackCommand(chatId, rawText, username, senderName);
            } else if (text.startsWith("/testalert")) {
                handleTestAlert(chatId);
            } else if (text.startsWith("/help") || text.equals("❓ help")) {
                sendReply(chatId, formatter.formatHelp(), ReplyKeyboardMarkup.defaultMenu());
            } else if (text.startsWith("/ipo ") || text.startsWith("/search ")) {
                String query = rawText.substring(rawText.indexOf(' ') + 1).trim();
                handleSearch(chatId, query);
            } else {
                // Check if user typed a symbol or company name directly
                Optional<Ipo> match = ipoService.getIpoById(rawText);
                if (match.isPresent()) {
                    boolean isTracked = subscriberRepository.isInWatchlist(chatId, match.get().symbol());
                    sendReply(chatId, formatter.formatSingleIpo(match.get()), formatter.createSingleIpoKeyboard(match.get(), isTracked));
                } else {
                    List<Ipo> matches = ipoService.getAllIpos(null, null, rawText);
                    if (!matches.isEmpty()) {
                        sendReply(chatId, formatter.formatActiveIpos(matches), formatter.createActiveIposKeyboard(matches));
                    } else {
                        String notFound = "🔍 No IPO found matching '<b>" + escapeHtml(rawText) + "</b>'.\n\n" +
                                "Tap 🟢 <b>Active IPOs</b> or 🎲 <b>Allotment Odds</b> below:";
                        sendReply(chatId, notFound, ReplyKeyboardMarkup.defaultMenu());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process message from chatId {}: {}", chatId, e.getMessage(), e);
            sendReply(chatId, "⚠️ An error occurred while retrieving IPO details. Please try again.", null);
        }
    }

    private void handleOddsCommand(Long chatId, String rawText) {
        String[] parts = rawText.trim().split("\\s+");
        if (parts.length == 1) {
            List<Ipo> active = ipoService.getActiveIpos();
            if (active.isEmpty()) {
                sendReply(chatId, "🎲 <b>Multi-PAN Allotment Odds Calculator</b>\n\nNo IPOs currently open. Try typing: <code>/odds &lt;symbol&gt; [pans]</code>, e.g. <code>/odds swiggy 3</code>", ReplyKeyboardMarkup.defaultMenu());
                return;
            }
            sendReply(chatId, "🎲 <b>Multi-PAN Allotment Odds Calculator</b>\n\nSelect an active IPO below to view allotment probabilities across family accounts, or type <code>/odds &lt;symbol&gt; [pans]</code>:\n", formatter.createActiveIposKeyboard(active));
            return;
        }

        String query = parts[1].trim();
        int pans = 0;
        if (parts.length >= 3) {
            try {
                pans = Integer.parseInt(parts[2].trim());
            } catch (Exception ignored) {}
        }

        Optional<Ipo> ipo = ipoService.getIpoById(query);
        if (ipo.isPresent()) {
            sendReply(chatId, formatter.formatMultiPanOdds(ipo.get(), pans), formatter.createMultiPanOddsKeyboard(ipo.get(), pans));
        } else {
            sendReply(chatId, "🔍 No IPO found matching '<b>" + escapeHtml(query) + "</b>' for odds calculation. Example: <code>/odds swiggy 3</code>", ReplyKeyboardMarkup.defaultMenu());
        }
    }

    private void handleCalcCommand(Long chatId, String rawText) {
        String[] parts = rawText.trim().split("\\s+");
        if (parts.length == 1) {
            List<Ipo> active = ipoService.getActiveIpos();
            if (active.isEmpty()) {
                sendReply(chatId, "🧮 <b>Listing Profit Calculator</b>\n\nNo IPOs currently open. Try typing: <code>/calc &lt;symbol&gt; [lots]</code>, e.g. <code>/calc swiggy 2</code>", ReplyKeyboardMarkup.defaultMenu());
                return;
            }
            sendReply(chatId, "🧮 <b>Listing Profit Calculator</b>\n\nSelect an active IPO below to calculate listing gains, or type <code>/calc &lt;symbol&gt; [lots]</code> (e.g. <code>/calc swiggy 5</code>):\n", formatter.createActiveIposKeyboard(active));
            return;
        }

        String query = parts[1].trim();
        int lots = 0;
        if (parts.length >= 3) {
            try {
                lots = Integer.parseInt(parts[2].trim());
            } catch (Exception ignored) {}
        }

        Optional<Ipo> ipo = ipoService.getIpoById(query);
        if (ipo.isPresent()) {
            sendReply(chatId, formatter.formatProfitCalculation(ipo.get(), lots), formatter.createProfitCalcKeyboard(ipo.get(), lots));
        } else {
            sendReply(chatId, "🔍 No IPO found matching '<b>" + escapeHtml(query) + "</b>' for profit calculation. Example: <code>/calc swiggy 2</code>", ReplyKeyboardMarkup.defaultMenu());
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
                String remainder = data.substring(5).trim();
                String symbol = remainder;
                int lots = 0;
                if (remainder.contains(":")) {
                    String[] split = remainder.split(":");
                    symbol = split[0];
                    try { lots = Integer.parseInt(split[1]); } catch (Exception ignored) {}
                }
                answerCallbackQuery(queryId, lots > 0 ? "Calculating for " + lots + " lot(s)..." : "Calculating listing profit...", false);
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    sendReply(chatId, formatter.formatProfitCalculation(ipo.get(), lots), formatter.createProfitCalcKeyboard(ipo.get(), lots));
                } else {
                    sendReply(chatId, "⚠️ IPO details not found for symbol: " + symbol, null);
                }
            } else if (data.startsWith("odds:")) {
                String remainder = data.substring(5).trim();
                String symbol = remainder;
                int pans = 0;
                if (remainder.contains(":")) {
                    String[] split = remainder.split(":");
                    symbol = split[0];
                    try { pans = Integer.parseInt(split[1]); } catch (Exception ignored) {}
                }
                answerCallbackQuery(queryId, pans > 0 ? "Simulating for " + pans + " family PAN(s)..." : "Calculating allotment odds...", false);
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    sendReply(chatId, formatter.formatMultiPanOdds(ipo.get(), pans), formatter.createMultiPanOddsKeyboard(ipo.get(), pans));
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
                    boolean isTracked = subscriberRepository.isInWatchlist(chatId, ipo.get().symbol());
                    sendReply(chatId, formatter.formatSingleIpo(ipo.get()), formatter.createSingleIpoKeyboard(ipo.get(), isTracked));
                } else {
                    sendReply(chatId, "⚠️ IPO details not found for symbol: " + symbol, null);
                }
            } else if (data.startsWith("track:")) {
                String symbol = data.substring(6).trim().toUpperCase();
                boolean added = subscriberRepository.toggleWatchlist(chatId, symbol);
                answerCallbackQuery(queryId, added ? "Added " + symbol + " to watchlist! 🔔" : "Removed " + symbol + " from watchlist 🔕", false);
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    sendReply(chatId, formatter.formatSingleIpo(ipo.get()), formatter.createSingleIpoKeyboard(ipo.get(), added));
                }
            } else if (data.startsWith("toggle:")) {
                String toggleType = data.substring(7).trim();
                Subscriber current = subscriberRepository.getSubscriber(chatId)
                        .orElseGet(() -> subscriberRepository.registerOrUpdate(chatId, null, null));
                Boolean newMorning = null, newClosing = null, newGmp = null;
                String toast = "";
                if ("morning".equals(toggleType)) {
                    newMorning = !current.morningDigestEnabled();
                    toast = newMorning ? "Morning Digest enabled! 🌅" : "Morning Digest disabled ❌";
                } else if ("closing".equals(toggleType)) {
                    newClosing = !current.closingAlertEnabled();
                    toast = newClosing ? "Closing Cutoff alert enabled! ⏰" : "Closing Cutoff alert disabled ❌";
                } else if ("gmp".equals(toggleType)) {
                    newGmp = !current.gmpSurgeEnabled();
                    toast = newGmp ? "GMP Surge alerts enabled! ⚡️" : "GMP Surge alerts disabled ❌";
                }
                Subscriber updated = subscriberRepository.updateSettings(chatId, newMorning, newClosing, newGmp);
                answerCallbackQuery(queryId, toast, false);
                sendReply(chatId, formatter.formatAlertsSettings(updated), formatter.createAlertsSettingsKeyboard(updated));
            } else if (data.equals("nav:alerts")) {
                answerCallbackQuery(queryId, "Opening alert settings...", false);
                Subscriber sub = subscriberRepository.getSubscriber(chatId)
                        .orElseGet(() -> subscriberRepository.registerOrUpdate(chatId, null, null));
                sendReply(chatId, formatter.formatAlertsSettings(sub), formatter.createAlertsSettingsKeyboard(sub));
            } else if (data.equals("test:alert")) {
                answerCallbackQuery(queryId, "Sending test alert...", false);
                handleTestAlert(chatId);
            } else if (data.startsWith("allot:")) {
                String symbol = data.substring(6).trim();
                answerCallbackQuery(queryId, "Opening allotment checker...", false);
                List<SavedPan> saved = subscriberRepository.getSavedPans(chatId);
                if (!saved.isEmpty()) {
                    String prompt = "🔍 <b>Check Allotment: " + escapeHtml(symbol) + "</b>\n\nSelect a family PAN card to verify:";
                    sendReply(chatId, prompt, formatter.createSavedPansKeyboard(saved, symbol));
                } else {
                    String prompt = "🔍 <b>Check Allotment: " + escapeHtml(symbol) + "</b>\n\n"
                            + "You haven't saved any PAN cards yet.\n\n"
                            + "Type: <code>/status " + escapeHtml(symbol) + " &lt;PAN&gt;</code>\n"
                            + "Or save your PAN once with: <code>/savepan &lt;PAN&gt; [label]</code>";
                    Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                    boolean isTracked = ipo.isPresent() && subscriberRepository.isInWatchlist(chatId, ipo.get().symbol());
                    sendReply(chatId, prompt, ipo.isPresent() ? formatter.createSingleIpoKeyboard(ipo.get(), isTracked) : ReplyKeyboardMarkup.defaultMenu());
                }
            } else if (data.startsWith("allotcheck:")) {
                String payload = data.substring(11).trim();
                String[] split = payload.split(":");
                String symbol = split[0];
                String pan = split.length > 1 ? split[1] : "";
                answerCallbackQuery(queryId, "Checking allotment status...", false);
                String label = subscriberRepository.getSavedPans(chatId).stream()
                        .filter(p -> p.pan().equalsIgnoreCase(pan))
                        .map(SavedPan::label)
                        .findFirst()
                        .orElse("Applicant");
                executeAllotmentCheck(chatId, symbol, pan, label);
            } else if (data.startsWith("delpan:")) {
                String pan = data.substring(7).trim();
                subscriberRepository.removeSavedPan(chatId, pan);
                answerCallbackQuery(queryId, "Deleted PAN card", false);
                List<SavedPan> remaining = subscriberRepository.getSavedPans(chatId);
                sendReply(chatId, formatter.formatSavedPans(remaining), formatter.createSavedPansKeyboard(remaining, null));
            } else if (data.equals("nav:mypans")) {
                answerCallbackQuery(queryId, "Opening saved PANs...", false);
                handleMyPansCommand(chatId);
            } else if (data.startsWith("refresh:")) {
                String symbol = data.substring(8).trim();
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                if (ipo.isPresent()) {
                    answerCallbackQuery(queryId, "Refreshed live data for " + symbol + "!", false);
                    boolean isTracked = subscriberRepository.isInWatchlist(chatId, ipo.get().symbol());
                    sendReply(chatId, formatter.formatSingleIpo(ipo.get()), formatter.createSingleIpoKeyboard(ipo.get(), isTracked));
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

    private void handleStatusCommand(Long chatId, String rawText) {
        String[] parts = rawText.trim().split("\\s+");
        if (parts.length == 1) {
            List<SavedPan> saved = subscriberRepository.getSavedPans(chatId);
            List<Ipo> active = ipoService.getActiveIpos();
            List<Ipo> closed = ipoService.getClosedIpos();

            StringBuilder sb = new StringBuilder();
            sb.append("🔍 <b>Live IPO Allotment Status Checker</b>\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            if (!saved.isEmpty()) {
                sb.append("You have <b>").append(saved.size()).append("</b> saved family PAN card(s).\n");
            }
            sb.append("Select an IPO below to check allotment, or check using command:\n");
            sb.append("<code>/status &lt;symbol&gt; &lt;PAN&gt;</code>\n\n");
            sb.append("<i>Example:</i> <code>/status swiggy ABCDE1234F</code>\n");
            sb.append("<i>Save PAN:</i> <code>/savepan ABCDE1234F Dad</code>");

            List<Ipo> candidates = new ArrayList<>();
            if (!closed.isEmpty()) candidates.addAll(closed.subList(0, Math.min(4, closed.size())));
            if (!active.isEmpty()) candidates.addAll(active.subList(0, Math.min(4, active.size())));

            sendReply(chatId, sb.toString(), formatter.createActiveIposKeyboard(candidates));
            return;
        }

        String symbol = parts[1].trim();
        String pan = null;
        if (parts.length >= 3) {
            pan = parts[2].trim().toUpperCase();
        } else {
            List<SavedPan> saved = subscriberRepository.getSavedPans(chatId);
            if (!saved.isEmpty()) {
                Optional<Ipo> ipo = ipoService.getIpoById(symbol);
                String sym = ipo.map(Ipo::symbol).orElse(symbol.toUpperCase());
                String prompt = "🔍 <b>Check Allotment for " + escapeHtml(sym) + "</b>\n\nChoose which family PAN card to verify:";
                sendReply(chatId, prompt, formatter.createSavedPansKeyboard(saved, sym));
                return;
            } else {
                sendReply(chatId, "Please specify your PAN card number:\n<code>/status " + escapeHtml(symbol) + " &lt;PAN&gt;</code>\nExample: <code>/status " + escapeHtml(symbol) + " ABCDE1234F</code>\n\n<i>Or save it once with:</i> <code>/savepan ABCDE1234F Self</code>", ReplyKeyboardMarkup.defaultMenu());
                return;
            }
        }

        executeAllotmentCheck(chatId, symbol, pan, "Applicant");
    }

    private void executeAllotmentCheck(Long chatId, String symbol, String pan, String label) {
        AllotmentResult result = allotmentService.checkAllotment(symbol, pan, label);
        sendReply(chatId, formatter.formatAllotmentResult(result), formatter.createAllotmentResultKeyboard(result));
    }

    private void handleSavePanCommand(Long chatId, String rawText) {
        String[] parts = rawText.trim().split("\\s+", 3);
        if (parts.length < 2) {
            sendReply(chatId, "⚠️ <b>Usage:</b> <code>/savepan &lt;PAN&gt; [label]</code>\n\n<i>Example:</i> <code>/savepan ABCDE1234F Dad</code>", ReplyKeyboardMarkup.defaultMenu());
            return;
        }
        String pan = parts[1].trim().toUpperCase();
        String label = parts.length >= 3 ? parts[2].trim() : "Account";

        if (!allotmentService.isValidPan(pan)) {
            sendReply(chatId, "⚠️ <b>Invalid PAN format:</b> <code>" + escapeHtml(pan) + "</code>\n\nMust be 10 characters (5 letters, 4 digits, 1 letter, e.g. <code>ABCDE1234F</code>).", ReplyKeyboardMarkup.defaultMenu());
            return;
        }

        boolean saved = subscriberRepository.addSavedPan(chatId, pan, label);
        if (saved) {
            String masked = allotmentService.maskPan(pan);
            String text = "✅ <b>Saved PAN Card!</b>\n\n"
                    + "🏷 <b>Label:</b> " + escapeHtml(label) + "\n"
                    + "💳 <b>PAN:</b> <code>" + masked + "</code>\n\n"
                    + "Now you can check allotment in 1 click whenever an IPO closes!";
            List<SavedPan> all = subscriberRepository.getSavedPans(chatId);
            sendReply(chatId, text, formatter.createSavedPansKeyboard(all, null));
        } else {
            sendReply(chatId, "⚠️ Could not save PAN card. Please check the format.", ReplyKeyboardMarkup.defaultMenu());
        }
    }

    private void handleMyPansCommand(Long chatId) {
        List<SavedPan> pans = subscriberRepository.getSavedPans(chatId);
        sendReply(chatId, formatter.formatSavedPans(pans), formatter.createSavedPansKeyboard(pans, null));
    }

    private void handleDeletePanCommand(Long chatId, String rawText) {
        String[] parts = rawText.trim().split("\\s+");
        if (parts.length < 2) {
            sendReply(chatId, "Usage: <code>/delpan &lt;PAN&gt;</code>, or tap Delete in <code>/mypans</code>", ReplyKeyboardMarkup.defaultMenu());
            return;
        }
        String pan = parts[1].trim().toUpperCase();
        boolean removed = subscriberRepository.removeSavedPan(chatId, pan);
        if (removed) {
            sendReply(chatId, "🗑 Removed PAN <code>" + escapeHtml(allotmentService.maskPan(pan)) + "</code>.", ReplyKeyboardMarkup.defaultMenu());
        } else {
            sendReply(chatId, "PAN not found in your saved list.", ReplyKeyboardMarkup.defaultMenu());
        }
    }

    private void handleAlertsCommand(Long chatId, String username, String senderName) {
        Subscriber sub = subscriberRepository.getSubscriber(chatId)
                .orElseGet(() -> subscriberRepository.registerOrUpdate(chatId, username, senderName));
        sendReply(chatId, formatter.formatAlertsSettings(sub), formatter.createAlertsSettingsKeyboard(sub));
    }

    private void handleTrackCommand(Long chatId, String rawText, String username, String senderName) {
        String[] parts = rawText.trim().split("\\s+");
        if (parts.length < 2) {
            sendReply(chatId, "Please specify an IPO symbol to track/untrack, e.g. <code>/track swiggy</code>", ReplyKeyboardMarkup.defaultMenu());
            return;
        }
        String symbol = parts[1].trim().toUpperCase();
        boolean added = subscriberRepository.toggleWatchlist(chatId, symbol);
        String msg = added
                ? "🔔 Added <code>" + escapeHtml(symbol) + "</code> to your personal watchlist! You will receive GMP surges and closing alerts for this IPO."
                : "🔕 Removed <code>" + escapeHtml(symbol) + "</code> from your watchlist.";

        Subscriber sub = subscriberRepository.getSubscriber(chatId)
                .orElseGet(() -> subscriberRepository.registerOrUpdate(chatId, username, senderName));
        sendReply(chatId, msg + "\n\n" + formatter.formatAlertsSettings(sub), formatter.createAlertsSettingsKeyboard(sub));
    }

    private void handleTestAlert(Long chatId) {
        List<Ipo> active = ipoService.getActiveIpos();
        Ipo preview = !active.isEmpty() ? active.get(0) : (!ipoService.getUpcomingIpos().isEmpty() ? ipoService.getUpcomingIpos().get(0) : null);
        if (preview != null) {
            String alertText = "🧪 <b>TEST ALERT: Sample 2:00 PM Closing Cutoff Notification</b>\n\n"
                    + formatter.formatClosingDayAlert(preview);
            sendReply(chatId, alertText, formatter.createClosingDayAlertKeyboard(preview));
        } else {
            sendReply(chatId, "🧪 <b>Test Alert:</b> Alert notification pipeline is operational!", ReplyKeyboardMarkup.defaultMenu());
        }
    }

    private void handleSearch(Long chatId, String query) {
        if (query.isBlank()) {
            sendReply(chatId, "Please specify an IPO symbol or company name, e.g. <code>/ipo swiggy</code>", null);
            return;
        }

        Optional<Ipo> exactMatch = ipoService.getIpoById(query);
        if (exactMatch.isPresent()) {
            boolean isTracked = subscriberRepository.isInWatchlist(chatId, exactMatch.get().symbol());
            sendReply(chatId, formatter.formatSingleIpo(exactMatch.get()), formatter.createSingleIpoKeyboard(exactMatch.get(), isTracked));
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
