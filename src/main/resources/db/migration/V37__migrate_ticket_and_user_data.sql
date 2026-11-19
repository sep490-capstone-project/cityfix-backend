-- Migration V37: Migrate data from ticket and user_account to the newly created 1-1 tables

-- 1. Migrate spatial data from ticket to ticket_location
INSERT INTO "ticket_location" (ticket_id, location, address, location_method)
SELECT ticket_id, location, address, location_method
FROM "ticket"
ON CONFLICT (ticket_id) DO UPDATE SET
    location = EXCLUDED.location,
    address = EXCLUDED.address,
    location_method = EXCLUDED.location_method;

-- 2. Migrate system/assessment data from ticket to ticket_assessment
INSERT INTO "ticket_assessment" (
    ticket_id, priority, priority_reasoning, is_life_threatening, needs_manual_review, review_note, deadline
)
SELECT
    ticket_id,
    priority,
    priority_reasoning,
    COALESCE(is_life_threatening, FALSE),
    COALESCE(needs_manual_review, FALSE),
    review_note,
    deadline
FROM "ticket"
ON CONFLICT (ticket_id) DO UPDATE SET
    priority = EXCLUDED.priority,
    priority_reasoning = EXCLUDED.priority_reasoning,
    is_life_threatening = EXCLUDED.is_life_threatening,
    needs_manual_review = EXCLUDED.needs_manual_review,
    review_note = EXCLUDED.review_note,
    deadline = EXCLUDED.deadline;

-- 3. Ensure all remaining user_account.department_id mappings are preserved in user_department before dropping department_id
INSERT INTO "user_department" (id, user_id, department_id)
SELECT gen_random_uuid(), u.user_id, u.department_id
FROM "user_account" u
WHERE u.department_id IS NOT NULL
ON CONFLICT (user_id, department_id) DO NOTHING;

-- 4. Migrate profile data from user_account to user_profile
INSERT INTO "user_profile" (user_id, full_name, phone_number, role_id)
SELECT user_id, full_name, phone_number, role_id
FROM "user_account"
ON CONFLICT (user_id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    phone_number = EXCLUDED.phone_number,
    role_id = EXCLUDED.role_id;

-- 5. Migrate address data from user_account to user_address
INSERT INTO "user_address" (user_id, province, ward, address)
SELECT user_id, COALESCE(province, ''), COALESCE(ward, ''), COALESCE(address, '')
FROM "user_account"
ON CONFLICT (user_id) DO UPDATE SET
    province = EXCLUDED.province,
    ward = EXCLUDED.ward,
    address = EXCLUDED.address;

-- 6. Validate row counts after data migration
DO $$
DECLARE
    v_ticket_count BIGINT;
    v_ticket_loc_count BIGINT;
    v_ticket_ass_count BIGINT;
    v_user_count BIGINT;
    v_user_prof_count BIGINT;
    v_user_addr_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_ticket_count FROM "ticket";
    SELECT COUNT(*) INTO v_ticket_loc_count FROM "ticket_location";
    SELECT COUNT(*) INTO v_ticket_ass_count FROM "ticket_assessment";

    IF v_ticket_count <> v_ticket_loc_count OR v_ticket_count <> v_ticket_ass_count THEN
        RAISE EXCEPTION 'Data migration validation failed for ticket: ticket=%, location=%, assessment=%',
            v_ticket_count, v_ticket_loc_count, v_ticket_ass_count;
    END IF;

    SELECT COUNT(*) INTO v_user_count FROM "user_account";
    SELECT COUNT(*) INTO v_user_prof_count FROM "user_profile";
    SELECT COUNT(*) INTO v_user_addr_count FROM "user_address";

    IF v_user_count <> v_user_prof_count OR v_user_count <> v_user_addr_count THEN
        RAISE EXCEPTION 'Data migration validation failed for user_account: user=%, profile=%, address=%',
            v_user_count, v_user_prof_count, v_user_addr_count;
    END IF;
END $$;
