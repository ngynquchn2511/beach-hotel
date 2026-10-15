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

import java.util.Comparator;

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
}
