package com.ipo.tracker.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
    @JsonProperty("message_id") Long messageId,
    TelegramUser from,
    TelegramChat chat,
    Long date,
    String text
) {}
