package com.hotel.service;

import com.hotel.entity.GuestPreference;
import com.hotel.entity.GuestPreference.Priority;
import com.hotel.entity.GuestPreference.Purpose;
import com.hotel.entity.GuestPreference.View;
import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.RoomType;
import com.hotel.entity.User;
import com.hotel.repository.GuestPreferenceRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Goi y phong theo so thich khach tu tra loi (so nguoi, muc dich, ngan sach, canh nhin, uu tien):
// cham diem tung loai phong theo so nguoi toi da, gia, tu khoa trong ten / tien nghi / mo ta; neu khach chon ngay thi chi
// lay loai con phong trong. Tra ve toi da 3 loai kem ly do (de khach hieu vi sao duoc goi y)
@Service
public class RoomSuggestionService {

    public static final int MAX_GUESTS = 10;
    public static final int TOP = 3;
    // Khach chon ngay: toi da 30 dem
    public static final int MAX_NIGHTS = 30;

    private static final Map<View, List<String>> VIEW_WORDS = Map.of(
            View.SEA, List.of("bien", "vinh", "dao", "sea", "bay", "ocean", "beach"),
            View.GARDEN, List.of("vuon", "garden"),
            View.POOL, List.of("ho boi", "pool"));
    private static final Map<Priority, List<String>> PRIORITY_WORDS = Map.of(
            Priority.BATHTUB, List.of("bon tam", "be ngam", "bathtub", "tub", "jacuzzi"),
            Priority.BALCONY, List.of("ban cong", "san hien", "san thuong", "balcony", "terrace"),
            Priority.KING_BED, List.of("king"),
            Priority.LIVING_ROOM, List.of("phong khach", "sofa", "living"),
            Priority.PRIVACY, List.of("bungalow", "penthouse", "rieng", "private"));
    // Dien tich tu 45 m2 = rong rai
    static final BigDecimal SPACIOUS_AREA = BigDecimal.valueOf(45);

    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final GuestPreferenceRepository preferenceRepository;

    public RoomSuggestionService(RoomRepository roomRepository, RoomService roomService, GuestPreferenceRepository preferenceRepository) {
        this.roomRepository = roomRepository;
        this.roomService = roomService;
        this.preferenceRepository = preferenceRepository;
    }

    public record Answers(int guests, Purpose purpose, Long maxBudget, View view, Set<Priority> priorities,
                          LocalDate checkIn, LocalDate checkOut) {
        public Answers {
            guests = Math.max(1, Math.min(MAX_GUESTS, guests));
            view = view == null ? View.ANY : view;
            priorities = priorities == null ? EnumSet.noneOf(Priority.class) : priorities;
            maxBudget = maxBudget != null && maxBudget <= 0 ? null : maxBudget;
        }

        public boolean hasDates() {
            return RoomService.isValidStay(checkIn, checkOut) && ChronoUnit.DAYS.between(checkIn, checkOut) <= MAX_NIGHTS;
        }
    }

    // Ly do goi y: key trong messages (suggest.reason.*), arg (co the null)
    public record Reason(String key, Object arg, boolean good) {
    }

    public record Suggestion(RoomType type, Room room, BigDecimal price, int available, int score, int match, List<Reason> reasons) {
    }

    public record Result(List<Suggestion> top, int consideredTypes, boolean byDates, boolean nothingFits) {
    }

