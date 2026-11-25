package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 1 cau hoi khach gui tro ly "Song" + chu de Song nhan ra (de thong ke khach hoi gi nhieu nhat)
@Entity
@Table(name = "chat_questions", indexes = @Index(name = "idx_chat_questions_asked", columnList = "asked_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "asked_at", nullable = false)
    private LocalDateTime askedAt;

    @Column(name = "question", nullable = false, length = 500)
    private String text;

    // Ma chu de, cach nhau dau phay (VD "PRICE,AVAILABILITY"); trong = Song khong hieu
    @Column(name = "topics", length = 200)
    private String topics;

    // Ten loai phong khach nhac toi, cach nhau dau phay
    @Column(name = "room_types", length = 200)
    private String roomTypes;

    // false = Song tra loi mac dinh "chua hieu"
    @Column(name = "answered", nullable = false)
    private boolean answered;

    // Cau tra loi cua Song (cat bot) de quan tri xem lai khi khach chua hai long
    @Column(name = "answer", length = 2000)
    private String answer;
}
