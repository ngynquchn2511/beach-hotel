package com.hotel.controller;

import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.service.RoomService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

// robots.txt + sitemap.xml cho cong cu tim kiem: chi cac trang cong khai; chan trang quan tri, trang don cua khach
// (URL trang don chua ma truy cap rieng) va API
@RestController
public class SeoController {

    private static final List<String> PUBLIC_PAGES = List.of("/", "/about", "/rooms", "/services", "/customer/rooms");

    private final RoomService roomService;
    private final String baseUrl;

    public SeoController(RoomService roomService, @Value("${app.base-url:}") String baseUrl) {
        this.roomService = roomService;
        this.baseUrl = baseUrl;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return String.join("\n",
                "User-agent: *",
                "Disallow: /admin",
                "Disallow: /staff",
                "Disallow: /customer/bookings",
                "Disallow: /customer/account",
                "Disallow: /api/",
                "Disallow: /reset-password",
                "Disallow: /verify-email",
                "Allow: /",
                "",
                "Sitemap: " + SeoModelAdvice.resolve(baseUrl) + "/sitemap.xml",
                "");
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String base = SeoModelAdvice.resolve(baseUrl);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String page : PUBLIC_PAGES) {
            url(xml, base + page, page.equals("/") ? "1.0" : "0.8");
        }
        roomService.findAll().stream()
                .filter(r -> r.getStatus() != RoomStatus.MAINTENANCE)
                .sorted(Comparator.comparing(Room::getRoomNumber))
                .forEach(r -> url(xml, base + "/customer/rooms/" + r.getId(), "0.6"));
        xml.append("</urlset>\n");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml.toString());
    }

    private static void url(StringBuilder xml, String loc, String priority) {
        xml.append("  <url><loc>").append(loc.replace("&", "&amp;")).append("</loc><priority>")
                .append(priority).append("</priority></url>\n");
    }
}
