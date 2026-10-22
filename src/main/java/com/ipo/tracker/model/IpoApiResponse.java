package com.ipo.tracker.model;

import java.time.LocalDateTime;

public record IpoApiResponse<T>(
    boolean success,
    String message,
    int count,
    LocalDateTime timestamp,
    T data
) {
    public static <T> IpoApiResponse<T> of(String message, int count, T data) {
        return new IpoApiResponse<>(true, message, count, LocalDateTime.now(), data);
    }

    public static <T> IpoApiResponse<T> of(String message, T data) {
        return new IpoApiResponse<>(true, message, 1, LocalDateTime.now(), data);
    }
}
