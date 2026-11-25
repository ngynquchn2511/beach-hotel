package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Cai dat AI tra loi khach (1 dong, id = 1): bat / tat, tran chi phi thang / ngay (USD), so cau AI moi khach / ngay,
// gioi han nhan tin chong spam (moi phut / moi gio). Het tran -> Song quay ve tra loi theo luat + goi y nhan Zalo
@Entity
@Table(name = "chat_ai_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAiSettings {

    @Id
    private Long id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "monthly_budget_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyBudgetUsd;

    @Column(name = "daily_budget_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyBudgetUsd;

    // So cau AI tra loi toi da cho 1 khach trong 1 ngay
    @Column(name = "per_user_daily", nullable = false)
    private int perUserDaily;

    // Chong spam: so tin nhan toi da gui Song (ca khong dung AI) trong 1 phut / 1 gio
    @Column(name = "per_minute", nullable = false)
    private int perMinute;

    @Column(name = "per_hour", nullable = false)
    private int perHour;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    public static ChatAiSettings defaults() {
        return ChatAiSettings.builder().id(1L).enabled(true).monthlyBudgetUsd(new BigDecimal("5.00"))
                .dailyBudgetUsd(new BigDecimal("0.50")).perUserDaily(15).perMinute(8).perHour(60).build();
    }
}
