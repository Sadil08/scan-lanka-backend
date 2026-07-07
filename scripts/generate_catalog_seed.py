#!/usr/bin/env python3
"""Generate the catalog seed migration from the owner's product sheet (01/catalog-seed.md).

Reads `Product list Scan Lanka - weight, diamentions and citrek and Fardar courier.xlsx` (Sheet1)
directly (stdlib zipfile + ElementTree — no new deps) and emits an idempotent Flyway migration:
categories (free-text `product.category`), products, size variants, delivery cells (lorry
price/gate/enabled per zone, courier tier, the two outer-restriction flags).

This is a **starting point, not a source of truth** (catalog-seed.md): every seeded field is
admin-editable afterward. The parser applies a generic "family block + common base name" grouping
engine plus the explicit owner rules from catalog-seed.md §1.2/1.3; ambiguous or unparseable rows are
still seeded (as their own single-price product) rather than silently dropped, and every heuristic
decision is listed in the printed report for review.
"""

from __future__ import annotations

import re
import sys
import unicodedata
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from xml.etree import ElementTree as ET

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
      "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships"}

XLSX_PATH = Path(__file__).resolve().parents[2] / (
    "Product list Scan Lanka - weight, diamentions and citrek and Fardar courier.xlsx")
OUT_PATH = Path(__file__).resolve().parents[1] / "src/main/resources/db/migration/V36__seed_catalog.sql"

# --- category taxonomy (hand-checked map, PDF wins for taxonomy — catalog-seed.md §2) ---------------

CATEGORY_BY_FAMILY: dict[str, str] = {
    "white board": "White Board",
    "white board stand": "White Board",
    "magnetic white board": "White Board",
    "ceramic steel magnetic": "White Board",
    "green board": "Green Board",
    "green magnetic board": "Green Board",
    "black board": "Black Board",
    "five rule": "Five Rule",
    "eco board": "Eco Board",
    "glass board": "Glass Writing Board",
    "glass board with stand": "Glass Writing Board with Stand",
    "pin board": "Pin Board / Notice Board",
    "cork board": "Pin Board / Notice Board",
    "combination board": "Pin Board / Notice Board",
    "sliding glass notice board": "Pin Board / Notice Board",
    "front door glass notice board": "Pin Board / Notice Board",
    "shock door glass notice board": "Pin Board / Notice Board",
    "moderation zopp board": "Pin Board / Notice Board",
    "flip chart": "Flip Chart and Paper Set",
    "canvas": "Canvas",
    "menu board": "Menu Board",
    "table top": "Menu Board",
    "buffet tag": "Menu Board",
    "price tag": "Menu Board",
    "acrylic name board": "Menu Board",
    "wooden easel": "Wooden Easel",
    "carrom board": "Carrom Board",
    "sport items": "Carrom Board",
    "dam board": "Dam / Chess Board",
    "chess board": "Dam / Chess Board",
    "kids board": "Kids Board",
    "a4 size": "A4 Size Writing Board",
    "key holder": "Key Holder",
    "mobile partition": "Mobile Partition",
}

# family_label keywords (lowercased) that force category O even inside the "sport items" block
DAM_CHESS_KEYWORDS = ("dam men set",)

# --- typo / phrasing normalization (owner sheet has a few) -------------------------------------------

TYPO_FIXES = [
    (re.compile(r"\bSri Lakan\b", re.I), "Sri Lankan"),
    (re.compile(r"\bSpecial Teal\b", re.I), "Special Teak"),
    (re.compile(r"\s{2,}"), " "),
]

# --- size-token stripping (to derive a product's "base name" across its size rows) ------------------

FT_DIM = re.compile(
    r"\b\d+(?:\.\d+)?(?:\s*\d+/\d+)?\s*[xX]\s*\d+(?:\.\d+)?(?:\s*\d+/\d+)?(?:\s*(?:[Ii]nch(?:es)?|\"))?\b")
FT_SINGLE = re.compile(r"\b\d+'\s*[Ff]t\b")
QUOTE_DIM = re.compile(r"\b\d+\"\s*[xX]\s*\d+\"")
MM_DIM = re.compile(r"\b\d+\s*mm\b", re.I)
KEYS = re.compile(r"\(\s*\d+\s*keys\s*\)", re.I)
STAND_INCH = re.compile(r"\(\s*\d+\"\s*\)")
PAGES = re.compile(r"\b\d+\s*[Pp]ages\b")
COLOUR_WORD = re.compile(r"\b(?:Blue|Maroon|Green)\s+Colour\b", re.I)

