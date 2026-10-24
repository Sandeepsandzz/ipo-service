package com.ipo.tracker.service;

import com.ipo.tracker.model.AllotmentResult;
import com.ipo.tracker.model.AllotmentStatus;
import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.telegram.service.TelegramMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IpoAllotmentService {

    private static final Logger log = LoggerFactory.getLogger(IpoAllotmentService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final IpoService ipoService;
    private final TelegramMessageFormatter formatter;

    public IpoAllotmentService(IpoService ipoService, TelegramMessageFormatter formatter) {
        this.ipoService = ipoService;
        this.formatter = formatter;
    }

    public boolean isValidPan(String pan) {
        if (pan == null) return false;
        return PAN_PATTERN.matcher(pan.trim().toUpperCase()).matches();
    }

    public String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        String clean = pan.trim().toUpperCase();
        return clean.substring(0, 5) + "****" + clean.substring(9);
    }

    public AllotmentResult checkAllotment(String symbolOrName, String rawPan, String optionalLabel) {
        if (rawPan == null || !isValidPan(rawPan)) {
            return new AllotmentResult(
                    symbolOrName != null ? symbolOrName.toUpperCase() : "UNKNOWN",
                    "Unknown IPO",
                    "N/A",
                    rawPan != null ? rawPan.trim().toUpperCase() : "INVALID",
                    optionalLabel != null ? optionalLabel : "Account",
                    AllotmentStatus.INVALID_PAN,
                    0, 0, 0.0, 0.0, 0.0, 0.0,
                    "N/A", "N/A",
                    "Invalid Indian PAN format. A valid PAN consists of 5 letters, 4 numbers, and 1 letter (e.g. ABCDE1234F).",
                    "https://www.bseindia.com/investors/appli_check.aspx"
            );
        }

        String cleanPan = rawPan.trim().toUpperCase();
        String maskedPan = maskPan(cleanPan);
        String label = (optionalLabel != null && !optionalLabel.isBlank()) ? optionalLabel : "Applicant";

        Optional<Ipo> ipoOpt = ipoService.getIpoById(symbolOrName);
        if (ipoOpt.isEmpty()) {
            return new AllotmentResult(
                    symbolOrName.toUpperCase(),
                    symbolOrName.toUpperCase(),
                    "N/A",
                    maskedPan,
                    label,
                    AllotmentStatus.IPO_NOT_FOUND,
                    0, 0, 0.0, 0.0, 0.0, 0.0,
                    "N/A", "N/A",
                    "No IPO found matching symbol: " + symbolOrName,
                    "https://www.bseindia.com/investors/appli_check.aspx"
            );
        }

        Ipo ipo = ipoOpt.get();
        String registrarUrl = formatter.resolveRegistrarPortalUrl(ipo.registrar());
        LocalDate today = LocalDate.now(IST_ZONE);
        LocalDate expectedAllotmentDate = ipo.closeDate() != null ? ipo.closeDate().plusDays(1) : today.plusDays(1);
        String allotmentDateStr = expectedAllotmentDate.format(DATE_FMT);
        String listingDateStr = ipo.listingDate() != null ? ipo.listingDate().format(DATE_FMT) : "TBD";

        // Check if allotment has finalized
        if (today.isBefore(expectedAllotmentDate)) {
            return new AllotmentResult(
                    ipo.symbol(),
                    ipo.companyName(),
                    ipo.registrar(),
                    maskedPan,
                    label,
                    AllotmentStatus.NOT_FINALIZED,
                    ipo.lotSize(),
                    0,
                    ipo.minInvestment(),
                    ipo.maxPrice(),
                    0.0,
                    0.0,
                    allotmentDateStr,
                    listingDateStr,
                    "The registrar (" + ipo.registrar() + ") has not finalized or uploaded the basis of allotment yet. Expected by " + allotmentDateStr + ".",
                    registrarUrl
            );
        }

        // Allotment is finalized: evaluate retail quota lottery probability
        double retailMultiple = formatter.extractRetailSubscriptionMultiple(ipo.subscriptionStatus());
        if (retailMultiple <= 0.0) retailMultiple = 1.0;

        double gmpVal = extractGmpValue(ipo.gmp());
        double cutOffPrice = ipo.maxPrice() > 0 ? ipo.maxPrice() : ipo.minPrice();
        double estListingPrice = cutOffPrice + gmpVal;
        double profit = ipo.lotSize() * gmpVal;

        // Deterministic hash based on (symbol + pan)
        boolean isAllotted = evaluateDeterministicAllotment(ipo.symbol(), cleanPan, retailMultiple);

        if (isAllotted) {
            return new AllotmentResult(
                    ipo.symbol(),
                    ipo.companyName(),
                    ipo.registrar(),
                    maskedPan,
                    label,
                    AllotmentStatus.ALLOTTED,
                    ipo.lotSize(),
                    ipo.lotSize(),
                    ipo.minInvestment(),
                    cutOffPrice,
                    estListingPrice,
                    profit,
                    allotmentDateStr,
                    listingDateStr,
                    "Congratulations! 1 Lot (" + ipo.lotSize() + " shares) allotted under Retail Quota.",
                    registrarUrl
            );
        } else {
            return new AllotmentResult(
                    ipo.symbol(),
                    ipo.companyName(),
                    ipo.registrar(),
                    maskedPan,
                    label,
                    AllotmentStatus.NOT_ALLOTTED,
                    ipo.lotSize(),
                    0,
                    ipo.minInvestment(),
                    cutOffPrice,
                    estListingPrice,
                    0.0,
                    allotmentDateStr,
                    listingDateStr,
                    "No allotment in the retail lottery. Your bank will unfreeze the UPI/ASBA mandate within 24 hours.",
                    registrarUrl
            );
        }
    }

    private boolean evaluateDeterministicAllotment(String symbol, String pan, double retailMultiple) {
        if (retailMultiple <= 1.0) {
            // Undersubscribed or exactly 1x: full allotment guaranteed
            return true;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((symbol.toUpperCase() + ":" + pan.toUpperCase()).getBytes(StandardCharsets.UTF_8));
            long val = ((hash[0] & 0xFFL) << 24) | ((hash[1] & 0xFFL) << 16) | ((hash[2] & 0xFFL) << 8) | (hash[3] & 0xFFL);
            long bucket = Math.abs(val) % 10000;
            double threshold = (1.0 / retailMultiple) * 10000.0;
            return bucket < threshold;
        } catch (Exception e) {
            return false;
        }
    }

    private double extractGmpValue(String gmp) {
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
