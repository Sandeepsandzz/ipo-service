package com.ipo.tracker.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ipo.tracker.model.SavedPan;
import com.ipo.tracker.model.Subscriber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class SubscriberRepository {

    private static final Logger log = LoggerFactory.getLogger(SubscriberRepository.class);

    @Value("${ipo.subscribers.file-path:data/subscribers.json}")
    private String configuredFilePath = "data/subscribers.json";

    @Value("${ipo.pan-encryption-key:}")
    private String panEncryptionKey = "";

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

    public synchronized boolean addSavedPan(Long chatId, String rawPan, String label) {
        if (chatId == null || rawPan == null || rawPan.isBlank()) return false;
        String cleanPan = rawPan.trim().toUpperCase();
        if (!cleanPan.matches("^[A-Z]{5}[0-9]{4}[A-Z]{1}$")) {
            return false;
        }

        Subscriber sub = subscribers.get(chatId);
        if (sub == null) {
            sub = Subscriber.createDefault(chatId, null, null);
        }

        List<SavedPan> current = new ArrayList<>(sub.savedPans());
        current.removeIf(p -> p.pan().equalsIgnoreCase(cleanPan));
        current.add(SavedPan.of(cleanPan, label != null && !label.isBlank() ? label : "Account " + (current.size() + 1)));

        subscribers.put(chatId, sub.withSavedPans(current));
        saveToFile();
        return true;
    }

    public synchronized boolean removeSavedPan(Long chatId, String rawPan) {
        if (chatId == null || rawPan == null || rawPan.isBlank()) return false;
        String cleanPan = rawPan.trim().toUpperCase();

        Subscriber sub = subscribers.get(chatId);
        if (sub == null) return false;

        List<SavedPan> current = new ArrayList<>(sub.savedPans());
        boolean removed = current.removeIf(p -> p.pan().equalsIgnoreCase(cleanPan));
        if (removed) {
            subscribers.put(chatId, sub.withSavedPans(current));
            saveToFile();
        }
        return removed;
    }

    public List<SavedPan> getSavedPans(Long chatId) {
        if (chatId == null) return Collections.emptyList();
        Subscriber sub = subscribers.get(chatId);
        if (sub == null) return Collections.emptyList();
        return sub.savedPans();
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
            Files.writeString(resolvedFilePath, encrypt(objectMapper.writeValueAsBytes(subscribers.values())));
        } catch (Exception e) {
            Path fallback = Path.of(System.getProperty("java.io.tmpdir", "/tmp"), "ipo-subscribers.json");
            if (!resolvedFilePath.equals(fallback)) {
                log.warn("Failed to write to {}: {}. Retrying with fallback: {}", resolvedFilePath, e.getMessage(), fallback);
                try {
                    resolvedFilePath = fallback;
                    Files.writeString(resolvedFilePath, encrypt(objectMapper.writeValueAsBytes(subscribers.values())));
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
            String stored = Files.readString(file.toPath());
            byte[] json = stored.startsWith("ENC1:") ? decrypt(stored) : stored.getBytes(StandardCharsets.UTF_8);
            List<Subscriber> list = objectMapper.readValue(json, new TypeReference<>() {});
            if (list != null) {
                for (Subscriber s : list) {
                    if (s.chatId() != null) {
                        subscribers.put(s.chatId(), s);
                    }
                }
                log.info("Loaded {} alert subscribers from {}", subscribers.size(), file.getAbsolutePath());
                if (!stored.startsWith("ENC1:")) saveToFile();
            }
        } catch (Exception e) {
            log.warn("Failed to read subscribers from {}: {}", file.getAbsolutePath(), e.getMessage());
        }
    }

    private String encrypt(byte[] plain) throws Exception {
        if (panEncryptionKey == null || panEncryptionKey.length() < 16)
            throw new IllegalStateException("Set IPO_PAN_ENCRYPTION_KEY (at least 16 characters) to persist subscriber data securely.");
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(plain);
        return "ENC1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
    }

    private byte[] decrypt(String encoded) throws Exception {
        if (panEncryptionKey == null || panEncryptionKey.length() < 16)
            throw new IllegalStateException("IPO_PAN_ENCRYPTION_KEY is required to read encrypted subscriber data.");
        byte[] payload = Base64.getDecoder().decode(encoded.substring(5));
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] nonce = new byte[12]; buffer.get(nonce);
        byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(encrypted);
    }

    private SecretKeySpec key() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(panEncryptionKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
