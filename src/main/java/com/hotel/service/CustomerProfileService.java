package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingCharge;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.CustomerType;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Ho so khach cho quan tri: lich su dat phong day du + thong ke, va hang khach (lan dau / quen / VIP)
// tu tinh theo so lan luu tru da hoan tat (da tra phong) - khong chinh tay nua
@Service
public class CustomerProfileService {

    private static final Logger log = LoggerFactory.getLogger(CustomerProfileService.class);

    // Thong ke nhanh 1 khach o danh sach
    public record Summary(long bookings, long stays, long upcoming, long cancelled, LocalDate lastStay) {
    }

    public record Row(User customer, Summary summary) {
    }

    public record Profile(User customer, List<Booking> bookings, Map<Long, BigDecimal> spentByBooking,
                          long totalBookings, long stays, long inHouse, long upcoming, long pending, long cancelled,
                          long noShows, long nights, BigDecimal totalSpent, LocalDate firstStay, LocalDate lastStay,
                          String favoriteRoomType, CustomerType next, long staysToNext) {
        public BigDecimal getAverageSpent() {
            return stays == 0 ? BigDecimal.ZERO
                    : totalSpent.divide(BigDecimal.valueOf(stays), 0, java.math.RoundingMode.HALF_UP);
        }

        // % tien do len hang tiep theo
        public int getProgressPercent() {
            if (next == null) {
                return 100;
            }
            int from = customer.getCustomerType() == null ? 0 : customer.getCustomerType().minStays();
            int span = next.minStays() - from;
            return span <= 0 ? 100 : (int) Math.min(100, Math.max(0, (stays - from) * 100 / span));
        }
    }

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingChargeRepository chargeRepository;

    public CustomerProfileService(UserRepository userRepository, BookingRepository bookingRepository,
                                  BookingChargeRepository chargeRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.chargeRepository = chargeRepository;
    }

    // ===== Hang khach tu dong =====

    // Tinh lai hang 1 khach theo so lan luu tru hoan tat (len hoac xuong). Tra ve hang moi
    @Transactional
    public CustomerType syncTier(User customer) {
        if (customer == null || customer.getRole() != UserRole.CUSTOMER) {
            return customer == null ? null : customer.getCustomerType();
        }
        long stays = bookingRepository.countByCustomerIdAndStatus(customer.getId(), BookingStatus.CHECKED_OUT);
        CustomerType tier = CustomerType.forCompletedStays(stays);
        if (customer.getCustomerType() != tier) {
            customer.setCustomerType(tier);
            userRepository.save(customer);
        }
        return tier;
    }

