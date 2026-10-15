-- Yeu cau nhan phong som / tra phong muon (ngoai khung 14:00 - 12:00, co phu phi, le tan duyet).
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
-- Phu phi khi duoc duyet luu thanh booking_charges loai EARLY_CHECKIN / LATE_CHECKOUT (xem cuoi file).
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

CALL hb_add_column('bookings', 'requested_check_in_time', 'TIME NULL');
CALL hb_add_column('bookings', 'requested_check_out_time', 'TIME NULL');
CALL hb_add_column('bookings', 'time_request_fee', 'DECIMAL(12,2) NULL');
CALL hb_add_column('bookings', 'time_request_status', 'VARCHAR(10) NULL');
CALL hb_add_column('bookings', 'time_requested_at', 'DATETIME(6) NULL');
CALL hb_add_column('bookings', 'time_request_note', 'VARCHAR(200) NULL');

DROP PROCEDURE hb_add_column;

-- booking_charges.charge_type do Hibernate tao co the la ENUM(...) co dinh -> them gia tri EARLY_CHECKIN.
-- Cot la VARCHAR hoac da co gia tri moi thi bo qua (chay lai van an toan)
DELIMITER //
DROP PROCEDURE IF EXISTS hb_charge_type //
CREATE PROCEDURE hb_charge_type()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_charges' AND COLUMN_NAME = 'charge_type'
                 AND DATA_TYPE = 'enum' AND COLUMN_TYPE NOT LIKE '%EARLY_CHECKIN%') THEN
        ALTER TABLE booking_charges MODIFY charge_type
            ENUM('AIRPORT_TRANSFER','DAMAGE','DIVING_TOUR','EARLY_CHECKIN','LATE_CHECKOUT','LAUNDRY','MINIBAR','OTHER') NOT NULL;
    END IF;
END //
DELIMITER ;
CALL hb_charge_type();
DROP PROCEDURE hb_charge_type;
