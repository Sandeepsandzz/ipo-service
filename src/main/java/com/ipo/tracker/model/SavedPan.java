package com.ipo.tracker.model;

import java.time.Instant;

public record SavedPan(
    String pan,
    String label,
    Instant addedAt
) {
    public SavedPan {
        if (pan != null) {
            pan = pan.trim().toUpperCase();
        }
        if (label == null || label.isBlank()) {
            label = "Account";
        }
        if (addedAt == null) {
            addedAt = Instant.now();
        }
    }

    public static SavedPan of(String pan, String label) {
        return new SavedPan(pan, label, Instant.now());
    }

    public String maskedPan() {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 5) + "****" + pan.substring(9);
    }
}
