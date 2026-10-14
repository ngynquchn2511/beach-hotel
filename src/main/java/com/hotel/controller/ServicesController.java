package com.hotel.controller;

import com.hotel.entity.Airport;
import com.hotel.entity.VehicleType;
import com.hotel.service.DivingTourService;
import com.hotel.service.ServiceOrderService;
import com.hotel.service.TransferRateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// Trang gioi thieu dich vu them (tour lan bien, dua don san bay) - ai cung xem duoc.
// Dat dich vu thuc hien o trang chi tiet don phong (dich vu luon gan voi 1 don)
@Controller
public class ServicesController {

    private final DivingTourService divingTourService;
    private final TransferRateService transferRateService;

    public ServicesController(DivingTourService divingTourService, TransferRateService transferRateService) {
        this.divingTourService = divingTourService;
        this.transferRateService = transferRateService;
    }

    @GetMapping("/services")
    public String services(Model model) {
        model.addAttribute("tours", divingTourService.findActive());
        model.addAttribute("airports", Airport.values());
        model.addAttribute("vehicles", VehicleType.values());
        model.addAttribute("rates", transferRateService.priceTable());
        model.addAttribute("tourLeadHours", ServiceOrderService.TOUR_LEAD_HOURS);
        model.addAttribute("transferLeadHours", ServiceOrderService.TRANSFER_LEAD_HOURS);
        return "services";
    }
}
