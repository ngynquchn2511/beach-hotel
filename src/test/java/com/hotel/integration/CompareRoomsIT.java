package com.hotel.integration;

import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.RoomType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// So sanh phong: chon toi da 3 phong, giu thu tu, to xanh o tot nhat
class CompareRoomsIT extends ItFixtures {

    @Test
    void compare_keepsOrder_dropsDuplicatesUnknownAndExtra() throws Exception {
        RoomType small = persistRoomType("Hang Nho IT", 700_000, 2);
        small.setArea(BigDecimal.valueOf(25));
        RoomType big = persistRoomType("Hang Lon IT", 2_000_000, 4);
        big.setArea(BigDecimal.valueOf(60));
        roomTypeRepository.saveAll(List.of(small, big));
        Room a = persistRoom(small, 700_000, RoomStatus.AVAILABLE);
        Room b = persistRoom(big, 2_000_000, RoomStatus.AVAILABLE);
        Room c = persistRoom(small, 750_000, RoomStatus.AVAILABLE);
        Room d = persistRoom(big, 2_100_000, RoomStatus.AVAILABLE);

        MvcResult result = mockMvc.perform(get("/rooms/compare")
                        .param("ids", b.getId() + ",x," + a.getId() + "," + b.getId() + ",999999," + c.getId() + "," + d.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ô xanh: tốt nhất")))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Room> rooms = (List<Room>) result.getModelAndView().getModel().get("rooms");
        // x / trung / 999999 khong ton tai -> bo; da du 3 phong nen d bi bo
        assertThat(rooms).extracting(Room::getId).containsExactly(b.getId(), a.getId(), c.getId());
        assertThat(result.getModelAndView().getModel().get("maxGuests")).isEqualTo(4);
        assertThat((BigDecimal) result.getModelAndView().getModel().get("minPrice")).isEqualByComparingTo("700000");
    }

    @Test
    void compare_lessThanTwoRooms_showsHint() throws Exception {
        Room a = persistRoom(persistRoomType("Hang Mot IT", 700_000, 2), 700_000, RoomStatus.AVAILABLE);

        mockMvc.perform(get("/rooms/compare").param("ids", a.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hãy chọn ít nhất 2 phòng")));
        mockMvc.perform(get("/rooms/compare"))
                .andExpect(status().isOk());
    }

    @Test
    void roomListPages_haveCompareButtonsAndBar() throws Exception {
        Room a = persistRoom(persistRoomType("Hang Nut IT", 700_000, 2), 700_000, RoomStatus.AVAILABLE);

        mockMvc.perform(get("/rooms"))
                .andExpect(content().string(containsString("data-room-id=\"" + a.getId() + "\"")))
                .andExpect(content().string(containsString("id=\"compareBar\"")));
    }
}
