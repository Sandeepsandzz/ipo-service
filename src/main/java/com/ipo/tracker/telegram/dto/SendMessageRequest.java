package com.ipo.tracker.telegram.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageRequest(
    @JsonProperty("chat_id") Long chatId,
    String text,
    @JsonProperty("parse_mode") String parseMode,
    @JsonProperty("reply_markup") Object replyMarkup
) {
    public static SendMessageRequest html(Long chatId, String text, Object replyMarkup) {
        return new SendMessageRequest(chatId, text, "HTML", replyMarkup);
    }

    public static SendMessageRequest html(Long chatId, String text) {
        return new SendMessageRequest(chatId, text, "HTML", null);
    }
}
