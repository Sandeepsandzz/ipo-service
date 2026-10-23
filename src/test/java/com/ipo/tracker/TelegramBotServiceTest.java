package com.ipo.tracker;

import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.IpoStatus;
import com.ipo.tracker.model.IpoType;
import com.ipo.tracker.service.IpoService;
import com.ipo.tracker.telegram.TelegramBotProperties;
import com.ipo.tracker.telegram.dto.*;
import com.ipo.tracker.telegram.service.TelegramBotService;
import com.ipo.tracker.telegram.service.TelegramMessageFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TelegramBotServiceTest {

    @Autowired
    private TelegramBotProperties properties;

    @Autowired
    private IpoService ipoService;

    @Autowired
    private TelegramMessageFormatter formatter;

    private TelegramBotService botService;

    private Ipo sampleIpo;

    @BeforeEach
    void setUp() {
        botService = new TelegramBotService(properties, ipoService, formatter);
        sampleIpo = new Ipo(
                "sample-ipo",
                "Sample Company Limited",
                "SAMPLE",
                "NSE, BSE",
                IpoType.MAINBOARD,
                LocalDate.now(),
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(5),
                500.0,
                520.0,
                28,
                28 * 520.0,
                "₹1,500 Cr",
                IpoStatus.OPEN,
                "+₹50 (9.6%)",
                "2.5x overall",
                "Link Intime India Pvt Ltd"
        );
    }

    @Test
    void testFormatterGeneratesActiveIposCard() {
        String formatted = formatter.formatActiveIpos(List.of(sampleIpo));

        assertTrue(formatted.contains("IPOs Open to Apply Today"));
        assertTrue(formatted.contains("SAMPLE"));
        assertTrue(formatted.contains("GMP:"));
        assertTrue(formatted.contains("Lot Size:"));
    }

    @Test
    void testFormatterGeneratesUpcomingIposCard() {
        Ipo upcoming = new Ipo(
                "upcoming-sample",
                "Upcoming Tech Ltd",
                "UPTECH",
                "NSE, BSE",
                IpoType.MAINBOARD,
                LocalDate.now().plusDays(5),
                LocalDate.now().plusDays(8),
                LocalDate.now().plusDays(12),
                100.0,
                110.0,
                135,
                135 * 110.0,
                "₹500 Cr",
                IpoStatus.UPCOMING,
                "+₹15",
                "N/A",
                "KFintech"
        );

        String formatted = formatter.formatUpcomingIpos(List.of(upcoming));

        assertTrue(formatted.contains("Upcoming IPOs"));
        assertTrue(formatted.contains("UPTECH"));
    }

    @Test
    void testInlineKeyboardCreation() {
        InlineKeyboardMarkup keyboard = formatter.createSingleIpoKeyboard(sampleIpo);
        assertNotNull(keyboard);
        assertNotNull(keyboard.inlineKeyboard());
        assertFalse(keyboard.inlineKeyboard().isEmpty());

        // Verify action buttons exist
        List<InlineKeyboardButton> row1 = keyboard.inlineKeyboard().get(0);
        assertEquals("🧮 Profit Calc", row1.get(0).text());
        assertEquals("calc:SAMPLE", row1.get(0).callbackData());
        assertEquals("🎯 Apply Score", row1.get(1).text());
        assertEquals("score:SAMPLE", row1.get(1).callbackData());
    }

    @Test
    void testProfitCalculation() {
        String calcText = formatter.formatProfitCalculation(sampleIpo);
        assertNotNull(calcText);
        assertTrue(calcText.contains("Listing Profit Estimator"));
        assertTrue(calcText.contains("Profit / Loss Scenarios"));
        assertTrue(calcText.contains("1 Retail Lot"));

        InlineKeyboardMarkup markup = formatter.createProfitCalcKeyboard(sampleIpo);
        assertNotNull(markup);
    }

    @Test
    void testApplyScore() {
        String scoreText = formatter.formatApplyScore(sampleIpo);
        assertNotNull(scoreText);
        assertTrue(scoreText.contains("IPO Smart Score & Rating"));
        assertTrue(scoreText.contains("SAMPLE"));
        assertTrue(scoreText.contains("Overall Score:"));
        assertTrue(scoreText.contains("Verdict:"));

        InlineKeyboardMarkup markup = formatter.createApplyScoreKeyboard(sampleIpo);
        assertNotNull(markup);
    }

    @Test
    void testRegistrarDetails() {
        String regText = formatter.formatRegistrarDetails(sampleIpo);
        assertNotNull(regText);
        assertTrue(regText.contains("Link Intime"));

        InlineKeyboardMarkup markup = formatter.createRegistrarKeyboard(sampleIpo);
        assertNotNull(markup);
        assertTrue(markup.inlineKeyboard().get(0).get(0).url().contains("mufg.com"));
        assertTrue(markup.inlineKeyboard().get(0).get(1).url().contains("bseindia.com"));
    }

    @Test
    void testHandleIncomingMessageWithoutTokenDoesNotThrow() {
        TelegramChat chat = new TelegramChat(123456789L, "private", null, "testuser", "Test");
        TelegramUser user = new TelegramUser(123456789L, false, "Test", "User", "testuser");
        TelegramMessage message = new TelegramMessage(1L, user, chat, 1729600000L, "/active");

        assertDoesNotThrow(() -> botService.handleIncomingMessage(message));
    }

    @Test
    void testHandleCallbackQueryWithoutTokenDoesNotThrow() {
        TelegramChat chat = new TelegramChat(123456789L, "private", null, "testuser", "Test");
        TelegramUser user = new TelegramUser(123456789L, false, "Test", "User", "testuser");
        TelegramMessage message = new TelegramMessage(1L, user, chat, 1729600000L, "Sample text");
        TelegramCallbackQuery query = new TelegramCallbackQuery("cb_123", user, message, null, "calc:SAMPLE");

        assertDoesNotThrow(() -> botService.handleCallbackQuery(query));
    }
}
