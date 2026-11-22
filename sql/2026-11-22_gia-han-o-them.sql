-- Gia han luu tru / khach o qua ngay tra phong: tien cac dem o them ghi thanh phu phi loai EXTENDED_STAY,
-- kem khoang dem [stay_from, stay_to) de hoan tac dung lan gia han gan nhat.
-- Moi truong chay that (DDL_AUTO=validate) phai chay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.
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

-- booking_charges.charge_type do Hibernate tao co the la ENUM(...) co dinh -> them gia tri EXTENDED_STAY
DROP PROCEDURE IF EXISTS hb_charge_type //
CREATE PROCEDURE hb_charge_type()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_charges' AND COLUMN_NAME = 'charge_type'
                 AND DATA_TYPE = 'enum' AND COLUMN_TYPE NOT LIKE '%EXTENDED_STAY%') THEN
        ALTER TABLE booking_charges MODIFY charge_type
            ENUM('AIRPORT_TRANSFER','DAMAGE','DIVING_TOUR','EARLY_CHECKIN','EXTENDED_STAY','LATE_CHECKOUT',
                 'LAUNDRY','MINIBAR','OTHER','ROOM_SERVICE','SPA') NOT NULL;
    END IF;
END //
DELIMITER ;

CALL hb_add_column('booking_charges', 'stay_from', 'DATE NULL');
CALL hb_add_column('booking_charges', 'stay_to', 'DATE NULL');
CALL hb_charge_type();
DROP PROCEDURE hb_charge_type;
DROP PROCEDURE hb_add_column;
