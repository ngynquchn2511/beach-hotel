-- Tro ly phan tich quan tri bang AI: luu ban phan tich thang va cau hoi / tra loi (kem so token de theo doi chi phi).
-- May dev (ddl-auto=update) Hibernate tu tao bang khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS ai_reports (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    report_month   DATE          NOT NULL,
    kind           VARCHAR(10)   NOT NULL,
    question       VARCHAR(1000) NULL,
    content        TEXT          NOT NULL,
    model          VARCHAR(60)   NULL,
    input_tokens   BIGINT        NULL,
    output_tokens  BIGINT        NULL,
    created_by     VARCHAR(100)  NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_report_month (report_month, kind, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
