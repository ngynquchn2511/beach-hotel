package com.hotel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Mot lan le tan goi dien nhac khach (sap den nhan phong / sap het han chuyen coc) va ket qua cuoc goi.
// Moi don co the goi nhieu lan; ket qua "xong" (isDone) thi don roi khoi danh sach can goi cua muc dich do
@Entity
@Table(name = "call_reminders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallReminder {

    public enum Purpose {
        ARRIVAL, DEPOSIT;

        public String getLabel() {
            return switch (this) {
                case ARRIVAL -> "Nhắc ngày nhận phòng";
                case DEPOSIT -> "Nhắc chuyển cọc";
            };
        }
    }

    public enum Result {
        CONFIRMED, CHANGE_REQUEST, CALL_BACK, NO_ANSWER, WRONG_NUMBER;

        public String getLabel() {
            return switch (this) {
                case CONFIRMED -> "Khách xác nhận";
                case CHANGE_REQUEST -> "Khách muốn đổi / hủy";
                case CALL_BACK -> "Hẹn gọi lại";
                case NO_ANSWER -> "Không nghe máy";
                case WRONG_NUMBER -> "Sai số / không liên lạc được";
            };
        }

        public String getBadgeClass() {
            return switch (this) {
                case CONFIRMED -> "bg-success";
                case CHANGE_REQUEST -> "bg-warning text-dark";
                case CALL_BACK, NO_ANSWER -> "bg-secondary";
                case WRONG_NUMBER -> "bg-danger";
            };
        }

        // Khong can goi lai nua
        public boolean isDone() {
            return this == CONFIRMED || this == CHANGE_REQUEST || this == WRONG_NUMBER;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Purpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Result result;

    @Column(length = 300)
    private String note;

    @Column(name = "staff_name", length = 100)
    private String staffName;

    @Column(name = "called_at", nullable = false)
    private LocalDateTime calledAt;

    @PrePersist
    void onCreate() {
        if (calledAt == null) {
            calledAt = LocalDateTime.now();
        }
    }
}