FRACTION = {"1/2": 0.5, "1/4": 0.25, "3/4": 0.75}


def normalize_ws(s: str) -> str:
    return re.sub(r"\s+", " ", s).strip()


def fix_typos(s: str) -> str:
    for pat, repl in TYPO_FIXES:
        s = pat.sub(repl, s)
    return s


def parse_ft_pair(name: str) -> tuple[float, float] | None:
    """First 'A x B' dimension in the name, both sides as floats (feet)."""
    m = FT_DIM.search(name)
    if not m:
        return None
    text = m.group(0)
    if '"' in text or "inch" in text.lower():
        return None  # inch dimension, not feet — handled by has_inch() instead
    parts = re.split(r"[xX]", text)
    if len(parts) != 2:
        return None
    def to_float(tok: str) -> float:
        tok = tok.strip()
        whole = re.match(r"\d+(?:\.\d+)?", tok)
        base = float(whole.group(0)) if whole else 0.0
        for frac, val in FRACTION.items():
            if frac in tok:
                base += val
        return base
    try:
        return (to_float(parts[0]), to_float(parts[1]))
    except ValueError:
        return None


def has_inch(name: str) -> bool:
    return bool(re.search(r'inch|"', name, re.I))


def strip_size_tokens(name: str) -> str:
    s = name
    s = COLOUR_WORD.sub(" ", s)
    s = QUOTE_DIM.sub(" ", s)
    s = FT_DIM.sub(" ", s)
    s = FT_SINGLE.sub(" ", s)
    s = MM_DIM.sub(" ", s)
    s = KEYS.sub(" ", s)
    s = STAND_INCH.sub(" ", s)
    s = PAGES.sub(" ", s)
    return normalize_ws(s)


def size_label(name: str) -> str:
    """The part of the name that IS the size — used as the Size spec-option value."""
    for pat in (QUOTE_DIM, FT_DIM, FT_SINGLE, MM_DIM, KEYS, STAND_INCH, PAGES):
        m = pat.search(name)
        if m:
            return normalize_ws(m.group(0))
    return name  # no size token found — whole (unique) name is the "size"


# --- xlsx reading (stdlib only) -----------------------------------------------------------------

def read_sheet1_rows() -> list[list[str]]:
    z = zipfile.ZipFile(XLSX_PATH)
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    relmap = {r.get("Id"): r.get("Target") for r in rels}
    try:
        ss = ET.fromstring(z.read("xl/sharedStrings.xml"))
        strings = ["".join(t.text or "" for t in si.iter("{%s}t" % NS["m"])) for si in ss.findall("m:si", NS)]
    except KeyError:
        strings = []

    def colnum(ref: str) -> int:
        m = re.match(r"([A-Z]+)", ref)
        n = 0
        for c in m.group(1):
            n = n * 26 + ord(c) - 64
        return n

    sheet1_target = None
    for sh in wb.find("m:sheets", NS):
        if sh.get("name") == "Sheet1":
            rid = sh.get("{%s}id" % NS["r"])
            tgt = relmap[rid]
            sheet1_target = tgt if tgt.startswith("xl/") else "xl/" + tgt
            break
    if sheet1_target is None:
        raise SystemExit("Sheet1 not found in workbook")

    root = ET.fromstring(z.read(sheet1_target))
    rows: list[list[str]] = []
    for row in root.iter("{%s}row" % NS["m"]):
        cells: dict[int, str] = {}
        for c in row.iter("{%s}c" % NS["m"]):
            v = c.find("m:v", NS)
            if v is None:
                iv = c.find("m:is", NS)
                val = "".join(t.text or "" for t in iv.iter("{%s}t" % NS["m"])) if iv is not None else ""
            else:
                val = strings[int(v.text)] if c.get("t") == "s" else v.text
            cells[colnum(c.get("r"))] = (val or "").strip()
        mx = max(cells) if cells else 0
        rows.append([cells.get(i, "") for i in range(1, max(mx, 7) + 1)])
    return rows


# --- cell-state parsing (colombo / suburb / outer) -------------------------------------------------

