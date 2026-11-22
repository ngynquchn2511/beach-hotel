package com.hotel.service;

import com.hotel.entity.DivingTour;
import com.hotel.entity.Room;
import com.hotel.entity.RoomType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Ke hoach cho tung ngay du bao (trang Goi y phong cua khach): bam 1 ngay -> diem du lich hop thoi tiet ngay do
// (gio nen di, tour cua resort neu co) + phong con trong dem do (dat ngay voi ngay dien san)
@Service
public class TripPlanService {

    static final int MAX_SPOTS = 4;
    static final int MAX_ROOMS = 3;

    private final RoomService roomService;
    private final DivingTourService tourService;

    public TripPlanService(RoomService roomService, DivingTourService tourService) {
        this.roomService = roomService;
        this.tourService = tourService;
    }

    // Diem nen di: spot, gio dep nhat trong khung gio cua diem (null voi diem trong nha / da qua gio), tour cua resort khop
    public record SpotPick(HaLongSpot spot, WeatherService.Hour bestHour, DivingTour tour) {
    }

    // Loai phong con trong dem do: phong re nhat lam dai dien + so phong trong
    public record RoomPick(RoomType type, Room room, BigDecimal price, int available) {
    }

    public record DayPlan(List<SpotPick> spots, List<RoomPick> rooms) {
    }

    private PricingService pricingService;

    // Gia dem do theo bang gia (cuoi tuan, ngay le...); khong co thi dung gia goc
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPricingService(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public Map<java.time.LocalDate, DayPlan> plan(List<WeatherService.Day> days, int guests) {
        List<DivingTour> tours = tourService.findActive();
        Map<java.time.LocalDate, DayPlan> out = new LinkedHashMap<>();
        for (WeatherService.Day d : days) {
            out.put(d.date(), new DayPlan(spots(d, tours), rooms(d.date(), guests)));
        }
        return out;
    }

    List<SpotPick> spots(WeatherService.Day d, List<DivingTour> tours) {
        record Scored(HaLongSpot spot, int fit) {
        }
        List<Scored> scored = new ArrayList<>();
        for (HaLongSpot s : HaLongSpot.values()) {
            int fit = s.fit(d);
            // Hom nay da qua khung gio cua diem thi bo
            if (fit < 0 || (d.isToday() && java.time.LocalTime.now().getHour() > s.getTo())) {
                continue;
            }
            scored.add(new Scored(s, fit));
        }
        scored.sort(Comparator.comparingInt(Scored::fit).reversed());
        List<SpotPick> out = new ArrayList<>();
        for (Scored x : scored.subList(0, Math.min(MAX_SPOTS, scored.size()))) {
            WeatherService.Hour best = x.spot().isOutdoor() ? d.bestHourBetween(x.spot().getFrom(), x.spot().getTo()) : null;
            out.add(new SpotPick(x.spot(), best, tourFor(x.spot(), tours)));
        }
        return out;
    }

    static DivingTour tourFor(HaLongSpot spot, List<DivingTour> tours) {
        for (String kw : spot.tourKeywords()) {
            for (DivingTour t : tours) {
                String name = " " + RoomSuggestionService.normalize((t.getName() == null ? "" : t.getName()) + " "
                        + (t.getNameEn() == null ? "" : t.getNameEn())).replaceAll("[^a-z0-9]+", " ") + " ";
                if (name.contains(" " + kw + " ")) {
                    return t;
                }
            }
        }
        return null;
    }

    List<RoomPick> rooms(java.time.LocalDate night, int guests) {
        Map<Long, List<Room>> byType = new LinkedHashMap<>();
        for (Room r : roomService.searchAvailableRooms(night, night.plusDays(1), null, null)) {
            RoomType t = r.getRoomType();
            if (t.getMaxGuests() != null && guests > t.getMaxGuests()) {
                continue;
            }
            byType.computeIfAbsent(t.getId(), k -> new ArrayList<>()).add(r);
        }
        return byType.values().stream()
                .map(list -> {
                    Room cheapest = list.stream().min(Comparator.comparing(Room::getPrice)).orElseThrow();
                    BigDecimal price = pricingService == null ? cheapest.getPrice()
                            : pricingService.roomAmount(cheapest.getPrice(), night, night.plusDays(1));
                    return new RoomPick(cheapest.getRoomType(), cheapest, price, list.size());
                })
                .sorted(Comparator.comparing(RoomPick::price))
                .limit(MAX_ROOMS)
                .toList();
    }
}
