-- Tu dong tra loi danh gia cua khach theo tu khoa: bang quy tac review_reply_rules,
-- cot reviews.auto_reply_rule (ten quy tac da tu tra loi; NULL = nhan vien tra loi tay / chua tra loi).
-- Moi truong chay that (DDL_AUTO=validate) phai chay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.
-- Quy tac mac dinh do app tu tao luc khoi dong neu bang con trong.
DELIMITER //
DROP PROCEDURE IF EXISTS hb_add_column //
CREATE PROCEDURE hb_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN definition VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL hb_add_column('reviews', 'auto_reply_rule', 'VARCHAR(80) NULL');

CREATE TABLE IF NOT EXISTS review_reply_rules (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    keywords   VARCHAR(500) NULL,
    min_rating INT NOT NULL,
    max_rating INT NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    sort_order INT NOT NULL,
    active     BIT(1) NOT NULL,
    used_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL
);

DROP PROCEDURE IF EXISTS hb_add_column;
