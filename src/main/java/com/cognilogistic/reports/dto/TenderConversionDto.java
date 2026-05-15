package com.cognilogistic.reports.dto;

/**
 * Wire response for {@code GET /api/v1/reports/tenders/conversion}.
 *
 * <p>"Conversion rate" here is the proportion of tenders that reached {@code COMPLETED}
 * out of all tenders created in the date range. {@link #cancelled} is reported alongside
 * for context — a TP can have a high creation count but low conversion if many tenders
 * are cancelled.
 *
 * @param tpAccountId    the TP whose tenders this covers
 * @param totalTenders   tenders created in the date range
 * @param awardedTenders tenders that reached {@code COMPLETED} status
 * @param cancelledTenders tenders that reached {@code CANCELLED} status
 * @param conversionRate {@code awardedTenders / totalTenders} as a fraction in [0, 1];
 *                       0 when {@link #totalTenders} is 0
 */
public record TenderConversionDto(
        String tpAccountId,
        long totalTenders,
        long awardedTenders,
        long cancelledTenders,
        double conversionRate) {

    /** Computes the conversion rate safely, treating zero-total as zero conversion. */
    public static TenderConversionDto compute(String tpAccountId, long total, long awarded, long cancelled) {
        double rate = total == 0 ? 0.0 : (double) awarded / (double) total;
        return new TenderConversionDto(tpAccountId, total, awarded, cancelled, rate);
    }
}
