package com.hotel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Quy tac tu dong tra loi danh gia: danh gia co chua 1 trong cac tu khoa (khong phan biet dau / hoa thuong)
// va so sao trong khoang [minRating, maxRating] thi dung noi dung mau de tra loi. Tu khoa trong = ap dung cho moi
// danh gia trong khoang sao (dung lam quy tac du phong). Quy tac co thu tu nho hon duoc xet truoc
@Entity
@Table(name = "review_reply_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewReplyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    // Cac tu khoa cach nhau dau phay
    @Column(length = 500)
    private String keywords;

    @Column(name = "min_rating", nullable = false)
    private Integer minRating;

    @Column(name = "max_rating", nullable = false)
    private Integer maxRating;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean active;

    // So lan quy tac da duoc dung de tra loi
    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isOpen() {
        return Boolean.TRUE.equals(active);
    }

    public boolean isFallback() {
        return keywords == null || keywords.isBlank();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (usedCount == null) {
            usedCount = 0;
        }
    }
}
