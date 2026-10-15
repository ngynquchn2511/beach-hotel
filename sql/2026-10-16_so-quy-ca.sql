-- So quy ca le tan: nhan ca (tien mat dau ca) -> dong ca (chot thu theo phuong thuc, dem tien, nop ket, ghi chu ban giao).
-- May dev (ddl-auto=update) Hibernate tu them khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS cash_shifts (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    status              VARCHAR(10)   NOT NULL,
    opened_by           BIGINT        NOT NULL,
    opened_at           DATETIME(6)   NOT NULL,
    opening_cash        DECIMAL(12,2) NOT NULL,
    previous_left_cash  DECIMAL(12,2) NULL,
    closed_by           BIGINT        NULL,
    closed_at           DATETIME(6)   NULL,
    cash_in             DECIMAL(12,2) NULL,
    transfer_in         DECIMAL(12,2) NULL,
    online_in           DECIMAL(12,2) NULL,
    counted_cash        DECIMAL(12,2) NULL,
    cash_to_safe        DECIMAL(12,2) NULL,
    handover_note       VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY idx_shift_status (status, opened_at),
    KEY idx_shift_opened_by (opened_by),
    KEY idx_shift_closed_by (closed_by),
    CONSTRAINT fk_shift_opened_by FOREIGN KEY (opened_by) REFERENCES users (id),
    CONSTRAINT fk_shift_closed_by FOREIGN KEY (closed_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
