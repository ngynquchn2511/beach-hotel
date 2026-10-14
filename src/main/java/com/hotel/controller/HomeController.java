package com.hotel.controller;

import com.hotel.repository.ComboRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ComboRepository comboRepository;
    private final ReviewService reviewService;

    public HomeController(RoomTypeRepository roomTypeRepository,
                          RoomRepository roomRepository,
                          ComboRepository comboRepository,
                          ReviewService reviewService) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.comboRepository = comboRepository;
        this.reviewService = reviewService;
    }

    @GetMapping({"/", "/home"})
    public String home() {
        return "home";
    }

    // Trang gioi thieu khach san - so lieu (loai phong, so phong, combo) lay that tu CSDL
    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("roomTypes", roomTypeRepository.findAll());
        model.addAttribute("roomCount", roomRepository.count());
        model.addAttribute("combos", comboRepository.findByActiveTrue());
        // Danh gia that cua khach da tra phong - neu chua co thi trang hien cac nhan xet mau
        model.addAttribute("reviews", reviewService.findLatestPublicReviews());
        return "about";
    }
}
