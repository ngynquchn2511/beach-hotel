-- Goi dich vu len phong: thuc don, yeu cau cua khach dang o va tung mon trong yeu cau.
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS room_service_items (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    name_en     VARCHAR(100)  NULL,
    category    VARCHAR(10)   NOT NULL,
    price       DECIMAL(12,2) NOT NULL,
    active      BIT(1)        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Xoa don phong thi xoa luon yeu cau goi mon cua don do
CREATE TABLE IF NOT EXISTS room_service_orders (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id    BIGINT        NOT NULL,
    status        VARCHAR(10)   NOT NULL,
    note          VARCHAR(200)  NULL,
    total_amount  DECIMAL(12,2) NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    delivered_at  DATETIME(6)   NULL,
    handled_by    VARCHAR(100)  NULL,
    PRIMARY KEY (id),
    KEY idx_rs_order_status (status, created_at),
    KEY idx_rs_order_booking (booking_id),
    CONSTRAINT fk_rs_order_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Luu ten / gia mon luc goi (khong noi khoa toi thuc don: sua / ngung ban mon khong anh huong don cu)
CREATE TABLE IF NOT EXISTS room_service_order_lines (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    item_name   VARCHAR(100)  NOT NULL,
    category    VARCHAR(10)   NOT NULL,
    unit_price  DECIMAL(12,2) NOT NULL,
    quantity    INT           NOT NULL,
    PRIMARY KEY (id),
    KEY idx_rs_line_order (order_id),
    CONSTRAINT fk_rs_line_order FOREIGN KEY (order_id) REFERENCES room_service_orders (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Tien do an / do uong cong vao hoa don duoi loai phu phi moi ROOM_SERVICE:
-- booking_charges.charge_type do Hibernate tao co the la ENUM(...) co dinh -> them gia tri (chay lai van an toan)
DELIMITER //
DROP PROCEDURE IF EXISTS hb_charge_type //
CREATE PROCEDURE hb_charge_type()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'booking_charges' AND COLUMN_NAME = 'charge_type'
                 AND DATA_TYPE = 'enum' AND COLUMN_TYPE NOT LIKE '%ROOM_SERVICE%') THEN
        ALTER TABLE booking_charges MODIFY charge_type
            ENUM('AIRPORT_TRANSFER','DAMAGE','DIVING_TOUR','EARLY_CHECKIN','LATE_CHECKOUT','LAUNDRY','MINIBAR','OTHER','ROOM_SERVICE') NOT NULL;
    END IF;
END //
DELIMITER ;
CALL hb_charge_type();
DROP PROCEDURE hb_charge_type;
