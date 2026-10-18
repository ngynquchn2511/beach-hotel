-- Chi phi van hanh (1 lan / hang thang), ty le gia von theo loai dich vu, phi cong thanh toan + chi phi bien doi moi phong-dem.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS expenses (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    category      VARCHAR(20)   NOT NULL,
    description   VARCHAR(200)  NOT NULL,
    amount        DECIMAL(14,2) NOT NULL,
    recurrence    VARCHAR(10)   NOT NULL,
    expense_date  DATE          NULL,
    start_month   DATE          NULL,
    end_month     DATE          NULL,
    created_by    VARCHAR(100)  NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_expense_dates (expense_date, start_month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS service_cost_rates (
    charge_type  VARCHAR(20) NOT NULL,
    percent      INT         NOT NULL,
    PRIMARY KEY (charge_type)
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

CALL hb_add_column('pricing_settings', 'payment_fee_percent', 'DECIMAL(5,2) NULL');
CALL hb_add_column('pricing_settings', 'variable_cost_per_night', 'DECIMAL(12,2) NULL');

DROP PROCEDURE hb_add_column;