GATE_RE = re.compile(r"more than\s*([\d,]+)", re.I)
WHATSAPP_RE = re.compile(r"whatsapp", re.I)


@dataclass
class CellState:
    kind: str  # 'blank' | 'flat' | 'gate' | 'whatsapp'
    cents: int | None = None       # flat price OR gate threshold, in cents


def parse_cell(zone: str, text: str) -> CellState:
    text = text.strip()
    if not text:
        return CellState("blank")
    if WHATSAPP_RE.search(text):
        return CellState("whatsapp")
    m = GATE_RE.search(text)
    if m:
        n = int(m.group(1).replace(",", ""))
        if zone == "outer":
            return CellState("whatsapp")  # sheet's outer "more than N" text = contact us, not a paid gate
        return CellState("gate", n * 100)
    # plain number, possibly with trailing "/-"
    num = re.sub(r"[^\d.]", "", text)
    if not num:
        return CellState("blank")
    return CellState("flat", round(float(num) * 100))


# --- product model --------------------------------------------------------------------------------

@dataclass
class SizeRow:
    name: str
    price_cents: int
    weight_kg: float | None
    colombo: CellState
    suburb: CellState
    outer: CellState
    tier: str | None
    courier_outer_blocked: bool
    lorry_outer_whatsapp: bool


@dataclass
class ProductSeed:
    name: str
    category: str
    sizes: list[SizeRow] = field(default_factory=list)
    colour_options: list[str] | None = None
    whatsapp_only: bool = False  # whole product has no automated rail (e.g. fully unpriced size)


def category_for(family_label: str, first_row_name: str) -> str:
    fl = family_label.lower()
    name_l = first_row_name.lower()
    if any(k in name_l for k in DAM_CHESS_KEYWORDS):
        return "Dam / Chess Board"
    if "a4 size" in name_l:
        return "A4 Size Writing Board"
    if "kids board" in name_l and "a4" not in name_l:
        return "Kids Board"
    for key, cat in CATEGORY_BY_FAMILY.items():
        if key in fl:
            return cat
    for key, cat in CATEGORY_BY_FAMILY.items():
        if key in name_l:
            return cat
    return "Uncategorized"


NOT_COURIABLE_FAMILY = ("glass board", "sliding glass notice board", "front door glass notice board",
                        "shock door glass notice board", "key holder", "mobile partition")


def compute_tier(family_label: str, raw_name: str, weight_kg: float | None) -> str | None:
    fl = family_label.lower()
    if any(k in fl for k in NOT_COURIABLE_FAMILY):
        return None
    dims = parse_ft_pair(raw_name)
    if has_inch(raw_name):
        return "UNDER_2FT"
    if dims:
        return "UNDER_2FT" if max(dims) < 2.0 else "BETWEEN_2FT_6FT"
    lname = raw_name.lower()
    if "easel" in lname or "boric" in lname or "paper set" in lname:
        return "UNDER_2FT"
    if "flip chart" in lname:
        return "BETWEEN_2FT_6FT"
    if weight_kg is not None and weight_kg >= 5:
        return "BETWEEN_2FT_6FT"
    return "UNDER_2FT"


OUTER_BLOCK_PAIRS = {(3.0, 6.0), (4.0, 6.0), (4.0, 8.0)}
OUTER_WHATSAPP_DIM_PAIRS = {(4.0, 6.0), (4.0, 8.0)}


def outer_flags(raw_name: str) -> tuple[bool, bool]:
    dims = parse_ft_pair(raw_name)
    if not dims:
        return False, False
    pair = tuple(sorted(dims))
    return pair in OUTER_BLOCK_PAIRS, pair in OUTER_WHATSAPP_DIM_PAIRS


def core_words(label: str) -> set[str]:
    stop = {"of", "the", "a", "an", "with", "and", "for", "board", "only"}
    return {w for w in re.findall(r"[a-z]+", label.lower()) if w not in stop and len(w) > 2}


def is_long_note(text: str) -> bool:
    t = text.strip()
    if not t:
        return True
    if len(t.split()) > 6:
        return True
    return t.startswith("(") or t.lower().startswith("this ")


# --- main parse ------------------------------------------------------------------------------------

REPORT: list[str] = []
SKIPPED: list[str] = []


