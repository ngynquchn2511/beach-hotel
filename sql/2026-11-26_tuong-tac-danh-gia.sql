-- Tuong tac voi danh gia cua khach: tha tim (review_likes, moi nguoi 1 lan / danh gia), binh luan (review_comments,
-- quan tri an duoc), luot chia se (review_shares, theo kenh link / facebook / native) cho trang "Khach noi gi" (/reviews).
-- Moi truong chay that (DDL_AUTO=validate) phai chay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.

CREATE TABLE IF NOT EXISTS review_likes (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id  BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_review_likes UNIQUE (review_id, user_id),
    INDEX idx_review_likes_created (created_at),
    CONSTRAINT fk_review_likes_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_review_likes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS review_comments (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id  BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    content    VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    hidden     BIT(1) NULL,
    INDEX idx_review_comments_created (created_at),
    CONSTRAINT fk_review_comments_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT fk_review_comments_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS review_shares (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id  BIGINT NOT NULL,
    channel    VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_review_shares_created (created_at),
    CONSTRAINT fk_review_shares_review FOREIGN KEY (review_id) REFERENCES reviews (id)
);