    @Transactional(readOnly = true)
    public Result suggest(Answers a) {
        List<Room> rooms = a.hasDates()
                ? roomService.searchAvailableRooms(a.checkIn(), a.checkOut(), null, null)
                : roomRepository.findAll().stream().filter(r -> r.getStatus() != RoomStatus.MAINTENANCE).toList();
        // Moi loai phong: phong re nhat (dai dien) + so phong
        Map<Long, List<Room>> byType = new LinkedHashMap<>();
        for (Room r : rooms) {
            byType.computeIfAbsent(r.getRoomType().getId(), k -> new ArrayList<>()).add(r);
        }
        BigDecimal maxPrice = rooms.stream().map(Room::getPrice).max(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
        List<Suggestion> all = new ArrayList<>();
        for (List<Room> list : byType.values()) {
            Room cheapest = list.stream().min(Comparator.comparing(Room::getPrice)).orElseThrow();
            RoomType t = cheapest.getRoomType();
            if (t.getMaxGuests() != null && a.guests() > t.getMaxGuests()) {
                continue;
            }
            all.add(score(a, t, cheapest, list.size(), maxPrice));
        }
        all.sort(Comparator.comparingInt(Suggestion::score).reversed().thenComparing(Suggestion::price));
        boolean nothingFits = all.isEmpty();
        return new Result(all.stream().limit(TOP).toList(), all.size(), a.hasDates(), nothingFits);
    }

    Suggestion score(Answers a, RoomType t, Room room, int available, BigDecimal maxPrice) {
        String text = normalize(String.join(" ", nz(t.getName()), nz(t.getNameEn()), nz(t.getAmenities()), nz(t.getAmenitiesEn()),
                nz(t.getDescription()), nz(room.getDescription())));
        BigDecimal price = room.getPrice();
        int maxGuests = t.getMaxGuests() == null ? a.guests() : t.getMaxGuests();
        List<Reason> reasons = new ArrayList<>();
        int score = 0;
        int max = 0;

        // Hop so nguoi / muc dich
        max += 18;
        Purpose p = a.purpose();
        if (p == Purpose.COUPLE || p == Purpose.SOLO) {
            if (maxGuests <= 2) {
                score += 10;
                reasons.add(new Reason("suggest.reason.cozy", null, true));
            }
            if (p == Purpose.COUPLE && (contains(text, PRIORITY_WORDS.get(Priority.BATHTUB)) || contains(text, VIEW_WORDS.get(View.SEA)))) {
                score += 8;
                reasons.add(new Reason("suggest.reason.romantic", null, true));
            }
        } else if (p == Purpose.FAMILY || p == Purpose.FRIENDS) {
            if (maxGuests >= 3) {
                score += 10;
                reasons.add(new Reason("suggest.reason.group", maxGuests, true));
            }
            if (p == Purpose.FAMILY && contains(text, List.of("gia dinh", "family", "sofa"))) {
                score += 8;
                reasons.add(new Reason("suggest.reason.family", null, true));
            }
            if (p == Purpose.FRIENDS && contains(text, List.of("2 giuong", "twin", "2 phong ngu"))) {
                score += 8;
                reasons.add(new Reason("suggest.reason.twin", null, true));
            }
        } else if (p == Purpose.BUSINESS) {
            if (contains(text, List.of("wi-fi", "wifi"))) {
                score += 8;
                reasons.add(new Reason("suggest.reason.wifi", null, true));
            }
            if (maxGuests <= 2) {
                score += 10;
            }
        }

        // Ngan sach
        if (a.maxBudget() != null) {
            max += 30;
            BigDecimal budget = BigDecimal.valueOf(a.maxBudget());
            if (price.compareTo(budget) <= 0) {
                score += 30;
                reasons.add(new Reason("suggest.reason.inBudget", null, true));
            } else {
                int over = price.subtract(budget).multiply(BigDecimal.valueOf(100)).divide(budget, 0, java.math.RoundingMode.UP).intValue();
                score += over <= 20 ? 5 : -40;
                reasons.add(new Reason("suggest.reason.overBudget", over, false));
            }
        }

        // Canh nhin
        if (a.view() != View.ANY) {
            max += 25;
            if (contains(text, VIEW_WORDS.get(a.view()))) {
                score += 25;
                reasons.add(new Reason("suggest.reason.view." + a.view().name(), null, true));
            } else {
                reasons.add(new Reason("suggest.reason.noView." + a.view().name(), null, false));
            }
        }

        // Uu tien
        for (Priority pr : a.priorities()) {
            max += pr == Priority.BUDGET ? 15 : 12;
            boolean hit = switch (pr) {
                case SPACIOUS -> t.getArea() != null && t.getArea().compareTo(SPACIOUS_AREA) >= 0;
                case BUDGET -> false;
                default -> contains(text, PRIORITY_WORDS.get(pr));
            };
            if (pr == Priority.BUDGET) {
                int bonus = 15 - price.multiply(BigDecimal.valueOf(15)).divide(maxPrice, 0, java.math.RoundingMode.HALF_UP).intValue();
                score += Math.max(0, bonus);
                if (bonus >= 8) {
                    reasons.add(new Reason("suggest.reason.cheap", null, true));
                }
            } else if (hit) {
                score += 12;
                reasons.add(new Reason("suggest.reason.priority." + pr.name(), pr == Priority.SPACIOUS ? t.getArea().intValue() : null, true));
            }
        }
        int match = max == 0 ? 100 : Math.max(0, Math.min(100, score * 100 / max));
        // Ly do tot len truoc
        reasons.sort(Comparator.comparing(r -> !r.good()));
        return new Suggestion(t, room, price, available, score, match, reasons);
    }

    // ===== Luu so thich (khach da dang nhap) =====

    @Transactional(readOnly = true)
    public Optional<GuestPreference> preferenceOf(Long userId) {
        return preferenceRepository.findByUserId(userId);
    }

    @Transactional
    public GuestPreference save(User user, Answers a, String note) {
        GuestPreference p = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> GuestPreference.builder().user(user).build());
        p.setGuests(a.guests());
        p.setPurpose(a.purpose());
        p.setMaxBudget(a.maxBudget());
        p.setPreferredView(a.view());
        p.setPriorities(GuestPreference.join(a.priorities()));
        String text = note == null || note.isBlank() ? null : note.trim();
        p.setNote(text != null && text.length() > 300 ? text.substring(0, 300) : text);
        p.setUpdatedAt(LocalDateTime.now());
        return preferenceRepository.save(p);
    }

    static String normalize(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('đ', 'd');
    }

    private static boolean contains(String text, List<String> words) {
        return words.stream().anyMatch(w -> {
            int i = text.indexOf(w);
            // "dao" khong khop "dao tao"... chi can khop dau tu
            while (i >= 0) {
                if (i == 0 || !Character.isLetter(text.charAt(i - 1))) {
                    return true;
                }
                i = text.indexOf(w, i + 1);
            }
            return false;
        });
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
