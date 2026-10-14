package com.hotel.controller;

import com.hotel.entity.Airport;
import com.hotel.entity.ServiceStatus;
import com.hotel.entity.TransferDirection;
import com.hotel.entity.VehicleType;
import com.hotel.exception.BusinessException;
import com.hotel.service.ServiceOrderService;
import com.hotel.service.TransferRateService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

// Le tan quan ly dich vu them: danh sach tour lan / xe dua don sap toi, xac nhan, huy, dat ho khach tai quay.
// Bang gia dua don (/admin/transfer-rates) chi ADMIN sua (SecurityConfig)
@Controller
public class AdminServiceOrderController {

    private final ServiceOrderService serviceOrderService;
    private final TransferRateService transferRateService;

    public AdminServiceOrderController(ServiceOrderService serviceOrderService, TransferRateService transferRateService) {
        this.serviceOrderService = serviceOrderService;
        this.transferRateService = transferRateService;
    }

    @GetMapping("/admin/service-orders")
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) ServiceStatus status, Model model) {
        LocalDate fromDate = from != null ? from : LocalDate.now();
        model.addAttribute("from", fromDate);
        model.addAttribute("status", status);
        model.addAttribute("statuses", ServiceStatus.values());
        model.addAttribute("tours", serviceOrderService.toursForStaff(fromDate, status));
        model.addAttribute("transfers", serviceOrderService.transfersForStaff(fromDate, status));
        return "admin/service-orders/list";
    }

    @PostMapping("/admin/service-orders/tours/{orderId}/confirm")
    public String confirmTour(@PathVariable Long orderId, @RequestParam(required = false) String back,
                              RedirectAttributes redirectAttributes) {
        return run(() -> serviceOrderService.confirmTour(orderId), "Đã xác nhận tour, tiền tour đã cộng vào đơn phòng",
                back, redirectAttributes);
    }

    @PostMapping("/admin/service-orders/tours/{orderId}/cancel")
    public String cancelTour(@PathVariable Long orderId, @RequestParam(required = false) String back,
                             RedirectAttributes redirectAttributes) {
        return run(() -> serviceOrderService.cancelTourByStaff(orderId), "Đã hủy tour", back, redirectAttributes);
    }

    @PostMapping("/admin/service-orders/transfers/{orderId}/confirm")
    public String confirmTransfer(@PathVariable Long orderId, @RequestParam(required = false) String back,
                                  RedirectAttributes redirectAttributes) {
        return run(() -> serviceOrderService.confirmTransfer(orderId),
                "Đã xác nhận xe đưa đón, tiền xe đã cộng vào đơn phòng", back, redirectAttributes);
    }

    @PostMapping("/admin/service-orders/transfers/{orderId}/cancel")
    public String cancelTransfer(@PathVariable Long orderId, @RequestParam(required = false) String back,
                                 RedirectAttributes redirectAttributes) {
        return run(() -> serviceOrderService.cancelTransferByStaff(orderId), "Đã hủy xe đưa đón", back, redirectAttributes);
    }

    // Le tan dat ho khach (VD khach goi dien / den quay) - xac nhan luon
    @PostMapping("/admin/bookings/{id}/tours")
    public String bookTourForGuest(@PathVariable Long id, @RequestParam(required = false) Long tourId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tourDate,
                                   @RequestParam(required = false) Integer participants,
                                   @RequestParam(required = false) String note,
                                   RedirectAttributes redirectAttributes) {
        return run(() -> serviceOrderService.bookTour(serviceOrderService.bookingForStaff(id), tourId, tourDate,
                        participants, note, true),
                "Đã đặt tour cho khách", "/admin/bookings/" + id, redirectAttributes);
    }

    @PostMapping("/admin/bookings/{id}/transfers")
    public String bookTransferForGuest(@PathVariable Long id,
                                       @RequestParam(required = false) TransferDirection direction,
                                       @RequestParam(required = false) Airport airport,
                                       @RequestParam(required = false) VehicleType vehicleType,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time,
                                       @RequestParam(required = false) String flightNumber,
                                       @RequestParam(required = false) Integer passengers,
                                       @RequestParam(required = false) String note,
                                       RedirectAttributes redirectAttributes) {
        return run(() -> serviceOrderService.bookTransfer(serviceOrderService.bookingForStaff(id), direction, airport,
                        vehicleType, date, time, flightNumber, passengers, note, true),
                "Đã đặt xe đưa đón cho khách", "/admin/bookings/" + id, redirectAttributes);
    }

    @GetMapping("/admin/transfer-rates")
    public String rates(Model model) {
        model.addAttribute("airports", Airport.values());
        model.addAttribute("vehicles", VehicleType.values());
        model.addAttribute("rates", transferRateService.priceTable());
        return "admin/transfer-rates/index";
    }

    @PostMapping("/admin/transfer-rates")
    public String updateRates(@RequestParam Map<String, String> form, RedirectAttributes redirectAttributes) {
        try {
            transferRateService.updatePrices(form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã lưu bảng giá đưa đón");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/transfer-rates";
    }

    private String run(Runnable action, String success, String back, RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        // Chi cho quay lai trang noi bo /admin/... (tranh chuyen huong ra trang ngoai)
        boolean safeBack = back != null && back.startsWith("/admin/") && !back.startsWith("//") && !back.contains("\\");
        return "redirect:" + (safeBack ? back : "/admin/service-orders");
    }
}
