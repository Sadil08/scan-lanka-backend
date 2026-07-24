-- V56 — Split "Sports Items" out of "Carrom Board", and move Baby Carrom to Kids Corner
-- (owner 2026-07-24). All rows stay under the "5.) Sport / Game Boards" group (except Baby Carrom).
--
-- The seed filed every sport accessory (men sets, winning disk, borics, dam men set) and the
-- dam/chess boards under the "Carrom Board" / "Dam / Chess Board" categories. Owner wants the actual
-- carrom boards alone under "Carrom Board", with all the accessories and the dam/chess boards under a
-- new "Sports Items" category. Baby Carrom Board is a kids item and moves to the Kids Corner group
-- (it stays a "Carrom Board" category — it is merchandised under Kids Corner, still a carrom board).
--
-- Pure UPDATEs, naturally idempotent. Every sport accessory carries the "sport-items-" slug prefix
-- (the seed prepended the family label), so that prefix targets them without touching the real
-- boards (practice/champion/…-carrom-board have no such prefix).

-- Accessories: Scan/Basco carrom men set, Winning Disk, Dam Men Set, Boric small/large.
UPDATE product SET category = 'Sports Items'
 WHERE slug LIKE 'sport-items-%';

-- Dam & Chess boards (Dam Men Set already moved above by its slug prefix).
UPDATE product SET category = 'Sports Items'
 WHERE category = 'Dam / Chess Board';

-- Keep the new category under the same top-level storefront group.
UPDATE product SET category_group = 'Sport / Game Boards'
 WHERE category = 'Sports Items';

-- Baby Carrom Board -> Kids Corner (group only; it remains a Carrom Board category).
UPDATE product SET category_group = 'Kids Corner'
 WHERE slug = 'baby-carrom-board';
