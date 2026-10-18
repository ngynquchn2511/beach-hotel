package com.hotel.service;

import com.hotel.entity.Booking;
import com.hotel.entity.BookingCharge;
import com.hotel.entity.BookingStatus;
import com.hotel.entity.Room;
import com.hotel.entity.RoomType;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Hieu qua theo loai phong trong thang: cong suat, ADR, RevPAR, doanh thu dich vu kem don, dong gop sau chi phi bien doi
// (gia von dich vu + chi phi moi phong-dem). Chi phi van hanh co dinh khong chia cho tung loai phong
@Service
public class RoomTypePerformanceService {

    private static final Set<BookingStatus> SOLD = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);

    private final BookingRepository bookingRepository;
    private final BookingChargeRepository chargeRepository;
    private final RoomRepository roomRepository;
    private final ExpenseService expenseService;

    public RoomTypePerformanceService(BookingRepository bookingRepository, BookingChargeRepository chargeRepository,
                                      RoomRepository roomRepository, ExpenseService expenseService) {
        this.bookingRepository = bookingRepository;
        this.chargeRepository = chargeRepository;
        this.roomRepository = roomRepository;
        this.expenseService = expenseService;
    }

    public record Row(RoomType type, int rooms, long soldNights, long availableNights, BigDecimal roomRevenue,
                      BigDecimal serviceRevenue, BigDecimal variableCost) {
        public double occupancy() {
            return availableNights == 0 ? 0 : Math.round(soldNights * 1000.0 / availableNights) / 10.0;
        }

        public BigDecimal adr() {
            return soldNights == 0 ? BigDecimal.ZERO : roomRevenue.divide(BigDecimal.valueOf(soldNights), 0, RoundingMode.HALF_UP);
        }

        public BigDecimal revPar() {
            return availableNights == 0 ? BigDecimal.ZERO : roomRevenue.divide(BigDecimal.valueOf(availableNights), 0, RoundingMode.HALF_UP);
        }

        public BigDecimal contribution() {
            return roomRevenue.add(serviceRevenue).subtract(variableCost);
        }

        // Dong gop tren moi phong-dem san co: so sanh cong bang giua loai nhieu / it phong
        public BigDecimal contributionPerAvailableNight() {
            return availableNights == 0 ? BigDecimal.ZERO : contribution().divide(BigDecimal.valueOf(availableNights), 0, RoundingMode.HALF_UP);
        }
    }

    @Transactional(readOnly = true)
    public List<Row> month(YearMonth m) {
        LocalDate from = m.atDay(1);
        LocalDate to = m.plusMonths(1).atDay(1);
        Map<Long, RoomType> types = new LinkedHashMap<>();
        Map<Long, Integer> roomsPerType = new HashMap<>();
        for (Room r : roomRepository.findAll()) {
            types.putIfAbsent(r.getRoomType().getId(), r.getRoomType());
            roomsPerType.merge(r.getRoomType().getId(), 1, Integer::sum);
        }
        Map<Long, long[]> nights = new HashMap<>();
        Map<Long, BigDecimal> revenue = new HashMap<>();
        for (Booking b : bookingRepository.findAll()) {
            if (!SOLD.contains(b.getStatus())) {
                continue;
            }
            long total = ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate());
            LocalDate s = b.getCheckInDate().isAfter(from) ? b.getCheckInDate() : from;
            LocalDate e = b.getCheckOutDate().isBefore(to) ? b.getCheckOutDate() : to;
            long in = ChronoUnit.DAYS.between(s, e);
            if (total <= 0 || in <= 0) {
                continue;
            }
            Long typeId = b.getRoom().getRoomType().getId();
            nights.computeIfAbsent(typeId, k -> new long[1])[0] += in;
            revenue.merge(typeId, b.getTotalAmount().multiply(BigDecimal.valueOf(in)).divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP),
                    BigDecimal::add);
        }
        Map<com.hotel.entity.ChargeType, Integer> rates = expenseService.costRates();
        Map<Long, BigDecimal> services = new HashMap<>();
        Map<Long, BigDecimal> serviceCost = new HashMap<>();
        for (BookingCharge c : chargeRepository.findAll()) {
            if (c.getCreatedAt() == null || !YearMonth.from(c.getCreatedAt()).equals(m) || c.getBooking().getStatus() == BookingStatus.CANCELLED) {
                continue;
            }
            Long typeId = c.getBooking().getRoom().getRoomType().getId();
            services.merge(typeId, c.getAmount(), BigDecimal::add);
            int pct = rates.getOrDefault(c.getType(), 0);
            if (pct > 0) {
                serviceCost.merge(typeId, c.getAmount().multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP),
                        BigDecimal::add);
            }
        }
        BigDecimal perNight = expenseService.variableCostPerNight();
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<Long, RoomType> t : types.entrySet()) {
            long sold = nights.getOrDefault(t.getKey(), new long[1])[0];
            int rooms = roomsPerType.get(t.getKey());
            rows.add(new Row(t.getValue(), rooms, sold, (long) rooms * m.lengthOfMonth(), revenue.getOrDefault(t.getKey(), BigDecimal.ZERO),
                    services.getOrDefault(t.getKey(), BigDecimal.ZERO),
                    perNight.multiply(BigDecimal.valueOf(sold)).add(serviceCost.getOrDefault(t.getKey(), BigDecimal.ZERO))));
        }
        rows.sort(Comparator.comparing(Row::contributionPerAvailableNight).reversed());
        return rows;
    }
}
