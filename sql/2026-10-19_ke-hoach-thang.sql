-- Ke hoach (chi tieu) theo thang: doanh thu, tran chi phi, cong suat, ADR - de so thuc te / ke hoach.
-- May dev (ddl-auto=update) Hibernate tu tao bang khi khoi dong. Moi truong chay that (DDL_AUTO=validate)
-- phai chay file nay TRUOC khi deploy ban moi, neu khong app se khong khoi dong duoc. Chay lai nhieu lan van an toan.
CREATE TABLE IF NOT EXISTS monthly_budgets (
    budget_month      DATE          NOT NULL,
    revenue_target    DECIMAL(14,2) NULL,
    cost_budget       DECIMAL(14,2) NULL,
    occupancy_target  INT           NULL,
    adr_target        DECIMAL(12,2) NULL,
    PRIMARY KEY (budget_month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
