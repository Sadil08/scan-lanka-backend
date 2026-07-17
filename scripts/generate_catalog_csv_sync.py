#!/usr/bin/env python3
"""Generate V47 — sync the seeded catalog (V36/V40/V41) to the owner's corrected 2026-07-16 sheet
("Product list Scan Lanka - weight, diamentions (1) - categories .csv").

The corrected CSV re-states the whole catalog and adds what V36 never had:
  - EIGHT numbered top-level storefront groups ("1.) Writing Boards" … "8.) Portable Partition")
    — seeded as product.category_group (V46); the letter-categories of the PDF stay as
    product.category (the middle level); sizes are the variants.
  - a Description column (per size row; identical within a product except per-size fragments).
    Where the corrected sheet blanked a description the earlier Sheet1 CSV still had, the old text
    is used as fallback (the blanking is a spreadsheet merge artifact, not an owner edit).
  - the storefront display order = the sheet's row order ("most going products first")
  - three new rows: Basco Carrom Men Set + the two Architectural Drawing Boards
  - renamed A4 board rows (colour suffix added to the name)
  - two value changes on "Scan Carrom Men Set With Disk" (price, colombo cell -> gate)

Baby Carrom Board appears twice (Sport/Game Boards AND Kids Corner) — a product has one category,
so the first occurrence wins and the duplicate row is skipped (cross-listing unsupported).

It reuses the V36 generator's grouping engine (generate_catalog_seed.py) so size rows collapse
into exactly the same products/slugs V36 created — the script FAILS LOUDLY if any derived slug is
neither already seeded nor in the known-new list, so a re-grouped product can never be inserted
twice. Existing products are only UPDATEd (by slug); description updates keep any admin-typed text
(COALESCE) per the seed contract (catalog-seed.md: seed never overwrites admin edits).
"""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_catalog_seed as g  # noqa: E402  (helpers + grouping engine)

ROOT = Path(__file__).resolve().parents[2]
CSV_PATH = ROOT / "Product list Scan Lanka - weight, diamentions (1) - categories .csv"
OLD_CSV_PATH = ROOT / "Product list Scan Lanka - weight, diamentions (1) - Sheet1.csv"
MIG_DIR = Path(__file__).resolve().parents[1] / "src/main/resources/db/migration"
OUT_PATH = MIG_DIR / "V47__catalog_csv_sync.sql"

GROUP_HEADER = re.compile(r"^(\d+)\s*[.)]+\s*(.+?)\s*$")

# Owner header text -> canonical group value stored on product.category_group.
GROUP_CANONICAL = {
    "writing boards": "Writing Boards",
    "pin up board / notice board": "Pin Up Board / Notice Board",
    "art supplies": "Art Supplies",
    "menu board and other restaurant items": "Menu Board And Other Restaurant Items",
    "sport / game boards": "Sport / Game Boards",
    "kids corner": "Kids Corner",
    "key holder": "Key Holder",
    "portable partition": "Portable Partition",
}

# New-name slug -> already-seeded slug (A4 boards gained a colour suffix on the sheet; we keep the
# seeded slug/URL and just update the display name).
RENAMED = {
    "kids-board-a4-size-white-board-double-sided-white": "kids-board-a4-size-white-board-double-sided",
    "kids-board-a4-size-black-board-double-sided-black": "kids-board-a4-size-black-board-double-sided",
    "kids-board-a4-size-green-board-double-sided-white-green": "kids-board-a4-size-green-board-double-sided",
}

# Slugs that are genuinely new on this sheet (inserted, V36-style).
KNOWN_NEW = {"sport-items-basco-carrom-men-set"}

ARCH_FAMILY = re.compile(r"^arch[ei]tectural drawing board", re.I)

# The corrected sheet promoted three of the old column-A family labels into the numbered group
# headers ("6.) Kids Corner", "7.) Key holder", "8.) Portable Partition"), leaving those blocks
# label-less. Re-inject the legacy label on the block's first data row so the grouping engine
# derives exactly the V36 product names/slugs (e.g. "Kids Board A4 Size …", "Key Holder",
# "Mobile Partition …").
GROUP_FAMILY_LABEL = {
    "Kids Corner": "kids board",
    "Key Holder": "key holder",
    "Portable Partition": "Mobile Partition",
}

