package com.ipo.tracker.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.IpoStatus;
import com.ipo.tracker.model.IpoType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NseLiveIpoClient {

    private static final Logger log = LoggerFactory.getLogger(NseLiveIpoClient.class);

    private static final String NSE_HOME_URL = "https://www.nseindia.com";
    private static final String NSE_CURRENT_IPO_URL = "https://www.nseindia.com/api/ipo-current-issue";
    private static final String NSE_UPCOMING_IPO_URL = "https://www.nseindia.com/api/all-upcoming-issues?category=ipo";
    private static final String NSE_PAST_IPO_URL = "https://www.nseindia.com/api/public-past-issues";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d-MMM-yyyy")
            .toFormatter(Locale.ENGLISH);

    private static final DateTimeFormatter MONTH_DAY_FMT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d-MMM")
            .toFormatter(Locale.ENGLISH);

    private static final Pattern PRICE_NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;
    private final GmpLiveClient gmpLiveClient;

    private Instant lastHandshakeTime = Instant.EPOCH;

    // In-memory caches to prevent rate limiting and ensure high responsiveness
    private final List<Ipo> cachedActive = new CopyOnWriteArrayList<>();
    private final List<Ipo> cachedUpcoming = new CopyOnWriteArrayList<>();
    private final List<Ipo> cachedPast = new CopyOnWriteArrayList<>();
    private Instant lastCacheUpdate = Instant.EPOCH;

    public NseLiveIpoClient(ObjectMapper objectMapper, GmpLiveClient gmpLiveClient) {
        this.objectMapper = objectMapper;
        this.gmpLiveClient = gmpLiveClient;
        this.cookieManager = new CookieManager();
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private synchronized void ensureNseSession() {
        // Refresh session handshake if older than 10 minutes or cookie jar is empty
        if (Duration.between(lastHandshakeTime, Instant.now()).toMinutes() > 10 || cookieManager.getCookieStore().getCookies().isEmpty()) {
            try {
                log.info("Performing handshake with NSE India (https://www.nseindia.com)...");
                HttpRequest initRequest = HttpRequest.newBuilder()
                        .uri(URI.create(NSE_HOME_URL))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                httpClient.send(initRequest, HttpResponse.BodyHandlers.discarding());
                lastHandshakeTime = Instant.now();
                log.info("NSE session initialized successfully with {} cookies.", cookieManager.getCookieStore().getCookies().size());
            } catch (Exception e) {
                log.warn("Failed to initialize NSE session handshake: {}", e.getMessage());
            }
        }
    }

    public synchronized void refreshAllLiveIpos() {
        try {
            ensureNseSession();

            List<Ipo> active = fetchActiveFromNse();
            List<Ipo> upcoming = fetchUpcomingFromNse();
            List<Ipo> past = fetchPastFromNse();

            if (active.isEmpty() && upcoming.isEmpty()) {
                log.warn("NSE India returned empty/403 (cloud datacenter IP detected). Activating live market tracker fallback...");
                fetchFromMarketTrackerFallback();
            } else {
                if (!active.isEmpty()) {
                    cachedActive.clear();
                    cachedActive.addAll(active);
                }
                if (!upcoming.isEmpty()) {
                    cachedUpcoming.clear();
                    cachedUpcoming.addAll(upcoming);
                }
                if (!past.isEmpty()) {
                    cachedPast.clear();
                    cachedPast.addAll(past);
                }
            }

            lastCacheUpdate = Instant.now();
            log.info("Live IPO cache refreshed: {} active, {} upcoming, {} past issues.",
                    cachedActive.size(), cachedUpcoming.size(), cachedPast.size());
        } catch (Exception e) {
            log.error("Failed to refresh live IPO data from NSE: {}", e.getMessage(), e);
            fetchFromMarketTrackerFallback();
        }
    }

    public List<Ipo> getLiveActiveIpos() {
        if (cachedActive.isEmpty() || Duration.between(lastCacheUpdate, Instant.now()).toMinutes() >= 5) {
            refreshAllLiveIpos();
        }
        return new ArrayList<>(cachedActive);
    }

    public List<Ipo> getLiveUpcomingIpos() {
        if (cachedUpcoming.isEmpty() || Duration.between(lastCacheUpdate, Instant.now()).toMinutes() >= 5) {
            refreshAllLiveIpos();
        }
        return new ArrayList<>(cachedUpcoming);
    }

    public List<Ipo> getLivePastIpos() {
        if (cachedPast.isEmpty() || Duration.between(lastCacheUpdate, Instant.now()).toMinutes() >= 5) {
            refreshAllLiveIpos();
        }
        return new ArrayList<>(cachedPast);
    }

    private List<Ipo> fetchActiveFromNse() {
        String json = executeGet(NSE_CURRENT_IPO_URL);
        if (json == null || json.isBlank()) return Collections.emptyList();

        List<Ipo> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    Ipo ipo = parseNseItem(item, IpoStatus.OPEN);
                    if (ipo != null) {
                        result.add(ipo);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing NSE current issue JSON: {}", e.getMessage());
        }
        return result;
    }

    private List<Ipo> fetchUpcomingFromNse() {
        String json = executeGet(NSE_UPCOMING_IPO_URL);
        if (json == null || json.isBlank()) return Collections.emptyList();

        List<Ipo> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.isArray() ? root : root.path("value");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String statusStr = item.path("status").asText("");
                    if ("Forthcoming".equalsIgnoreCase(statusStr) || "Upcoming".equalsIgnoreCase(statusStr)) {
                        Ipo ipo = parseNseItem(item, IpoStatus.UPCOMING);
                        if (ipo != null) {
                            result.add(ipo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing NSE upcoming issue JSON: {}", e.getMessage());
        }
        return result;
    }

    private List<Ipo> fetchPastFromNse() {
        String json = executeGet(NSE_PAST_IPO_URL);
        if (json == null || json.isBlank()) return Collections.emptyList();

        List<Ipo> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.isArray() ? root : root.path("value");
            if (items.isArray()) {
                int count = 0;
                for (JsonNode item : items) {
                    Ipo ipo = parseNseItem(item, IpoStatus.CLOSED);
                    if (ipo != null) {
                        result.add(ipo);
                        count++;
                        if (count >= 15) break; // Limit to 15 latest closed IPOs
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing NSE past issue JSON: {}", e.getMessage());
        }
        return result;
    }

    private Ipo parseNseItem(JsonNode node, IpoStatus defaultStatus) {
        try {
            String companyName = node.hasNonNull("companyName") ? node.path("companyName").asText() : node.path("company").asText("");
            if (companyName.isBlank()) return null;

            String symbol = node.path("symbol").asText("");
            if (symbol.isBlank()) {
                symbol = companyName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (symbol.length() > 10) symbol = symbol.substring(0, 10);
            }

            String id = symbol.toLowerCase().replaceAll("[^a-z0-9]", "-");

            String priceStr = node.hasNonNull("issuePrice") ? node.path("issuePrice").asText("") : node.path("priceRange").asText("");
            double[] priceBand = extractPriceBand(priceStr);
            double minPrice = priceBand[0];
            double maxPrice = priceBand[1];

            // SEBI standard retail minimum investment is ~₹14,500
            int lotSize = maxPrice > 0 ? Math.max(1, (int) Math.round(14500.0 / maxPrice)) : 100;
            double minInvestment = lotSize * maxPrice;

            String startDateStr = node.hasNonNull("issueStartDate") ? node.path("issueStartDate").asText("") : node.path("ipoStartDate").asText("");
            String endDateStr = node.hasNonNull("issueEndDate") ? node.path("issueEndDate").asText("") : node.path("ipoEndDate").asText("");
            String listDateStr = node.path("listingDate").asText("");

            LocalDate openDate = parseNseDate(startDateStr, LocalDate.now());
            LocalDate closeDate = parseNseDate(endDateStr, openDate.plusDays(3));
            LocalDate listingDate = parseNseDate(listDateStr, closeDate.plusDays(4));

            String securityType = node.hasNonNull("series") ? node.path("series").asText("EQ") : node.path("securityType").asText("EQ");
            IpoType issueType = "SME".equalsIgnoreCase(securityType) ? IpoType.SME : IpoType.MAINBOARD;

            String issueSize = formatIssueSize(node.path("issueSize").asText(""));

            String subStatus = "N/A";
            if (node.hasNonNull("noOfTime")) {
                try {
                    double times = Double.parseDouble(node.path("noOfTime").asText());
                    subStatus = String.format("%.2fx overall", times);
                } catch (Exception ignored) {}
            }

            String gmp = gmpLiveClient.getGmpForCompany(companyName, symbol);

            return new Ipo(
                    id,
                    companyName,
                    symbol,
                    "NSE, BSE",
                    issueType,
                    openDate,
                    closeDate,
                    listingDate,
                    minPrice,
                    maxPrice,
                    lotSize,
                    minInvestment,
                    issueSize,
                    defaultStatus,
                    gmp,
                    subStatus,
                    "Link Intime / KFintech"
            );
        } catch (Exception e) {
            log.debug("Error mapping individual NSE item: {}", e.getMessage());
            return null;
        }
    }

    private double[] extractPriceBand(String text) {
        if (text == null || text.isBlank()) return new double[]{100.0, 100.0};
        Matcher m = PRICE_NUMBER_PATTERN.matcher(text.replace(",", ""));
        List<Double> numbers = new ArrayList<>();
        while (m.find()) {
            try {
                numbers.add(Double.parseDouble(m.group(1)));
            } catch (Exception ignored) {}
        }
        if (numbers.isEmpty()) {
            return new double[]{100.0, 100.0};
        } else if (numbers.size() == 1) {
            return new double[]{numbers.get(0), numbers.get(0)};
        } else {
            return new double[]{numbers.get(0), numbers.get(numbers.size() - 1)};
        }
    }

    private LocalDate parseNseDate(String dateStr, LocalDate defaultDate) {
        if (dateStr == null || dateStr.isBlank() || dateStr.equals("-")) return defaultDate;
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            return defaultDate;
        }
    }

    private String formatIssueSize(String raw) {
        if (raw == null || raw.isBlank()) return "₹1,000+ Cr";
        try {
            double shares = Double.parseDouble(raw);
            if (shares >= 10_000_000) {
                return String.format("%.2f Cr shares", shares / 10_000_000.0);
            } else if (shares >= 100_000) {
                return String.format("%.2f Lakh shares", shares / 100_000.0);
            }
            return String.format("%,.0f shares", shares);
        } catch (Exception e) {
            return raw;
        }
    }

    private void fetchFromMarketTrackerFallback() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.investorgain.com/report/live-ipo-gmp/331/"))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Market tracker fallback returned status {}", resp.statusCode());
                return;
            }

            String html = resp.body();
            String[] rows = html.split("<tr[^>]*>");
            LocalDate today = LocalDate.now();

            List<Ipo> fallbackActive = new ArrayList<>();
            List<Ipo> fallbackUpcoming = new ArrayList<>();
            List<Ipo> fallbackPast = new ArrayList<>();

            Pattern namePat = Pattern.compile("title=\"([^\"]+)\"[^>]*>([^<]+)</a>");
            Pattern badgePat = Pattern.compile("<span class=\"badge[^\"]*\">([^<]+)</span>");
            Pattern gmpPat = Pattern.compile("data-label=\"GMP\"[^>]*>.*?<b>([^<]+)</b>(?:\\s*\\(([^)]+)\\))?", Pattern.DOTALL);
            Pattern subPat = Pattern.compile("data-label=\"Sub\"[^>]*>.*?<div class=\"mono-num\">([^<]*)</div>", Pattern.DOTALL);
            Pattern pricePat = Pattern.compile("data-label=\"Price \\(₹\\)\"[^>]*>.*?<div class=\"mono-num\">([^<]*)</div>", Pattern.DOTALL);
            Pattern sizePat = Pattern.compile("data-label=\"IPO Size\"[^>]*>.*?<div class=\"mono-num\">([^<]*)</div>", Pattern.DOTALL);
            Pattern lotPat = Pattern.compile("data-label=\"Lot\"[^>]*>.*?<div class=\"mono-num\">([^<]*)</div>", Pattern.DOTALL);
            Pattern openPat = Pattern.compile("data-label=\"Open\"[^>]*>.*?<div class=\"mono-num\">([^<\\n]+)", Pattern.DOTALL);
            Pattern closePat = Pattern.compile("data-label=\"Close\"[^>]*>.*?<div class=\"mono-num\">([^<\\n]+)", Pattern.DOTALL);
            Pattern listPat = Pattern.compile("data-label=\"Listing\"[^>]*>.*?<div class=\"mono-num\">([^<\\n]+)", Pattern.DOTALL);

            for (String row : rows) {
                Matcher nm = namePat.matcher(row);
                if (!nm.find()) continue;

                String companyName = nm.group(2).trim();
                if (companyName.equalsIgnoreCase("GMP") || companyName.equalsIgnoreCase("Name")) continue;

                String badge = "";
                Matcher bm = badgePat.matcher(row);
                if (bm.find()) badge = bm.group(1).trim();
                IpoType issueType = badge.equalsIgnoreCase("SME") ? IpoType.SME : IpoType.MAINBOARD;

                String gmp = "₹0 (0.00%)";
                Matcher gm = gmpPat.matcher(row);
                if (gm.find()) {
                    String val = gm.group(1).trim();
                    String pct = gm.group(2) != null ? gm.group(2).trim() : "-";
                    if (!val.equals("--")) {
                        gmp = "+₹" + val + " (" + pct + ")";
                    }
                }

                String subStatus = "N/A";
                Matcher sm = subPat.matcher(row);
                if (sm.find() && !sm.group(1).isBlank() && !sm.group(1).equals("-")) {
                    subStatus = sm.group(1).trim() + " overall";
                }

                String priceStr = "100";
                Matcher pm = pricePat.matcher(row);
                if (pm.find()) priceStr = pm.group(1).trim();
                double[] prices = extractPriceBand(priceStr);
                double minPrice = prices[0];
                double maxPrice = prices[1];

                int lotSize = 100;
                Matcher lm = lotPat.matcher(row);
                if (lm.find() && !lm.group(1).isBlank() && !lm.group(1).equals("-")) {
                    try {
                        lotSize = Integer.parseInt(lm.group(1).trim());
                    } catch (Exception ignored) {}
                } else if (maxPrice > 0) {
                    lotSize = Math.max(1, (int) Math.round(14500.0 / maxPrice));
                }
                double minInvestment = lotSize * maxPrice;

                String issueSize = "₹100+ Cr";
                Matcher szm = sizePat.matcher(row);
                if (szm.find() && !szm.group(1).isBlank()) {
                    issueSize = szm.group(1).replaceAll("&#8377;", "₹").trim();
                }

                LocalDate openDate = parseShortDate(row, openPat, today);
                LocalDate closeDate = parseShortDate(row, closePat, today);
                LocalDate listingDate = parseShortDate(row, listPat, closeDate != null ? closeDate.plusDays(4) : today.plusDays(7));

                if (openDate == null || closeDate == null) continue;

                String symbol = companyName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (symbol.length() > 10) symbol = symbol.substring(0, 10);
                String id = symbol.toLowerCase();

                IpoStatus status;
                if (!today.isBefore(openDate) && !today.isAfter(closeDate)) {
                    status = IpoStatus.OPEN;
                } else if (today.isBefore(openDate)) {
                    status = IpoStatus.UPCOMING;
                } else {
                    status = IpoStatus.CLOSED;
                }

                Ipo ipo = new Ipo(
                        id,
                        companyName,
                        symbol,
                        "NSE, BSE",
                        issueType,
                        openDate,
                        closeDate,
                        listingDate,
                        minPrice,
                        maxPrice,
                        lotSize,
                        minInvestment,
                        issueSize,
                        status,
                        gmp,
                        subStatus,
                        "Link Intime / KFintech"
                );

                if (status == IpoStatus.OPEN) {
                    fallbackActive.add(ipo);
                } else if (status == IpoStatus.UPCOMING) {
                    fallbackUpcoming.add(ipo);
                } else if (fallbackPast.size() < 15) {
                    fallbackPast.add(ipo);
                }
            }

            if (!fallbackActive.isEmpty() || !fallbackUpcoming.isEmpty()) {
                cachedActive.clear();
                cachedActive.addAll(fallbackActive);

                cachedUpcoming.clear();
                cachedUpcoming.addAll(fallbackUpcoming);

                cachedPast.clear();
                cachedPast.addAll(fallbackPast);

                lastCacheUpdate = Instant.now();
                log.info("Market tracker fallback loaded: {} active, {} upcoming, {} past IPOs.",
                        cachedActive.size(), cachedUpcoming.size(), cachedPast.size());
            }
        } catch (Exception e) {
            log.error("Failed to load live market tracker fallback: {}", e.getMessage(), e);
        }
    }

    private LocalDate parseShortDate(String row, Pattern pat, LocalDate today) {
        Matcher m = pat.matcher(row);
        if (m.find()) {
            String str = m.group(1).replaceAll("<[^>]*>", "").trim();
            if (!str.isBlank() && !str.equals("-")) {
                try {
                    MonthDay md = MonthDay.parse(str, MONTH_DAY_FMT);
                    return md.atYear(today.getYear());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String executeGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://www.nseindia.com/market-data/all-upcoming-issues-ipo")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Received {} from NSE, re-authenticating session...", response.statusCode());
                lastHandshakeTime = Instant.EPOCH;
                ensureNseSession();
            }
        } catch (Exception e) {
            log.warn("Failed GET from {}: {}", url, e.getMessage());
        }
        return null;
    }
}