    // Dong bo hang cho moi khach (chay khi khoi dong app): sua cac hang da chinh tay truoc day cho khop so lan o
    @Transactional
    public int syncAllTiers() {
        Map<Long, Long> stays = completedStaysByCustomer();
        int changed = 0;
        for (User u : userRepository.findByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER)) {
            CustomerType tier = CustomerType.forCompletedStays(stays.getOrDefault(u.getId(), 0L));
            if (u.getCustomerType() != tier) {
                u.setCustomerType(tier);
                userRepository.save(u);
                changed++;
            }
        }
        return changed;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            int changed = syncAllTiers();
            if (changed > 0) {
                log.info("Da cap nhat hang cho {} khach theo so lan luu tru", changed);
            }
        } catch (RuntimeException e) {
            log.warn("Khong dong bo duoc hang khach: {}", e.getMessage());
        }
    }

    private Map<Long, Long> completedStaysByCustomer() {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : bookingRepository.countByCustomerAndStatus()) {
            if (r[1] == BookingStatus.CHECKED_OUT) {
                map.put((Long) r[0], ((Number) r[2]).longValue());
            }
        }
        return map;
    }

    // ===== Danh sach =====

    @Transactional(readOnly = true)
    public List<Row> list(String q, CustomerType type) {
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] r : bookingRepository.countByCustomerAndStatus()) {
            long[] c = counts.computeIfAbsent((Long) r[0], k -> new long[4]);
            long n = ((Number) r[2]).longValue();
            BookingStatus s = (BookingStatus) r[1];
            c[0] += n;
            if (s == BookingStatus.CHECKED_OUT) {
                c[1] += n;
            } else if (s == BookingStatus.CANCELLED) {
                c[3] += n;
            } else {
                c[2] += n;
            }
        }
        Map<Long, LocalDate> last = new HashMap<>();
        for (Object[] r : bookingRepository.lastStayByCustomer()) {
            last.put((Long) r[0], (LocalDate) r[1]);
        }
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER).stream()
                .filter(u -> type == null || u.getCustomerType() == type)
                .filter(u -> needle == null || contains(u.getFullName(), needle) || contains(u.getEmail(), needle)
                        || contains(u.getPhoneNumber(), needle))
                .map(u -> {
                    long[] c = counts.getOrDefault(u.getId(), new long[4]);
                    return new Row(u, new Summary(c[0], c[1], c[2], c[3], last.get(u.getId())));
                })
                .toList();
    }

    private static boolean contains(String s, String needle) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(needle);
    }

    // ===== Ho so 1 khach =====

    @Transactional(readOnly = true)
    public Profile profile(Long customerId) {
        User u = userRepository.findById(customerId).filter(x -> x.getRole() == UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException("Không tìm thấy khách hàng"));
        List<Booking> bookings = new ArrayList<>(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId));
        bookings.sort(Comparator.comparing(Booking::getCheckInDate).reversed());

        // Tien khach da tra cho don da o xong: tien don + phu phi / dich vu them
        Map<Long, BigDecimal> charges = bookings.isEmpty() ? Map.of()
                : chargeRepository.findByBookingIdIn(bookings.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.groupingBy(c -> c.getBooking().getId(),
                        Collectors.reducing(BigDecimal.ZERO, BookingCharge::getAmount, BigDecimal::add)));
        Map<Long, BigDecimal> spent = new HashMap<>();
        for (Booking b : bookings) {
            BigDecimal total = (b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount())
                    .add(charges.getOrDefault(b.getId(), BigDecimal.ZERO));
            spent.put(b.getId(), total);
        }

        List<Booking> done = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CHECKED_OUT).toList();
        long stays = done.size();
        BigDecimal totalSpent = done.stream().map(b -> spent.get(b.getId())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long nights = done.stream().mapToLong(Booking::getNights).sum();
        String favorite = done.stream()
                .collect(Collectors.groupingBy(b -> b.getRoom().getRoomType().getName(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.<String, Long>comparingByValue())
                .map(e -> e.getKey() + " (" + e.getValue() + " lần)").orElse(null);
        LocalDate today = LocalDate.now();
        CustomerType current = CustomerType.forCompletedStays(stays);
        CustomerType next = current.next();

        return new Profile(u, bookings, spent, bookings.size(), stays,
                count(bookings, b -> b.getStatus() == BookingStatus.CHECKED_IN),
                count(bookings, b -> b.getStatus() == BookingStatus.CONFIRMED && !b.getCheckInDate().isBefore(today)),
                count(bookings, b -> b.getStatus() == BookingStatus.PENDING),
                count(bookings, b -> b.getStatus() == BookingStatus.CANCELLED && !b.isNoShow()),
                count(bookings, Booking::isNoShow),
                nights, totalSpent,
                done.stream().map(Booking::getCheckInDate).min(Comparator.naturalOrder()).orElse(null),
                done.stream().map(Booking::getCheckOutDate).max(Comparator.naturalOrder()).orElse(null),
                favorite, next, next == null ? 0 : Math.max(0, next.minStays() - stays));
    }

    private static long count(List<Booking> list, Function<Booking, Boolean> f) {
        return list.stream().filter(f::apply).count();
    }
}
