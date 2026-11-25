package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 1 lan khach bam chia se 1 danh gia (dem luot chia se theo ngay cho bieu do quan tri)
@Entity
@Table(name = "review_shares", indexes = @Index(name = "idx_review_shares_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    // Kenh: link (chep link / chia se cua may), facebook, zalo
    @Column(name = "channel", length = 20)
    private String channel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
