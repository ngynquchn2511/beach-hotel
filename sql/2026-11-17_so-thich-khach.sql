-- So thich luu tru khach tu khai o trang "Goi y phong" (/rooms/suggest): moi tai khoan 1 dong.
-- Moi truong chay that (DDL_AUTO=validate) phai chay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS guest_preferences (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    guests         INT NOT NULL,
    purpose        ENUM('COUPLE','FAMILY','FRIENDS','BUSINESS','SOLO') NULL,
    max_budget     BIGINT NULL,
    preferred_view ENUM('SEA','GARDEN','POOL','ANY') NULL,
    priorities     VARCHAR(120) NULL,
    note           VARCHAR(300) NULL,
    updated_at     DATETIME(6) NOT NULL,
    CONSTRAINT uk_guest_pref_user UNIQUE (user_id),
    CONSTRAINT fk_guest_pref_user FOREIGN KEY (user_id) REFERENCES users (id)
);
