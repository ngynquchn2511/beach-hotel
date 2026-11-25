package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 1 cuoc tro chuyen cua khach voi tro ly "Song" (tu luc mo chat den khi bam lam moi).
// Sau 30 giay khach khong hoi them, widget hoi "da giai quyet chua?" -> luu ket qua + gop y khi khach chua hai long
@Entity
@Table(name = "chat_sessions", indexes = @Index(name = "idx_chat_sessions_started", columnList = "started_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    // Ket qua cuoc tro chuyen (luu chuoi, khong dung ENUM cua MySQL)
    public static final String OPEN = "OPEN";             // chua hoi / khach chua tra loi cau hoi kiem tra
    public static final String RESOLVED = "RESOLVED";     // khach bao da giai quyet / tim duoc phong
    public static final String UNRESOLVED = "UNRESOLVED"; // khach bao chua -> gop y vao phan quan tri

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @Column(name = "locale", length = 5)
    private String locale;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_at", nullable = false)
    private LocalDateTime lastAt;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    // So cau Song khong hieu (tra loi mac dinh)
    @Column(name = "unanswered_count", nullable = false)
    private int unansweredCount;

    @Column(name = "outcome", nullable = false, length = 20)
    @Builder.Default
    private String outcome = OPEN;

    // Luc widget hien cau hoi "da giai quyet chua?" (null = chua hoi)
    @Column(name = "check_asked_at")
    private LocalDateTime checkAskedAt;

    // Ly do chua hai long (ma: NO_ROOM, PRICE, WRONG_ANSWER, WANT_STAFF, OTHER)
    @Column(name = "reason", length = 20)
    private String reason;

    @Column(name = "feedback", length = 500)
    private String feedback;

    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    // Nhan vien da lien he / xu ly gop y
    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private User handledBy;

    @Column(name = "staff_note", length = 300)
    private String staffNote;

    public boolean isResolved() {
        return RESOLVED.equals(outcome);
    }

    public boolean isUnresolved() {
        return UNRESOLVED.equals(outcome);
    }

    public boolean isHandled() {
        return handledAt != null;
    }

    // Da hoi ma khach khong bam gi
    public boolean isNoResponse() {
        return OPEN.equals(outcome) && checkAskedAt != null;
    }
}
