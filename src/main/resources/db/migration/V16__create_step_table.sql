CREATE TABLE step (
    step_id UUID PRIMARY KEY,
    sub_category_id UUID NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    description TEXT,
    duration INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    FOREIGN KEY (sub_category_id) REFERENCES sub_category(sub_category_id)
);
