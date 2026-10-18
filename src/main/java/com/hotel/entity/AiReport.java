package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 1 lan AI phan tich thang (ban phan tich) hoac tra loi cau hoi quan ly - luu lai de xem lai, khong goi lai ton tien
@Entity
@Table(name = "ai_reports", indexes = @Index(name = "idx_ai_report_month", columnList = "report_month, kind, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiReport {

    public enum Kind { ANALYSIS, QUESTION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ngay 1 cua thang duoc phan tich
    @Column(name = "report_month", nullable = false)
    private LocalDate reportMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    // Cau hoi cua quan ly (QUESTION)
    @Column(length = 1000)
    private String question;

    // ANALYSIS: JSON theo AiAnalysis; QUESTION: van ban tra loi
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 60)
    private String model;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
