package com.ipo.tracker;

import com.ipo.tracker.model.*;
import com.ipo.tracker.repository.SubscriberRepository;
import com.ipo.tracker.service.IpoAlertService;
import com.ipo.tracker.service.IpoAllotmentService;
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

    @Autowired
    private SubscriberRepository subscriberRepository;

    @Autowired
    private IpoAlertService alertService;

    @Autowired
    private IpoAllotmentService allotmentService;

    private TelegramBotService botService;

    private Ipo sampleIpo;

    @BeforeEach
    void setUp() {
        botService = new TelegramBotService(properties, ipoService, formatter, subscriberRepository, allotmentService);
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
                "2.5x (Retail: 3.50x, QIB: 4.20x, NII: 1.80x)",
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

        List<InlineKeyboardButton> row1 = keyboard.inlineKeyboard().get(0);
        assertEquals("🧮 Profit Calc", row1.get(0).text());
        assertEquals("calc:SAMPLE", row1.get(0).callbackData());
        assertEquals("🎲 Multi-PAN Odds", row1.get(1).text());
        assertEquals("odds:SAMPLE", row1.get(1).callbackData());
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
    void testCustomLotProfitCalculation() {
        String customLotsText = formatter.formatProfitCalculation(sampleIpo, 5);
        assertNotNull(customLotsText);
        assertTrue(customLotsText.contains("5 Lot(s)"));
        assertTrue(customLotsText.contains("Estimated Net Listing Profit:"));

        InlineKeyboardMarkup markup = formatter.createProfitCalcKeyboard(sampleIpo, 5);
        assertNotNull(markup);
        assertEquals("✅ 5 Lots", markup.inlineKeyboard().get(0).get(2).text());
    }

    @Test
    void testMultiPanOddsTable() {
        String oddsText = formatter.formatMultiPanOdds(sampleIpo, 0);
        assertNotNull(oddsText);
        assertTrue(oddsText.contains("Multi-PAN Allotment Odds Calculator"));
        assertTrue(oddsText.contains("1 PAN:"));
        assertTrue(oddsText.contains("3 PANs:"));
        assertTrue(oddsText.contains("5 PANs:"));
        assertTrue(oddsText.contains("SEBI"));

        InlineKeyboardMarkup markup = formatter.createMultiPanOddsKeyboard(sampleIpo, 0);
        assertNotNull(markup);
    }

    @Test
    void testMultiPanOddsSpecificCount() {
        String oddsSimText = formatter.formatMultiPanOdds(sampleIpo, 3);
        assertNotNull(oddsSimText);
        assertTrue(oddsSimText.contains("3 Family PANs"));
        assertTrue(oddsSimText.contains("Chance of at least 1 Allotment:"));
        assertTrue(oddsSimText.contains("Capital Blocked:"));

        InlineKeyboardMarkup markup = formatter.createMultiPanOddsKeyboard(sampleIpo, 3);
        assertNotNull(markup);
        assertEquals("✅ 3 PANs", markup.inlineKeyboard().get(0).get(2).text());
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

        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(1L, user, chat, 1729600000L, "/active")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(2L, user, chat, 1729600000L, "/odds")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(3L, user, chat, 1729600000L, "/odds SAMPLE 3")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(4L, user, chat, 1729600000L, "/calc SAMPLE 5")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(5L, user, chat, 1729600000L, "/alerts")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(6L, user, chat, 1729600000L, "/subscribe")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(7L, user, chat, 1729600000L, "/track SAMPLE")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(8L, user, chat, 1729600000L, "/testalert")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(9L, user, chat, 1729600000L, "/unsubscribe")));
    }

    @Test
    void testHandleCallbackQueryWithoutTokenDoesNotThrow() {
        TelegramChat chat = new TelegramChat(123456789L, "private", null, "testuser", "Test");
        TelegramUser user = new TelegramUser(123456789L, false, "Test", "User", "testuser");
        TelegramMessage message = new TelegramMessage(1L, user, chat, 1729600000L, "Sample text");

        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_1", user, message, null, "calc:SAMPLE:2")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_2", user, message, null, "odds:SAMPLE:3")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_3", user, message, null, "odds:SAMPLE:0")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_4", user, message, null, "track:SAMPLE")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_5", user, message, null, "toggle:morning")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_6", user, message, null, "toggle:closing")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_7", user, message, null, "toggle:gmp")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_8", user, message, null, "nav:alerts")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_9", user, message, null, "test:alert")));
    }

    @Test
    void testSubscriberRepositoryOperations() {
        Long testChatId = System.currentTimeMillis();
        Subscriber sub = subscriberRepository.registerOrUpdate(testChatId, "johndoe", "John");
        assertNotNull(sub);
        assertTrue(sub.morningDigestEnabled());
        assertTrue(sub.closingAlertEnabled());
        assertTrue(sub.gmpSurgeEnabled());

        // Toggle watchlist
        boolean added = subscriberRepository.toggleWatchlist(testChatId, "SWIGGY");
        assertTrue(added);
        assertTrue(subscriberRepository.isInWatchlist(testChatId, "SWIGGY"));

        boolean removed = subscriberRepository.toggleWatchlist(testChatId, "SWIGGY");
        assertFalse(removed);
        assertFalse(subscriberRepository.isInWatchlist(testChatId, "SWIGGY"));

        // Update settings
        Subscriber updated = subscriberRepository.updateSettings(testChatId, false, true, false);
        assertFalse(updated.morningDigestEnabled());
        assertTrue(updated.closingAlertEnabled());
        assertFalse(updated.gmpSurgeEnabled());

        // Unsubscribe
        subscriberRepository.unsubscribe(testChatId);
        Subscriber unsubbed = subscriberRepository.getSubscriber(testChatId).orElseThrow();
        assertFalse(unsubbed.morningDigestEnabled());
        assertFalse(unsubbed.closingAlertEnabled());
        assertFalse(unsubbed.gmpSurgeEnabled());
    }

    @Test
    void testMorningDigestFormatting() {
        String digest = formatter.formatMorningDigest(List.of(sampleIpo), List.of(), List.of());
        assertNotNull(digest);
        assertTrue(digest.contains("IPO Daily Morning Digest"));
        assertTrue(digest.contains("Opening Today"));
        assertTrue(digest.contains("SAMPLE"));

        InlineKeyboardMarkup keyboard = formatter.createMorningDigestKeyboard();
        assertNotNull(keyboard);
        assertEquals(2, keyboard.inlineKeyboard().size());
    }

    @Test
    void testClosingDayAlertFormatting() {
        String alert = formatter.formatClosingDayAlert(sampleIpo);
        assertNotNull(alert);
        assertTrue(alert.contains("FINAL CALL: Bidding Closes at 5:00 PM Today!"));
        assertTrue(alert.contains("SAMPLE"));
        assertTrue(alert.contains("UPI mandate authorization"));

        InlineKeyboardMarkup keyboard = formatter.createClosingDayAlertKeyboard(sampleIpo);
        assertNotNull(keyboard);
        assertEquals(2, keyboard.inlineKeyboard().size());
    }

    @Test
    void testGmpSurgeAlertFormatting() {
        String alert = formatter.formatGmpSurgeAlert(sampleIpo, "₹35", "₹50", 42.8);
        assertNotNull(alert);
        assertTrue(alert.contains("GMP MOVEMENT ALERT: 🚀 SURGED"));
        assertTrue(alert.contains("Shift:</b> <b>+42.8%</b>"));

        InlineKeyboardMarkup keyboard = formatter.createGmpSurgeKeyboard(sampleIpo);
        assertNotNull(keyboard);
    }

    @Test
    void testAlertsSettingsFormattingAndKeyboard() {
        Subscriber sub = Subscriber.createDefault(123L, "alice", "Alice");
        String settingsText = formatter.formatAlertsSettings(sub);
        assertNotNull(settingsText);
        assertTrue(settingsText.contains("Push Notification & Watchlist Preferences"));
        assertTrue(settingsText.contains("Daily Morning Digest:</b> ✅ Active"));

        InlineKeyboardMarkup keyboard = formatter.createAlertsSettingsKeyboard(sub);
        assertNotNull(keyboard);
        assertTrue(keyboard.inlineKeyboard().get(0).get(0).text().contains("Morning: ON ✅"));
    }

    @Test
    void testAlertServiceDirectExecution() {
        assertDoesNotThrow(() -> alertService.triggerMorningDigest(123456789L));
        assertDoesNotThrow(() -> alertService.triggerClosingAlerts(123456789L));
        assertDoesNotThrow(() -> alertService.triggerGmpSurgeCheck(123456789L));
        assertDoesNotThrow(() -> alertService.sendTestAlert(123456789L));
    }

    @Test
    void testPanValidationAndMasking() {
        assertTrue(allotmentService.isValidPan("ABCDE1234F"));
        assertTrue(allotmentService.isValidPan("abcde1234f")); // case-insensitive check
        assertFalse(allotmentService.isValidPan("ABCDE12345")); // ends in digit
        assertFalse(allotmentService.isValidPan("ABCD12345F")); // only 4 leading letters
        assertFalse(allotmentService.isValidPan(""));
        assertFalse(allotmentService.isValidPan(null));

        assertEquals("ABCDE****F", allotmentService.maskPan("ABCDE1234F"));
        assertEquals("ABCDE****F", allotmentService.maskPan("abcde1234f"));
    }

    @Test
    void testAllotmentCheckMechanics() {
        // Invalid PAN check
        AllotmentResult invalidPanRes = allotmentService.checkAllotment("SWIGGY", "INVALIDPAN", "Self");
        assertEquals(AllotmentStatus.INVALID_PAN, invalidPanRes.status());
        assertEquals("INVALIDPAN", invalidPanRes.maskedPan());

        // Unknown IPO check
        AllotmentResult unknownIpoRes = allotmentService.checkAllotment("NONEXISTENT_IPO_XYZ", "ABCDE1234F", "Dad");
        assertEquals(AllotmentStatus.IPO_NOT_FOUND, unknownIpoRes.status());

        // Check against active or closed IPO in client cache
        List<Ipo> ipos = ipoService.getActiveIpos();
        if (ipos.isEmpty()) {
            ipos = ipoService.getUpcomingIpos();
        }
        assertFalse(ipos.isEmpty());
        Ipo firstIpo = ipos.get(0);

        AllotmentResult res = allotmentService.checkAllotment(firstIpo.symbol(), "ABCDE1234F", "Self");
        assertNotNull(res);
        assertNotNull(res.status());
        assertEquals("ABCDE****F", res.maskedPan());
        assertEquals("Self", res.label());
        assertTrue(res.sharesApplied() > 0);
        assertNotNull(res.registrar());
    }

    @Test
    void testSavedPansRepositoryOperations() {
        Long testChatId = System.currentTimeMillis() + 999;
        subscriberRepository.registerOrUpdate(testChatId, "panuser", "PAN User");

        // Initial list empty
        List<SavedPan> initial = subscriberRepository.getSavedPans(testChatId);
        assertTrue(initial.isEmpty());

        // Add 1st PAN
        boolean added1 = subscriberRepository.addSavedPan(testChatId, "ABCDE1234F", "Self");
        assertTrue(added1);
        List<SavedPan> afterAdd1 = subscriberRepository.getSavedPans(testChatId);
        assertEquals(1, afterAdd1.size());
        assertEquals("ABCDE1234F", afterAdd1.get(0).pan());
        assertEquals("ABCDE****F", afterAdd1.get(0).maskedPan());
        assertEquals("Self", afterAdd1.get(0).label());

        // Add 2nd PAN
        boolean added2 = subscriberRepository.addSavedPan(testChatId, "XYZPK9876Q", "Dad");
        assertTrue(added2);
        assertEquals(2, subscriberRepository.getSavedPans(testChatId).size());

        // Duplicate PAN upsert should update existing record without exceeding 2 PANs
        boolean dupAdded = subscriberRepository.addSavedPan(testChatId, "abcde1234f", "Self Duplicate");
        assertTrue(dupAdded);
        assertEquals(2, subscriberRepository.getSavedPans(testChatId).size());
        assertEquals("Self Duplicate", subscriberRepository.getSavedPans(testChatId).get(1).label());

        // Remove PAN
        boolean removed = subscriberRepository.removeSavedPan(testChatId, "ABCDE1234F");
        assertTrue(removed);
        List<SavedPan> afterRemove = subscriberRepository.getSavedPans(testChatId);
        assertEquals(1, afterRemove.size());
        assertEquals("XYZPK9876Q", afterRemove.get(0).pan());
    }

    @Test
    void testAllotmentFormattingAndKeyboards() {
        AllotmentResult sampleResult = new AllotmentResult(
                "SAMPLE",
                "Sample Company Limited",
                "Link Intime India Pvt Ltd",
                "ABCDE****F",
                "Self",
                AllotmentStatus.ALLOTTED,
                28,
                28,
                14560.0,
                520.0,
                570.0,
                1400.0,
                "2026-10-28",
                "2026-10-31",
                "Congratulations! 1 Retail Lot (28 shares) allotted.",
                "https://linkintime.co.in/initial_offer/public-issues.html"
        );

        String formatted = formatter.formatAllotmentResult(sampleResult);
        assertNotNull(formatted);
        assertTrue(formatted.contains("ALLOTMENT CONFIRMED!"));
        assertTrue(formatted.contains("Shares Allotted"));
        assertTrue(formatted.contains("ABCDE****F"));
        assertTrue(formatted.contains("Link Intime"));

        InlineKeyboardMarkup resultKeyboard = formatter.createAllotmentResultKeyboard(sampleResult);
        assertNotNull(resultKeyboard);
        assertFalse(resultKeyboard.inlineKeyboard().isEmpty());

        // Test Saved PANs formatter
        SavedPan p1 = SavedPan.of("ABCDE1234F", "Self");
        SavedPan p2 = SavedPan.of("XYZPK9876Q", "Dad");
        String walletText = formatter.formatSavedPans(List.of(p1, p2));
        assertNotNull(walletText);
        assertTrue(walletText.contains("Saved Family PAN Cards"));
        assertTrue(walletText.contains("Self"));
        assertTrue(walletText.contains("Dad"));

        InlineKeyboardMarkup walletKeyboard = formatter.createSavedPansKeyboard(List.of(p1, p2), "SAMPLE");
        assertNotNull(walletKeyboard);
        assertFalse(walletKeyboard.inlineKeyboard().isEmpty());
    }

    @Test
    void testAllotmentBotCommandsAndCallbacks() {
        TelegramChat chat = new TelegramChat(123456789L, "private", null, "testuser", "Test");
        TelegramUser user = new TelegramUser(123456789L, false, "Test", "User", "testuser");
        TelegramMessage message = new TelegramMessage(1L, user, chat, 1729600000L, "Sample text");

        // Commands
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(10L, user, chat, 1729600000L, "/status")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(11L, user, chat, 1729600000L, "/status SAMPLE")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(12L, user, chat, 1729600000L, "/status SAMPLE ABCDE1234F")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(13L, user, chat, 1729600000L, "/savepan ABCDE1234F Self")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(14L, user, chat, 1729600000L, "/mypans")));
        assertDoesNotThrow(() -> botService.handleIncomingMessage(new TelegramMessage(15L, user, chat, 1729600000L, "/delpan ABCDE1234F")));

        // Callbacks
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_10", user, message, null, "allot:SAMPLE")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_11", user, message, null, "allotcheck:SAMPLE:ABCDE1234F")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_12", user, message, null, "nav:mypans")));
        assertDoesNotThrow(() -> botService.handleCallbackQuery(new TelegramCallbackQuery("cb_13", user, message, null, "delpan:ABCDE1234F")));
    }
}

