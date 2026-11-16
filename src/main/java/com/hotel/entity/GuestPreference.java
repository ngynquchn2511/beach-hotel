package com.hotel.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

// So thich luu tru khach tu tra loi o trang "Goi y phong" (khach da dang nhap): dien san lan sau,
// le tan xem o trang khach hang de chuan bi phong / tu van
@Entity
@Table(name = "guest_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestPreference {

    public enum Purpose { COUPLE, FAMILY, FRIENDS, BUSINESS, SOLO }

    public enum View { SEA, GARDEN, POOL, ANY }

    public enum Priority { BATHTUB, BALCONY, SPACIOUS, KING_BED, LIVING_ROOM, PRIVACY, BUDGET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private Integer guests;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Purpose purpose;

    // Ngan sach toi da / dem (null = khong gioi han)
    @Column(name = "max_budget")
    private Long maxBudget;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_view", length = 10)
    private View preferredView;

    // Danh sach Priority, cach nhau dau phay
    @Column(length = 120)
    private String priorities;

    @Column(length = 300)
    private String note;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Set<Priority> getPrioritySet() {
        if (priorities == null || priorities.isBlank()) {
            return EnumSet.noneOf(Priority.class);
        }
        Set<Priority> out = EnumSet.noneOf(Priority.class);
        for (String p : priorities.split(",")) {
            try {
                out.add(Priority.valueOf(p.trim()));
            } catch (IllegalArgumentException ignored) {
                // gia tri cu khong con dung
            }
        }
        return out;
    }

    public static String join(Set<Priority> set) {
        return set == null ? null : set.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    public static Set<Priority> parse(java.util.Collection<String> values) {
        Set<Priority> out = EnumSet.noneOf(Priority.class);
        if (values != null) {
            values.forEach(v -> Arrays.stream(Priority.values()).filter(p -> p.name().equals(v)).forEach(out::add));
        }
        return out;
    }
}
