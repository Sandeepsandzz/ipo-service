package com.ipo.tracker.model;

import java.time.LocalDate;

public record Ipo(
    String id,
    String companyName,
    String symbol,
    String exchange,
    IpoType issueType,
    LocalDate openDate,
    LocalDate closeDate,
    LocalDate listingDate,
    double minPrice,
    double maxPrice,
    int lotSize,
    double minInvestment,
    String issueSize,
    IpoStatus status,
    String gmp,
    String subscriptionStatus,
    String registrar
) {
    public IpoStatus computeStatus(LocalDate asOfDate) {
        if (asOfDate.isBefore(openDate)) {
            return IpoStatus.UPCOMING;
        } else if (!asOfDate.isAfter(closeDate)) {
            return IpoStatus.OPEN;
        } else {
            return IpoStatus.CLOSED;
        }
    }

    public boolean isOpenOn(LocalDate asOfDate) {
        return computeStatus(asOfDate) == IpoStatus.OPEN;
    }

    public Ipo withComputedStatus(LocalDate asOfDate) {
        return new Ipo(
            id,
            companyName,
            symbol,
            exchange,
            issueType,
            openDate,
            closeDate,
            listingDate,
            minPrice,
            maxPrice,
            lotSize,
            minInvestment,
            issueSize,
            computeStatus(asOfDate),
            gmp,
            subscriptionStatus,
            registrar
        );
    }
}
