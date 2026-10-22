package com.ipo.tracker;

import com.ipo.tracker.model.Ipo;
import com.ipo.tracker.model.IpoStatus;
import com.ipo.tracker.model.IpoType;
import com.ipo.tracker.service.IpoService;
import com.ipo.tracker.telegram.TelegramBotProperties;
import com.ipo.tracker.telegram.dto.TelegramChat;
import com.ipo.tracker.telegram.dto.TelegramMessage;
import com.ipo.tracker.telegram.dto.TelegramUser;
import com.ipo.tracker.telegram.service.TelegramBotService;
import com.ipo.tracker.telegram.service.TelegramMessageFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TelegramBotServiceTest {

    @Autowired
    private TelegramBotProperties properties;

    @Autowired
    private IpoService ipoService;

    @Autowired
    private TelegramMessageFormatter formatter;

    private TelegramBotService botService;

    @BeforeEach
    void setUp() {
        botService = new TelegramBotService(properties, ipoService, formatter);
    }

    @Test
    void testFormatterGeneratesActiveIposCard() {
        Ipo sampleIpo = new Ipo(
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
                "Link Intime"
        );

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
    void testHandleIncomingMessageWithoutTokenDoesNotThrow() {
        TelegramChat chat = new TelegramChat(123456789L, "private", null, "testuser", "Test");
        TelegramUser user = new TelegramUser(123456789L, false, "Test", "User", "testuser");
        TelegramMessage message = new TelegramMessage(1L, user, chat, 1729600000L, "/active");

        assertDoesNotThrow(() -> botService.handleIncomingMessage(message));
    }
}
