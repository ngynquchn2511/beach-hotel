package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// SEO: mo ta + Open Graph tren trang cong khai, robots.txt / sitemap.xml, trang rieng tu khong cho lap chi muc
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeoIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/about", "/rooms", "/services", "/customer/rooms"})
    void publicPages_haveDescriptionAndOpenGraph(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"")))
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"")))
                // Anh Open Graph phai la URL tuyet doi (Zalo / Facebook khong doc duoc duong dan tuong doi)
                .andExpect(content().string(containsString("<meta property=\"og:image\" content=\"http")))
                .andExpect(content().string(containsString("<link rel=\"canonical\"")));
    }

    @Test
    void roomDetail_ogUsesRoomTypeAndCoverImage() throws Exception {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Suite SeoIT").basePrice(BigDecimal.valueOf(2500000)).maxGuests(2).build());
        Room room = Room.builder().roomNumber("SEO-1").roomType(rt).price(BigDecimal.valueOf(2500000)).status(RoomStatus.AVAILABLE).build();
        room.getImages().add(RoomImage.builder().room(room).imageUrl("/page-images/rooms/suite-1.jpg").build());
        room = roomRepository.save(room);

        mockMvc.perform(get("/customer/rooms/{id}", room.getId()))
                .andExpect(content().string(containsString("Suite SeoIT")))
                .andExpect(content().string(containsString("2.500.000 VND")))
                .andExpect(content().string(containsString("/page-images/rooms/suite-1.jpg\"")))
                .andExpect(content().string(containsString("/customer/rooms/" + room.getId() + "\"")))
                // Nut chia se: link phong tuyet doi + chia se Facebook
                .andExpect(content().string(containsString("id=\"shareRoom\"")))
                .andExpect(content().string(containsString("facebook.com/sharer/sharer.php?u=http")));
    }

    @Test
    void robotsAndSitemap() throws Exception {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Phong SeoIT").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build());
        Room open = roomRepository.save(Room.builder().roomNumber("SEO-2").roomType(rt).price(BigDecimal.valueOf(500000)).status(RoomStatus.AVAILABLE).build());
        Room maint = roomRepository.save(Room.builder().roomNumber("SEO-3").roomType(rt).price(BigDecimal.valueOf(500000)).status(RoomStatus.MAINTENANCE).build());

        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Disallow: /admin")))
                .andExpect(content().string(containsString("Disallow: /customer/bookings")))
                .andExpect(content().string(containsString("/sitemap.xml")));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("/services</loc>")))
                .andExpect(content().string(containsString("/customer/rooms/" + open.getId() + "</loc>")))
                .andExpect(content().string(not(containsString("/customer/rooms/" + maint.getId() + "</loc>"))))
                .andExpect(content().string(not(containsString("/admin"))));
    }
}
