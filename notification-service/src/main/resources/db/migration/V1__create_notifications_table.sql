CREATE TABLE notifications (
                               id BIGSERIAL PRIMARY KEY,
                               event_id UUID NOT NULL UNIQUE,
                               user_id BIGINT NOT NULL,
                               type VARCHAR(50) NOT NULL,
                               message TEXT NOT NULL,
                               status VARCHAR(20) NOT NULL,
                               created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);