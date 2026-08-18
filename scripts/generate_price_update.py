#!/usr/bin/env python3
"""Generate V59 — sync product prices to the owner's 2026-08-18 "new mrp" column
("Product list Scan Lanka - weight, diamentions (1) - categories (2).csv").

The sheet is the same row-for-row structure as the one V47 (generate_catalog_csv_sync.py) synced
into the catalog, with one new column inserted ("new mrp", right after the old "MRP"). This script
does NOT re-run the full catalog sync (categories/descriptions/display order are untouched) — it
only updates price_cents / single_price_cents, guarded by the CURRENT seeded price so an admin price
edit made after V47 is never silently overwritten (same convention V47 itself uses).

Reuses generate_catalog_csv_sync's parsing (group headers, dedup, family-label reinjection) and
generate_catalog_seed's grouping engine so every row lands on exactly the same product/size the V36
seed + V47 sync produced — verified against a live DB dump (see backend/scripts/README section
below) before this script was written. Six products were merged into other canonical products by
V51-V55 (after V47) and are handled as an explicit slug+label remap; three sizes never got seeded at
all (blank MRP historically) and are skipped with a comment. A handful of legacy size labels differ
in punctuation from the CSV (e.g. DB "1x1" vs sheet "1 x 1") and are matched by their (unique, within
the product) old price instead.
"""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_catalog_csv_sync as vsync  # noqa: E402  (reuse the CSV/grouping pipeline)

g = vsync.g

NEW_CSV = ROOT / "Product list Scan Lanka - weight, diamentions (1) - categories (2).csv"
MIG_DIR = BACKEND / "src/main/resources/db/migration"
OUT_PATH = MIG_DIR / "V59__price_sync_2026_08.sql"

# Products V51-V55 merged into a canonical product (by slug) after V47 seeded them separately.
# slug the grouping engine derives from this sheet -> (canonical slug, DB option value), or
# (canonical slug, None) for what used to be a SINGLE product folded in as one new size option.
MERGED_REMAP: dict[str, tuple[str, str | None]] = {
    "single-side-teak-menu-board": ("single-side-menu-board", "2 x 2 1/2 (Teak)"),
    "dual-teak-menu-board": ("dual-menu-board", "2 x 2 1/2 (Teak)"),
}
# single-side-menu-board-2 / dual-menu-board-2 keep their own size labels ('2 x 3', '4 x 2') once
# re-pointed at the canonical slug — only the slug changes.
MERGED_SLUG_ONLY: dict[str, str] = {
    "single-side-menu-board-2": "single-side-menu-board",
    "dual-menu-board-2": "dual-menu-board",
}

# Legacy size labels that don't textually match this sheet's wording (pre-dates the CSV export /
# hand-typed originally) — matched instead by (slug, unique old price) below.
PRICE_FALLBACK_SLUGS = {"scan-white-board"}

# Sizes the sheet prices but that were never seeded (blank MRP historically -> V36 skipped them).
# Confirmed against the live DB: these options don't exist, so there is nothing to update.
KNOWN_UNSEEDED_SIZES = {("glass-board-with-stand", "5 x 3"), ("glass-board-with-stand", "6 x 3")}


def clean(s: str) -> str:
    s = s.replace("\xa0", " ").replace("’", "'").replace("”", '"')
    s = s.replace("–", "-").replace("×", "x")
    return re.sub(r"\s+", " ", s).strip()


def read_rows9() -> list[list[str]]:
    rows = []
    with open(NEW_CSV, encoding="utf-8", newline="") as f:
        for r in csv.reader(f):
            r = [clean(c) for c in r]
            r += [""] * (9 - len(r))
            rows.append(r)
    return rows


