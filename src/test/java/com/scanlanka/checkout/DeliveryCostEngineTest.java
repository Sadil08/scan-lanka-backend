package com.scanlanka.checkout;

import com.scanlanka.checkout.app.DeliveryCostEngine;
import com.scanlanka.checkout.app.DeliveryCostEngine.Config;
import com.scanlanka.checkout.app.DeliveryCostEngine.Item;
import com.scanlanka.checkout.app.DeliveryCostEngine.ZoneRates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryCostEngineTest {

    private final DeliveryCostEngine engine = new DeliveryCostEngine();
    private final Config config = new Config(10000, 4000, 25000, 30000); // pick 100/40, fragile 250, oversize 300

    @Test
    void baseChargeForAStandardItem() {
        // zone base 500, no per-kg/fuel; 1 standard item, weight 0
        long cost = engine.compute(new ZoneRates(50000, 0, 0),
            config, List.of(new Item("STANDARD", 1, 0)));
        // 50000 base + pickPack(100 first) + 0 handling
        assertThat(cost).isEqualTo(50000 + 10000);
    }

    @Test
    void fragileGlassAddsSurcharge() {
        long cost = engine.compute(new ZoneRates(50000, 0, 0),
            config, List.of(new Item("FRAGILE_GLASS", 1, 0)));
        assertThat(cost).isEqualTo(50000 + 10000 + 25000); // + fragile surcharge
    }

    @Test
    void billedWeightDrivesPerKg() {
        // 10kg × Rs80/kg = Rs800; + base Rs500 + pick 100
        long cost = engine.compute(new ZoneRates(50000, 8000, 0),
            config, List.of(new Item("STANDARD", 1, 10)));
        assertThat(cost).isEqualTo(50000 + 80000 + 10000);
    }

    @Test
    void multipleItemsStepPickPackAndFuelApplies() {
        // 2 items: pickPack = 100 + 40 = 140 (14000c); base 500; 5% fuel
        long cost = engine.compute(new ZoneRates(50000, 0, 5),
            config, List.of(new Item("STANDARD", 2, 0)));
        long subtotal = 50000 + (10000 + 4000); // base + pickPack
        long expected = subtotal + Math.round(subtotal * 0.05);
        assertThat(cost).isEqualTo(expected);
    }
}
