package com.ipo.tracker.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ipo.tracker.model.Subscriber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class SubscriberRepository {

    private static final Logger log = LoggerFactory.getLogger(SubscriberRepository.class);

    @Value("${ipo.subscribers.file-path:data/subscribers.json}")
    private String configuredFilePath = "data/subscribers.json";

    private Path resolvedFilePath;
    private final Map<Long, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SubscriberRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        resolveStoragePath();
        loadFromFile();
    }

    public synchronized Subscriber registerOrUpdate(Long chatId, String username, String firstName) {
        if (chatId == null) return null;

        Subscriber existing = subscribers.get(chatId);
        if (existing == null) {
            Subscriber newSub = Subscriber.createDefault(chatId, username, firstName);
            subscribers.put(chatId, newSub);
            saveToFile();
            log.info("Registered new alert subscriber: chatId={} username={}", chatId, username);
            return newSub;
        } else {
            // Update username/firstName if changed
            if (!Objects.equals(existing.username(), username) || !Objects.equals(existing.firstName(), firstName)) {
                Subscriber updated = new Subscriber(
                        chatId, username, firstName,
                        existing.morningDigestEnabled(),
                        existing.closingAlertEnabled(),
                        existing.gmpSurgeEnabled(),
                        existing.watchlist(),
                        existing.registeredAt()
                );
                subscribers.put(chatId, updated);
                saveToFile();
                return updated;
            }
            return existing;
        }
    }

    public Optional<Subscriber> getSubscriber(Long chatId) {
        if (chatId == null) return Optional.empty();
        return Optional.ofNullable(subscribers.get(chatId));
    }

    public List<Subscriber> getAllSubscribers() {
        return new ArrayList<>(subscribers.values());
    }

    public synchronized Subscriber updateSettings(Long chatId, Boolean morning, Boolean closing, Boolean gmpSurge) {
        Subscriber sub = subscribers.get(chatId);
        if (sub == null) {
            sub = Subscriber.createDefault(chatId, null, null);
        }

        boolean m = morning != null ? morning : sub.morningDigestEnabled();
        boolean c = closing != null ? closing : sub.closingAlertEnabled();
        boolean g = gmpSurge != null ? gmpSurge : sub.gmpSurgeEnabled();

        Subscriber updated = new Subscriber(
                chatId, sub.username(), sub.firstName(),
                m, c, g, sub.watchlist(), sub.registeredAt()
        );
        subscribers.put(chatId, updated);
        saveToFile();
        return updated;
    }

    public synchronized boolean toggleWatchlist(Long chatId, String rawSymbol) {
        if (chatId == null || rawSymbol == null || rawSymbol.isBlank()) return false;
        String symbol = rawSymbol.trim().toUpperCase();

        Subscriber sub = subscribers.get(chatId);
        if (sub == null) {
            sub = Subscriber.createDefault(chatId, null, null);
        }

        Set<String> newWatchlist = new HashSet<>(sub.watchlist());
        boolean added;
        if (newWatchlist.contains(symbol)) {
            newWatchlist.remove(symbol);
            added = false;
        } else {
            newWatchlist.add(symbol);
            added = true;
        }

        subscribers.put(chatId, sub.withWatchlist(newWatchlist));
        saveToFile();
        return added;
    }

    public boolean isInWatchlist(Long chatId, String rawSymbol) {
        if (chatId == null || rawSymbol == null || rawSymbol.isBlank()) return false;
        Subscriber sub = subscribers.get(chatId);
        if (sub == null) return false;
        return sub.watchlist().contains(rawSymbol.trim().toUpperCase());
    }

    public synchronized boolean unsubscribe(Long chatId) {
        Subscriber sub = subscribers.get(chatId);
        if (sub != null) {
            subscribers.put(chatId, sub.withMorningDigest(false).withClosingAlert(false).withGmpSurge(false));
            saveToFile();
            return true;
        }
        return false;
    }

    public List<Subscriber> getSubscribersForMorningDigest() {
        return subscribers.values().stream()
                .filter(Subscriber::morningDigestEnabled)
                .collect(Collectors.toList());
    }

    public List<Subscriber> getSubscribersForClosingAlert() {
        return subscribers.values().stream()
                .filter(Subscriber::closingAlertEnabled)
                .collect(Collectors.toList());
    }

    public List<Subscriber> getSubscribersForGmpSurge(String symbol) {
        String clean = symbol != null ? symbol.trim().toUpperCase() : "";
        return subscribers.values().stream()
                .filter(s -> s.gmpSurgeEnabled() || s.watchlist().contains(clean))
                .collect(Collectors.toList());
    }

    private synchronized void resolveStoragePath() {
        if (resolvedFilePath != null) return;
        try {
            Path target = Path.of(configuredFilePath != null ? configuredFilePath : "data/subscribers.json");
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            // Test write permission by touching and deleting a hidden test file
            Path testFile = parent != null ? parent.resolve(".perm_test") : Path.of(".perm_test");
            Files.writeString(testFile, "ok");
            Files.deleteIfExists(testFile);
            this.resolvedFilePath = target;
            log.info("Subscriber persistence initialized at: {}", resolvedFilePath.toAbsolutePath());
        } catch (Exception e) {
            Path fallback = Path.of(System.getProperty("java.io.tmpdir", "/tmp"), "ipo-subscribers.json");
            log.warn("Cannot write to configured path '{}' ({}). Falling back to temporary directory: {}",
                    configuredFilePath, e.getMessage(), fallback.toAbsolutePath());
            this.resolvedFilePath = fallback;
        }
    }

    private synchronized void saveToFile() {
        if (resolvedFilePath == null) {
            resolveStoragePath();
        }
        try {
            Path parent = resolvedFilePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(resolvedFilePath.toFile(), subscribers.values());
        } catch (Exception e) {
            Path fallback = Path.of(System.getProperty("java.io.tmpdir", "/tmp"), "ipo-subscribers.json");
            if (!resolvedFilePath.equals(fallback)) {
                log.warn("Failed to write to {}: {}. Retrying with fallback: {}", resolvedFilePath, e.getMessage(), fallback);
                try {
                    resolvedFilePath = fallback;
                    objectMapper.writeValue(resolvedFilePath.toFile(), subscribers.values());
                } catch (Exception ex) {
                    log.error("Failed to write to fallback subscriber file {}: {}", fallback, ex.getMessage());
                }
            } else {
                log.warn("Could not persist subscribers to file {}: {}", resolvedFilePath, e.getMessage());
            }
        }
    }

    private synchronized void loadFromFile() {
        if (resolvedFilePath == null) {
            resolveStoragePath();
        }
        File file = resolvedFilePath.toFile();
        if (!file.exists()) {
            File alt = new File("data/subscribers.json");
            if (alt.exists()) {
                file = alt;
            } else {
                return;
            }
        }

        try {
            List<Subscriber> list = objectMapper.readValue(file, new TypeReference<>() {});
            if (list != null) {
                for (Subscriber s : list) {
                    if (s.chatId() != null) {
                        subscribers.put(s.chatId(), s);
                    }
                }
                log.info("Loaded {} alert subscribers from {}", subscribers.size(), file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Failed to read subscribers from {}: {}", file.getAbsolutePath(), e.getMessage());
        }
    }
}
