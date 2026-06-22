package com.scanlanka.catalog.app;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Variant generation (01-product-catalog FR-CATALOG-6/7). A product's variants are the **cartesian
 * product of the price-affecting groups' options**. k=0 price-affecting groups → no variants (SINGLE).
 * Informational groups never participate. Pure logic (operates on option ids) — DB-free, fully testable.
 */
@Service
public class VariantService {

    /** Cartesian product over the option-id lists of each price-affecting group. */
    public List<List<Long>> cartesian(List<List<Long>> priceAffectingGroups) {
        if (priceAffectingGroups == null || priceAffectingGroups.isEmpty()) {
            return List.of(); // SINGLE-priced product: no variants
        }
        List<List<Long>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (List<Long> group : priceAffectingGroups) {
            List<List<Long>> next = new ArrayList<>();
            for (List<Long> combo : result) {
                for (Long optionId : group) {
                    List<Long> extended = new ArrayList<>(combo);
                    extended.add(optionId);
                    next.add(extended);
                }
            }
            result = next;
        }
        return result;
    }

    /** Stable signature for a variant = sorted price-affecting option ids (unique per combination). */
    public String signature(Collection<Long> optionIds) {
        return optionIds.stream().sorted().map(String::valueOf).collect(Collectors.joining("-"));
    }

    /** Cartesian product of option value labels (admin variant grid preview). */
    public List<List<String>> cartesianValues(List<List<String>> priceAffectingGroups) {
        if (priceAffectingGroups == null || priceAffectingGroups.isEmpty()) {
            return List.of();
        }
        List<List<String>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (List<String> group : priceAffectingGroups) {
            List<List<String>> next = new ArrayList<>();
            for (List<String> combo : result) {
                for (String value : group) {
                    List<String> extended = new ArrayList<>(combo);
                    extended.add(value);
                    next.add(extended);
                }
            }
            result = next;
        }
        return result;
    }
}
