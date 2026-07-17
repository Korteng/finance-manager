CREATE TABLE app_user (
                          id BIGSERIAL PRIMARY KEY,
                          username VARCHAR(100) NOT NULL UNIQUE,
                          password_hash VARCHAR(255) NOT NULL,
                          role VARCHAR(20) NOT NULL DEFAULT 'USER',
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE transaction ADD CONSTRAINT fk_transaction_user
    FOREIGN KEY (user_id) REFERENCES app_user(id);

CREATE INDEX idx_transaction_user_id ON transaction(user_id);