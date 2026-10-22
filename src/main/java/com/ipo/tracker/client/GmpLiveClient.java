package com.ipo.tracker.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GmpLiveClient {

    private static final Logger log = LoggerFactory.getLogger(GmpLiveClient.class);
    private static final String GMP_URL = "https://www.investorgain.com/report/live-ipo-gmp/331/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern GMP_PATTERN = Pattern.compile(
            "title=\"([^\"]+)\"[^>]*>([^<]+)</a>.*?data-label=\"GMP\"[^>]*>.*?<b>([^<]+)</b>\\s*\\(([^)]+)\\)",
            Pattern.DOTALL
    );

    private final HttpClient httpClient;
    private final Map<String, String> cachedGmp = new ConcurrentHashMap<>();
    private Instant lastFetchTime = Instant.EPOCH;

    public GmpLiveClient() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public synchronized void refreshGmp() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GMP_URL))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String html = response.body();
                Matcher matcher = GMP_PATTERN.matcher(html);

                Map<String, String> newMap = new HashMap<>();
                while (matcher.find()) {
                    String name = cleanName(matcher.group(2).trim());
                    String gmpVal = matcher.group(3).trim();
                    String pct = matcher.group(4).trim();

                    if (!gmpVal.equals("--")) {
                        newMap.put(name, "+₹" + gmpVal + " (" + pct + ")");
                    } else {
                        newMap.put(name, "₹0 (0.00%)");
                    }
                }

                if (!newMap.isEmpty()) {
                    cachedGmp.clear();
                    cachedGmp.putAll(newMap);
                    lastFetchTime = Instant.now();
                    log.info("Live GMP updated successfully for {} IPOs.", cachedGmp.size());
                }
            } else {
                log.warn("Failed to fetch live GMP: HTTP status {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Error fetching live GMP from market tracker: {}", e.getMessage());
        }
    }

    public String getGmpForCompany(String companyName, String symbol) {
        if (cachedGmp.isEmpty() || Duration.between(lastFetchTime, Instant.now()).toMinutes() >= 5) {
            refreshGmp();
        }

        if (companyName == null || companyName.isBlank()) {
            return "₹0 (0.00%)";
        }

        String target = cleanName(companyName);

        // 1. Direct exact normalized match
        if (cachedGmp.containsKey(target)) {
            return cachedGmp.get(target);
        }

        // 2. Partial containment match (e.g. "Paras Healthcare Limited" matches "paras healthcare")
        for (Map.Entry<String, String> entry : cachedGmp.entrySet()) {
            String key = entry.getKey();
            if (target.contains(key) || key.contains(target)) {
                return entry.getValue();
            }
        }

        // 3. Match by key word components (e.g. "Jio Platforms")
        String[] words = target.split("\\s+");
        if (words.length >= 2) {
            String prefix = (words[0] + " " + words[1]).trim();
            for (Map.Entry<String, String> entry : cachedGmp.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    return entry.getValue();
                }
            }
        }

        return "₹0 (0.00%) - No active premium";
    }

    private String cleanName(String name) {
        return name.toLowerCase()
                .replaceAll("\\b(limited|ltd|industries|technologies|solutions|india|pvt|co|corporation)\\b", "")
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
