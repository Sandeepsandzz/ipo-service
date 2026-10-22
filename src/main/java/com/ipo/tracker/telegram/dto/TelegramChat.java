package com.ipo.tracker.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramChat(
    Long id,
    String type,
    String title,
    String username,
    @JsonProperty("first_name") String firstName
) {}
