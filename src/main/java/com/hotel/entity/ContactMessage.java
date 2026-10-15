package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Yeu cau tu van khach gui tu form "Lien he" o trang gioi thieu - le tan tra loi qua email trong hop thu quan tri
@Entity
@Table(name = "contact_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Cau tra loi da gui qua email (null = chua tra loi)
    @Column(length = 2000)
    private String reply;

    @Column(name = "replied_at")
    private LocalDateTime repliedAt;

    // Ten nhan vien da tra loi / danh dau da xu ly
    @Column(name = "handled_by", length = 100)
    private String handledBy;

    // Da xu ly (tra loi qua email, hoac goi dien / nhan Zalo roi danh dau tay)
    @Column(name = "handled", nullable = false)
    private boolean handled;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isReplied() {
        return reply != null;
    }
}
