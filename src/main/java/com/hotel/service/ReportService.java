package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingChannel;
import com.hotel.entity.BookingCharge;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.ChargeType;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Bao cao chi tiet cho dashboard + file Excel. Cung cach tinh voi "Doanh thu theo loai phong":
// don TAO trong khoang ngay, bo don da huy, doanh thu = tien don + phu phi.
@Service
public class ReportService {

    private final BookingRepository bookingRepository;
    private final BookingChargeRepository chargeRepository;

    public ReportService(BookingRepository bookingRepository, BookingChargeRepository chargeRepository) {
        this.bookingRepository = bookingRepository;
        this.chargeRepository = chargeRepository;
    }

    // share: ty trong doanh thu (%) cua kenh trong tong doanh thu cac kenh
    public record ChannelRow(BookingChannel channel, long bookings, BigDecimal revenue, double share) {
        public String label() { return channel.getVietnameseLabel(); }
    }

    public record DiscountRow(String code, long uses, BigDecimal discountTotal, BigDecimal revenue) {
    }

    // Don huy gom ca khach tu huy, le tan huy va he thong tu huy (qua han coc / khong xac nhan kip)
    public record CancellationStats(long created, long cancelled, double cancelRate, long withDepositPaid,
                                    BigDecimal refunded, BigDecimal forfeited) {
    }

    public record ServiceRow(ChargeType type, long count, BigDecimal amount) {
        public String label() { return type.getVietnameseLabel(); }
    }

    public record Report(List<ChannelRow> channels, List<DiscountRow> discounts, CancellationStats cancellations,
                         List<ServiceRow> services) {
        public BigDecimal servicesTotal() {
            return services.stream().map(ServiceRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    @Transactional(readOnly = true)
    public Report build(LocalDate from, LocalDate to) {
        List<Booking> created = bookingRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(b -> b.getCreatedAt() != null)
                .filter(b -> !b.getCreatedAt().toLocalDate().isBefore(from) && !b.getCreatedAt().toLocalDate().isAfter(to))
                .toList();
        List<Booking> kept = created.stream().filter(b -> b.getStatus() != BookingStatus.CANCELLED).toList();
        Set<Long> keptIds = kept.stream().map(Booking::getId).collect(Collectors.toSet());

        List<BookingCharge> charges = chargeRepository.findAll().stream()
                .filter(c -> keptIds.contains(c.getBooking().getId()))
                .toList();
        Map<Long, BigDecimal> chargeByBooking = new java.util.HashMap<>();
        charges.forEach(c -> chargeByBooking.merge(c.getBooking().getId(), c.getAmount(), BigDecimal::add));

        return new Report(channels(kept, chargeByBooking), discounts(kept, chargeByBooking),
                cancellations(created), services(charges));
    }

    private static BigDecimal revenueOf(Booking b, Map<Long, BigDecimal> charges) {
        return b.getTotalAmount().add(charges.getOrDefault(b.getId(), BigDecimal.ZERO));
    }

    private List<ChannelRow> channels(List<Booking> kept, Map<Long, BigDecimal> charges) {
        Map<BookingChannel, long[]> counts = new EnumMap<>(BookingChannel.class);
        Map<BookingChannel, BigDecimal> revenue = new EnumMap<>(BookingChannel.class);
        for (BookingChannel ch : BookingChannel.values()) {
            counts.put(ch, new long[1]);
            revenue.put(ch, BigDecimal.ZERO);
        }
        for (Booking b : kept) {
            BookingChannel ch = b.getEffectiveChannel();
            counts.get(ch)[0]++;
            revenue.merge(ch, revenueOf(b, charges), BigDecimal::add);
        }
        BigDecimal total = revenue.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ChannelRow> rows = new ArrayList<>();
        for (BookingChannel ch : BookingChannel.values()) {
            double share = total.signum() == 0 ? 0
                    : revenue.get(ch).multiply(BigDecimal.valueOf(1000)).divide(total, 0, java.math.RoundingMode.HALF_UP).doubleValue() / 10;
            rows.add(new ChannelRow(ch, counts.get(ch)[0], revenue.get(ch), share));
        }
        return rows;
    }

    // Ma giam gia: so lan dung, tong tien da giam, doanh thu mang ve - xep theo so lan dung
    private List<DiscountRow> discounts(List<Booking> kept, Map<Long, BigDecimal> charges) {
        Map<String, DiscountRow> byCode = new LinkedHashMap<>();
        for (Booking b : kept) {
            if (b.getDiscountCode() == null) {
                continue;
            }
            String code = b.getDiscountCode().getCode();
            DiscountRow prev = byCode.getOrDefault(code, new DiscountRow(code, 0, BigDecimal.ZERO, BigDecimal.ZERO));
            BigDecimal discount = b.getDiscountAmount() != null ? b.getDiscountAmount() : BigDecimal.ZERO;
            byCode.put(code, new DiscountRow(code, prev.uses() + 1, prev.discountTotal().add(discount),
                    prev.revenue().add(revenueOf(b, charges))));
        }
        return byCode.values().stream()
                .sorted(Comparator.comparingLong(DiscountRow::uses).reversed().thenComparing(DiscountRow::code))
                .toList();
    }

    private CancellationStats cancellations(List<Booking> created) {
        List<Booking> cancelled = created.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).toList();
        long withDeposit = cancelled.stream().filter(Booking::isDepositPaid).count();
        BigDecimal refunded = cancelled.stream()
                .map(b -> b.getRefundAmount() != null ? b.getRefundAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal forfeited = cancelled.stream().map(Booking::getForfeitedDepositAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double rate = created.isEmpty() ? 0 : Math.round(1000.0 * cancelled.size() / created.size()) / 10.0;
        return new CancellationStats(created.size(), cancelled.size(), rate, withDeposit, refunded, forfeited);
    }

    // Dich vu them / phu phi theo loai (tour lan, xe dua don, minibar...) - xep theo doanh thu
    private List<ServiceRow> services(List<BookingCharge> charges) {
        Map<ChargeType, long[]> counts = new EnumMap<>(ChargeType.class);
        Map<ChargeType, BigDecimal> amounts = new EnumMap<>(ChargeType.class);
        for (BookingCharge c : charges) {
            counts.computeIfAbsent(c.getType(), k -> new long[1])[0]++;
            amounts.merge(c.getType(), c.getAmount(), BigDecimal::add);
        }
        return amounts.entrySet().stream()
                .map(e -> new ServiceRow(e.getKey(), counts.get(e.getKey())[0], e.getValue()))
                .sorted(Comparator.comparing(ServiceRow::amount).reversed())
                .toList();
    }
}
