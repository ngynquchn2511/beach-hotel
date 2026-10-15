-- Khuyen mai tu dong theo luat (o dai ngay / dat som / gio chot) + khuyen mai da ap tren tung don.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS promotion_rules (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(100) NOT NULL,
    name_en           VARCHAR(100) NULL,
    type              VARCHAR(20)  NOT NULL,
    threshold         INT          NOT NULL,
    discount_percent  INT          NOT NULL,
    active            BIT(1)       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
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

CALL hb_add_column('bookings', 'promotion_name', 'VARCHAR(100) NULL');
CALL hb_add_column('bookings', 'promotion_name_en', 'VARCHAR(100) NULL');
CALL hb_add_column('bookings', 'promotion_percent', 'INT NULL');
CALL hb_add_column('bookings', 'promotion_amount', 'DECIMAL(12,2) NULL');

DROP PROCEDURE hb_add_column;
