-- Phan ca nhan vien (lich truc), cham cong tu dong, tinh / tra luong theo ngay cong.
-- Bang: staff_duty_shifts (lich truc), staff_attendances (cham cong moi ngay), staff_payslips (phieu luong da tra);
-- cot users.daily_wage (luong 1 ngay cong).
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
DELIMITER ;

CALL hb_add_column('users', 'daily_wage', 'DECIMAL(12,2) NULL');

CREATE TABLE IF NOT EXISTS staff_duty_shifts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_date  DATE NOT NULL,
    shift      ENUM('MORNING','AFTERNOON','NIGHT') NOT NULL,
    staff_id   BIGINT NOT NULL,
    note       VARCHAR(200) NULL,
    created_by VARCHAR(150) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_duty_shift UNIQUE (work_date, shift, staff_id),
    INDEX idx_duty_date (work_date),
    CONSTRAINT fk_duty_staff FOREIGN KEY (staff_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS staff_attendances (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    staff_id     BIGINT NOT NULL,
    work_date    DATE NOT NULL,
    shift        ENUM('MORNING','AFTERNOON','NIGHT') NULL,
    check_in_at  DATETIME(6) NOT NULL,
    check_out_at DATETIME(6) NOT NULL,
    ended        BIT(1) NULL,
    late_minutes INT NULL,
    manual_units DECIMAL(3,1) NULL,
    note         VARCHAR(300) NULL,
    CONSTRAINT uk_attendance_day UNIQUE (staff_id, work_date),
    INDEX idx_attendance_date (work_date),
    CONSTRAINT fk_attendance_staff FOREIGN KEY (staff_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS staff_payslips (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    staff_id   BIGINT NOT NULL,
    pay_month  DATE NOT NULL,
    work_units DECIMAL(5,1) NOT NULL,
    daily_wage DECIMAL(12,2) NOT NULL,
    bonus      DECIMAL(12,2) NULL,
    deduction  DECIMAL(12,2) NULL,
    amount     DECIMAL(14,2) NOT NULL,
    note       VARCHAR(300) NULL,
    expense_id BIGINT NULL,
    paid_at    DATETIME(6) NOT NULL,
    paid_by    VARCHAR(150) NULL,
    CONSTRAINT uk_payslip_month UNIQUE (staff_id, pay_month),
    CONSTRAINT fk_payslip_staff FOREIGN KEY (staff_id) REFERENCES users (id)
);

DROP PROCEDURE IF EXISTS hb_add_column;
