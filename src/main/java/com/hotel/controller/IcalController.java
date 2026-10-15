package com.hotel.controller;

import com.hotel.entity.Room;
import com.hotel.repository.RoomRepository;
import com.hotel.service.IcalService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;

// Link lich .ics cho Google Calendar / Booking.com (khong dang nhap, kiem tra ma bi mat ?key=).
// Trang lay link + doi ma cho admin o AdminIcalController
@Controller
public class IcalController {

    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final IcalService icalService;
    private final RoomRepository roomRepository;

    public IcalController(IcalService icalService, RoomRepository roomRepository) {
        this.icalService = icalService;
        this.roomRepository = roomRepository;
    }

    @GetMapping("/ical/rooms/{roomId}.ics")
    public ResponseEntity<String> roomFeed(@PathVariable Long roomId, @RequestParam(required = false) String key) {
        Room room = icalService.isValid(key) ? roomRepository.findById(roomId).orElse(null) : null;
        if (room == null) {
            // Sai ma / khong co phong: khong cho biet ly do
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ics(icalService.calendar(roomId, "Sóng Xanh - Phòng " + room.getRoomNumber()), "phong-" + room.getRoomNumber());
    }

    @GetMapping("/ical/all.ics")
    public ResponseEntity<String> allFeed(@RequestParam(required = false) String key) {
        if (!icalService.isValid(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ics(icalService.calendar(null, "Sóng Xanh - Tất cả phòng"), "tat-ca-phong");
    }

    private static ResponseEntity<String> ics(String body, String fileName) {
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"songxanh-" + fileName + ".ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(body);
    }
}
