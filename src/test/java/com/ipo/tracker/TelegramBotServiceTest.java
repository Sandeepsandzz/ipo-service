package com.ipo.tracker;

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
        var active = ipoService.getActiveIpos();
        String formatted = formatter.formatActiveIpos(active);

        assertTrue(formatted.contains("IPOs Open to Apply Today"));
        assertTrue(formatted.contains("SWIGGY"));
        assertTrue(formatted.contains("GMP:"));
        assertTrue(formatted.contains("Lot Size:"));
    }

    @Test
    void testFormatterGeneratesUpcomingIposCard() {
        var upcoming = ipoService.getUpcomingIpos();
        String formatted = formatter.formatUpcomingIpos(upcoming);

        assertTrue(formatted.contains("Upcoming IPOs"));
        assertTrue(formatted.contains("NTPCGREEN"));
    }

    @Test
    void testHandleIncomingMessageWithoutTokenDoesNotThrow() {
        TelegramChat chat = new TelegramChat(123456789L, "private", null, "testuser", "Test");
        TelegramUser user = new TelegramUser(123456789L, false, "Test", "User", "testuser");
        TelegramMessage message = new TelegramMessage(1L, user, chat, 1729600000L, "/active");

        assertDoesNotThrow(() -> botService.handleIncomingMessage(message));
    }
}