def parse() -> list[ProductSeed]:
    rows = read_sheet1_rows()
    # locate the header row ("Model No") to know where data starts
    start = next(i for i, r in enumerate(rows) if r[0].strip().lower().startswith("model no")) + 1

    products: list[ProductSeed] = []
    block: list[list[str]] = []
    family_label = ""

    def flush_block():
        nonlocal block, family_label
        if block:
            products.extend(parse_family_block(family_label, block))
        block = []
        family_label = ""

    for r in rows[start:]:
        name = r[1].strip() if len(r) > 1 else ""
        colA = r[0].strip() if r else ""
        is_blank = not name
        if is_blank:
            flush_block()
            continue
        if colA and not is_long_note(colA) and not block:
            family_label = colA
        block.append(r)
        if name.strip().lower().startswith(("citrek currier", "fardar currier")) or \
           re.match(r"^\d+\s*kg\s*$", name.strip(), re.I) or name.strip().lower() == "other":
            block.pop()  # stray rate-card leftover rows in the sheet — not products
    flush_block()
    return products


def parse_family_block(family_label: str, rows: list[list[str]]) -> list[ProductSeed]:
    # Key Holder rows are pure "{dimension} ({N} keys)" descriptors with no shared product name in
    # column B at all (the branding lives only in column A) — and two rows can share the exact same
    # dimension with different key counts ("2 X 1 1/2 ft (20 keys)" / "(25 keys)"), so stripping the
    # dimension the way the generic engine does for board families would either collapse everything
    # into one meaningless base ("ft") or produce duplicate size labels. Special-case it: one product,
    # every row's own descriptor is the (already-unique) size label.
    if "key holder" in family_label.lower():
        runs = [(family_label.lower(), rows)]
    else:
        # split the block into runs of rows sharing the same normalized base name
        runs = []
        for r in rows:
            raw_name = fix_typos(normalize_ws(r[1]))
            base = strip_size_tokens(raw_name) or raw_name
            base_key = base.lower()
            if runs and runs[-1][0] == base_key:
                runs[-1][1].append(r)
            else:
                runs.append((base_key, [r]))

    out: list[ProductSeed] = []
    fam_words = core_words(family_label) if family_label else set()
    is_key_holder = "key holder" in family_label.lower()
    for base_key, run_rows in runs:
        first_raw = fix_typos(normalize_ws(run_rows[0][1]))
        if is_key_holder:
            product_name = "Key Holder"
        else:
            base = strip_size_tokens(first_raw) or first_raw
            if fam_words and not (fam_words & core_words(base)):
                product_name = normalize_ws(f"{family_label.title()} {base}")
            else:
                product_name = base
        category = category_for(family_label, first_raw)

        sizes: list[SizeRow] = []
        for r in run_rows:
            raw_name = fix_typos(normalize_ws(r[1]))
            mrp = r[2].strip() if len(r) > 2 else ""
            weight_raw = r[3].strip() if len(r) > 3 else ""
            colombo_raw = r[4].strip() if len(r) > 4 else ""
            suburb_raw = r[5].strip() if len(r) > 5 else ""
            outer_raw = r[6].strip() if len(r) > 6 else ""

            if not mrp:
                SKIPPED.append(f"'{raw_name}' (family '{family_label}') — no price on the sheet, "
                               f"seed skips it; add manually once the owner prices it")
                continue
            price_cents = round(float(mrp) * 100)
            weight_kg = float(weight_raw) if weight_raw else None

            colombo = parse_cell("colombo", colombo_raw)
            suburb = parse_cell("suburb", suburb_raw)
            outer = parse_cell("outer", outer_raw)

            tier = compute_tier(family_label, raw_name, weight_kg)
            dim_blocked, dim_wa = outer_flags(raw_name)
            lorry_outer_wa = dim_wa or outer.kind == "whatsapp"
            courier_blocked = dim_blocked

            label = raw_name if is_key_holder else size_label(raw_name)
            sizes.append(SizeRow(label, price_cents, weight_kg,
                                 colombo, suburb, outer, tier, courier_blocked, lorry_outer_wa))

        if not sizes:
            continue  # every row in this run was unpriced (e.g. Glass Board 8x4 alone)

        # carry-forward: a blank cell inherits the last EXPLICIT state in that column, within this run
        for col in ("colombo", "suburb", "outer"):
            last: CellState = CellState("blank")
            for s in sizes:
                cur: CellState = getattr(s, col)
                if cur.kind == "blank":
                    setattr(s, col, last)
                else:
                    last = cur
        # lorry_outer_whatsapp was computed from each row's OWN (pre-carry-forward) outer cell; a row
        # whose outer cell was blank and inherited a 'whatsapp' state from an earlier row in the run
        # must pick up the flag too, so re-derive after carry-forward runs.
        for s in sizes:
            if s.outer.kind == "whatsapp":
                s.lorry_outer_whatsapp = True

        colour_options = None
        if len(run_rows) > 0 and any(is_long_note(r[0]) and "blue, maroon and green" in r[0].lower()
                                     for r in rows):
            colour_options = ["Blue", "Maroon", "Green"]

        out.append(ProductSeed(product_name, category, sizes, colour_options))
    return out


