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

// Goi spa ban kem don phong (gia / nguoi) - admin quan ly o /admin/spa-packages
@Entity
@Table(name = "spa_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(nullable = false)
    private Integer minutes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getLocalizedName() {
        return Texts.pick(name, nameEn);
    }

    public boolean isOpen() {
        return Boolean.TRUE.equals(active);
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