# Per-size description fragments (the size dropdown / weight column already carry these).
SIZE_FRAGMENTS = [
    re.compile(r"THICKNESS\s*:\s*\d+\s*MM\s*,?\s*", re.I),
    re.compile(r"WEIGHT\s*:\s*[\d.]+\s*KG\s*,?\s*", re.I),
    re.compile(r",?\s*Qty\s*:\s*\d+\s*pages\s*", re.I),
]


def clean(s: str) -> str:
    s = s.replace("\xa0", " ").replace("’", "'").replace("”", '"').replace("ʺ", '"')
    s = s.replace("–", "-").replace("×", "x")
    return re.sub(r"\s+", " ", s).strip()


def read_rows(path: Path) -> list[list[str]]:
    rows = []
    with open(path, encoding="utf-8", newline="") as f:
        for r in csv.reader(f):
            r = [clean(c) for c in r]
            r += [""] * (8 - len(r))
            r[2] = r[2].replace(",", "")  # MRP thousands separators
            rows.append(r)
    return rows


def read_csv_rows() -> tuple[list[list[str]], list[tuple[int, str]], list[str]]:
    """(rows with duplicates blanked, [(row_index, group_name)], report of skipped dupes)."""
    rows = read_rows(CSV_PATH)
    groups: list[tuple[int, str]] = []
    skipped: list[str] = []
    seen: set[tuple[str, str]] = set()
    out: list[list[str]] = []
    pending_label: str | None = None
    for i, r in enumerate(rows):
        m = GROUP_HEADER.match(r[0]) if not r[1] else None
        if m:
            key = m.group(2).strip().lower()
            if key not in GROUP_CANONICAL:
                raise SystemExit(f"ERROR: unknown top-level group header '{r[0]}' (row {i})")
            canonical = GROUP_CANONICAL[key]
            groups.append((i, canonical))
            pending_label = GROUP_FAMILY_LABEL.get(canonical)
            out.append([""] * 8)  # header row acts as a block separator for the grouping engine
            continue
        if r[1]:
            key = (r[1].lower(), r[2])
            if key in seen:
                skipped.append(f"row {i}: duplicate '{r[1]}' (cross-listed) — first occurrence wins")
                out.append([""] * 8)
                continue
            seen.add(key)
            if pending_label is not None:
                if not r[0]:
                    r = [pending_label] + r[1:]
                pending_label = None
        out.append(r)
    if len(groups) != len(GROUP_CANONICAL):
        raise SystemExit(f"ERROR: expected {len(GROUP_CANONICAL)} group headers, found {len(groups)}")
    return out, groups, skipped


def group_for(index: int, groups: list[tuple[int, str]]) -> str:
    name = None
    for start, gname in groups:
        if start <= index:
            name = gname
    if name is None:
        raise SystemExit(f"ERROR: row {index} precedes the first group header")
    return name


def product_description(descs: list[str], fallbacks: list[str]) -> str | None:
    """First non-empty row description (old-sheet fallback); strip per-size fragments on variance."""
    nonempty = [d for d in descs if d] or [d for d in fallbacks if d]
    if not nonempty:
        return None
    desc = nonempty[0]
    if len(set(nonempty)) > 1:
        for pat in SIZE_FRAGMENTS:
            desc = pat.sub("", desc)
    desc = re.sub(r"^\(\s*tag line\s*\)\s*", "", desc, flags=re.I)
    desc = re.sub(r"\s+", " ", desc).strip(" ,")
    return desc or None


# --- run bookkeeping: same grouping as g.parse(), but also capture desc + first row index ---------

def collect_runs(rows: list[list[str]], start: int, old_desc_by_name: dict[str, str]) -> list[dict]:
    """Replicates g.parse()/parse_family_block run segmentation, keeping descriptions and order."""
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
                continue  # fully unpriced run — parse_family_block drops it too
            runs.append({
                "first_index": priced[0][0],
                "descs": [r[7] for _, r in priced],
                "fallback_descs": [old_desc_by_name.get(r[1].lower(), "") for _, r in priced],
            })
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


# --- architectural drawing boards (family label defeats the generic engine: special-cased) --------

