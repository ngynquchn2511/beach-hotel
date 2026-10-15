package com.hotel.controller;

import com.hotel.entity.Room;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.util.Pager;
import com.hotel.util.RoomListing;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class RoomController {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;

    public RoomController(RoomRepository roomRepository, RoomTypeRepository roomTypeRepository) {
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
    }

    // So the phong moi trang (luoi 3 cot)
    static final int ROOMS_PER_PAGE = 9;

    // Danh sach phong cong khai - loc theo loai phong va khoang gia, sap xep theo so phong, chia trang
    @GetMapping("/rooms")
    public String listRooms(@RequestParam(required = false) Long roomTypeId,
                            @RequestParam(required = false) String price,
                            @RequestParam(defaultValue = "1") int page, Model model) {
        var rooms = roomRepository.findAll().stream()
                .filter(r -> roomTypeId == null || r.getRoomType().getId().equals(roomTypeId))
                .sorted(Comparator.comparing(Room::getRoomNumber))
                .toList();
        Pager<Room> pager = Pager.of(RoomListing.apply(rooms, price, null), page, ROOMS_PER_PAGE);

        model.addAttribute("pager", pager);
        model.addAttribute("rooms", pager.items());
        model.addAttribute("price", price);
        model.addAttribute("priceRanges", RoomListing.PriceRange.values());
        model.addAttribute("roomTypes", roomTypeRepository.findAll());
        model.addAttribute("roomTypeId", roomTypeId);
        return "rooms";
    }

    // So sanh toi da 3 phong canh nhau: ?ids=12,15,40 (bo id trung / khong ton tai)
    static final int MAX_COMPARE = 3;

    @GetMapping("/rooms/compare")
    public String compare(@RequestParam(required = false) String ids, Model model) {
        List<Long> wanted = new ArrayList<>();
        if (ids != null) {
            for (String part : ids.split(",")) {
                try {
                    Long id = Long.valueOf(part.trim());
                    // Gioi han so id doc tu URL; loc phong co that roi moi lay 3 phong dau
                    if (!wanted.contains(id) && wanted.size() < 10) {
                        wanted.add(id);
                    }
                } catch (NumberFormatException ignored) {
                    // bo qua gia tri la
                }
            }
        }
        // Giu dung thu tu khach da chon
        Map<Long, Room> found = roomRepository.findAllById(wanted).stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));
        List<Room> rooms = wanted.stream().map(found::get).filter(Objects::nonNull).limit(MAX_COMPARE).toList();

        model.addAttribute("rooms", rooms);
        // To xanh o tot nhat: gia thap nhat, dien tich / so khach lon nhat
        model.addAttribute("minPrice", rooms.stream().map(Room::getPrice).min(Comparator.naturalOrder()).orElse(null));
        model.addAttribute("maxArea", rooms.stream().map(r -> r.getRoomType().getArea()).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
        model.addAttribute("maxGuests", rooms.stream().map(r -> r.getRoomType().getMaxGuests()).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
        return "compare";
    }
}
