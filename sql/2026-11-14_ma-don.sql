-- Ma don dang thang + ngay tao don + so thu tu 4 chu so trong ngay, VD don dau tien ngay 14/11 la "11140001".
-- Ung dung gan ma khi luu don moi (BookingCodeListener / BookingCodeGenerator), dem so theo ngay o bang
-- booking_code_counters. File nay them cot + bang dem va dien ma cho cac don cu theo thu tu tao.
-- May dev (ddl-auto=update) Hibernate tu them cot / bang nhung KHONG dien ma don cu - van nen chay file nay.
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
DROP PROCEDURE IF EXISTS hb_add_index //
CREATE PROCEDURE hb_add_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl = CONCAT('CREATE INDEX `', idx, '` ON `', tbl, '` (', cols, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL hb_add_column('bookings', 'booking_code', 'VARCHAR(12) NULL');
CALL hb_add_index('bookings', 'idx_bookings_code', '`booking_code`');

CREATE TABLE IF NOT EXISTS booking_code_counters (
    code_date   DATE NOT NULL PRIMARY KEY,
    last_number INT  NOT NULL
);

-- Dien ma cho don chua co: so thu tu theo gio tao trong ngay (trung gio thi theo id)
UPDATE bookings b
JOIN (SELECT id, DATE(created_at) AS d,
             ROW_NUMBER() OVER (PARTITION BY DATE(created_at) ORDER BY created_at, id) AS rn
      FROM bookings) x ON x.id = b.id
SET b.booking_code = CONCAT(DATE_FORMAT(x.d, '%m%d'), LPAD(x.rn, GREATEST(4, CHAR_LENGTH(x.rn)), '0'))
WHERE b.booking_code IS NULL;

-- Bo dem bat dau tu so lon nhat da dung moi ngay de don moi khong trung ma
INSERT INTO booking_code_counters (code_date, last_number)
SELECT DATE(created_at), COUNT(*) FROM bookings GROUP BY DATE(created_at)
ON DUPLICATE KEY UPDATE last_number = GREATEST(last_number, VALUES(last_number));

DROP PROCEDURE IF EXISTS hb_add_column;
DROP PROCEDURE IF EXISTS hb_add_index;
