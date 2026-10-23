package com.ipo.tracker.telegram.service;

import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.IpoType;
import com.ipo.tracker.telegram.dto.InlineKeyboardButton;
import com.ipo.tracker.telegram.dto.InlineKeyboardMarkup;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TelegramMessageFormatter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public String formatWelcome(String name) {
        String displayName = (name != null && !name.isBlank()) ? name : "Investor";
        return """
            👋 <b>Welcome to IPO Tracker Bot, %s!</b>

            I track Mainboard and SME Initial Public Offerings (IPOs) in India in real time.

            <b>Quick Commands:</b>
            🟢 <b>/active</b> - View IPOs open to apply today
            📅 <b>/upcoming</b> - View upcoming IPO announcements
            📊 <b>/closed</b> - View recently closed IPOs
            🔍 <b>/ipo &lt;name&gt;</b> - Search details of an IPO (e.g. <code>/ipo swiggy</code>)
            ❓ <b>/help</b> - Show full command guide

            <i>Use the interactive keyboard and inline buttons below for 1-click access!</i>
            """.formatted(displayName);
    }

    public String formatActiveIpos(List<Ipo> ipos) {
        if (ipos.isEmpty()) {
            return "🟢 <b>No IPOs currently open for bidding today.</b>\n\nCheck back soon or tap 📅 <b>Upcoming IPOs</b> below!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🟢 <b>IPOs Open to Apply Today (").append(ipos.size()).append(" Available):</b>\n\n");

        for (int i = 0; i < ipos.size(); i++) {
            Ipo ipo = ipos.get(i);
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("<b>").append(i + 1).append(". ").append(ipo.companyName()).append("</b> (").append(ipo.issueType()).append(")\n");
            sb.append("🏷 <b>Symbol:</b> <code>").append(ipo.symbol()).append("</code> (").append(ipo.exchange()).append(")\n");
            sb.append("💰 <b>Price Band:</b> ₹").append(ipo.minPrice()).append(" - ₹").append(ipo.maxPrice()).append("\n");
            sb.append("📦 <b>Lot Size:</b> ").append(ipo.lotSize()).append(" shares (Min: ₹").append(String.format("%,.0f", ipo.minInvestment())).append(")\n");
            sb.append("📈 <b>GMP:</b> <code>").append(ipo.gmp()).append("</code>\n");
            sb.append("📊 <b>Subscription:</b> ").append(ipo.subscriptionStatus()).append("\n");
            sb.append("🗓 <b>Closes On:</b> ").append(ipo.closeDate().format(DATE_FMT)).append("\n");
            sb.append("🏦 <b>Registrar:</b> ").append(ipo.registrar()).append("\n\n");
        }

        sb.append("<i>💡 Tap any IPO button below for 1-click Profit Calc, Apply Score & details!</i>");
        return sb.toString();
    }

    public InlineKeyboardMarkup createActiveIposKeyboard(List<Ipo> ipos) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int i = 0; i < Math.min(ipos.size(), 8); i++) {
            Ipo ipo = ipos.get(i);
            currentRow.add(InlineKeyboardButton.callback("🏢 " + ipo.symbol(), "ipo:" + ipo.symbol()));
            if (currentRow.size() == 2) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        rows.add(List.of(
            InlineKeyboardButton.callback("📅 Upcoming IPOs", "nav:upcoming"),
            InlineKeyboardButton.callback("📊 Closed IPOs", "nav:closed")
        ));

        return InlineKeyboardMarkup.of(rows);
    }

    public String formatUpcomingIpos(List<Ipo> ipos) {
        if (ipos.isEmpty()) {
            return "📅 <b>No upcoming IPOs announced at this moment.</b>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📅 <b>Upcoming IPOs (").append(ipos.size()).append(" Expected):</b>\n\n");

        for (int i = 0; i < ipos.size(); i++) {
            Ipo ipo = ipos.get(i);
            sb.append("━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("<b>").append(i + 1).append(". ").append(ipo.companyName()).append("</b>\n");
            sb.append("🏷 <b>Symbol:</b> <code>").append(ipo.symbol()).append("</code>\n");
            sb.append("💰 <b>Price Band:</b> ₹").append(ipo.minPrice()).append(" - ₹").append(ipo.maxPrice()).append("\n");
            sb.append("📊 <b>Issue Size:</b> ").append(ipo.issueSize()).append("\n");
            sb.append("📈 <b>Expected GMP:</b> <code>").append(ipo.gmp()).append("</code>\n");
            sb.append("🗓 <b>Opens:</b> ").append(ipo.openDate().format(DATE_FMT))
              .append(" ➔ <b>Closes:</b> ").append(ipo.closeDate().format(DATE_FMT)).append("\n\n");
        }

        sb.append("<i>💡 Tap any IPO button below for details & calculators:</i>");
        return sb.toString();
    }

    public InlineKeyboardMarkup createUpcomingIposKeyboard(List<Ipo> ipos) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        for (int i = 0; i < Math.min(ipos.size(), 6); i++) {
            Ipo ipo = ipos.get(i);
            currentRow.add(InlineKeyboardButton.callback("📅 " + ipo.symbol(), "ipo:" + ipo.symbol()));
            if (currentRow.size() == 2) {
                rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        rows.add(List.of(
            InlineKeyboardButton.callback("🟢 Active IPOs", "nav:active")
        ));

        return InlineKeyboardMarkup.of(rows);
    }

    public String formatClosedIpos(List<Ipo> ipos) {
        if (ipos.isEmpty()) {
            return "📊 <b>No recently closed IPOs found.</b>";
        }

        StringBuilder sb = new StringBuilder();
        int displayCount = Math.min(ipos.size(), 10);
        sb.append("📊 <b>Recently Closed / Listed IPOs (Top ").append(displayCount).append("):</b>\n\n");

        for (int i = 0; i < displayCount; i++) {
            Ipo ipo = ipos.get(i);
            sb.append("<b>").append(i + 1).append(". ").append(ipo.companyName()).append("</b>\n");
            sb.append("🏷 <code>").append(ipo.symbol()).append("</code> (").append(ipo.issueType()).append(")\n");
            sb.append("💰 <b>Price:</b> ₹").append(ipo.minPrice() == ipo.maxPrice() ? String.format("%.0f", ipo.maxPrice()) : ipo.minPrice() + " - ₹" + ipo.maxPrice()).append("\n");
            sb.append("🗓 <b>Bidding Closed:</b> ").append(ipo.closeDate().format(DATE_FMT)).append("\n");
            if (ipo.listingDate() != null) {
                sb.append("🔔 <b>Listing Date:</b> ").append(ipo.listingDate().format(DATE_FMT)).append("\n");
            }
            sb.append("────────────────────\n");
        }

        sb.append("\n<i>💡 Type <code>/ipo &lt;symbol&gt;</code> for in-depth details of any IPO.</i>");
        return sb.toString();
    }

    public InlineKeyboardMarkup createClosedIposKeyboard() {
        return InlineKeyboardMarkup.of(List.of(
            List.of(
                InlineKeyboardButton.callback("🟢 Active IPOs", "nav:active"),
                InlineKeyboardButton.callback("📅 Upcoming IPOs", "nav:upcoming")
            )
        ));
    }

    public String formatSingleIpo(Ipo ipo) {
        return """
            🏢 <b>%s</b> (%s)
            ━━━━━━━━━━━━━━━━━━━━
            🏷 <b>Symbol:</b> <code>%s</code>
            🏛 <b>Exchange:</b> %s
            📊 <b>Status:</b> <b>%s</b>

            💰 <b>Price Range:</b> ₹%.2f - ₹%.2f
            📦 <b>Lot Size:</b> %d shares
            💵 <b>Min Retail Investment:</b> ₹%,.0f
            💼 <b>Total Issue Size:</b> %s

            📈 <b>Grey Market Premium (GMP):</b> <code>%s</code>
            📊 <b>Live Subscription:</b> %s

            🗓 <b>Bidding Opens:</b> %s
            🗓 <b>Bidding Closes:</b> %s
            🔔 <b>Listing Date:</b> %s
            🏦 <b>Registrar:</b> %s
            """.formatted(
                ipo.companyName(),
                ipo.issueType(),
                ipo.symbol(),
                ipo.exchange(),
                ipo.status(),
                ipo.minPrice(),
                ipo.maxPrice(),
                ipo.lotSize(),
                ipo.minInvestment(),
                ipo.issueSize(),
                ipo.gmp(),
                ipo.subscriptionStatus(),
                ipo.openDate().format(DATE_FMT),
                ipo.closeDate().format(DATE_FMT),
                ipo.listingDate() != null ? ipo.listingDate().format(DATE_FMT) : "TBD",
                ipo.registrar()
        );
    }

    public InlineKeyboardMarkup createSingleIpoKeyboard(Ipo ipo) {
        List<InlineKeyboardButton> row1 = List.of(
            InlineKeyboardButton.callback("🧮 Profit Calc", "calc:" + ipo.symbol()),
            InlineKeyboardButton.callback("🎯 Apply Score", "score:" + ipo.symbol())
        );
        List<InlineKeyboardButton> row2 = List.of(
            InlineKeyboardButton.callback("📊 Subscription", "sub:" + ipo.symbol()),
            InlineKeyboardButton.callback("🏦 Registrar Link", "reg:" + ipo.symbol())
        );
        List<InlineKeyboardButton> row3 = List.of(
            InlineKeyboardButton.callback("🔄 Refresh", "refresh:" + ipo.symbol()),
            InlineKeyboardButton.callback("🟢 Active List", "nav:active")
        );
        return InlineKeyboardMarkup.of(List.of(row1, row2, row3));
    }

    public String formatProfitCalculation(Ipo ipo) {
        double gmpVal = extractGmpValue(ipo.gmp());
        int lotSize = ipo.lotSize() > 0 ? ipo.lotSize() : 1;
        double price = ipo.maxPrice() > 0 ? ipo.maxPrice() : (ipo.minPrice() > 0 ? ipo.minPrice() : 100.0);
        double minInv = lotSize * price;
        double estListingPrice = price + gmpVal;
        double gainPct = price > 0 ? (gmpVal / price) * 100 : 0;

        double profit1Lot = lotSize * gmpVal;
        double profit2Lots = 2 * lotSize * gmpVal;
        double profit5Lots = 5 * lotSize * gmpVal;
        double profit14Lots = 14 * lotSize * gmpVal;

        String prefix = gmpVal >= 0 ? "+" : "";
        String listingDateStr = ipo.listingDate() != null ? ipo.listingDate().format(DATE_FMT) : "listing day";

        String priceFormatted = String.format("%.2f", price);
        String minInvFormatted = String.format("%,.0f", minInv);
        String estListingFormatted = String.format("%.2f", estListingPrice);
        String gainPctFormatted = String.format("%s%.2f%%", prefix, gainPct);

        String profit1Formatted = String.format("%s₹%,.0f", prefix, profit1Lot);
        String profit2Formatted = String.format("%s₹%,.0f", prefix, profit2Lots);
        String profit5Formatted = String.format("%s₹%,.0f", prefix, profit5Lots);
        String profit14Formatted = String.format("%s₹%,.0f", prefix, profit14Lots);

        return """
            🧮 <b>Listing Profit Estimator</b>
            🏢 <b>%s (%s)</b>
            ━━━━━━━━━━━━━━━━━━━━
            📦 <b>Lot Size:</b> %d shares
            💰 <b>Cut-off Price:</b> ₹%s
            💵 <b>1 Lot Investment:</b> ₹%s

            📈 <b>Current GMP:</b> <code>%s</code>
            🎯 <b>Estimated Listing Price:</b> ₹%s (%s)

            ━━━━━━━━━━━━━━━━━━━━
            <b>Estimated Profit / Loss Scenarios:</b>
            • <b>1 Retail Lot (%d shares):</b> <code>%s</code>
            • <b>2 Retail Lots (%d shares):</b> <code>%s</code>
            • <b>5 Lots (%d shares):</b> <code>%s</code>
            • <b>14 Lots (sHNI quota):</b> <code>%s</code>

            <i>⚠️ Note: GMP is an unlisted indicator. Actual listing price on %s depends on stock market momentum.</i>
            """.formatted(
                ipo.companyName(),
                ipo.symbol(),
                lotSize,
                priceFormatted,
                minInvFormatted,
                ipo.gmp(),
                estListingFormatted,
                gainPctFormatted,
                lotSize,
                profit1Formatted,
                2 * lotSize,
                profit2Formatted,
                5 * lotSize,
                profit5Formatted,
                14 * lotSize,
                profit14Formatted,
                listingDateStr
        );
    }

    public InlineKeyboardMarkup createProfitCalcKeyboard(Ipo ipo) {
        return InlineKeyboardMarkup.of(List.of(
            List.of(
                InlineKeyboardButton.callback("🎯 Apply Score", "score:" + ipo.symbol()),
                InlineKeyboardButton.callback("📊 Subscription", "sub:" + ipo.symbol())
            ),
            List.of(
                InlineKeyboardButton.callback("🏦 Registrar", "reg:" + ipo.symbol()),
                InlineKeyboardButton.callback("⬅️ Back to " + ipo.symbol(), "ipo:" + ipo.symbol())
            )
        ));
    }

    public String formatApplyScore(Ipo ipo) {
        double gmpVal = extractGmpValue(ipo.gmp());
        double price = ipo.maxPrice() > 0 ? ipo.maxPrice() : (ipo.minPrice() > 0 ? ipo.minPrice() : 100.0);
        double gmpPct = price > 0 ? (gmpVal / price) * 100 : 0;

        int score = 45; // baseline
        if (gmpPct >= 35) score += 30;
        else if (gmpPct >= 18) score += 22;
        else if (gmpPct >= 5) score += 12;
        else if (gmpPct < 0) score -= 25;

        double subMultiple = extractFirstMultiple(ipo.subscriptionStatus());
        if (subMultiple >= 10.0) score += 22;
        else if (subMultiple >= 3.0) score += 15;
        else if (subMultiple >= 1.0) score += 8;

        if (ipo.issueType() == IpoType.MAINBOARD) score += 8;

        score = Math.max(18, Math.min(score, 97));

        String verdictBadge;
        String verdictDetail;
        if (score >= 80) {
            verdictBadge = "🟢 STRONG APPLY";
            verdictDetail = "Robust institutional & grey market demand. Favorable setup for listing gains.";
        } else if (score >= 65) {
            verdictBadge = "🟡 MODERATE / APPLY FOR LISTING GAINS";
            verdictDetail = "Positive GMP and steady subscription. Good listing gain prospect with controlled risk.";
        } else if (score >= 50) {
            verdictBadge = "🟠 CAUTION / HIGH RISK";
            verdictDetail = "Subdued grey market premium or early subscription numbers. Apply only if confident in fundamentals.";
        } else {
            verdictBadge = "🔴 AVOID / OVERPRICED";
            verdictDetail = "Weak demand and flat/negative GMP. High risk of listing at a discount.";
        }

        return """
            🎯 <b>IPO Smart Score & Rating</b>
            🏢 <b>%s (%s)</b>
            ━━━━━━━━━━━━━━━━━━━━
            🏆 <b>Overall Score:</b> <b>%d / 100</b>
            📊 <b>Verdict:</b> <b>%s</b>

            <b>Factor Breakdown:</b>
            • <b>GMP Trend:</b> <code>%s</code> (%.1f%%)
            • <b>Demand Multiplier:</b> %s
            • <b>Issue Category:</b> %s Issue

            💡 <b>Investor Verdict:</b>
            <i>%s</i>
            """.formatted(
                ipo.companyName(),
                ipo.symbol(),
                score,
                verdictBadge,
                ipo.gmp(),
                gmpPct,
                ipo.subscriptionStatus(),
                ipo.issueType(),
                verdictDetail
        );
    }

    public InlineKeyboardMarkup createApplyScoreKeyboard(Ipo ipo) {
        return InlineKeyboardMarkup.of(List.of(
            List.of(
                InlineKeyboardButton.callback("🧮 Profit Calc", "calc:" + ipo.symbol()),
                InlineKeyboardButton.callback("📊 Subscription", "sub:" + ipo.symbol())
            ),
            List.of(
                InlineKeyboardButton.callback("🏦 Registrar", "reg:" + ipo.symbol()),
                InlineKeyboardButton.callback("⬅️ Back to " + ipo.symbol(), "ipo:" + ipo.symbol())
            )
        ));
    }

    public String formatSubscriptionBreakdown(Ipo ipo) {
        return """
            📊 <b>Live Subscription Breakdown</b>
            🏢 <b>%s (%s)</b>
            ━━━━━━━━━━━━━━━━━━━━
            📈 <b>Total Subscription:</b> %s

            <b>Issue Details:</b>
            • <b>Issue Size:</b> %s
            • <b>Price Band:</b> ₹%.2f - ₹%.2f
            • <b>Min Investment:</b> ₹%,.0f (%d shares)

            🗓 <b>Bidding Dates:</b> %s to %s
            ⏰ <i>Bidding closes sharply at 5:00 PM on %s via UPI/ASBA.</i>
            """.formatted(
                ipo.companyName(),
                ipo.symbol(),
                ipo.subscriptionStatus(),
                ipo.issueSize(),
                ipo.minPrice(),
                ipo.maxPrice(),
                ipo.minInvestment(),
                ipo.lotSize(),
                ipo.openDate().format(DATE_FMT),
                ipo.closeDate().format(DATE_FMT),
                ipo.closeDate().format(DATE_FMT)
        );
    }

    public InlineKeyboardMarkup createSubscriptionKeyboard(Ipo ipo) {
        return InlineKeyboardMarkup.of(List.of(
            List.of(
                InlineKeyboardButton.callback("🧮 Profit Calc", "calc:" + ipo.symbol()),
                InlineKeyboardButton.callback("🎯 Apply Score", "score:" + ipo.symbol())
            ),
            List.of(
                InlineKeyboardButton.callback("⬅️ Back to " + ipo.symbol(), "ipo:" + ipo.symbol()),
                InlineKeyboardButton.callback("🟢 Active List", "nav:active")
            )
        ));
    }

    public String formatRegistrarDetails(Ipo ipo) {
        String listingDateStr = ipo.listingDate() != null ? ipo.listingDate().format(DATE_FMT) : "TBD";
        String closeDateStr = ipo.closeDate() != null ? ipo.closeDate().format(DATE_FMT) : "TBD";
        String allotmentDateStr = ipo.closeDate() != null ? ipo.closeDate().plusDays(1).format(DATE_FMT) : "TBD";

        return """
            🏦 <b>Registrar & Allotment Information</b>
            🏢 <b>%s (%s)</b>
            ━━━━━━━━━━━━━━━━━━━━
            🏛 <b>Official Registrar:</b> <b>%s</b>
            🗓 <b>Bidding Closes:</b> %s
            🔔 <b>Expected Allotment:</b> %s
            🔔 <b>Expected Listing:</b> %s

            ℹ️ <b>How Allotment Verification Works:</b>
            1. Allotment status is uploaded by the registrar 24–48 hours after bidding closes (around <b>%s</b>).
            2. The company name only appears in the registrar portal dropdown <i>after</i> the basis of allotment is officially finalized.
            3. You can verify your status on the <b>official Registrar portal</b> or the universal <b>BSE India portal</b> using your PAN number.
            """.formatted(
                ipo.companyName(),
                ipo.symbol(),
                ipo.registrar(),
                closeDateStr,
                allotmentDateStr,
                listingDateStr,
                allotmentDateStr
        );
    }

    public InlineKeyboardMarkup createRegistrarKeyboard(Ipo ipo) {
        String regUrl = resolveRegistrarPortalUrl(ipo.registrar());
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
            InlineKeyboardButton.url("🌐 Registrar Portal", regUrl),
            InlineKeyboardButton.url("🏛 BSE India Portal", "https://www.bseindia.com/investors/appli_check.aspx")
        ));

        rows.add(List.of(
            InlineKeyboardButton.callback("🧮 Profit Calc", "calc:" + ipo.symbol()),
            InlineKeyboardButton.callback("🎯 Apply Score", "score:" + ipo.symbol())
        ));
        rows.add(List.of(
            InlineKeyboardButton.callback("⬅️ Back to " + ipo.symbol(), "ipo:" + ipo.symbol())
        ));

        return InlineKeyboardMarkup.of(rows);
    }

    public String resolveRegistrarPortalUrl(String registrar) {
        if (registrar == null || registrar.isBlank()) {
            return "https://in.mpms.mufg.com/Initial_Offer/public-issues.html";
        }
        String lower = registrar.toLowerCase();
        if (lower.contains("link intime") || lower.contains("linkintime") || lower.contains("mufg")) {
            return "https://in.mpms.mufg.com/Initial_Offer/public-issues.html";
        } else if (lower.contains("kfin") || lower.contains("karvy")) {
            return "https://ipostatus.kfintech.com/";
        } else if (lower.contains("bigshare")) {
            return "https://ipo.bigshareonline.com/ipo_status.html";
        } else if (lower.contains("cameo")) {
            return "https://ipo.cameoindia.com/";
        } else if (lower.contains("maashitla")) {
            return "https://maashitla.com/allotment-status/";
        } else if (lower.contains("purva")) {
            return "https://purvashare.com/queries/";
        }
        return "https://www.bseindia.com/investors/appli_check.aspx";
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

    private double extractFirstMultiple(String subscription) {
        if (subscription == null || subscription.isBlank()) return 0.0;
        try {
            Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*x").matcher(subscription.toLowerCase());
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    public String formatHelp() {
        return """
            📖 <b>IPO Tracker Bot Help</b>
            ━━━━━━━━━━━━━━━━━━━━
            <b>Commands:</b>
            • <b>/start</b> - Open main menu & keyboard
            • <b>/active</b> or <b>/open</b> - List all IPOs open today with interactive buttons
            • <b>/upcoming</b> - List upcoming IPOs
            • <b>/closed</b> - List recently closed IPOs
            • <b>/ipo &lt;symbol&gt;</b> - Search for a specific IPO (e.g. <code>/ipo swiggy</code>)
            • <b>/help</b> - Show this message

            <b>Interactive Features:</b>
            • <b>🧮 Profit Calc</b>: Calculates estimated listing profit across 1, 2, 5 & 14 lots based on live GMP.
            • <b>🎯 Apply Score</b>: Heuristic rating (0-100) based on GMP, QIB demand & fundamentals.
            • <b>🏦 Registrar Link</b>: Direct 1-click link to official allotment status portals.
            • <b>🏢 Quick Buttons</b>: Tap any company symbol button for instant access!
            """;
    }
}