# --- SQL emission ------------------------------------------------------------------------------------

def slugify(name: str) -> str:
    s = unicodedata.normalize("NFKD", name)
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = re.sub(r"[^a-zA-Z0-9]+", "-", s.lower()).strip("-")
    return s or "product"


def sql_str(s: str) -> str:
    return "'" + s.replace("'", "''") + "'"


def cents_or_null(v: int | None) -> str:
    return str(v) if v is not None else "NULL"


def cell_columns(cell: CellState) -> tuple[str, str]:
    """(cents_sql, gate_cents_sql) for a lorry_<zone>_cents / lorry_<zone>_gate_cents pair."""
    if cell.kind == "flat":
        return cents_or_null(cell.cents), "NULL"
    if cell.kind == "gate":
        return "NULL", cents_or_null(cell.cents)
    return "NULL", "NULL"


def emit(products: list[ProductSeed]) -> str:
    lines: list[str] = [
        "-- V36 — Catalog seed from the owner's product sheet (01/catalog-seed.md).",
        "-- Generated by backend/scripts/generate_catalog_seed.py — a starting point, not a source of",
        "-- truth: every field here is admin-editable afterward. Re-running the generator only ever",
        "-- produces INSERTs guarded by slug/sku uniqueness (ON CONFLICT DO NOTHING) so a later Flyway",
        "-- repair/reseed never overwrites admin edits. Money in integer LKR cents.",
        "",
    ]
    used_slugs: set[str] = set()
    total_variants = 0
    per_category: dict[str, int] = {}

    for p in products:
        base_slug = slugify(p.name)
        slug = base_slug
        i = 2
        while slug in used_slugs:
            slug = f"{base_slug}-{i}"
            i += 1
        used_slugs.add(slug)
        sku = "SEED-" + slug.upper().replace("-", "")[:40]
        per_category[p.category] = per_category.get(p.category, 0) + 1

        lines.append(f"-- {p.name}  ({p.category}, {len(p.sizes)} size{'s' if len(p.sizes) != 1 else ''})")
        if len(p.sizes) == 1 and not p.colour_options:
            s = p.sizes[0]
            colombo_c, colombo_g = cell_columns(s.colombo)
            suburb_c, suburb_g = cell_columns(s.suburb)
            outer_c, outer_g = cell_columns(s.outer)
            lines.append(f"""WITH ins AS (
    INSERT INTO product (name, slug, sku, category, price_mode, single_price_cents, weight_kg,
        board_size_tier, lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
        lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
        lorry_outer_whatsapp, courier_outer_blocked, active)
    VALUES ({sql_str(p.name)}, {sql_str(slug)}, {sql_str(sku)}, {sql_str(p.category)}, 'SINGLE',
        {s.price_cents}, {s.weight_kg if s.weight_kg is not None else 'NULL'},
        {sql_str(s.tier) if s.tier else 'NULL'}, {colombo_c}, {suburb_c}, {outer_c},
        {colombo_g}, {suburb_g}, {outer_g}, {str(s.lorry_outer_whatsapp).lower()},
        {str(s.courier_outer_blocked).lower()}, true)
    ON CONFLICT (slug) DO NOTHING RETURNING id
)
SELECT 1;""")
            total_variants += 1
        else:
            lines.append(f"""WITH prod AS (
    INSERT INTO product (name, slug, sku, category, price_mode, active)
    VALUES ({sql_str(p.name)}, {sql_str(slug)}, {sql_str(sku)}, {sql_str(p.category)}, 'VARIANT', true)
    ON CONFLICT (slug) DO NOTHING RETURNING id
), grp AS (
    INSERT INTO spec_group (product_id, name, price_affecting, display_order)
    SELECT id, 'Size', true, 0 FROM prod
    RETURNING id, product_id
), opts AS (
    INSERT INTO spec_option (spec_group_id, value, display_order)
    SELECT grp.id, v.value, v.ord FROM grp,
        (VALUES {','.join(f"({sql_str(s.name)},{i})" for i, s in enumerate(p.sizes))}) AS v(value, ord)
    RETURNING id, spec_group_id, value
)""")
            if p.colour_options:
                lines.append(f""", cgrp AS (
    INSERT INTO spec_group (product_id, name, price_affecting, display_order)
    SELECT product_id, 'Colour', false, 1 FROM grp
    RETURNING id
), copts AS (
    INSERT INTO spec_option (spec_group_id, value, display_order)
    SELECT cgrp.id, v.value, v.ord FROM cgrp,
        (VALUES {','.join(f"({sql_str(c)},{i})" for i, c in enumerate(p.colour_options))}) AS v(value, ord)
    RETURNING id
)""")
            values_rows = []
            for i, s in enumerate(p.sizes):
                colombo_c, colombo_g = cell_columns(s.colombo)
                suburb_c, suburb_g = cell_columns(s.suburb)
                outer_c, outer_g = cell_columns(s.outer)
                # explicit casts: when an entire VALUES column is NULL across every row for a product
                # (e.g. glass boards have no gate cents anywhere), Postgres infers that column as
                # untyped/text and refuses to implicitly cast it into the bigint target column.
                weight_sql = f"{s.weight_kg}::numeric" if s.weight_kg is not None else "NULL::numeric"
                tier_sql = f"{sql_str(s.tier)}::varchar" if s.tier else "NULL::varchar"
                def cents(v: str) -> str:
                    return "NULL::bigint" if v == "NULL" else v
                values_rows.append(
                    f"({sql_str(s.name)},{s.price_cents},{weight_sql},{tier_sql},"
                    f"{cents(colombo_c)},{cents(suburb_c)},{cents(outer_c)},"
                    f"{cents(colombo_g)},{cents(suburb_g)},{cents(outer_g)},"
                    f"{str(s.lorry_outer_whatsapp).lower()},{str(s.courier_outer_blocked).lower()})")
            lines.append(f"""INSERT INTO product_variant (product_id, sku, price_cents, weight_kg,
    board_size_tier, lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
    lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
    lorry_outer_whatsapp, courier_outer_blocked, options_signature)
SELECT grp.product_id, {sql_str(sku)} || '-' || opts.id, v.price_cents, v.weight_kg, v.tier,
    v.colombo_c, v.suburb_c, v.outer_c, v.colombo_g, v.suburb_g, v.outer_g, v.outer_wa, v.courier_blocked,
    opts.id::text
FROM opts JOIN grp ON opts.spec_group_id = grp.id
JOIN (VALUES {','.join(values_rows)})
    AS v(value, price_cents, weight_kg, tier, colombo_c, suburb_c, outer_c,
         colombo_g, suburb_g, outer_g, outer_wa, courier_blocked)
    ON v.value = opts.value
ON CONFLICT (product_id, options_signature) DO NOTHING;""")
            total_variants += len(p.sizes)
        lines.append("")

    header = [f"-- Categories: {len(per_category)}  Products: {len(products)}  "
              f"Size rows (variants + single): {total_variants}"]
    for cat, count in sorted(per_category.items()):
        header.append(f"--   {cat}: {count} products")
    return "\n".join(header) + "\n\n" + "\n".join(lines)


def main() -> None:
    products = parse()
    if not products:
        print("ERROR: no products parsed — check the sheet layout", file=sys.stderr)
        sys.exit(1)
    sql = emit(products)
    OUT_PATH.write_text(sql, encoding="utf-8")
    print(f"Wrote {len(products)} products to {OUT_PATH}")
    print(f"Skipped (no price on sheet): {len(SKIPPED)}")
    for s in SKIPPED:
        print(f"  - {s}")


if __name__ == "__main__":
    main()
