package com.hotel.entity;

import com.hotel.util.Texts;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Mon trong thuc don goi len phong (do an, do uong, minibar, giat ui) - admin quan ly
@Entity
@Table(name = "room_service_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomServiceItem {

    public enum Category {
        FOOD, DRINK, MINIBAR, LAUNDRY;

        public String getVietnameseLabel() {
            return switch (this) {
                case FOOD -> "Đồ ăn";
                case DRINK -> "Đồ uống";
                case MINIBAR -> "Minibar";
                case LAUNDRY -> "Giặt ủi";
            };
        }

        // Tien mon nay cong vao hoa don duoi loai phu phi nao
        public ChargeType chargeType() {
            return switch (this) {
                case MINIBAR -> ChargeType.MINIBAR;
                case LAUNDRY -> ChargeType.LAUNDRY;
                default -> ChargeType.ROOM_SERVICE;
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
    @Column(nullable = false, length = 10)
    private Category category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

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
