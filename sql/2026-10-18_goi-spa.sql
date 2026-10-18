-- Goi spa chuyen tu enum trong code (SpaTreatment) sang bang spa_packages de admin sua tren giao dien.
-- facility_reservations.treatment (ten enum cu) -> facility_reservations.spa_package_id.
-- May dev (ddl-auto=update) Hibernate tu tao bang / cot khi khoi dong, nhung VAN PHAI chay file nay de chuyen du lieu cu.
-- Moi truong chay that (DDL_AUTO=validate) chay file nay TRUOC khi deploy ban moi. Chay lai nhieu lan van an toan.
-- Cot treatment cu duoc giu lai (app khong doc nua) de co the doi chieu; chuyen thanh NULL duoc vi ban moi khong ghi vao.
CREATE TABLE IF NOT EXISTS spa_packages (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    name_en     VARCHAR(100)  NULL,
    minutes     INT           NOT NULL,
    price       DECIMAL(12,2) NOT NULL,
    active      BIT           NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 4 goi mac dinh (giong enum cu), chi them goi con thieu
INSERT INTO spa_packages (name, name_en, minutes, price, active, created_at)
SELECT 'Massage chân thảo dược', 'Herbal foot massage', 60, 350000, 1, NOW(6) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM spa_packages WHERE name = 'Massage chân thảo dược');
INSERT INTO spa_packages (name, name_en, minutes, price, active, created_at)
SELECT 'Massage body tinh dầu', 'Aromatherapy body massage', 90, 550000, 1, NOW(6) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM spa_packages WHERE name = 'Massage body tinh dầu');
INSERT INTO spa_packages (name, name_en, minutes, price, active, created_at)
SELECT 'Massage đá nóng', 'Hot stone massage', 90, 750000, 1, NOW(6) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM spa_packages WHERE name = 'Massage đá nóng');
INSERT INTO spa_packages (name, name_en, minutes, price, active, created_at)
SELECT 'Chăm sóc da mặt', 'Facial care', 60, 450000, 1, NOW(6) FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM spa_packages WHERE name = 'Chăm sóc da mặt');

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

CALL hb_add_column('facility_reservations', 'spa_package_id', 'BIGINT NULL');

DROP PROCEDURE hb_add_column;

-- Khoa ngoai + index (chi tao khi chua co)
SET @fk := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'facility_reservations' AND CONSTRAINT_NAME = 'fk_facility_spa_package');
SET @s := IF(@fk = 0, 'ALTER TABLE facility_reservations ADD CONSTRAINT fk_facility_spa_package FOREIGN KEY (spa_package_id) REFERENCES spa_packages (id)', 'SELECT 1');
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Chuyen lich spa cu: ten enum -> goi tuong ung
UPDATE facility_reservations r
JOIN spa_packages p ON p.name = CASE r.treatment
    WHEN 'FOOT_MASSAGE' THEN 'Massage chân thảo dược'
    WHEN 'BODY_MASSAGE' THEN 'Massage body tinh dầu'
    WHEN 'HOT_STONE' THEN 'Massage đá nóng'
    WHEN 'FACIAL' THEN 'Chăm sóc da mặt' END
SET r.spa_package_id = p.id
WHERE r.spa_package_id IS NULL AND r.treatment IS NOT NULL;
