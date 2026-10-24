package com.ipo.tracker.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record Subscriber(
    Long chatId,
    String username,
    String firstName,
    boolean morningDigestEnabled,
    boolean closingAlertEnabled,
    boolean gmpSurgeEnabled,
    Set<String> watchlist,
    List<SavedPan> savedPans,
    Instant registeredAt
) {
    public Subscriber {
        if (watchlist == null) {
            watchlist = Collections.emptySet();
        } else {
            watchlist = Collections.unmodifiableSet(new HashSet<>(watchlist));
        }
        if (savedPans == null) {
            savedPans = Collections.emptyList();
        } else {
            savedPans = Collections.unmodifiableList(new ArrayList<>(savedPans));
        }
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }

    // Overload for backwards compatibility
    public Subscriber(
        Long chatId,
        String username,
        String firstName,
        boolean morningDigestEnabled,
        boolean closingAlertEnabled,
        boolean gmpSurgeEnabled,
        Set<String> watchlist,
        Instant registeredAt
    ) {
        this(chatId, username, firstName, morningDigestEnabled, closingAlertEnabled, gmpSurgeEnabled, watchlist, Collections.emptyList(), registeredAt);
    }

    public static Subscriber createDefault(Long chatId, String username, String firstName) {
        return new Subscriber(chatId, username, firstName, true, true, true, Collections.emptySet(), Collections.emptyList(), Instant.now());
    }

    public Subscriber withMorningDigest(boolean enabled) {
        return new Subscriber(chatId, username, firstName, enabled, closingAlertEnabled, gmpSurgeEnabled, watchlist, savedPans, registeredAt);
    }

    public Subscriber withClosingAlert(boolean enabled) {
        return new Subscriber(chatId, username, firstName, morningDigestEnabled, enabled, gmpSurgeEnabled, watchlist, savedPans, registeredAt);
    }

    public Subscriber withGmpSurge(boolean enabled) {
        return new Subscriber(chatId, username, firstName, morningDigestEnabled, closingAlertEnabled, enabled, watchlist, savedPans, registeredAt);
    }

    public Subscriber withWatchlist(Set<String> newWatchlist) {
        return new Subscriber(chatId, username, firstName, morningDigestEnabled, closingAlertEnabled, gmpSurgeEnabled, newWatchlist, savedPans, registeredAt);
    }

    public Subscriber withSavedPans(List<SavedPan> newSavedPans) {
        return new Subscriber(chatId, username, firstName, morningDigestEnabled, closingAlertEnabled, gmpSurgeEnabled, watchlist, newSavedPans, registeredAt);
    }
}
