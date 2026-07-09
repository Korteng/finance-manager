ALTER TABLE transaction
    ADD COLUMN category_id BIGINT REFERENCES category(id),
    ADD COLUMN description VARCHAR(500),
    ADD COLUMN user_id BIGINT;