def collect_runs_with_indices(rows: list[list[str]], start: int) -> list[dict]:
    """Same run segmentation as vsync.collect_runs, but keeps each priced row's own CSV index
    (not just the run's first index) so every SizeRow can be matched back to its 'new mrp' cell."""
    runs: list[dict] = []
    block_rows: list[tuple[int, list[str]]] = []
    family_label = ""

    def flush():
        nonlocal block_rows, family_label
        if not block_rows:
            return
        if "key holder" in family_label.lower():
            segmented = [block_rows]
        else:
            segmented = []
            last_key = None
            for idx, r in block_rows:
                raw = g.fix_typos(g.normalize_ws(r[1]))
                base = (g.strip_size_tokens(raw) or raw).lower()
                if segmented and base == last_key:
                    segmented[-1].append((idx, r))
                else:
                    segmented.append([(idx, r)])
                last_key = base
        for seg in segmented:
            priced = [(i, r) for i, r in seg if len(r) > 2 and r[2].strip()]
            if not priced:
                continue
            runs.append({"first_index": priced[0][0], "indices": [i for i, _r in priced]})
        block_rows = []
        family_label = ""

    for idx, r in enumerate(rows[start:], start):
        name = r[1].strip()
        colA = r[0].strip()
        if not name:
            flush()
            continue
        if colA and not g.is_long_note(colA) and not block_rows:
            family_label = colA
        block_rows.append((idx, r))
        if name.lower().startswith(("citrek currier", "fardar currier")) or \
           re.match(r"^\d+\s*kg\s*$", name, re.I) or name.lower() == "other":
            block_rows.pop()
    flush()
    return runs


