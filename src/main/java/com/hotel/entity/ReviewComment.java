package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Binh luan cua khach khac duoi 1 danh gia (quan tri an duoc binh luan khong phu hop)
@Entity
@Table(name = "review_comments", indexes = @Index(name = "idx_review_comments_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Boolean de cot moi null = dang hien
    @Column(name = "hidden")
    private Boolean hidden;

    public boolean isHidden() {
        return Boolean.TRUE.equals(hidden);
    }

    // Ten cong khai: 2 chu cuoi cua ho ten (giong ten nguoi danh gia)
    public String getDisplayName() {
        String name = user != null && user.getFullName() != null ? user.getFullName().trim() : "";
        if (name.isEmpty()) {
            return "Khách hàng";
        }
        String[] parts = name.split("\s+");
        return parts.length <= 2 ? name : parts[parts.length - 2] + " " + parts[parts.length - 1];
    }
}
