package com.ipo.tracker.service;

import com.ipo.tracker.model.AllotmentResult;
import com.ipo.tracker.model.AllotmentStatus;
import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.telegram.service.TelegramMessageFormatter;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class IpoAllotmentService {
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");
    private final IpoService ipoService;
    private final TelegramMessageFormatter formatter;

    public IpoAllotmentService(IpoService ipoService, TelegramMessageFormatter formatter) {
        this.ipoService = ipoService;
        this.formatter = formatter;
    }

    public boolean isValidPan(String pan) {
        return pan != null && PAN_PATTERN.matcher(pan.trim().toUpperCase()).matches();
    }

    public String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        String clean = pan.trim().toUpperCase();
        return clean.substring(0, 5) + "****" + clean.substring(9);
    }

    public AllotmentResult checkAllotment(String symbolOrName, String rawPan, String optionalLabel) {
        if (!isValidPan(rawPan)) {
            return new AllotmentResult(
                    symbolOrName != null ? symbolOrName.toUpperCase() : "UNKNOWN", "Unknown IPO", "N/A",
                    rawPan != null && rawPan.length() >= 10 ? maskPan(rawPan) : "INVALID",
                    optionalLabel != null ? optionalLabel : "Account", AllotmentStatus.INVALID_PAN,
                    0, 0, 0.0, 0.0, 0.0, 0.0, "N/A", "N/A",
                    "Invalid Indian PAN format. A valid PAN consists of 5 letters, 4 numbers, and 1 letter (e.g. ABCDE1234F).",
                    "https://www.bseindia.com/investors/appli_check.aspx");
        }

        String maskedPan = maskPan(rawPan.trim().toUpperCase());
        String label = optionalLabel != null && !optionalLabel.isBlank() ? optionalLabel : "Applicant";
        Optional<Ipo> ipoOpt = ipoService.getIpoById(symbolOrName);
        if (ipoOpt.isEmpty()) {
            String symbol = symbolOrName == null ? "UNKNOWN" : symbolOrName.toUpperCase();
            return new AllotmentResult(symbol, symbol, "N/A", maskedPan, label, AllotmentStatus.IPO_NOT_FOUND,
                    0, 0, 0.0, 0.0, 0.0, 0.0, "N/A", "N/A",
                    "No IPO found matching symbol: " + symbol,
                    "https://www.bseindia.com/investors/appli_check.aspx");
        }

        Ipo ipo = ipoOpt.get();
        return new AllotmentResult(ipo.symbol(), ipo.companyName(), ipo.registrar(), maskedPan, label,
                AllotmentStatus.UNAVAILABLE, ipo.lotSize(), 0, ipo.minInvestment(),
                0.0, 0.0, 0.0, "N/A", "N/A",
                "This service does not have access to registrar allotment records. Verify your application directly on the official registrar portal.",
                formatter.resolveRegistrarPortalUrl(ipo.registrar()));
    }
}
