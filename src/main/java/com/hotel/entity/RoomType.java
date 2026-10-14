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
@Table(name = "room_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "max_guests", nullable = false)
    private Integer maxGuests;

    @Column(precision = 6, scale = 2)
    private BigDecimal area;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String amenities;

    // Ban tieng Anh (tuy chon) cho khach chon EN - bo trong thi hien ban tieng Viet
    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "description_en", length = 1000)
    private String descriptionEn;

    @Column(name = "amenities_en", length = 500)
    private String amenitiesEn;

    // Noi dung theo ngon ngu khach dang chon (dung tren trang khach)
    public String getLocalizedName() {
        return Texts.pick(name, nameEn);
    }

    public String getLocalizedDescription() {
        return Texts.pick(description, descriptionEn);
    }

    public String getLocalizedAmenities() {
        return Texts.pick(amenities, amenitiesEn);
    }
}
