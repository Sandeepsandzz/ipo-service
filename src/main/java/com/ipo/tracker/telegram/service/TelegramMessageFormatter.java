package com.ipo.tracker.telegram.service;

import com.ipo.tracker.model.Ipo;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

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

            <i>Use the interactive keyboard below for 1-click access!</i>
            """.formatted(displayName);
    }

    public String formatActiveIpos(List<Ipo> ipos) {
        if (ipos.isEmpty()) {
            return "🟢 <b>No IPOs currently open for bidding today.</b>\n\nCheck back soon or tap 📅 <b>Upcoming IPOs</b>!";
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

        sb.append("<i>💡 Tap on any symbol to copy it for your UPI broker app.</i>");
        return sb.toString();
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

        return sb.toString();
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
                ipo.listingDate().format(DATE_FMT),
                ipo.registrar()
        );
    }

    public String formatHelp() {
        return """
            📖 <b>IPO Tracker Bot Help</b>
            ━━━━━━━━━━━━━━━━━━━━
            <b>Commands:</b>
            • <b>/start</b> - Open main menu & keyboard
            • <b>/active</b> or <b>/open</b> - List all IPOs open today
            • <b>/upcoming</b> - List upcoming IPOs
            • <b>/closed</b> - List recently closed IPOs
            • <b>/ipo &lt;symbol&gt;</b> - Search for a specific IPO (e.g. <code>/ipo swiggy</code>)
            • <b>/help</b> - Show this message

            <i>You can also simply tap the buttons on your keyboard!</i>
            """;
    }
}
