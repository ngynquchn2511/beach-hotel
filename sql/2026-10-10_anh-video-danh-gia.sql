-- Anh / video khach dinh kem khi danh gia (toi da 5 tep moi danh gia).
-- May dev (ddl-auto=update) Hibernate tu tao bang khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi.
CREATE TABLE IF NOT EXISTS review_media (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    url VARCHAR(500) NOT NULL,
    media_type VARCHAR(10) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_media_review (review_id),
    CONSTRAINT fk_review_media_review FOREIGN KEY (review_id) REFERENCES reviews (id)
);
