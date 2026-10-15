package com.hotel.integration;

import com.hotel.entity.Room;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.User;
import com.hotel.entity.UserRole;
import com.hotel.util.CurrencyDisplay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Gia quy doi USD cho khach xem tieng Anh; ty gia admin chinh trong trang Gia & dat coc
class UsdPriceIT extends ItFixtures {

    @Autowired private CurrencyDisplay currencyDisplay;

    private Room room;

    @BeforeEach
    void seed() {
        currencyDisplay.evict();
        room = persistRoom(persistRoomType("Hang USD IT", 1_270_000, 2), 1_270_000, RoomStatus.AVAILABLE);
    }

    private String page(String path, String lang) throws Exception {
        return mockMvc.perform(get(path).param("lang", lang)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void englishShowsApproxUsd_vietnameseDoesNot() throws Exception {
        // 1.270.000 / 25.400 = 50
        assertThat(page("/customer/rooms/" + room.getId(), "en")).contains("≈ US$50").contains("you pay in VND");
        assertThat(page("/rooms", "en")).contains("≈ US$");
        assertThat(page("/customer/rooms/" + room.getId(), "vi")).doesNotContain("US$");
    }

    @Test
    void adminChangesRate_andValidatesRange() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/pricing/usd-rate").with(csrf()).with(as(admin)).param("usdRate", "25000"))
                .andExpect(flash().attribute("successMessage", containsString("Đã lưu tỷ giá")));
        // 1.270.000 / 25.000 = 50,8 -> 51
        assertThat(page("/customer/rooms/" + room.getId(), "en")).contains("≈ US$51");

        mockMvc.perform(post("/admin/pricing/usd-rate").with(csrf()).with(as(admin)).param("usdRate", "500"))
                .andExpect(flash().attribute("errorMessage", containsString("10.000 đến 100.000")));
    }

    @Test
    void conversionRounding() {
        assertThat(CurrencyDisplay.toUsd(BigDecimal.valueOf(5_500_000), 25_400)).isEqualTo(217);
        assertThat(CurrencyDisplay.toUsd(BigDecimal.valueOf(12_700), 25_400)).isEqualTo(1);
    }
}
