ALTER TABLE category DROP COLUMN sla;

CREATE TABLE sub_category (
    sub_category_id UUID PRIMARY KEY,
    category_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sla INTEGER,
    priority VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES category(category_id)
);

ALTER TABLE ticket ADD COLUMN sub_category_id UUID;
ALTER TABLE ticket ADD CONSTRAINT fk_ticket_sub_category FOREIGN KEY (sub_category_id) REFERENCES sub_category(sub_category_id);

ALTER TABLE ticket RENAME COLUMN danger_level TO priority;