def sql_str(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def main() -> None:
    rows9 = read_rows9()

    # rows8: same 8-column shape as the sheet V47 consumed (drop the inserted "new mrp" column) so
    # the exact same grouping engine + dedup/group-header handling applies unchanged.
    rows8 = []
    for r in rows9:
        r8 = r[:2] + [r[2]] + r[4:9]
        r8[2] = r8[2].replace(",", "")
        rows8.append(r8)

    tmp_csv = MIG_DIR / "_price_sync_tmp.csv"
    with open(tmp_csv, "w", encoding="utf-8", newline="") as f:
        csv.writer(f).writerows(rows8)
    try:
        vsync.CSV_PATH = tmp_csv
        rows, _groups, dup_report = vsync.read_csv_rows()
        rows, arch_products = vsync.extract_architectural(rows)
        assert len(arch_products) == 2

        g.read_sheet1_rows = lambda: rows
        products = g.parse()

        start = next(i for i, r in enumerate(rows) if r[0].strip().lower().startswith("model no")) + 1
        runs = collect_runs_with_indices(rows, start)
        assert len(runs) == len(products), f"{len(runs)} runs vs {len(products)} products"
        for p, run in zip(products, runs):
            assert len(run["indices"]) == len(p.sizes), \
                f"{p.name}: {len(run['indices'])} indices vs {len(p.sizes)} sizes"
    finally:
        tmp_csv.unlink(missing_ok=True)

    used: set[str] = set()
    single_updates: list[tuple[str, int, int]] = []             # (slug, old_cents, new_cents)
    variant_updates: list[tuple[str, str, int, int]] = []        # (slug, label, old_cents, new_cents)
    price_fallback: list[tuple[str, int, int]] = []              # (slug, old_cents, new_cents) — match by price
    unseeded_notes: list[str] = []
    unresolved: list[str] = []

    for p, run in zip(products, runs):
        base = g.slugify(p.name)
        slug = base
        i = 2
        while slug in used:
            slug = f"{base}-{i}"
            i += 1
        used.add(slug)
        target_slug = vsync.RENAMED.get(slug, slug)
        single = len(p.sizes) == 1 and not p.colour_options

        for size, row_idx in zip(p.sizes, run["indices"]):
            new_raw = rows9[row_idx][3].strip()
            if not new_raw:
                continue  # sheet has no updated price for this row
            new_cents = round(float(new_raw.replace(",", "")) * 100)
            old_cents = size.price_cents
            if new_cents == old_cents:
                continue  # no actual change

            label = None if single else size.name

            if target_slug in MERGED_REMAP:
                canon, canon_label = MERGED_REMAP[target_slug]
                variant_updates.append((canon, canon_label, old_cents, new_cents))
            elif target_slug in MERGED_SLUG_ONLY:
                canon = MERGED_SLUG_ONLY[target_slug]
                variant_updates.append((canon, label, old_cents, new_cents))
            elif (target_slug, label) in KNOWN_UNSEEDED_SIZES:
                unseeded_notes.append(f"{target_slug} / {label}: Rs {old_cents/100:,.2f} -> "
                                       f"Rs {new_cents/100:,.2f} — size was never seeded, skipped")
            elif label is None:
                single_updates.append((target_slug, old_cents, new_cents))
            elif target_slug in PRICE_FALLBACK_SLUGS:
                price_fallback.append((target_slug, old_cents, new_cents))
            else:
                variant_updates.append((target_slug, label, old_cents, new_cents))

    lines: list[str] = [
        "-- V59 — Price sync to the owner's 2026-08-18 sheet update (\"new mrp\" column), generated by",
        "-- backend/scripts/generate_price_update.py (see that header for scope/method). Only prices",
        "-- change here — category/description/display order are untouched (that's V47's job).",
        "-- Every UPDATE is guarded by the price the sheet says was current (WHERE ... = old cents),",
        "-- the same convention V47 uses, so an admin price edit made after V47 is never clobbered —",
        "-- it just silently no-ops for that one row instead (0 rows updated).",
        "",
    ]
    if unseeded_notes:
        lines.append("-- Sizes priced on the sheet but never seeded (no matching size to update):")
        for n in unseeded_notes:
            lines.append(f"--   {n}")
        lines.append("")

    lines.append(f"-- Single-priced products ({len(single_updates)}).")
    for slug, old_c, new_c in sorted(single_updates):
        lines.append(f"UPDATE product SET single_price_cents = {new_c}\n"
                     f"WHERE slug = {sql_str(slug)} AND single_price_cents = {old_c};")
    lines.append("")

    all_variant = variant_updates + [(s, None, o, n) for s, o, n in price_fallback]
    lines.append(f"-- Variant (per-size) prices ({len(all_variant)}).")
    for slug, label, old_c, new_c in variant_updates:
        lines.append(f"""UPDATE product_variant pv SET price_cents = {new_c}
FROM product p JOIN spec_group sg ON sg.product_id = p.id AND sg.price_affecting
JOIN spec_option so ON so.spec_group_id = sg.id AND so.value = {sql_str(label)}
WHERE p.slug = {sql_str(slug)} AND pv.product_id = p.id
  AND pv.options_signature = so.id::text AND pv.price_cents = {old_c};""")
    lines.append("")

    if price_fallback:
        # One UPDATE per slug (not per row): a single statement's WHERE clause is evaluated against
        # the pre-statement snapshot for every row, so a batch of sequential single-row UPDATEs
        # within one product cannot collide even when an earlier row's NEW price equals a later
        # row's OLD guard price (verified against this exact sheet: scan-white-board 2 1/2 x 1 1/2
        # 1,406.25 -> 1,500.00 and 2 x 2 1,500.00 -> 1,600.00 would otherwise both match on 150000).
        lines.append("-- Legacy size labels that don't textually match this sheet (matched by the "
                      "product's unique old price instead; one statement per product so the whole "
                      "old->new price map is matched against a single before-update snapshot).")
        by_slug: dict[str, list[tuple[int, int]]] = {}
        for slug, old_c, new_c in price_fallback:
            by_slug.setdefault(slug, []).append((old_c, new_c))
        for slug, pairs in by_slug.items():
            values = ", ".join(f"({o},{n})" for o, n in pairs)
            lines.append(f"""UPDATE product_variant pv SET price_cents = v.new_c
FROM product p, (VALUES {values}) AS v(old_c, new_c)
WHERE p.slug = {sql_str(slug)} AND pv.product_id = p.id AND pv.price_cents = v.old_c;""")
        lines.append("")

    lines.append("-- Refresh denormalized price_range_* for every VARIANT product touched above.")
    touched_slugs = sorted({s for s, *_ in variant_updates} | {s for s, *_ in price_fallback})
    lines.append(f"""UPDATE product p SET
    price_range_min_cents = sub.min_c,
    price_range_max_cents = sub.max_c
FROM (SELECT product_id, MIN(price_cents) AS min_c, MAX(price_cents) AS max_c
      FROM product_variant WHERE active GROUP BY product_id) sub
WHERE p.id = sub.product_id AND p.price_mode = 'VARIANT'
  AND p.slug IN ({', '.join(sql_str(s) for s in touched_slugs)});""")
    lines.append("")

    OUT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUT_PATH.name}")
    print(f"  single-price updates: {len(single_updates)}")
    print(f"  variant updates (direct): {len(variant_updates)}")
    print(f"  variant updates (price fallback): {len(price_fallback)}")
    print(f"  unseeded sizes skipped: {len(unseeded_notes)}")
    if unresolved:
        print("UNRESOLVED:", unresolved)


if __name__ == "__main__":
    main()
