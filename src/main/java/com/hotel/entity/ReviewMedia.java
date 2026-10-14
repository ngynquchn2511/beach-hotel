package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Anh / video khach dinh kem khi danh gia (toi da ReviewService.MAX_MEDIA_PER_REVIEW file moi danh gia)
@Entity
@Table(name = "review_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    // Duong dan web dang /uploads/reviews/<file>
    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    // Thu tu khach chon file (0, 1, 2...)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public boolean isVideo() {
        return mediaType == MediaType.VIDEO;
    }
}
