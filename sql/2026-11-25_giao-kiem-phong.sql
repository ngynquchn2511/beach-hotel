-- Kiem phong truoc khi tra: le tan giao cho 1 nhan vien buong phong (null = bao chung).
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

DROP PROCEDURE IF EXISTS hb_room_check_fk //
CREATE PROCEDURE hb_room_check_fk()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bookings' AND CONSTRAINT_NAME = 'fk_booking_room_check_assignee') THEN
        ALTER TABLE bookings ADD CONSTRAINT fk_booking_room_check_assignee
            FOREIGN KEY (room_check_assignee_id) REFERENCES users (id);
    END IF;
END //
DELIMITER ;

CALL hb_add_column('bookings', 'room_check_assignee_id', 'BIGINT NULL');
CALL hb_room_check_fk();
DROP PROCEDURE hb_room_check_fk;
DROP PROCEDURE hb_add_column;
