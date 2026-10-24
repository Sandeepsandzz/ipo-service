package com.ipo.tracker.controller;

import com.ipo.tracker.model.IpoApiResponse;
import com.ipo.tracker.model.Subscriber;
import com.ipo.tracker.repository.SubscriberRepository;
import com.ipo.tracker.service.IpoAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@CrossOrigin(origins = "*")
public class AlertController {

    private final IpoAlertService alertService;
    private final SubscriberRepository subscriberRepository;

    public AlertController(IpoAlertService alertService, SubscriberRepository subscriberRepository) {
        this.alertService = alertService;
        this.subscriberRepository = subscriberRepository;
    }

    /**
     * Trigger 9:00 AM Daily Morning Market Digest broadcast (or to a specific chatId).
     * POST /api/v1/alerts/morning-digest?chatId=123456789
     */
    @PostMapping("/morning-digest")
    public ResponseEntity<IpoApiResponse<Map<String, Object>>> triggerMorningDigest(
            @RequestParam(required = false) Long chatId) {
        int sent = alertService.triggerMorningDigest(chatId);
        return ResponseEntity.ok(IpoApiResponse.of(
                "Triggered morning digest alert",
                sent,
                Map.of("recipientsCount", sent, "targetChatId", chatId != null ? chatId : "BROADCAST")
        ));
    }

    /**
     * Trigger 2:00 PM Closing Day Cutoff Reminder (or to a specific chatId).
     * POST /api/v1/alerts/closing?chatId=123456789
     */
    @PostMapping("/closing")
    public ResponseEntity<IpoApiResponse<Map<String, Object>>> triggerClosingAlerts(
            @RequestParam(required = false) Long chatId) {
        int sent = alertService.triggerClosingAlerts(chatId);
        return ResponseEntity.ok(IpoApiResponse.of(
                "Triggered closing day cutoff alerts",
                sent,
                Map.of("deliveriesCount", sent, "targetChatId", chatId != null ? chatId : "BROADCAST")
        ));
    }

    /**
     * Trigger background GMP surge monitor (or send preview to specific chatId).
     * POST /api/v1/alerts/gmp-surge?chatId=123456789
     */
    @PostMapping("/gmp-surge")
    public ResponseEntity<IpoApiResponse<Map<String, Object>>> triggerGmpSurge(
            @RequestParam(required = false) Long chatId) {
        int alerts = alertService.triggerGmpSurgeCheck(chatId);
        return ResponseEntity.ok(IpoApiResponse.of(
                "Triggered GMP surge check",
                alerts,
                Map.of("alertsDispatched", alerts, "targetChatId", chatId != null ? chatId : "ALL_SUBSCRIBERS")
        ));
    }

    /**
     * Send an instant test cutoff reminder to a specific chatId.
     * POST /api/v1/alerts/test?chatId=123456789
     */
    @PostMapping("/test")
    public ResponseEntity<IpoApiResponse<String>> sendTestAlert(@RequestParam Long chatId) {
        alertService.sendTestAlert(chatId);
        return ResponseEntity.ok(IpoApiResponse.of(
                "Test alert dispatched to chatId " + chatId,
                1,
                "OK"
        ));
    }

    /**
     * List all registered subscribers and preferences.
     * GET /api/v1/alerts/subscribers
     */
    @GetMapping("/subscribers")
    public ResponseEntity<IpoApiResponse<List<Subscriber>>> getSubscribers() {
        List<Subscriber> all = subscriberRepository.getAllSubscribers();
        return ResponseEntity.ok(IpoApiResponse.of(
                "Found " + all.size() + " registered subscriber(s)",
                all.size(),
                all
        ));
    }
}
