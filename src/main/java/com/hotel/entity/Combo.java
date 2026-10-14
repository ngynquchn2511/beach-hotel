package com.hotel.entity;

import com.hotel.util.Texts;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "combos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Combo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // VD: "Combo an nuong", "Combo lau", "Phong thuong (khong combo)"
    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    // Ban tieng Anh (tuy chon) cho khach chon EN - bo trong thi hien ban tieng Viet
    @Column(name = "name_en", length = 150)
    private String nameEn;

    @Column(name = "description_en", length = 1000)
    private String descriptionEn;

    // Noi dung theo ngon ngu khach dang chon (dung tren trang khach)
    public String getLocalizedName() {
        return Texts.pick(name, nameEn);
    }

    public String getLocalizedDescription() {
        return Texts.pick(description, descriptionEn);
    }

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "max_guests")
    private Integer maxGuests;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active;
}
