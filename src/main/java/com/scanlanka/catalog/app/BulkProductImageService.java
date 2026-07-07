package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductImage;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.domain.SpecGroup;
import com.scanlanka.catalog.domain.SpecOption;
import com.scanlanka.catalog.infra.ProductImageRepository;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.catalog.infra.SpecGroupRepository;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import com.scanlanka.shared.storage.ImageStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bulk product-image import from a single zip (owner 2026-07-07 — manual one-at-a-time uploads don't
 * scale to a 300-variant catalog). Each image inside the zip is matched to a product (and optionally a
 * size/variant) by filename convention, so a whole folder of photos attaches in one pass.
 *
 * <p><b>Filename convention</b> (basename, extension ignored): {@code slug[__size][__label].ext}
 * split on double-underscore.
 * <ul>
 *   <li>token 0 = product slug (exact match on {@code product.slug}).</li>
 *   <li>token 1 (optional) = size — matched to a price-affecting spec option value (fuzzy: spaces,
 *       {@code x}/{@code ×}, and fractions like {@code 1 1/2} ⇔ {@code 1.5} are normalised). No match
 *       ⇒ the image attaches at product level and the row is flagged so the admin can rename.</li>
 *   <li>token 2+ (optional) = free label so several photos can share one product/size (e.g. {@code __2},
 *       {@code __front}).</li>
 * </ul>
 *
 * <p>{@link #preview} maps without writing anything (dry run the admin reviews first); {@link #apply}
 * re-encodes + stores the mappable images and inserts the rows. Hostile-zip hardening: entry count/
 * byte caps, basename only (no zip-slip), non-images skipped, per-image failures isolated (one bad
 * file never aborts the batch). Re-running adds images again — there is no dedupe (admin deletes).
 */
@Service
public class BulkProductImageService {

    private static final int MAX_ENTRIES = 500;
    private static final long MAX_TOTAL_UNCOMPRESSED = 250L * 1024 * 1024; // zip-bomb guard
    private static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
    private static final Pattern IMAGE_EXT = Pattern.compile(".*\\.(png|jpe?g|webp|gif)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIXED_FRACTION = Pattern.compile("(\\d+)\\s+(\\d+)/(\\d+)");
    private static final Pattern SIMPLE_FRACTION = Pattern.compile("(\\d+)/(\\d+)");

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final SpecGroupRepository groups;
    private final SpecOptionRepository options;
    private final ProductImageRepository images;
    private final ImageProcessing processing;
    private final ImageStorage storage;
    private final CatalogCacheEvictor cacheEvictor;

    public BulkProductImageService(ProductRepository products, ProductVariantRepository variants,
                                   SpecGroupRepository groups, SpecOptionRepository options,
                                   ProductImageRepository images, ImageProcessing processing,
                                   ImageStorage storage, CatalogCacheEvictor cacheEvictor) {
        this.products = products;
        this.variants = variants;
        this.groups = groups;
        this.options = options;
        this.images = images;
        this.processing = processing;
        this.storage = storage;
        this.cacheEvictor = cacheEvictor;
    }

    public enum RowStatus { OK_VARIANT, OK_PRODUCT, NO_PRODUCT, SIZE_NOT_MATCHED, BAD_IMAGE, NOT_AN_IMAGE }

    /** One image entry's mapping result. In preview, OK_* = "would import"; in apply = "imported". */
    public record ImportRow(String filename, String productSlug, String sizeToken,
                            Long productId, String productName, Long variantId, String sizeLabel,
                            RowStatus status, String message) {}

    public record ImportReport(int totalEntries, int imageEntries, int matchedVariant, int matchedProduct,
                               int unmatched, List<ImportRow> rows) {}

    @Transactional(readOnly = true)
    public ImportReport preview(byte[] zipBytes) {
        return process(zipBytes, false);
    }

    @Transactional
    public ImportReport apply(byte[] zipBytes) {
        ImportReport report = process(zipBytes, true);
        cacheEvictor.evictAll();
        return report;
    }

    private ImportReport process(byte[] zipBytes, boolean persist) {
        List<ImportRow> rows = new ArrayList<>();
        int totalEntries = 0, imageEntries = 0, okVariant = 0, okProduct = 0, unmatched = 0;
        long totalUncompressed = 0;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++totalEntries > MAX_ENTRIES) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Too many files in zip (max " + MAX_ENTRIES + ")");
                }
                String base = basename(entry.getName());
                if (!IMAGE_EXT.matcher(base).matches()) {
                    continue; // silently skip non-images (readmes, .DS_Store, nested junk)
                }
                imageEntries++;

                byte[] data = readEntry(zip);
                totalUncompressed += data.length;
                if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Zip contents too large");
                }

                ImportRow row = mapAndMaybeStore(base, data, persist);
                rows.add(row);
                switch (row.status()) {
                    case OK_VARIANT -> okVariant++;
                    case OK_PRODUCT -> okProduct++;
                    default -> unmatched++;
                }
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read zip file");
        }
        if (imageEntries == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image files found in the zip");
        }
        return new ImportReport(totalEntries, imageEntries, okVariant, okProduct, unmatched, rows);
    }

    private ImportRow mapAndMaybeStore(String filename, byte[] data, boolean persist) {
        String[] tokens = stripExtension(filename).split("__", -1);
        String slug = tokens[0].trim().toLowerCase();
        String sizeToken = tokens.length > 1 && !tokens[1].isBlank() ? tokens[1].trim() : null;

        Optional<Product> product = products.findBySlug(slug);
        if (product.isEmpty()) {
            return row(filename, slug, sizeToken, null, null, null, null,
                RowStatus.NO_PRODUCT, "No product with slug '" + slug + "'");
        }
        Product p = product.get();

        ProductVariant variant = sizeToken == null ? null : matchVariant(p, sizeToken);

        // A filename that names a size we can't match is a mistake to fix, not something to silently
        // dump at product level — skip it (nothing stored) so the admin renames and re-imports cleanly.
        if (sizeToken != null && variant == null) {
            return row(filename, slug, sizeToken, p.getId(), p.getName(), null, null,
                RowStatus.SIZE_NOT_MATCHED,
                "Size '" + sizeToken + "' didn't match any of this product's sizes — rename and re-import");
        }

        // Validate/re-encode + store only when applying; preview reports mapping without touching disk.
        if (persist) {
            byte[] png;
            try {
                png = processing.validateAndReencode(data);
            } catch (RuntimeException e) {
                return row(filename, slug, sizeToken, p.getId(), p.getName(),
                    variant != null ? variant.getId() : null, variant != null ? sizeToken : null,
                    RowStatus.BAD_IMAGE, "Not a valid image / too large");
            }
            ImageStorage.StoredImage stored = storage.store(png, processing.outputExtension());
            int order = images.findByProductIdOrderByDisplayOrderAsc(p.getId()).size();
            images.save(new ProductImage(p.getId(), variant != null ? variant.getId() : null,
                stored.key(), stored.url(), false, order));
        }

        if (variant != null) {
            return row(filename, slug, sizeToken, p.getId(), p.getName(), variant.getId(), sizeToken,
                RowStatus.OK_VARIANT, null);
        }
        return row(filename, slug, sizeToken, p.getId(), p.getName(), null, null, RowStatus.OK_PRODUCT, null);
    }

    /** Match a filename size token to a variant, only for products with exactly one price-affecting group. */
    private ProductVariant matchVariant(Product p, String sizeToken) {
        List<SpecGroup> priceAffecting = groups.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
            .filter(SpecGroup::isPriceAffecting).toList();
        if (priceAffecting.size() != 1) return null; // 0 = single-priced; >1 = ambiguous from one token
        String target = normSize(sizeToken);
        for (SpecOption opt : options.findBySpecGroupIdOrderByDisplayOrderAsc(priceAffecting.get(0).getId())) {
            if (normSize(opt.getValue()).equals(target)) {
                return variants.findByProductIdAndOptionsSignature(p.getId(), String.valueOf(opt.getId()))
                    .filter(ProductVariant::isActive).orElse(null);
            }
        }
        return null;
    }

    // --- helpers ---

    private static ImportRow row(String f, String slug, String size, Long pid, String pname,
                                 Long vid, String sizeLabel, RowStatus st, String msg) {
        return new ImportRow(f, slug, size, pid, pname, vid, sizeLabel, st, msg);
    }

    private static byte[] readEntry(ZipInputStream zip) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n; long total = 0;
        while ((n = zip.read(buf)) != -1) {
            total += n;
            if (total > MAX_ENTRY_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "An image in the zip is too large");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Basename only — never trust the entry path (anti zip-slip, T-20). */
    static String basename(String path) {
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /**
     * Canonical form for comparing a filename size token against a spec option value: lowercase, unify
     * the multiply sign to {@code x}, turn mixed/simple fractions into decimals ({@code 1 1/2}→{@code 1.5},
     * {@code 1/2}→{@code 0.5}), drop spaces and trailing decimal zeros. So "1 1/2 x 1 1/2" and "1.5x1.5"
     * both collapse to {@code 1.5x1.5}.
     */
    static String normSize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase().trim().replace('×', 'x').replace('*', 'x');
        t = t.replace("feet", "").replace("ft", "").replace("'", "").replace("\"", "");
        t = replaceAll(t, MIXED_FRACTION, m -> trimNum(Integer.parseInt(m.group(1)) + (double) Integer.parseInt(m.group(2)) / Integer.parseInt(m.group(3))));
        t = replaceAll(t, SIMPLE_FRACTION, m -> trimNum((double) Integer.parseInt(m.group(1)) / Integer.parseInt(m.group(2))));
        t = t.replaceAll("\\s+", "");
        t = t.replaceAll("(\\d)\\.0+(?!\\d)", "$1"); // 2.0 -> 2
        return t;
    }

    private static String trimNum(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private interface Repl { String apply(Matcher m); }

    private static String replaceAll(String input, Pattern pattern, Repl repl) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(repl.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
