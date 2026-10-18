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
import java.time.YearMonth;

// Chi phi van hanh: phat sinh 1 lan (theo ngay) hoac co dinh hang thang (tu thang .. den thang, de trong = con ap dung)
@Entity
@Table(name = "expenses", indexes = @Index(name = "idx_expense_dates", columnList = "expense_date, start_month"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    public enum Category {
        PAYROLL("Lương & bảo hiểm"),
        UTILITIES("Điện, nước, internet"),
        SUPPLIES("Vật tư buồng phòng"),
        FOOD_BEVERAGE("Nguyên liệu ăn uống"),
        MAINTENANCE("Sửa chữa, bảo trì"),
        MARKETING("Quảng cáo, marketing"),
        RENT("Thuê mặt bằng / khấu hao"),
        TAX_FEES("Thuế, phí, bảo hiểm tài sản"),
        OTHER("Khác");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Recurrence { ONE_OFF, MONTHLY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Recurrence recurrence;

    // ONE_OFF: ngay phat sinh
    @Column(name = "expense_date")
    private LocalDate expenseDate;

    // MONTHLY: ngay 1 cua thang bat dau / thang ket thuc (null = chua ket thuc)
    @Column(name = "start_month")
    private LocalDate startMonth;

    @Column(name = "end_month")
    private LocalDate endMonth;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isMonthly() {
        return recurrence == Recurrence.MONTHLY;
    }

    // Khoan chi nay co tinh vao thang m khong
    public boolean appliesTo(YearMonth m) {
        if (!isMonthly()) {
            return expenseDate != null && YearMonth.from(expenseDate).equals(m);
        }
        YearMonth from = YearMonth.from(startMonth);
        return !m.isBefore(from) && (endMonth == null || !m.isAfter(YearMonth.from(endMonth)));
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
