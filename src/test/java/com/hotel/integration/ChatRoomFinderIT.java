package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.repository.UserRepository;
import com.hotel.service.ChatbotService;
import com.hotel.util.Texts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

// Song tim phong theo nhu cau: "toi muon dat 1 phong ngay 25 - 27 view bien" -> chi hien phong lien quan (the phong),
// bam vao mo trang phong voi ngay / so khach dien san; phong kin thi goi y ngay gan nhat / phong khac
@SpringBootTest
@Transactional
class ChatRoomFinderIT {

    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("d/M");

    @Autowired private ChatbotService chatbot;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Room sea;
    private Room garden;
    private RoomType seaType;

    @BeforeEach
    void seed() {
        seaType = roomTypeRepository.save(RoomType.builder().name("Phòng Zeta view biển").basePrice(BigDecimal.valueOf(1_100_000))
                .maxGuests(2).amenities("Ban công hướng biển, bồn tắm").build());
        RoomType gardenType = roomTypeRepository.save(RoomType.builder().name("Phòng Zeta hướng vườn").basePrice(BigDecimal.valueOf(850_000))
                .maxGuests(2).amenities("Cửa sổ hướng vườn, vòi sen").build());
        sea = roomRepository.save(Room.builder().roomNumber("ZS1").roomType(seaType).price(BigDecimal.valueOf(1_100_000))
                .status(RoomStatus.AVAILABLE).build());
        garden = roomRepository.save(Room.builder().roomNumber("ZG1").roomType(gardenType).price(BigDecimal.valueOf(850_000))
                .status(RoomStatus.AVAILABLE).build());
    }

    private ChatbotService.Reply ask(String q) {
        return chatbot.reply(q, Texts.VIETNAMESE);
    }

    @Test
    void gardenView_showsOnlyGardenRooms_notPriceList() {
        ChatbotService.Reply r = ask("toi muon co view vuon");
        assertThat(r.topics()).contains("FIND_ROOM");
        assertThat(r.cards()).isNotEmpty().allMatch(c -> c.badges().contains("hướng vườn"));
        assertThat(r.cards()).anyMatch(c -> c.roomId().equals(garden.getId()));
        assertThat(r.cards()).noneMatch(c -> c.roomId().equals(sea.getId()));
        assertThat(r.text()).doesNotContain("Giá phòng ở Sóng Xanh");
        // Chua co ngay -> hoi ngay va so nguoi
        assertThat(r.text()).contains("ngày nào");
    }

    @Test
    void datesAndSeaView_showFreeSeaRoomWithPrefilledLink() {
        LocalDate in = LocalDate.now().plusDays(12);
        LocalDate out = in.plusDays(2);
        ChatbotService.Reply r = ask("tôi muốn đặt 1 phòng ngày " + in.format(DM) + " - " + out.format(DM) + " view biển cho 2 người");
        assertThat(r.cards()).isNotEmpty().allMatch(c -> c.badges().contains("view biển"));
        ChatbotService.RoomCard card = r.cards().stream().filter(c -> c.roomId().equals(sea.getId())).findFirst().orElseThrow();
        assertThat(card.url()).isEqualTo("/customer/rooms/" + sea.getId() + "?checkIn=" + in + "&checkOut=" + out + "&guests=2");
        assertThat(card.detail()).contains("Tổng 2 đêm");
        assertThat(r.text()).contains("2 đêm").contains("2 khách").contains("view biển");
    }

    @Test
    void bookedSeaRoom_suggestsNearestFreeDates() {
        // Chi giu lai loai phong view bien cua test de kiem tra goi y ngay khac
        LocalDate in = LocalDate.now().plusDays(20);
        User u = userRepository.save(User.builder().fullName("Khach Kin Phong").email("kin.phong.zeta@mail.com").phoneNumber("0966000111")
                .password(passwordEncoder.encode("x")).role(UserRole.CUSTOMER).build());
        bookingRepository.save(Booking.builder().customer(u).room(sea).checkInDate(in).checkOutDate(in.plusDays(2))
                .checkInTime(LocalTime.of(14, 0)).checkOutTime(LocalTime.of(12, 0)).guestName("Khach").guestPhone("0966000111")
                .guestEmail("kin.phong.zeta@mail.com").totalAmount(BigDecimal.valueOf(2_200_000)).discountAmount(BigDecimal.ZERO)
                .status(BookingStatus.CONFIRMED).build());
        ChatbotService.Reply r = ask("phòng Zeta ngày " + in.format(DM) + " - " + in.plusDays(2).format(DM) + " view biển");
        assertThat(r.cards()).noneMatch(c -> c.roomId().equals(sea.getId()) && c.url().contains("checkIn=" + in + "&"));
        // Co the con phong view bien khac trong DB test; neu khong thi goi y ngay gan nhat cua phong Zeta
        if (r.cards().stream().noneMatch(c -> c.badges().contains("view biển") && c.url().contains("checkIn=" + in + "&"))) {
            assertThat(r.text()).contains("còn trống từ");
        }
    }

    @Test
    void budgetAndGuests_filter() {
        ChatbotService.Reply r = ask("phòng dưới 900k");
        assertThat(r.cards()).isNotEmpty().allMatch(c -> !c.price().isBlank());
        assertThat(r.cards()).noneMatch(c -> c.roomId().equals(sea.getId()));
        ChatbotService.Reply big = ask("cần phòng cho 30 người");
        assertThat(big.text()).contains("nên đặt 2 phòng");
    }

    @Test
    void plainQuestions_keepOldAnswers() {
        assertThat(ask("Giá phòng bao nhiêu").cards()).isEmpty();
        assertThat(ask("Có cần đặt cọc không").cards()).isEmpty();
        assertThat(ask("Giá phòng bao nhiêu").text()).contains("Giá phòng");
    }
}
