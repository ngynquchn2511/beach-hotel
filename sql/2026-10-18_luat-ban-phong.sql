-- Luat ban phong theo mua: so dem toi thieu / dong ban theo khoang ngay (+ loai phong), gioi han dat truoc toi da N ngay.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS stay_rules (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    name_en       VARCHAR(100) NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    room_type_id  BIGINT       NULL,
    min_nights    INT          NULL,
    stop_sell     BIT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stay_rule_dates (start_date, end_date),
    KEY idx_stay_rule_room_type (room_type_id),
    CONSTRAINT fk_stay_rule_room_type FOREIGN KEY (room_type_id) REFERENCES room_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- MySQL 8.0 khong ho tro "ADD COLUMN IF NOT EXISTS" -> dung thu tuc tam kiem tra information_schema
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

CALL hb_add_column('pricing_settings', 'max_advance_days', 'INT NULL');

DROP PROCEDURE hb_add_column;
