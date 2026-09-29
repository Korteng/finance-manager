CREATE TABLE budget (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    category_id BIGINT NOT NULL REFERENCES category(id),
    period DATE NOT NULL,
    limit_amount NUMERIC(19,2) NOT NULL,
    CONSTRAINT uq_budget_user_category_period UNIQUE (user_id, category_id, period)
);

CREATE INDEX idx_budget_user_period ON budget(user_id, period);
