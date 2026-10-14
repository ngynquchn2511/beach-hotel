package com.hotel.integration;

import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.RoomType;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomBrowsingIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;

    private Room room;
    private RoomType maintenanceless;

    @BeforeEach
    void seedRoom() {
        RoomType rt = roomTypeRepository.save(RoomType.builder()
                .name("Phong Doi IT").basePrice(BigDecimal.valueOf(300000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder()
                .roomNumber("IT-" + System.nanoTime() % 100000)
                .roomType(rt).price(BigDecimal.valueOf(300000)).status(RoomStatus.AVAILABLE).build());
    }

    @Test
    void publicRoomList_returnsOkWithRoomsAndRoomTypes() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(view().name("rooms"))
                .andExpect(model().attributeExists("rooms"))
                .andExpect(model().attributeExists("roomTypes"));
    }

    @Test
    void aboutPage_anonymous_rendersHotelInfoAndRoomTypes() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(view().name("about"))
                .andExpect(model().attributeExists("roomTypes", "roomCount", "combos"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sóng Xanh Resort")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Phong Doi IT")));
    }

    @Test
    void customerRoomSearch_noDates_showsFormOnly() throws Exception {
        mockMvc.perform(get("/customer/rooms"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/rooms"))
                .andExpect(model().attribute("searched", false));
    }

    @Test
    void customerRoomSearch_checkInInPast_showsErrorMessageNotCrash() throws Exception {
        LocalDate past = LocalDate.now().minusDays(2);
        mockMvc.perform(get("/customer/rooms")
                        .param("checkIn", past.toString())
                        .param("checkOut", past.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void customerRoomSearch_validRange_returnsAvailableRooms() throws Exception {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(2);
        mockMvc.perform(get("/customer/rooms")
                        .param("checkIn", in.toString())
                        .param("checkOut", out.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("rooms"))
                .andExpect(model().attributeDoesNotExist("errorMessage"));
    }

    @Test
    void roomDetail_noDates_showsRoomWithoutAvailability() throws Exception {
        mockMvc.perform(get("/customer/rooms/{id}", room.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/room-detail"))
                .andExpect(model().attributeExists("room"))
                .andExpect(model().attributeDoesNotExist("available"));
    }

    @Test
    void roomDetail_validDatesAndRoomFree_availableTrueWithCorrectTotal() throws Exception {
        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = LocalDate.now().plusDays(3); // 2 dem
        BigDecimal expectedTotal = BigDecimal.valueOf(300000).multiply(BigDecimal.valueOf(2));

        mockMvc.perform(get("/customer/rooms/{id}", room.getId())
                        .param("checkIn", in.toString())
                        .param("checkOut", out.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("available", true))
                .andExpect(mvcResult -> {
                    BigDecimal actual = (BigDecimal) mvcResult.getModelAndView().getModel().get("totalAmount");
                    org.junit.jupiter.api.Assertions.assertEquals(0, expectedTotal.compareTo(actual),
                            "totalAmount phai bang 600000 ve mat gia tri (bat ke scale cua BigDecimal)");
                });
    }

    // Phat hien khoang trong thuc te: khong co @ControllerAdvice bat BusinessException, nen GET voi id
    // khong ton tai hien dang tra ve loi 500 (Whitelabel) thay vi mot trang/thong bao than thien.
    @Test
    void roomDetail_nonExistingId_doesNotReturnRawServerError() throws Exception {
        mockMvc.perform(get("/customer/rooms/{id}", 999999L))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status < 500,
                            "GET phong khong ton tai khong duoc tra ve loi may chu (500), nhan duoc: " + status);
                });
    }
}
