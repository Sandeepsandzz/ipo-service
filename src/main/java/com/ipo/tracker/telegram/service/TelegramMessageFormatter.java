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
            🎲 <b>/odds &lt;name&gt; [pans]</b> - Multi-PAN allotment probability calculator (e.g. <code>/odds swiggy 3</code>)
            🧮 <b>/calc &lt;name&gt; [lots]</b> - Listing profit calculator (e.g. <code>/calc swiggy 2</code>)
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

        sb.append("<i>💡 Tap any IPO button below for 1-click Profit Calc, Multi-PAN Odds & details!</i>");
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
            InlineKeyboardButton.callback("🎲 Multi-PAN Odds", "odds:" + ipo.symbol())
        );
        List<InlineKeyboardButton> row2 = List.of(
            InlineKeyboardButton.callback("🎯 Apply Score", "score:" + ipo.symbol()),
            InlineKeyboardButton.callback("📊 Subscription", "sub:" + ipo.symbol())
        );
        List<InlineKeyboardButton> row3 = List.of(
            InlineKeyboardButton.callback("🏦 Registrar Link", "reg:" + ipo.symbol()),
            InlineKeyboardButton.callback("🔄 Refresh", "refresh:" + ipo.symbol())
        );
        List<InlineKeyboardButton> row4 = List.of(
            InlineKeyboardButton.callback("🟢 Active List", "nav:active")
        );
        return InlineKeyboardMarkup.of(List.of(row1, row2, row3, row4));
    }

    public String formatProfitCalculation(Ipo ipo) {
        return formatProfitCalculation(ipo, 0);
    }

    public String formatProfitCalculation(Ipo ipo, int customLots) {
        double gmpVal = extractGmpValue(ipo.gmp());
        int lotSize = ipo.lotSize() > 0 ? ipo.lotSize() : 1;
        double price = ipo.maxPrice() > 0 ? ipo.maxPrice() : (ipo.minPrice() > 0 ? ipo.minPrice() : 100.0);
        double minInv = lotSize * price;
        double estListingPrice = price + gmpVal;
        double gainPct = price > 0 ? (gmpVal / price) * 100 : 0;

        String prefix = gmpVal >= 0 ? "+" : "";
        String listingDateStr = ipo.listingDate() != null ? ipo.listingDate().format(DATE_FMT) : "listing day";

        if (customLots > 0) {
            int totalShares = customLots * lotSize;
            double totalOutlay = customLots * minInv;
            double totalProfit = totalShares * gmpVal;
            String category = customLots >= 14 ? "sHNI Quota (₹2L - ₹10L)" : "Retail Quota";

            return """
                🧮 <b>Listing Profit Calculation: %d Lot(s)</b>
                🏢 <b>%s (%s)</b>
                ━━━━━━━━━━━━━━━━━━━━
                🏷 <b>Application Type:</b> %s
                📦 <b>Total Shares:</b> %d shares (%d lots × %d)
                💰 <b>Cut-off Price:</b> ₹%.2f
                💵 <b>Total Capital Required:</b> ₹%,.0f

                📈 <b>Current GMP:</b> <code>%s</code>
                🎯 <b>Estimated Listing Price:</b> ₹%.2f (%s%.2f%%)

                ━━━━━━━━━━━━━━━━━━━━
                💰 <b>Estimated Net Listing Profit:</b> <b>%s₹%,.0f</b>

                <i>⚠️ Note: Listing gains depend on market momentum on %s.</i>
                """.formatted(
                    customLots,
                    ipo.companyName(),
                    ipo.symbol(),
                    category,
                    totalShares,
                    customLots,
                    lotSize,
                    price,
                    totalOutlay,
                    ipo.gmp(),
                    estListingPrice,
                    prefix,
                    gainPct,
                    prefix,
                    totalProfit,
                    listingDateStr
            );
        }

        double profit1Lot = lotSize * gmpVal;
        double profit2Lots = 2 * lotSize * gmpVal;
        double profit5Lots = 5 * lotSize * gmpVal;
        double profit14Lots = 14 * lotSize * gmpVal;

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

            <i>👇 Tap a button below to simulate custom lot sizes:</i>
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
                profit14Formatted
        );
    }

    public InlineKeyboardMarkup createProfitCalcKeyboard(Ipo ipo) {
        return createProfitCalcKeyboard(ipo, 0);
    }

    public InlineKeyboardMarkup createProfitCalcKeyboard(Ipo ipo, int currentLots) {
        return InlineKeyboardMarkup.of(List.of(
            List.of(
                InlineKeyboardButton.callback(currentLots == 1 ? "✅ 1 Lot" : "1 Lot", "calc:" + ipo.symbol() + ":1"),
                InlineKeyboardButton.callback(currentLots == 2 ? "✅ 2 Lots" : "2 Lots", "calc:" + ipo.symbol() + ":2"),
                InlineKeyboardButton.callback(currentLots == 5 ? "✅ 5 Lots" : "5 Lots", "calc:" + ipo.symbol() + ":5"),
                InlineKeyboardButton.callback(currentLots == 14 ? "✅ 14 Lots (HNI)" : "14 Lots (HNI)", "calc:" + ipo.symbol() + ":14")
            ),
            List.of(
                InlineKeyboardButton.callback("🎲 Multi-PAN Odds", "odds:" + ipo.symbol()),
                InlineKeyboardButton.callback("🎯 Apply Score", "score:" + ipo.symbol())
            ),
            List.of(
                InlineKeyboardButton.callback("🏦 Registrar", "reg:" + ipo.symbol()),
                InlineKeyboardButton.callback("⬅️ Back to " + ipo.symbol(), "ipo:" + ipo.symbol())
            )
        ));
    }

    public String formatMultiPanOdds(Ipo ipo, int specificPanCount) {
        double retailSub = extractRetailSubscriptionMultiple(ipo.subscriptionStatus());
        double gmpVal = extractGmpValue(ipo.gmp());
        int lotSize = ipo.lotSize() > 0 ? ipo.lotSize() : 1;
        double price = ipo.maxPrice() > 0 ? ipo.maxPrice() : (ipo.minPrice() > 0 ? ipo.minPrice() : 100.0);
        double minInv = lotSize * price;
        double profitPerLot = lotSize * gmpVal;
        String profitPerLotFormatted = String.format("%s₹%,.0f", gmpVal >= 0 ? "+" : "", profitPerLot);

        if (specificPanCount > 0) {
            int n = specificPanCount;
            double p1 = Math.min(1.0, 1.0 / Math.max(0.01, retailSub));
            double pWin = (1.0 - Math.pow(1.0 - p1, n)) * 100.0;
            pWin = Math.min(100.0, Math.max(0.1, pWin));
            double totalCapital = n * minInv;
            double expectedProfit = (n * p1) * profitPerLot;

            String oddsDesc = pWin >= 99.0 ? "Almost Guaranteed (>99%)" : String.format("1 in %.1f chance", 100.0 / pWin);

            return """
                🎲 <b>Allotment Odds Simulation: %d Family PANs</b>
                🏢 <b>%s (%s)</b>
                ━━━━━━━━━━━━━━━━━━━━
                📊 <b>Retail Demand:</b> <code>%.2fx</code>
                💵 <b>Total Capital Blocked:</b> ₹%,.0f (%d lots × ₹%,.0f)
                📈 <b>Current GMP:</b> <code>%s</code>
                💰 <b>Listing Profit per Winning Lot:</b> %s

                ━━━━━━━━━━━━━━━━━━━━
                🎯 <b>Allotment Probability:</b>
                • <b>Chance of at least 1 Allotment:</b> <b>%.1f%%</b> (<b>%s</b>)
                • <b>Chance of Missing Allotment (0 lots):</b> <b>%.1f%%</b>

                <b>Outlay vs Return Scenarios:</b>
                • If <b>1 Lot</b> wins: %s (Net return: %.1f%% on capital)
                • If <b>2 Lots</b> win: %s
                • <b>Statistically Expected Profit:</b> <b>%s₹%,.0f</b>

                💡 <b>SEBI Application Rule:</b>
                <i>Unallotted application funds are automatically unblocked/refunded via your UPI mandate within 24 hours of the allotment date.</i>
                """.formatted(
                    n,
                    ipo.companyName(),
                    ipo.symbol(),
                    retailSub,
                    totalCapital,
                    n,
                    minInv,
                    ipo.gmp(),
                    profitPerLotFormatted,
                    pWin,
                    oddsDesc,
                    Math.max(0.0, 100.0 - pWin),
                    profitPerLotFormatted,
                    totalCapital > 0 ? (profitPerLot / totalCapital) * 100.0 : 0.0,
                    String.format("%s₹%,.0f", gmpVal >= 0 ? "+" : "", 2 * profitPerLot),
                    gmpVal >= 0 ? "+" : "",
                    expectedProfit
            );
        } else {
            double p1 = Math.min(1.0, 1.0 / Math.max(0.01, retailSub));
            double p1Pct = p1 * 100.0;
            double p2Pct = (1.0 - Math.pow(1.0 - p1, 2)) * 100.0;
            double p3Pct = (1.0 - Math.pow(1.0 - p1, 3)) * 100.0;
            double p5Pct = (1.0 - Math.pow(1.0 - p1, 5)) * 100.0;
            double p7Pct = (1.0 - Math.pow(1.0 - p1, 7)) * 100.0;

            return """
                🎲 <b>Multi-PAN Allotment Odds Calculator</b>
                🏢 <b>%s (%s)</b>
                ━━━━━━━━━━━━━━━━━━━━
                📊 <b>Retail Demand:</b> <code>%.2fx</code>
                📦 <b>1 Retail Lot:</b> %d shares (₹%,.0f)
                📈 <b>Current GMP:</b> <code>%s</code>
                💰 <b>Profit per Winning Lot:</b> %s

                ━━━━━━━━━━━━━━━━━━━━
                👨‍👩‍👧‍👦 <b>Family PAN Odds Comparison (At Least 1 Lot):</b>
                • <b>1 PAN:</b> <code>%.1f%%</code> (%s) | ₹%,.0f
                • <b>2 PANs:</b> <code>%.1f%%</code> (%s) | ₹%,.0f
                • <b>3 PANs:</b> <code>%.1f%%</code> (%s) | ₹%,.0f
                • <b>5 PANs:</b> <code>%.1f%%</code> (%s) | ₹%,.0f
                • <b>7 PANs:</b> <code>%.1f%%</code> (%s) | ₹%,.0f

                💡 <b>Why Multi-PAN Strategy Works:</b>
                <i>Under SEBI retail allotment rules, allotment is done by computer lottery per unique PAN. Bidding for 2+ lots on ONE PAN does not increase your lottery chances. Always apply 1 lot across different family PAN cards!</i>

                <i>👇 Tap any PAN button below for a detailed simulation:</i>
                """.formatted(
                    ipo.companyName(),
                    ipo.symbol(),
                    retailSub,
                    lotSize,
                    minInv,
                    ipo.gmp(),
                    profitPerLotFormatted,
                    p1Pct, p1Pct >= 99.0 ? "Guaranteed" : String.format("1 in %.1f", 100.0 / p1Pct), minInv,
                    p2Pct, p2Pct >= 99.0 ? "Guaranteed" : String.format("1 in %.1f", 100.0 / p2Pct), 2 * minInv,
                    p3Pct, p3Pct >= 99.0 ? "Guaranteed" : String.format("1 in %.1f", 100.0 / p3Pct), 3 * minInv,
                    p5Pct, p5Pct >= 99.0 ? "Guaranteed" : String.format("1 in %.1f", 100.0 / p5Pct), 5 * minInv,
                    p7Pct, p7Pct >= 99.0 ? "Guaranteed" : String.format("1 in %.1f", 100.0 / p7Pct), 7 * minInv
            );
        }
    }

    public InlineKeyboardMarkup createMultiPanOddsKeyboard(Ipo ipo) {
        return createMultiPanOddsKeyboard(ipo, 0);
    }

    public InlineKeyboardMarkup createMultiPanOddsKeyboard(Ipo ipo, int currentPans) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
            InlineKeyboardButton.callback(currentPans == 1 ? "✅ 1 PAN" : "1 PAN", "odds:" + ipo.symbol() + ":1"),
            InlineKeyboardButton.callback(currentPans == 2 ? "✅ 2 PANs" : "2 PANs", "odds:" + ipo.symbol() + ":2"),
            InlineKeyboardButton.callback(currentPans == 3 ? "✅ 3 PANs" : "3 PANs", "odds:" + ipo.symbol() + ":3")
        ));
        rows.add(List.of(
            InlineKeyboardButton.callback(currentPans == 4 ? "✅ 4 PANs" : "4 PANs", "odds:" + ipo.symbol() + ":4"),
            InlineKeyboardButton.callback(currentPans == 5 ? "✅ 5 PANs" : "5 PANs", "odds:" + ipo.symbol() + ":5"),
            InlineKeyboardButton.callback(currentPans == 7 ? "✅ 7 PANs" : "7 PANs", "odds:" + ipo.symbol() + ":7")
        ));
        rows.add(List.of(
            InlineKeyboardButton.callback("🧮 Profit Calc", "calc:" + ipo.symbol()),
            InlineKeyboardButton.callback("🎯 Apply Score", "score:" + ipo.symbol())
        ));
        rows.add(List.of(
            InlineKeyboardButton.callback("📊 Odds Table", "odds:" + ipo.symbol() + ":0"),
            InlineKeyboardButton.callback("⬅️ Back to " + ipo.symbol(), "ipo:" + ipo.symbol())
        ));
        return InlineKeyboardMarkup.of(rows);
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
                InlineKeyboardButton.callback("🎲 Multi-PAN Odds", "odds:" + ipo.symbol())
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
                InlineKeyboardButton.callback("🎲 Multi-PAN Odds", "odds:" + ipo.symbol())
            ),
            List.of(
                InlineKeyboardButton.callback("🎯 Apply Score", "score:" + ipo.symbol()),
                InlineKeyboardButton.callback("⬅️ Back to " + ipo.symbol(), "ipo:" + ipo.symbol())
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
            InlineKeyboardButton.callback("🎲 Multi-PAN Odds", "odds:" + ipo.symbol())
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

    public double extractRetailSubscriptionMultiple(String subscription) {
        if (subscription == null || subscription.isBlank()) return 1.0;
        try {
            Matcher mRetail = Pattern.compile("retail\\s*:\\s*(\\d+(?:\\.\\d+)?)\\s*x", Pattern.CASE_INSENSITIVE)
                    .matcher(subscription);
            if (mRetail.find()) {
                return Double.parseDouble(mRetail.group(1));
            }
            Matcher mOverall = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*x", Pattern.CASE_INSENSITIVE)
                    .matcher(subscription);
            if (mOverall.find()) {
                return Double.parseDouble(mOverall.group(1));
            }
        } catch (Exception ignored) {}
        return 1.0;
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
            • <b>/upcoming</b> - List upcoming IPO announcements
            • <b>/odds &lt;name&gt; [pans]</b> - Multi-PAN allotment probability calculator (e.g. <code>/odds swiggy 3</code>)
            • <b>/calc &lt;name&gt; [lots]</b> - Listing profit calculator (e.g. <code>/calc swiggy 2</code>)
            • <b>/closed</b> - List recently closed IPOs
            • <b>/ipo &lt;symbol&gt;</b> - Search for a specific IPO (e.g. <code>/ipo swiggy</code>)
            • <b>/help</b> - Show this message

            <b>Interactive Tools:</b>
            • <b>🎲 Multi-PAN Odds</b>: Calculates your family allotment probability across 1, 2, 3, 5 & 7 PAN cards using SEBI retail lottery rules.
            • <b>🧮 Profit Calc</b>: Calculates estimated listing gains across custom lot sizes (1, 2, 5 & 14 lots).
            • <b>🎯 Apply Score</b>: Automated 0-100 score based on GMP, QIB backing & issue type.
            • <b>🏦 Registrar Link</b>: Direct 1-click links to official allotment portals (MUFG, KFintech, Bigshare & BSE India).
            """;
    }
}
