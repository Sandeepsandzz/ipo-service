package com.ipo.tracker.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InlineKeyboardMarkup(
    @JsonProperty("inline_keyboard") List<List<InlineKeyboardButton>> inlineKeyboard
) {
    public static InlineKeyboardMarkup of(List<List<InlineKeyboardButton>> rows) {
        return new InlineKeyboardMarkup(rows);
    }
}
