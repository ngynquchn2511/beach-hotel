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

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_number", nullable = false, unique = true, length = 20)
    private String roomNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(length = 1000)
    private String description;

    // Ban tieng Anh (tuy chon) cho khach chon EN - bo trong thi hien ban tieng Viet
    @Column(name = "description_en", length = 1000)
    private String descriptionEn;

    // Mo ta theo ngon ngu khach dang chon (dung tren trang khach)
    public String getLocalizedDescription() {
        return Texts.pick(description, descriptionEn);
    }

    // ===== Don phong (bang "Don phong" cua le tan) =====
    // Khach tra phong luc nao -> phong bat dau can don (NOT_READY)
    @Column(name = "dirty_since")
    private LocalDateTime dirtySince;

    // Nhan vien dang don va bat dau luc nao (NOT_READY + co nguoi nhan = "Dang don")
    @Column(name = "cleaning_by", length = 100)
    private String cleaningBy;

    @Column(name = "cleaning_started_at")
    private LocalDateTime cleaningStartedAt;

    // Lan don xong gan nhat
    @Column(name = "last_cleaned_at")
    private LocalDateTime lastCleanedAt;

    @Column(name = "last_cleaned_by", length = 100)
    private String lastCleanedBy;

    // Phan cong: nhan vien buong duoc giao don phong nay (null = chua giao, ai nhan cung duoc)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaning_assignee_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User cleaningAssignee;

    // Buong phong bao don xong, cho le tan / quan ly kiem (khi bat buoc kiem phong)
    @Column(name = "inspection_requested_at")
    private LocalDateTime inspectionRequestedAt;

    // Ly do kiem chua dat lan gan nhat (nhan vien buong xem de lam lai)
    @Column(name = "inspection_note", length = 300)
    private String inspectionNote;

    public boolean isBeingCleaned() {
        return status == RoomStatus.NOT_READY && cleaningStartedAt != null && inspectionRequestedAt == null;
    }

    public boolean isAwaitingInspection() {
        return status == RoomStatus.NOT_READY && inspectionRequestedAt != null;
    }

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<RoomImage> images = new java.util.ArrayList<>();

    // Anh dai dien (anh dau tien) - dung cho cac noi chi can hien thi 1 anh nhu the/card
    public String getCoverImageUrl() {
        return images.isEmpty() ? null : images.get(0).getImageUrl();
    }
}
