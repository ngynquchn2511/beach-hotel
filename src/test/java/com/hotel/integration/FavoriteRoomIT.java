package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.FavoriteRoomRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.repository.UserRepository;
import com.hotel.security.CustomUserDetails;
import com.hotel.service.RoomService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Phong yeu thich: bam trai tim o trang phong -> hien trong "Phong yeu thich" -> bam lan nua de bo
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FavoriteRoomIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FavoriteRoomRepository favoriteRoomRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RoomService roomService;
    @Autowired private EntityManager entityManager;

    private Room room;
    private CustomUserDetails guest;

    @BeforeEach
    void setUp() {
        RoomType rt = roomTypeRepository.save(RoomType.builder().name("Phong FavoriteIT").basePrice(BigDecimal.valueOf(700000)).maxGuests(2).build());
        room = roomRepository.save(Room.builder().roomNumber("FV-" + System.nanoTime() % 100000).roomType(rt)
                .price(BigDecimal.valueOf(700000)).status(RoomStatus.AVAILABLE).build());
        User u = userRepository.save(User.builder().fullName("Khach Yeu Thich").email("yeuthich@mail.com").phoneNumber("0977000111")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).customerType(CustomerType.NEW).build());
        guest = new CustomUserDetails(u);
    }

    @Test
    void toggleFromRoomPage_showsInFavoritesList_thenRemove() throws Exception {
        String back = "/customer/rooms/" + room.getId() + "?checkIn=2030-01-01&checkOut=2030-01-03";

        mockMvc.perform(get("/customer/rooms/{id}", room.getId()).with(user(guest)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/customer/favorites/" + room.getId() + "/toggle")))
                .andExpect(content().string(containsString("Thêm vào yêu thích")));

        mockMvc.perform(post("/customer/favorites/{id}/toggle", room.getId()).with(csrf()).with(user(guest)).param("back", back))
                .andExpect(redirectedUrl(back))
                .andExpect(flash().attribute("favoriteMessage", "Đã thêm phòng vào danh sách yêu thích."));
        assertThat(favoriteRoomRepository.findByUserIdAndRoomId(guest.getUser().getId(), room.getId())).isPresent();

        // Trang phong + danh sach phong hien trai tim da to
        mockMvc.perform(get("/customer/rooms/{id}", room.getId()).with(user(guest)))
                .andExpect(content().string(containsString("Bỏ khỏi yêu thích")));
        mockMvc.perform(get("/rooms").with(user(guest)))
                .andExpect(content().string(containsString("is-favorite")));

        mockMvc.perform(get("/customer/favorites").with(user(guest)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phòng " + room.getRoomNumber())))
                .andExpect(content().string(containsString("Phong FavoriteIT")));

        mockMvc.perform(post("/customer/favorites/{id}/toggle", room.getId()).with(csrf()).with(user(guest)))
                .andExpect(redirectedUrl("/customer/favorites"))
                .andExpect(flash().attribute("favoriteMessage", "Đã bỏ phòng khỏi danh sách yêu thích."));
        assertThat(favoriteRoomRepository.findByUserIdAndRoomId(guest.getUser().getId(), room.getId())).isEmpty();

        mockMvc.perform(get("/customer/favorites").with(user(guest)))
                .andExpect(content().string(containsString("Bạn chưa lưu phòng nào")))
                .andExpect(content().string(not(containsString("Phong FavoriteIT"))));
    }

    @Test
    void externalBackUrl_isIgnored() throws Exception {
        for (String evil : new String[]{"https://evil.example", "//evil.example", "/\\evil.example"}) {
            mockMvc.perform(post("/customer/favorites/{id}/toggle", room.getId()).with(csrf()).with(user(guest)).param("back", evil))
                    .andExpect(redirectedUrl("/customer/favorites"));
        }
    }

    @Test
    void anonymous_seesLoginHeart_andCannotToggle() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(content().string(containsString("Đăng nhập để lưu phòng yêu thích")))
                .andExpect(content().string(not(containsString("/toggle"))));
        mockMvc.perform(post("/customer/favorites/{id}/toggle", room.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/customer/favorites"))
                .andExpect(redirectedUrlPattern("**/login"));
        assertThat(favoriteRoomRepository.findAll()).isEmpty();
    }

    @Test
    void unknownRoom_showsError() throws Exception {
        mockMvc.perform(post("/customer/favorites/{id}/toggle", 987654L).with(csrf()).with(user(guest)))
                .andExpect(redirectedUrl("/customer/favorites"))
                .andExpect(flash().attribute("errorMessage", "Không tìm thấy phòng."));
    }

    @Test
    void deletingRoom_removesFavorites() throws Exception {
        mockMvc.perform(post("/customer/favorites/{id}/toggle", room.getId()).with(csrf()).with(user(guest)));
        entityManager.flush();
        entityManager.clear();

        roomService.delete(room.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(favoriteRoomRepository.findAll()).isEmpty();
    }
}
