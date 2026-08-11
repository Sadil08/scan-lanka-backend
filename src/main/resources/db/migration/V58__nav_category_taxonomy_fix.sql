-- V58 — Owner nav taxonomy fixes (2026-08-11).
--
-- Our Products dropdown groups were missing expected browse categories:
--   • Sport / Game Boards: Dam & Chess were folded into "Sports Items"; carrom stands
--     sat under "Carrom Board". Restore Dam / Chess Board + Carrom Board Stand.
--   • Kids Corner: Baby Carrom kept category "Carrom Board" (Sport group wins on
--     category aggregation), so it never appeared under Kids Corner. Give it its own
--     Kids Corner category.
-- Pin Up / Menu Board product rows are already correct; the blank look is a frontend
-- nav collapse (single-category groups hide the child link).

-- Dam & Chess boards back under their own category in Sport / Game Boards.
UPDATE product
   SET category = 'Dam / Chess Board',
       category_group = 'Sport / Game Boards'
 WHERE slug IN (
     'dam-board',
     'dam-board-teak-wood',
     'chess-board',
     'chess-board-teak-wood'
 );

-- Carrom stands as their own Sport / Game Boards category (not buried in Carrom Board).
UPDATE product
   SET category = 'Carrom Board Stand',
       category_group = 'Sport / Game Boards'
 WHERE slug IN (
     'foldable-steel-carrom-board-stand',
     'foldable-wooden-carrom-board-stand'
 );

-- Baby Carrom merchandised under Kids Corner (must not share Carrom Board category).
UPDATE product
   SET category = 'Baby Carrom Board',
       category_group = 'Kids Corner'
 WHERE slug = 'baby-carrom-board';

-- Keep accessories + remaining Sport rows under the Sport umbrella.
UPDATE product
   SET category_group = 'Sport / Game Boards'
 WHERE category IN ('Sports Items', 'Carrom Board', 'Dam / Chess Board', 'Carrom Board Stand');

UPDATE product
   SET category_group = 'Kids Corner'
 WHERE category IN ('Kids Board', 'A4 Size Writing Board', 'Baby Carrom Board');

-- Ensure Pin Up / Menu Board groups stay wired (idempotent if already set).
UPDATE product
   SET category_group = 'Pin Up Board / Notice Board'
 WHERE category = 'Pin Board / Notice Board';

UPDATE product
   SET category_group = 'Menu Board And Other Restaurant Items'
 WHERE category IN ('Menu Board', 'Menu Board and Name Tags');
