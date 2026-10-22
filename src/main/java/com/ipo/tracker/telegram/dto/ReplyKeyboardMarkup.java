package com.ipo.tracker.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ReplyKeyboardMarkup(
    List<List<KeyboardButton>> keyboard,
    @JsonProperty("resize_keyboard") boolean resizeKeyboard,
    @JsonProperty("one_time_keyboard") boolean oneTimeKeyboard
) {
    public static ReplyKeyboardMarkup defaultMenu() {
        return new ReplyKeyboardMarkup(
            List.of(
                List.of(new KeyboardButton("🟢 Active IPOs"), new KeyboardButton("📅 Upcoming IPOs")),
                List.of(new KeyboardButton("📊 Closed IPOs"), new KeyboardButton("❓ Help"))
            ),
            true,
            false
        );
    }
}
