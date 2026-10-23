package com.ipo.tracker.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InlineKeyboardButton(
    String text,
    @JsonProperty("callback_data") String callbackData,
    String url
) {
    public static InlineKeyboardButton callback(String text, String callbackData) {
        return new InlineKeyboardButton(text, callbackData, null);
    }

    public static InlineKeyboardButton url(String text, String url) {
        return new InlineKeyboardButton(text, null, url);
    }
}
