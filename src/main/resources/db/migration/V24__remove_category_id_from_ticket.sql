-- Migration V24: Dam bao du lieu sub_category duoc giu lai truoc khi drop cot category_id

-- 1. Neu co ticket co category_id nhung chua co bat ky sub_category nao trong ticket_subcategory,
-- lien ket voi sub_category hop le dau tien thuoc category do de bao toan du lieu
INSERT INTO ticket_subcategory (ticket_id, sub_category_id)
SELECT t.ticket_id, sc.sub_category_id
FROM ticket t
JOIN (
    SELECT DISTINCT ON (category_id) sub_category_id, category_id
    FROM sub_category
    WHERE is_active = TRUE AND is_deleted = FALSE
    ORDER BY category_id, created_at ASC
) sc ON sc.category_id = t.category_id
WHERE t.category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM ticket_subcategory tsc WHERE tsc.ticket_id = t.ticket_id
  )
ON CONFLICT (ticket_id, sub_category_id) DO NOTHING;

-- 2. Xoa khoa ngoai, chi muc va cot category_id khoi bang ticket
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS fk_ticket_category;
ALTER TABLE ticket DROP CONSTRAINT IF EXISTS ticket_category_id_fkey;
DROP INDEX IF EXISTS idx_tickets_category_status;
DROP INDEX IF EXISTS idx_ticket_category_id;
ALTER TABLE ticket DROP COLUMN IF EXISTS category_id;

-- 3. Tao index cho sub_category_id tren bang ticket_subcategory de tang toc join
CREATE INDEX IF NOT EXISTS idx_ticket_subcategory_sub_cat_id ON ticket_subcategory(sub_category_id);
