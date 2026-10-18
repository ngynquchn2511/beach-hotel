package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

// Ke hoach (chi tieu) 1 thang: doanh thu, tran chi phi, cong suat, gia phong binh quan - de so thuc te voi ke hoach
@Entity
@Table(name = "monthly_budgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBudget {

    // Ngay 1 cua thang
    @Id
    @Column(name = "budget_month")
    private LocalDate month;

    @Column(name = "revenue_target", precision = 14, scale = 2)
    private BigDecimal revenueTarget;

    @Column(name = "cost_budget", precision = 14, scale = 2)
    private BigDecimal costBudget;

    @Column(name = "occupancy_target")
    private Integer occupancyTarget;

    @Column(name = "adr_target", precision = 12, scale = 2)
    private BigDecimal adrTarget;

    public boolean isEmpty() {
        return revenueTarget == null && costBudget == null && occupancyTarget == null && adrTarget == null;
    }
}
