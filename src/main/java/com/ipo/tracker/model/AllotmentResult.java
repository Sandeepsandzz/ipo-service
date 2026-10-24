package com.ipo.tracker.model;

public record AllotmentResult(
    String symbol,
    String companyName,
    String registrar,
    String maskedPan,
    String label,
    AllotmentStatus status,
    int sharesApplied,
    int sharesAllotted,
    double amountBlocked,
    double cutOffPrice,
    double estimatedListingPrice,
    double expectedProfit,
    String expectedAllotmentDate,
    String expectedListingDate,
    String message,
    String checkUrl
) {}
