package com.ipo.tracker.service;

import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.Subscriber;
import com.ipo.tracker.repository.SubscriberRepository;
import com.ipo.tracker.telegram.service.TelegramBotService;
import com.ipo.tracker.telegram.service.TelegramMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IpoAlertService {

    private static final Logger log = LoggerFactory.getLogger(IpoAlertService.class);
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final double SURGE_THRESHOLD_PERCENT = 15.0;

    private final IpoService ipoService;
    private final SubscriberRepository subscriberRepository;
    private final TelegramBotService telegramBotService;
    private final TelegramMessageFormatter formatter;

    private final Map<String, Double> previousGmpMap = new ConcurrentHashMap<>();

    public IpoAlertService(
            IpoService ipoService,
            SubscriberRepository subscriberRepository,
            TelegramBotService telegramBotService,
            TelegramMessageFormatter formatter) {
        this.ipoService = ipoService;
        this.subscriberRepository = subscriberRepository;
        this.telegramBotService = telegramBotService;
        this.formatter = formatter;
    }

    /**
     * 9:00 AM IST Daily Morning Market Digest.
     * Summarizes IPOs opening today, closing today, ongoing issues, and GMP movers.
     */
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Kolkata")
    public void scheduledDailyMorningDigest() {
        log.info("Starting scheduled 9:00 AM IST Daily Morning Digest...");
        int sent = triggerMorningDigest(null);
        log.info("Daily Morning Digest broadcast completed to {} recipients.", sent);
    }

    /**
     * 2:00 PM IST Closing Day Cutoff Reminder.
     * Fires 3 hours before SEBI 5:00 PM window closes for any IPO closing today.
     */
    @Scheduled(cron = "0 0 14 * * ?", zone = "Asia/Kolkata")
    public void scheduledClosingDayAlerts() {
        log.info("Starting scheduled 2:00 PM IST Closing Day Cutoff Alerts...");
        int sent = triggerClosingAlerts(null);
        log.info("Closing Day Cutoff Alerts completed for {} deliveries.", sent);
    }

    /**
     * Background GMP Surge Monitor.
     * Runs at 10 AM, 12 PM, 2 PM, 4 PM, 6 PM IST Monday-Friday during trading hours.
     */
    @Scheduled(cron = "0 0 10,12,14,16,18 * * MON-FRI", zone = "Asia/Kolkata")
    public void scheduledGmpSurgeMonitor() {
        log.info("Checking for GMP surges across active IPOs...");
        int count = triggerGmpSurgeCheck(null);
        log.info("GMP surge monitor finished. Alerts sent: {}", count);
    }

    /**
     * Broadcasts or sends morning digest to a specific chat (or all subscribers if chatId is null).
     */
    public int triggerMorningDigest(Long specificChatId) {
        LocalDate today = LocalDate.now(IST_ZONE);
        List<Ipo> active = ipoService.getActiveIpos();
        List<Ipo> upcoming = ipoService.getUpcomingIpos();

        List<Ipo> openingToday = new ArrayList<>();
        List<Ipo> closingToday = new ArrayList<>();
        List<Ipo> ongoing = new ArrayList<>();

        for (Ipo ipo : active) {
            if (ipo.openDate() != null && ipo.openDate().isEqual(today)) {
                openingToday.add(ipo);
            } else if (ipo.closeDate() != null && ipo.closeDate().isEqual(today)) {
                closingToday.add(ipo);
            } else {
                ongoing.add(ipo);
            }
        }

        // Also check if any upcoming IPO opens today
        for (Ipo ipo : upcoming) {
            if (ipo.openDate() != null && ipo.openDate().isEqual(today) && !openingToday.contains(ipo)) {
                openingToday.add(ipo);
            }
        }

        String message = formatter.formatMorningDigest(openingToday, closingToday, ongoing);
        var keyboard = formatter.createMorningDigestKeyboard();

        if (specificChatId != null) {
            telegramBotService.sendReply(specificChatId, message, keyboard);
            return 1;
        }

        List<Subscriber> recipients = subscriberRepository.getSubscribersForMorningDigest();
        int sent = 0;
        for (Subscriber sub : recipients) {
            telegramBotService.sendReply(sub.chatId(), message, keyboard);
            sent++;
        }
        return sent;
    }

    /**
     * Broadcasts closing day reminders for any IPO closing today (or sends preview if specificChatId is given).
     */
    public int triggerClosingAlerts(Long specificChatId) {
        LocalDate today = LocalDate.now(IST_ZONE);
        List<Ipo> active = ipoService.getActiveIpos();
        List<Ipo> closing = new ArrayList<>();

        for (Ipo ipo : active) {
            if (ipo.closeDate() != null && ipo.closeDate().isEqual(today)) {
                closing.add(ipo);
            }
        }

        // If no IPO is closing today and a test preview is requested, preview the first active or upcoming IPO
        if (closing.isEmpty() && specificChatId != null) {
            Ipo previewIpo = !active.isEmpty() ? active.get(0) : (!ipoService.getUpcomingIpos().isEmpty() ? ipoService.getUpcomingIpos().get(0) : null);
            if (previewIpo != null) {
                String alert = formatter.formatClosingDayAlert(previewIpo);
                var keyboard = formatter.createClosingDayAlertKeyboard(previewIpo);
                telegramBotService.sendReply(specificChatId, alert, keyboard);
                return 1;
            }
        }

        int deliveries = 0;
        for (Ipo ipo : closing) {
            String alert = formatter.formatClosingDayAlert(ipo);
            var keyboard = formatter.createClosingDayAlertKeyboard(ipo);

            if (specificChatId != null) {
                telegramBotService.sendReply(specificChatId, alert, keyboard);
                deliveries++;
            } else {
                List<Subscriber> subscribers = subscriberRepository.getSubscribersForClosingAlert();
                for (Subscriber sub : subscribers) {
                    telegramBotService.sendReply(sub.chatId(), alert, keyboard);
                    deliveries++;
                }
            }
        }
        return deliveries;
    }

    /**
     * Checks GMP values against historical baseline and broadcasts alerts if shift >= 15%.
     */
    public int triggerGmpSurgeCheck(Long specificChatId) {
        List<Ipo> active = ipoService.getActiveIpos();
        int alertsCount = 0;

        for (Ipo ipo : active) {
            double currentGmp = extractNumericGmp(ipo.gmp());
            String symbol = ipo.symbol().toUpperCase();

            Double previousGmp = previousGmpMap.get(symbol);
            if (previousGmp != null && previousGmp > 0 && currentGmp > 0) {
                double diff = currentGmp - previousGmp;
                double pctChange = (diff / previousGmp) * 100.0;

                if (Math.abs(pctChange) >= SURGE_THRESHOLD_PERCENT) {
                    String oldGmpStr = "₹" + String.format("%.0f", previousGmp);
                    String alert = formatter.formatGmpSurgeAlert(ipo, oldGmpStr, ipo.gmp(), pctChange);
                    var keyboard = formatter.createGmpSurgeKeyboard(ipo);

                    if (specificChatId != null) {
                        telegramBotService.sendReply(specificChatId, alert, keyboard);
                        alertsCount++;
                    } else {
                        List<Subscriber> interested = subscriberRepository.getSubscribersForGmpSurge(symbol);
                        for (Subscriber sub : interested) {
                            telegramBotService.sendReply(sub.chatId(), alert, keyboard);
                            alertsCount++;
                        }
                    }
                }
            }
            previousGmpMap.put(symbol, currentGmp);
        }

        // If specific user tested and no natural surge occurred, provide a sample preview
        if (specificChatId != null && alertsCount == 0 && !active.isEmpty()) {
            Ipo sample = active.get(0);
            double current = extractNumericGmp(sample.gmp());
            double simulatedOld = current > 0 ? current * 0.8 : 50.0;
            double simulatedNew = current > 0 ? current : 65.0;
            double pct = ((simulatedNew - simulatedOld) / simulatedOld) * 100.0;
            String oldGmpStr = "₹" + String.format("%.0f", simulatedOld);
            String newGmpStr = "₹" + String.format("%.0f", simulatedNew);

            String alert = formatter.formatGmpSurgeAlert(sample, oldGmpStr, newGmpStr, pct);
            telegramBotService.sendReply(specificChatId, alert, formatter.createGmpSurgeKeyboard(sample));
            alertsCount++;
        }

        return alertsCount;
    }

    /**
     * Sends an instant comprehensive test alert to the requested chatId.
     */
    public void sendTestAlert(Long chatId) {
        if (chatId == null) return;
        List<Ipo> active = ipoService.getActiveIpos();
        Ipo target = !active.isEmpty() ? active.get(0) : (!ipoService.getUpcomingIpos().isEmpty() ? ipoService.getUpcomingIpos().get(0) : null);

        if (target != null) {
            String text = "🧪 <b>TEST ALERT: Sample 2:00 PM Closing Cutoff Notification</b>\n\n"
                    + formatter.formatClosingDayAlert(target);
            telegramBotService.sendReply(chatId, text, formatter.createClosingDayAlertKeyboard(target));
        } else {
            telegramBotService.sendReply(chatId, "🧪 <b>Test Alert:</b> Alerts system is operational!", null);
        }
    }

    private double extractNumericGmp(String gmp) {
        if (gmp == null || gmp.isBlank() || gmp.equalsIgnoreCase("N/A")) return 0.0;
        try {
            Matcher m = Pattern.compile("([+-]?\\s*₹?\\s*\\d+(?:\\.\\d+)?)").matcher(gmp);
            if (m.find()) {
                String matched = m.group(1).replace("₹", "").replace(" ", "").trim();
                return Double.parseDouble(matched);
            }
        } catch (Exception ignored) {}
        return 0.0;
    }
}