def extract_architectural(rows: list[list[str]]) -> tuple[list[list[str]], list[dict]]:
    """Pull the Architectural Drawing Board rows out; return (remaining_rows, [product dicts])."""
    remaining: list[list[str]] = []
    products: list[dict] = []
    current: dict | None = None
    for idx, r in enumerate(rows):
        colA, name = r[0].strip(), r[1].strip()
        if ARCH_FAMILY.match(colA):
            wood = re.search(r"\(([^)]+)\)", colA)
            label = wood.group(1).strip() if wood else "Wood"
            pname = f"Architectural Drawing Board ( {label} )"
            current = {"name": pname, "first_index": idx, "sizes": []}
            products.append(current)
            remaining.append([""] * 8)  # keep row indexes stable for the rest of the sheet
        elif current is not None and name and not colA:
            remaining.append([""] * 8)
        else:
            current = None if not name else current
            remaining.append(r)
            continue
        # this row is an architectural size row
        colombo = g.parse_cell("colombo", r[4])
        suburb = g.parse_cell("suburb", r[5])
        outer = g.parse_cell("outer", r[6])
        current["sizes"].append({
            "label": name,
            "price_cents": round(float(r[2]) * 100),
            "weight_kg": float(r[3]) if r[3] else None,
            "colombo": colombo, "suburb": suburb, "outer": outer,
        })
    return remaining, products


# --- SQL emission ----------------------------------------------------------------------------------

