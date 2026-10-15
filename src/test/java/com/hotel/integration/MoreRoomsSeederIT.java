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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
}
