package com.ipo.tracker.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record Subscriber(
    Long chatId,
    String username,
    String firstName,
    boolean morningDigestEnabled,
    boolean closingAlertEnabled,
    boolean gmpSurgeEnabled,
    Set<String> watchlist,
    Instant registeredAt
) {
    public Subscriber {
        if (watchlist == null) {
            watchlist = Collections.emptySet();
        } else {
            watchlist = Collections.unmodifiableSet(new HashSet<>(watchlist));
        }
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }

    public static Subscriber createDefault(Long chatId, String username, String firstName) {
        return new Subscriber(chatId, username, firstName, true, true, true, Collections.emptySet(), Instant.now());
    }

    public Subscriber withMorningDigest(boolean enabled) {
        return new Subscriber(chatId, username, firstName, enabled, closingAlertEnabled, gmpSurgeEnabled, watchlist, registeredAt);
    }

    public Subscriber withClosingAlert(boolean enabled) {
        return new Subscriber(chatId, username, firstName, morningDigestEnabled, enabled, gmpSurgeEnabled, watchlist, registeredAt);
    }

    public Subscriber withGmpSurge(boolean enabled) {
        return new Subscriber(chatId, username, firstName, morningDigestEnabled, closingAlertEnabled, enabled, watchlist, registeredAt);
    }

    public Subscriber withWatchlist(Set<String> newWatchlist) {
        return new Subscriber(chatId, username, firstName, morningDigestEnabled, closingAlertEnabled, gmpSurgeEnabled, newWatchlist, registeredAt);
    }
}
