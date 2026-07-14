package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.SpecOption;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Resolves a variant's {@code options_signature} (joined spec_option ids) to its human-readable
 * label, e.g. "6 x 4" - shared by admin catalog views and order/quote line snapshots so the exact
 * size/spec ordered is always recoverable, not just the bare product name (owner 2026-07-14).
 */
@Component
public class VariantLabelResolver {

    private final SpecOptionRepository options;

    public VariantLabelResolver(SpecOptionRepository options) {
        this.options = options;
    }

    /** Null if there's no signature (single-priced product) or it resolves to nothing. */
    public String sizeLabel(String optionsSignature) {
        if (optionsSignature == null || optionsSignature.isBlank()) return null;
        String label = Arrays.stream(optionsSignature.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(this::parseOptionId)
            .filter(id -> id != null)
            .map(id -> options.findById(id).map(SpecOption::getValue).orElse(null))
            .filter(v -> v != null)
            .collect(Collectors.joining(" / "));
        return label.isBlank() ? null : label;
    }

    /** Product name with the resolved size appended, e.g. "Scan White Board (4 x 3)". */
    public String nameWithSize(String productName, String optionsSignature) {
        String size = sizeLabel(optionsSignature);
        return size == null ? productName : productName + " (" + size + ")";
    }

    private Long parseOptionId(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
