-- Dat lich spa / ban nha hang kem don phong + loai phu phi SPA (tao khi le tan xac nhan lich spa).
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS facility_reservations (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id     BIGINT        NOT NULL,
    kind           VARCHAR(12)   NOT NULL,
    treatment      VARCHAR(20)   NULL,
    reserved_date  DATE          NOT NULL,
    reserved_time  TIME(6)       NOT NULL,
    people         INT           NOT NULL,
    total_price    DECIMAL(12,2) NULL,
    note           VARCHAR(300)  NULL,
    status         VARCHAR(20)   NOT NULL,
    charge_id      BIGINT        NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_facility_slot (kind, reserved_date, reserved_time),
    KEY idx_facility_booking (booking_id),
    UNIQUE KEY uk_facility_charge (charge_id),
    CONSTRAINT fk_facility_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_facility_charge FOREIGN KEY (charge_id) REFERENCES booking_charges (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Cot loai phu phi la ENUM -> them gia tri SPA
ALTER TABLE booking_charges MODIFY charge_type ENUM ('AIRPORT_TRANSFER', 'DAMAGE', 'DIVING_TOUR', 'EARLY_CHECKIN', 'LATE_CHECKOUT',
    'LAUNDRY', 'MINIBAR', 'OTHER', 'ROOM_SERVICE', 'SPA') NOT NULL;
