package com.hotel.integration;

import com.hotel.entity.Room;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// DB trong + app.seed-sample-rooms=true: SampleRoomSeeder (10 phong) + MoreRoomsSeeder (50 phong) = 60 phong, 10 hang phong
@SpringBootTest(properties = {"app.seed-sample-rooms=true",
        "spring.datasource.url=jdbc:h2:mem:more_rooms_seed;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@Transactional
class MoreRoomsSeederIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;

    @Test
    void freshDb_has60RoomsAndNewRoomPagesRender() throws Exception {
        assertThat(roomRepository.count()).isEqualTo(60);
        assertThat(roomTypeRepository.count()).isEqualTo(10);

        Room bungalow = roomRepository.findAll().stream()
                .filter(r -> r.getRoomNumber().equals("B01")).findFirst().orElseThrow();
        mockMvc.perform(get("/customer/rooms/{id}", bungalow.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bungalow sát biển")))
                .andExpect(content().string(containsString("/page-images/rooms/bungalow-1.jpg")))
                .andExpect(content().string(containsString("Phòng tương tự")));
        mockMvc.perform(get("/customer/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Penthouse toàn cảnh vịnh")));
        mockMvc.perform(get("/page-images/rooms/penthouse-1.jpg"))
                .andExpect(status().isOk());
    }

    // 60 phong -> danh sach phong phai chia trang, loc gia / sap xep tinh tren toan bo roi moi cat trang
    @Test
    void publicRoomList_isPaginatedAndPriceFilterAppliesToAllRooms() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rooms", hasSize(9)))
                .andExpect(content().string(containsString("Hiển thị 1–9 trên tổng 60")))
                .andExpect(content().string(containsString("href=\"/rooms?page=7\"")))
                // Nut "trang truoc" o trang 1 khong co link (khong tro toi page=0)
                .andExpect(content().string(not(containsString("page=0"))));
        mockMvc.perform(get("/rooms").param("page", "7"))
                .andExpect(model().attribute("rooms", hasSize(6)))
                .andExpect(content().string(containsString("Hiển thị 55–60 trên tổng 60")))
                .andExpect(content().string(not(containsString("page=8"))));

        // Tren 3 trieu: 2 bungalow + penthouse, nam o trang khac nhau neu loc o trinh duyet
        var result = mockMvc.perform(get("/rooms").param("price", "r4"))
                .andExpect(content().string(not(containsString("Hiển thị"))))
                .andReturn();
        @SuppressWarnings("unchecked")
        List<Room> rooms = (List<Room>) result.getModelAndView().getModel().get("rooms");
        assertThat(rooms).extracting(Room::getRoomNumber).containsExactly("601", "B01", "B02");
    }

    @Test
    void customerSearch_sortsAllResultsThenPagesKeepingSearchParams() throws Exception {
        String in = LocalDate.now().plusDays(30).toString();
        String out = LocalDate.now().plusDays(32).toString();
        var result = mockMvc.perform(get("/customer/rooms").param("checkIn", in).param("checkOut", out)
                        .param("sort", "price-desc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rooms", hasSize(10)))
                .andExpect(content().string(containsString("checkIn=" + in + "&amp;checkOut=" + out + "&amp;sort=price-desc&amp;page=2")))
                .andReturn();
        @SuppressWarnings("unchecked")
        List<Room> rooms = (List<Room>) result.getModelAndView().getModel().get("rooms");
        assertThat(rooms.get(0).getRoomNumber()).isEqualTo("601");

        mockMvc.perform(get("/customer/rooms").param("checkIn", in).param("checkOut", out).param("page", "6"))
                .andExpect(model().attribute("rooms", hasSize(10)))
                .andExpect(content().string(containsString("Hiển thị 51–60 trên tổng 60")));
    }
}
