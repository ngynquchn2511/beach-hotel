package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Phieu luong thang da tra: so cong (tu cham cong) x luong ngay + thuong - khau tru.
// Tra xong ghi 1 khoan chi "Luong & bao hiem" de vao lai lo
@Entity
@Table(name = "staff_payslips",
        uniqueConstraints = @UniqueConstraint(name = "uk_payslip_month", columnNames = {"staff_id", "pay_month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    // Ngay 1 cua thang tinh luong
    @Column(name = "pay_month", nullable = false)
    private LocalDate payMonth;

    @Column(name = "work_units", nullable = false, precision = 5, scale = 1)
    private BigDecimal workUnits;

    @Column(name = "daily_wage", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyWage;

    @Column(precision = 12, scale = 2)
    private BigDecimal bonus;

    @Column(precision = 12, scale = 2)
    private BigDecimal deduction;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 300)
    private String note;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "paid_by", length = 150)
    private String paidBy;
}