def sql_str(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def main() -> None:
    rows, groups, dup_report = read_csv_rows()
    old_desc_by_name = {}
    for r in read_rows(OLD_CSV_PATH):
        if r[1] and r[7]:
            old_desc_by_name.setdefault(r[1].lower(), r[7])

    rows, arch_products = extract_architectural(rows)
    assert len(arch_products) == 2 and all(len(p["sizes"]) == 3 for p in arch_products), \
        f"architectural block parse drifted: {[(p['name'], len(p['sizes'])) for p in arch_products]}"

    g.read_sheet1_rows = lambda: rows
    products = g.parse()

    start = next(i for i, r in enumerate(rows) if r[0].strip().lower().startswith("model no")) + 1
    runs = collect_runs(rows, start, old_desc_by_name)
    assert len(runs) == len(products), \
        f"run bookkeeping drifted from the grouping engine: {len(runs)} runs vs {len(products)} products"

    existing = set()
    for mig in ("V36__seed_catalog.sql", "V41__scan_white_board_missing_sizes.sql"):
        existing |= set(re.findall(r"'([a-z0-9-]+)', 'SEED-", (MIG_DIR / mig).read_text()))

    used: set[str] = set()
    updates: list[tuple[str, object, int, str | None]] = []  # (slug, product, order, desc)
    inserts: list[tuple[object, int, str | None, str]] = []
    renames: list[tuple[str, str, str]] = []                 # (slug, new_engine_name, new_slug)
    category_group: dict[str, set[str]] = {}                 # category -> group names seen

    for p, run in zip(products, runs):
        base = g.slugify(p.name)
        slug = base
        i = 2
        while slug in used:
            slug = f"{base}-{i}"
            i += 1
        used.add(slug)
        desc = product_description(run["descs"], run["fallback_descs"])
        order = run["first_index"]
        grp = group_for(order, groups)
        # The A4 canvas board is category-corrected to Canvas below; group by effective category.
        eff_category = "Canvas" if slug == "scan-canvas-board-a4-size-canvas-board" else p.category
        eff_category = ("Glass Writing Board with Stand"
                        if slug == "glass-board-with-stand" else eff_category)
        category_group.setdefault(eff_category, set()).add(grp)

        if slug in RENAMED:
            target = RENAMED[slug]
            renames.append((target, p.name, slug))
            updates.append((target, p, order, desc))
        elif slug in existing:
            updates.append((slug, p, order, desc))
        elif slug in KNOWN_NEW:
            if slug == "sport-items-basco-carrom-men-set":
                # The sheet leaves Basco's zone cells blank — they are merged with the sport-items
                # block's first row ("bill value must be more than 6,000" for all three zones), and
                # the engine's carry-forward only runs within a product run. Inherit the block state
                # its siblings (Wining Disk, Dam Men Set, Borics) all carry.
                s = p.sizes[0]
                s.colombo = g.CellState("gate", 600000)
                s.suburb = g.CellState("gate", 600000)
                s.outer = g.CellState("whatsapp")
                s.lorry_outer_whatsapp = True
            inserts.append((p, order, desc, grp))
        else:
            raise SystemExit(f"ERROR: derived slug '{slug}' ({p.name}) is neither seeded nor known-new "
                             f"— refusing to emit (possible duplicate-product regrouping)")

    covered = {s for s, *_ in updates}
    missing = existing - covered
    if missing:
        raise SystemExit(f"ERROR: seeded products not matched by the CSV: {sorted(missing)}")

    for p in arch_products:
        category_group.setdefault("Architectural Drawing Board", set()).add(
            group_for(p["first_index"], groups))
    conflicts = {c: gs for c, gs in category_group.items() if len(gs) > 1}
    if conflicts:
        raise SystemExit(f"ERROR: category mapped to more than one top-level group: {conflicts}")

    lines: list[str] = [
        "-- V47 — Catalog sync to the owner's corrected 2026-07-16 product sheet (generated by",
        "-- backend/scripts/generate_catalog_csv_sync.py — see that header for scope).",
        "-- Existing products are updated by slug; description keeps admin-typed text (COALESCE);",
        "-- value fixes are guarded by the current seeded value so admin edits survive re-runs.",
        "",
        "-- V40 re-fix, keyed by slug: V40 moved the A4-size canvas board to 'Canvas' guarded by a",
        "-- hardcoded product id (383), which is a no-op on any freshly-seeded database where ids",
        "-- differ. Same correction, robust key (runs before the category_group updates below).",
        "UPDATE product SET category = 'Canvas'\n"
        "WHERE slug = 'scan-canvas-board-a4-size-canvas-board' AND category = 'A4 Size Writing Board';",
        "",
        "-- PDF letters G and H are separate categories; V36's category map filed the with-stand",
        "-- glass board under G ('glass board' matched before 'glass board with stand').",
        "UPDATE product SET category = 'Glass Writing Board with Stand'\n"
        "WHERE slug = 'glass-board-with-stand' AND category = 'Glass Writing Board';",
        "",
        "-- Top-level storefront groups (the sheet's eight numbered headers, V46 category_group).",
    ]
    for cat in sorted(category_group):
        grp = next(iter(category_group[cat]))
        lines.append(f"UPDATE product SET category_group = {sql_str(grp)} WHERE category = {sql_str(cat)};")
    lines.append("")

    lines.append("-- Storefront order + sheet descriptions, per product (sheet row order = display order).")
    for note in dup_report:
        lines.append(f"-- note: {note}")
    for slug, _p, order, desc in sorted(updates, key=lambda t: t[2]):
        set_desc = f",\n    description = COALESCE(NULLIF(description, ''), {sql_str(desc)})" if desc else ""
        lines.append(f"UPDATE product SET display_order = {order}{set_desc}\nWHERE slug = {sql_str(slug)};")
    lines.append("")

    lines.append("-- A4 boards: the sheet added the colour to the name; slug (URL) stays the seeded one.")
    for slug, new_name, _new_slug in renames:
        # V36 named these "Kids Board A4 Size ... ( Double Sided )"; the sheet's block prefix is
        # unchanged so the new engine name carries the same prefix — strip it for the display name
        # like the sheet's own row text (owner writes the row without the block label).
        display = re.sub(r"^Kids Board ", "", new_name)
        lines.append(f"UPDATE product SET name = {sql_str(display)}\n"
                     f"WHERE slug = {sql_str(slug)} AND name LIKE 'Kids Board A4 Size%( Double Sided )';")
    lines.append("")

    lines.append("-- Value changes on this sheet vs V36 (guarded by the seeded values):")
    lines.append("-- Scan Carrom Men Set With Disk: Rs 875 -> Rs 800; colombo flat Rs 500 -> Rs 6,000 bill gate.")
    lines.append(
        "UPDATE product SET single_price_cents = 80000,\n"
        "    lorry_colombo_cents = NULL, lorry_colombo_gate_cents = 600000\n"
        "WHERE slug = 'sport-items-scan-carrom-men-set-with-disk' AND single_price_cents = 87500;")
    lines.append("")

    lines.append("-- New products on this sheet (same idempotent shape as V36).")
    for p, order, desc, grp in inserts:
        s = p.sizes[0]
        slug = g.slugify(p.name)
        sku = "SEED-" + slug.upper().replace("-", "")[:40]
        colombo_c, colombo_g = g.cell_columns(s.colombo)
        suburb_c, suburb_g = g.cell_columns(s.suburb)
        outer_c, outer_g = g.cell_columns(s.outer)
        lines.append(f"""INSERT INTO product (name, slug, sku, category, category_group, price_mode,
    single_price_cents, weight_kg,
    board_size_tier, lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
    lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
    lorry_outer_whatsapp, courier_outer_blocked, description, display_order, active)
VALUES ({sql_str(p.name)}, {sql_str(slug)}, {sql_str(sku)}, {sql_str(p.category)}, {sql_str(grp)}, 'SINGLE',
    {s.price_cents}, {s.weight_kg if s.weight_kg is not None else 'NULL'},
    {sql_str(s.tier) if s.tier else 'NULL'}, {colombo_c}, {suburb_c}, {outer_c},
    {colombo_g}, {suburb_g}, {outer_g}, {str(s.lorry_outer_whatsapp).lower()},
    {str(s.courier_outer_blocked).lower()}, {sql_str(desc) if desc else 'NULL'}, {order}, true)
ON CONFLICT (slug) DO NOTHING;""")
        lines.append("")

    lines.append("-- Architectural Drawing Boards (new family; inch sizes => couriable UNDER_2FT tier;")
    lines.append("-- outer column says 'contact whatsapp' => WhatsApp contact for outer lorry).")
    for p in arch_products:
        slug = g.slugify(p["name"])
        sku = "SEED-" + slug.upper().replace("-", "")[:40]
        grp = group_for(p["first_index"], groups)
        prices = [s["price_cents"] for s in p["sizes"]]
        opt_vals = ",".join(f"({sql_str(s['label'])},{i})" for i, s in enumerate(p["sizes"]))
        value_rows = []
        for s in p["sizes"]:
            colombo_c, colombo_g = g.cell_columns(s["colombo"])
            suburb_c, suburb_g = g.cell_columns(s["suburb"])
            weight_sql = f"{s['weight_kg']}::numeric" if s["weight_kg"] is not None else "NULL::numeric"
            def cents(v: str) -> str:
                return "NULL::bigint" if v == "NULL" else v
            value_rows.append(
                f"({sql_str(s['label'])},{s['price_cents']},{weight_sql},"
                f"{cents(colombo_c)},{cents(suburb_c)},{cents(colombo_g)},{cents(suburb_g)})")
        lines.append(f"""WITH prod AS (
    INSERT INTO product (name, slug, sku, category, category_group, price_mode,
        price_range_min_cents, price_range_max_cents, display_order, active)
    VALUES ({sql_str(p['name'])}, {sql_str(slug)}, {sql_str(sku)}, 'Architectural Drawing Board', {sql_str(grp)}, 'VARIANT',
        {min(prices)}, {max(prices)}, {p['first_index']}, true)
    ON CONFLICT (slug) DO NOTHING RETURNING id
), grp AS (
    INSERT INTO spec_group (product_id, name, price_affecting, display_order)
    SELECT id, 'Size', true, 0 FROM prod
    RETURNING id, product_id
), opts AS (
    INSERT INTO spec_option (spec_group_id, value, display_order)
    SELECT grp.id, v.value, v.ord FROM grp,
        (VALUES {opt_vals}) AS v(value, ord)
    RETURNING id, spec_group_id, value
)
INSERT INTO product_variant (product_id, sku, price_cents, weight_kg,
    board_size_tier, lorry_colombo_cents, lorry_suburb_cents,
    lorry_colombo_gate_cents, lorry_suburb_gate_cents,
    lorry_outer_whatsapp, courier_outer_blocked, options_signature)
SELECT grp.product_id, {sql_str(sku)} || '-' || opts.id, v.price_cents, v.weight_kg, 'UNDER_2FT',
    v.colombo_c, v.suburb_c, v.colombo_g, v.suburb_g, true, false,
    opts.id::text
FROM opts JOIN grp ON opts.spec_group_id = grp.id
JOIN (VALUES {','.join(value_rows)})
    AS v(value, price_cents, weight_kg, colombo_c, suburb_c, colombo_g, suburb_g)
    ON v.value = opts.value
ON CONFLICT (product_id, options_signature) DO NOTHING;""")
        lines.append("")

    OUT_PATH.write_text("\n".join(lines), encoding="utf-8")
    with_desc = sum(1 for *_x, d in updates if d)
    print(f"Wrote {OUT_PATH.name}: {len(updates)} product updates ({with_desc} with descriptions), "
          f"{len(renames)} renames, {len(inserts) + len(arch_products)} inserts, "
          f"{len(category_group)} category->group rows")
    for note in dup_report:
        print("  note:", note)


if __name__ == "__main__":
    main()
