package com.hotel.controller;

import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RoomController {

    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;

    public RoomController(RoomRepository roomRepository, RoomTypeRepository roomTypeRepository) {
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
    }

    // Danh sach phong cong khai - co the loc theo loai phong. Loc theo gia lam o phia trinh duyet (JS).
    @GetMapping("/rooms")
    public String listRooms(@RequestParam(required = false) Long roomTypeId, Model model) {
        var rooms = roomRepository.findAll().stream()
                .filter(r -> roomTypeId == null || r.getRoomType().getId().equals(roomTypeId))
                .toList();

        model.addAttribute("rooms", rooms);
        model.addAttribute("roomTypes", roomTypeRepository.findAll());
        model.addAttribute("roomTypeId", roomTypeId);
        return "rooms";
    }
}
