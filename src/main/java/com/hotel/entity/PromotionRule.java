package com.hotel.entity;

import com.hotel.util.Texts;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Khuyen mai tu dong (khong can ma): khach dat online thoa dieu kien thi tu giam % tien phong.
// Nhieu luat cung thoa -> chi ap 1 luat giam nhieu nhat
@Entity
@Table(name = "promotion_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionRule {

    public enum Type {
        // O tu threshold dem tro len
        LONG_STAY,
        // Dat truoc ngay nhan phong it nhat threshold ngay
        EARLY_BIRD,
        // Nhan phong trong vong threshold ngay toi (lap day phong trong sat ngay)
        LAST_MINUTE;

        public String getVietnameseLabel() {
            return switch (this) {
                case LONG_STAY -> "Ở dài ngày";
                case EARLY_BIRD -> "Đặt sớm";
                case LAST_MINUTE -> "Giờ chót";
            };
        }

        // Mo ta dieu kien cho admin, VD "Ở từ 3 đêm"
        public String describe(int threshold) {
            return switch (this) {
                case LONG_STAY -> "Ở từ " + threshold + " đêm";
                case EARLY_BIRD -> "Đặt trước từ " + threshold + " ngày";
                case LAST_MINUTE -> "Nhận phòng trong " + threshold + " ngày tới";
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    // So dem toi thieu / so ngay dat truoc toi thieu / so ngay toi da truoc khi nhan phong (tuy loai)
    @Column(nullable = false)
    private Integer threshold;

    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getLocalizedName() {
        return Texts.pick(name, nameEn);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (active == null) {
            active = true;
        }
    }
}
