package com.ipo.tracker.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdatesResponse(
    Boolean ok,
    List<TelegramUpdate> result
) {}
