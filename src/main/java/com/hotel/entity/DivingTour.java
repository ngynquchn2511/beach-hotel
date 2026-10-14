package com.hotel.entity;

import com.hotel.util.Texts;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

// Tour lan bien resort ban kem (lan ong tho, lan binh, kayak...) - admin quan ly danh muc
@Entity
@Table(name = "diving_tours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DivingTour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "name_en", length = 150)
    private String nameEn;

    @Column(name = "description_en", length = 1000)
    private String descriptionEn;

    // Gia moi nguoi tham gia
    @Column(name = "price_per_person", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerPerson;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Column(name = "duration_hours", nullable = false)
    private Integer durationHours;

    @Column(name = "meeting_point", length = 200)
    private String meetingPoint;

    // So cho toi da cua 1 chuyen (moi ngay 1 chuyen) - cong don tat ca don chua huy trong ngay
    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    // Do tuoi toi thieu (null = khong gioi han)
    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active;

    public String getLocalizedName() {
        return Texts.pick(name, nameEn);
    }

    public String getLocalizedDescription() {
        return Texts.pick(description, descriptionEn);
    }
}
