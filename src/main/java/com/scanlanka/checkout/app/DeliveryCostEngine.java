package com.scanlanka.checkout.app;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic delivery-cost engine (05 FR-CHECKOUT-10, delivery-cost-model.md). NO AI.
 * delivery = (zone base + billed-weight × per-kg) + pick-pack(item count) + handling surcharges, + fuel%.
 * Weight defaults to 0 until owner supplies per-product weights (D-8) — base/handling dominate meanwhile.
 */
@Component
public class DeliveryCostEngine {

    public record ZoneRates(long baseCents, long perKgCents, double fuelPct) {}

    public record Config(long pickFirstCents, long pickNextCents, long fragileCents, long oversizeCents) {}

    public record Item(String handlingClass, int quantity, double weightKg) {}

    public long compute(ZoneRates zone, Config config, List<Item> items) {
        int totalQty = items.stream().mapToInt(Item::quantity).sum();
        double billedWeight = items.stream().mapToDouble(i -> i.weightKg() * i.quantity()).sum();

        long weightCost = zone.baseCents() + Math.round(billedWeight * zone.perKgCents());
        long pickPack = totalQty == 0 ? 0
            : config.pickFirstCents() + (long) (totalQty - 1) * config.pickNextCents();
        long handling = items.stream().mapToLong(i -> surcharge(config, i.handlingClass()) * i.quantity()).sum();

        long subtotal = weightCost + pickPack + handling;
        long fuel = Math.round(subtotal * (zone.fuelPct() / 100.0));
        return subtotal + fuel;
    }

    private static long surcharge(Config config, String handlingClass) {
        if ("FRAGILE_GLASS".equals(handlingClass)) return config.fragileCents();
        if ("OVERSIZE".equals(handlingClass)) return config.oversizeCents();
        return 0L;
    }
}
