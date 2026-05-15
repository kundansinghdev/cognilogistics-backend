package com.cognilogistic.tender.service;

/**
 * Namespace class for tender domain event records.
 *
 * <p>Mirrors {@link com.cognilogistic.order.service.OrderEvents} — immutable records
 * published in-process via Spring's
 * {@link org.springframework.context.ApplicationEventPublisher}. The notification
 * module's {@code EventListeners} consumes these to trigger SMS / WhatsApp / In-App
 * messages for tender lifecycle changes (notification.md §3.2 channel matrix).
 *
 * <p>For UAT these are published in-process; post-UAT they will be forwarded to Azure
 * Event Hubs (Kafka API) without any change to producers or listeners — the
 * @{@link org.springframework.transaction.event.TransactionalEventListener AFTER_COMMIT}
 * boundary is what makes that swap safe.
 *
 * <p><strong>Producer status:</strong> the {@code TenderService} class doesn't yet
 * publish these events — that wiring lands as part of tender-module PR T2 (notification
 * integration). Listeners are created here so the notification module's compilation
 * is closed; producers fire in a follow-up PR.
 *
 * <p>This class is not instantiable — use the inner record types directly.
 */
public final class TenderEvents {

    /**
     * Published when a TP_ADMIN moves a tender from DRAFT to IN_PROGRESS (publishes it
     * to invited LPs). The notification module fans out a WhatsApp template + In-App
     * notification to each invited LP.
     *
     * @param tenderId    the tender that became visible to LPs
     * @param tpAccountId the publishing TP's account id (for audit)
     * @param origin      origin city / hub
     * @param destination destination city / hub
     * @param vehicleType the requested vehicle category (e.g. "32-feet single-axle")
     * @param refPriceInr the TP's reference price in INR
     */
    public record TenderPublished(
            String tenderId,
            String tpAccountId,
            String origin,
            String destination,
            String vehicleType,
            Integer refPriceInr) {
    }

    /**
     * Published when an LP submits a bid on a tender. The notification module fires
     * an In-App notification to the owning TP_ADMIN ("you have a new bid").
     *
     * @param bidId       the new bid's id
     * @param tenderId    the tender being bid on
     * @param partnerId   the bidding LP's partner profile id
     * @param amountInr   the bid amount in INR
     */
    public record BidSubmitted(
            String bidId,
            String tenderId,
            String partnerId,
            Integer amountInr) {
    }

    /**
     * Published when a TP_ADMIN awards a bid. The winning LP receives SMS + WhatsApp
     * + In-App; sibling losing LPs receive an In-App "your bid was not accepted".
     *
     * @param tenderId    the awarded tender
     * @param awardedBidId the winning bid's id
     * @param winningPartnerId the LP that won
     * @param amountInr   the awarded amount
     */
    public record TenderAwarded(
            String tenderId,
            String awardedBidId,
            String winningPartnerId,
            Integer amountInr) {
    }

    /**
     * Published for each bid that lost when a tender is awarded. The notification
     * module fires an In-App "bid not accepted" message to each losing LP.
     *
     * @param bidId     the rejected bid
     * @param tenderId  the parent tender
     * @param partnerId the LP whose bid was rejected
     */
    public record BidRejected(
            String bidId,
            String tenderId,
            String partnerId) {
    }

    private TenderEvents() {}
}
