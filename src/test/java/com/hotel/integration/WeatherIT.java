package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.service.ChatbotService;
import com.hotel.service.WeatherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Du bao thoi tiet (Open-Meteo gia): doc JSON, luu tam, loi mang thi an khung; hien o trang chu / chi tiet phong / Hom nay / chatbot
class WeatherIT extends ItFixtures {

    @Autowired private WeatherService weather;
    @Autowired private ChatbotService chatbot;

    static String json(LocalDate start) {
        StringBuilder t = new StringBuilder();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            t.append(i == 0 ? "" : ",").append('"').append(start.plusDays(i)).append('"');
            code.append(i == 0 ? "" : ",").append(i == 0 ? 95 : i % 2 == 0 ? 0 : 61);
        }
        String n16 = "28,".repeat(15) + "28";
        return "{\"daily\":{\"time\":[" + t + "],\"weather_code\":[" + code + "],\"temperature_2m_max\":[" + n16
                + "],\"temperature_2m_min\":[" + "22.4,".repeat(15) + "22.4" + "],\"precipitation_probability_max\":[" + "40,".repeat(15) + "40"
                + "],\"wind_speed_10m_max\":[45" + ",12".repeat(15) + "]}}";
    }

    @AfterEach
    void off() {
        weather.useFetcherForTest(null);
    }

    @Test
    void parsesCaches_andHidesOnError() {
        AtomicInteger calls = new AtomicInteger();
        LocalDate today = LocalDate.now();
        weather.useFetcherForTest(uri -> {
            calls.incrementAndGet();
            assertThat(uri.toString()).contains("latitude=20.95").contains("forecast_days=16");
            return json(today);
        });
        assertThat(weather.next(7)).hasSize(7);
        WeatherService.Day d0 = weather.next(1).get(0);
        assertThat(d0.getKind()).isEqualTo(WeatherService.Kind.STORM);
        assertThat(d0.isRough()).isTrue();
        assertThat(d0.minTemp()).isEqualTo(22);
        assertThat(weather.on(today.plusDays(2)).orElseThrow().getKind()).isEqualTo(WeatherService.Kind.CLEAR);
        assertThat(weather.between(today.plusDays(3), today.plusDays(5))).hasSize(2);
        weather.forecast();
        assertThat(calls.get()).isEqualTo(1);

        weather.useFetcherForTest(uri -> {
            throw new IllegalStateException("mat mang");
        });
        assertThat(weather.forecast()).isEmpty();
    }

    @Test
    void pagesAndChatbotShowForecast() throws Exception {
        LocalDate today = LocalDate.now();
        weather.useFetcherForTest(uri -> json(today));

        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Thời tiết Hạ Long 7 ngày tới")))
                .andExpect(content().string(containsString("Giông")));

        Room room = persistRoom(persistRoomType("Hang Thoi Tiet IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        mockMvc.perform(get("/customer/rooms/" + room.getId()).param("checkIn", today.plusDays(2).toString())
                        .param("checkOut", today.plusDays(4).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thời tiết những ngày bạn ở")));

        mockMvc.perform(get("/admin/today").with(as(persistUser(UserRole.STAFF, null)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hôm nay gió mạnh")));

        assertThat(chatbot.answer("thời tiết mấy hôm tới thế nào")).contains("Dự báo Hạ Long").contains("Giông");
        assertThat(chatbot.reply("nên chọn phòng nào cho gia đình").actions()).anyMatch(a -> a.url().equals("/rooms/suggest"));

        // Tat -> khong co khung
        weather.useFetcherForTest(null);
        mockMvc.perform(get("/")).andExpect(content().string(not(containsString("7 ngày tới"))));
    }
}
