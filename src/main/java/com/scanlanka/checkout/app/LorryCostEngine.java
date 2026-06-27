package com.scanlanka.checkout.app;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In-house lorry cost (05 delivery-cost-model §2.1, 17 FR-DELIV-3). Pure, deterministic, NO AI.
 *
 * <p>The in-house lorry is a service for larger orders: it is offered <b>only when the order's product
 * subtotal is strictly greater than the global minimum bill</b> (owner: "greater than Rs 6,000"). Below
 * that, the lorry rail is unavailable and the customer uses courier or contacts WhatsApp.
 *
 * <p>When available, the prepaid delivery charge = Σ (selected variant's fixed charge for the resolved
 * lorry zone × qty). A line whose (variant, zone) cell has <b>no listed price</b> ({@code ARRANGED} — e.g.
 * the sheet's "bill &gt; 6,000" far cells) does <b>not</b> hide the lorry: the lorry is still offered, and
 * the admin coordinates the real delivery cost for those items manually after the order (owner 2026-06-27).
 * Such lines contribute 0 to the prepaid amount and set {@code someArranged}. Money in integer LKR cents.
 */
@Component
public class LorryCostEngine {

    public enum Kind { FLAT, ARRANGED }

    /** The resolved (variant, zone) lorry cell. {@code amountCents} is meaningful only for {@code FLAT}. */
    public record Charge(Kind kind, long amountCents) {
        public static Charge flat(long amountCents) { return new Charge(Kind.FLAT, amountCents); }
        /** No listed price for this (variant, zone): lorry still offered, admin arranges the cost manually. */
        public static final Charge ARRANGED = new Charge(Kind.ARRANGED, 0);
    }

    public record Line(Charge charge, int quantity) {}

    /** Either the lorry delivery quote, or a reason the lorry rail isn't offered for this order. */
    public sealed interface Result {
        /**
         * @param prepaidCents the charge collected online now (sum of priced lines)
         * @param someArranged true if ≥1 line had no listed price — admin will confirm the rest manually
         */
        record Available(long prepaidCents, boolean someArranged) implements Result {}
        record Unavailable(String reason) implements Result {}

        static Result available(long prepaidCents, boolean someArranged) {
            return new Available(prepaidCents, someArranged);
        }
        static Result unavailable(String reason) { return new Unavailable(reason); }
    }

    /**
     * @param subtotalCents the order's product subtotal; the lorry rail needs this strictly &gt; {@code minBillCents}
     * @param minBillCents  the global minimum bill for in-house lorry (e.g. Rs 6,000 = 600000 cents)
     */
    public Result compute(List<Line> lines, long subtotalCents, long minBillCents) {
        // Global gate (owner 2026-06-27): in-house lorry only for orders strictly OVER the minimum bill.
        if (subtotalCents <= minBillCents) {
            return Result.unavailable("MIN_BILL_NOT_MET");
        }
        long prepaid = 0;
        boolean someArranged = false;
        for (Line line : lines) {
            Charge charge = line.charge();
            switch (charge.kind()) {
                case FLAT -> prepaid += charge.amountCents() * (long) line.quantity();
                case ARRANGED -> someArranged = true; // offered anyway; admin arranges the far-delivery cost
            }
        }
        return Result.available(prepaid, someArranged);
    }
}
