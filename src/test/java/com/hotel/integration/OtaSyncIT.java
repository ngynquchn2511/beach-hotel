package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.OtaBlockRepository;
import com.hotel.repository.OtaFeedRepository;
import com.hotel.service.BookingService;
import com.hotel.service.IcalService;
import com.hotel.service.OtaSyncService;
import com.hotel.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Dong bo 2 chieu voi Booking.com / Airbnb qua iCal: luot dat tren kenh khoa phong website, kenh huy thi mo lai,
// trung don website thi canh bao, ghi nhan thanh don OTA, xuat lich cho tung kenh khong lap luot dat cua chinh kenh
class OtaSyncIT extends ItFixtures {

    @Autowired private OtaSyncService syncService;
    @Autowired private OtaFeedRepository feedRepository;
    @Autowired private OtaBlockRepository blockRepository;
    @Autowired private BookingService bookingService;
    @Autowired private RoomService roomService;
    @Autowired private IcalService icalService;
    @Autowired private com.hotel.repository.RoomRepository rooms;

    private static final DateTimeFormatter D = DateTimeFormatter.BASIC_ISO_DATE;
    private Room room;
    private OtaFeed booking;
    private OtaFeed airbnb;
    private final LocalDate in = LocalDate.now().plusDays(10);
    private final LocalDate out = in.plusDays(3);

    @BeforeEach
    void seed() {
        room = persistRoom(persistRoomType("Hang OTA IT", 900_000, 2), 900_000, RoomStatus.AVAILABLE);
        booking = feedRepository.save(OtaFeed.builder().room(room).channel(OtaFeed.Channel.BOOKING)
                .url("https://admin.booking.com/ical/x.ics").active(true).build());
        airbnb = feedRepository.save(OtaFeed.builder().room(room).channel(OtaFeed.Channel.AIRBNB)
                .url("https://www.airbnb.com/calendar/ical/1.ics").active(true).build());
    }

    // Dang Booking.com: CRLF, ngay VALUE=DATE, dong bi gap
    private String bookingIcs(LocalDate from, LocalDate to) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Booking.com//EN\r\nBEGIN:VEVENT\r\nDTSTART;VALUE=DATE:" + from.format(D)
                + "\r\nDTEND;VALUE=DATE:" + to.format(D) + "\r\nUID:a1b2c3d4e5f6@booking.com\r\nSUMMARY:CLOSED - Not\r\n  available\r\n"
                + "END:VEVENT\r\nEND:VCALENDAR\r\n";
    }

    private static final String EMPTY = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";

    @Test
    void parse_bookingAndAirbnbFormats() {
        List<OtaSyncService.Event> b = OtaSyncService.parse(bookingIcs(in, out));
        assertThat(b).singleElement().satisfies(e -> {
            assertThat(e.start()).isEqualTo(in);
            assertThat(e.end()).isEqualTo(out);
            assertThat(e.summary()).isEqualTo("CLOSED - Not available");
        });
        // Dang Airbnb: ngay gio UTC, khong co DTEND -> 1 dem
        String air = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTAMP:20261019T010000Z\nDTSTART:" + in.format(D) + "T000000Z\nUID:xyz@airbnb.com\n"
                + "SUMMARY:Reserved\nEND:VEVENT\nEND:VCALENDAR\n";
        assertThat(OtaSyncService.parse(air)).singleElement().satisfies(e -> {
            assertThat(e.end()).isEqualTo(in.plusDays(1));
            assertThat(e.uid()).isEqualTo("xyz@airbnb.com");
        });
    }

    @Test
    void channelBooking_blocksRoomEverywhere_andReleasesWhenCancelled() {
        OtaSyncService.SyncResult r = syncService.syncContent(booking, bookingIcs(in, out));
        assertThat(r.added()).isEqualTo(1);
        assertThat(r.conflicts()).isEmpty();

        assertThat(rooms.findAvailableRooms(in, out, null, null)).extracting(Room::getId).doesNotContain(room.getId());
        assertThat(roomService.isAvailableForDates(room.getId(), in.plusDays(1), in.plusDays(2))).isFalse();
        assertThat(roomService.findUnavailableDates(room.getId())).contains(in, in.plusDays(2)).doesNotContain(out);
        BookingService.Availability a = bookingService.checkAvailability(room, in, out);
        assertThat(a.isAvailable()).isFalse();
        assertThat(a.describe()).contains("Booking.com");
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        assertThatThrownBy(() -> bookingService.createBooking(guest, room.getId(), in.plusDays(1), in.plusDays(4),
                LocalTime.of(14, 0), LocalTime.of(12, 0), 2, null, null, "Khach", "0900000001", guest.getEmail()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Booking.com");

        // Lich phong cua le tan hien thanh "Booking.com"
        try {
            mockMvc.perform(get("/admin/room-calendar").with(as(persistUser(UserRole.STAFF, null))).param("from", in.minusDays(1).toString()))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("bấm để ghi nhận đơn")));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }

        // Kenh xoa luot dat (khach huy tren Booking.com) -> phong mo ban lai
        r = syncService.syncContent(booking, EMPTY);
        assertThat(r.removed()).isEqualTo(1);
        assertThat(roomService.isAvailableForDates(room.getId(), in, out)).isTrue();
    }

    @Test
    void overlapWithWebsiteBooking_isReportedAsConflict() {
        persistBooking(persistUser(UserRole.CUSTOMER, CustomerType.NEW), room, BookingStatus.CONFIRMED, 11, 2);
        OtaSyncService.SyncResult r = syncService.syncContent(airbnb, bookingIcs(in, out).replace("booking.com", "airbnb.com"));
        assertThat(r.conflicts()).singleElement().asString().contains(room.getRoomNumber());
        assertThat(syncService.upcoming()).anySatisfy(row -> assertThat(row.conflict()).isTrue());
    }

    @Test
    void linkToBooking_thenChannelCancels_flagsForStaff_andExportSkipsOwnChannel() throws Exception {
        syncService.syncContent(booking, bookingIcs(in, out));
        syncService.syncContent(airbnb, bookingIcs(out.plusDays(5), out.plusDays(7)).replace("a1b2c3d4e5f6@booking.com", "air-1@airbnb.com"));
        OtaBlock block = blockRepository.findByFeedId(booking.getId()).get(0);
        User staff = persistUser(UserRole.STAFF, null);

        mockMvc.perform(post("/admin/ota/blocks/" + block.getId() + "/link").with(as(staff)).with(csrf())
                        .param("guestName", "Kim Min-jun").param("guestPhone", "0912000111").param("guests", "2")
                        .param("amount", "2700000").param("note", "BK-4821"))
                .andExpect(status().is3xxRedirection());
        OtaBlock linked = blockRepository.findById(block.getId()).orElseThrow();
        Booking b = linked.getBooking();
        assertThat(b).isNotNull();
        assertThat(b.getChannel()).isEqualTo(BookingChannel.OTA);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.getTotalAmount()).isEqualByComparingTo("2700000");
        assertThat(b.getCheckInDate()).isEqualTo(in);
        // Phong van khong ban duoc (don giu phong), dot OTA da lien ket khong khoa them
        assertThat(roomService.isAvailableForDates(room.getId(), in, out)).isFalse();
        assertThat(bookingService.checkAvailability(room, in, out).otaBlocks()).isEmpty();

        // Xuat lich cho Booking.com: khong lap lai luot dat cua chinh Booking.com, co luot dat Airbnb
        String forBooking = icalService.calendar(room.getId(), "x", OtaFeed.Channel.BOOKING);
        assertThat(forBooking).doesNotContain("booking-" + b.getId() + "@").contains("Đã đặt qua Airbnb");
        String forAirbnb = icalService.calendar(room.getId(), "x", OtaFeed.Channel.AIRBNB);
        assertThat(forAirbnb).contains("booking-" + b.getId() + "@").doesNotContain("Đã đặt qua Airbnb");

        // Kenh huy luot dat da ghi nhan -> giu don, danh dau de le tan kiem tra
        syncService.syncContent(booking, EMPTY);
        assertThat(blockRepository.findById(block.getId()).orElseThrow().getRemovedAt()).isNotNull();
        mockMvc.perform(get("/admin/ota").with(as(staff)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Kênh đã hủy")));
    }

    @Test
    void feedManagement_adminOnly_andHttpsRequired() throws Exception {
        User staff = persistUser(UserRole.STAFF, null);
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/ota/feeds").with(as(staff)).with(csrf()).param("roomId", room.getId().toString())
                .param("channel", "OTHER").param("url", "https://example.com/a.ics")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/ota/feeds").with(as(admin)).with(csrf()).param("roomId", room.getId().toString())
                        .param("channel", "OTHER").param("url", "http://example.com/a.ics"))
                .andExpect(flash().attribute("errorMessage", containsString("https://")));
        mockMvc.perform(post("/admin/ota/feeds").with(as(admin)).with(csrf()).param("roomId", room.getId().toString())
                        .param("channel", "BOOKING").param("url", "https://admin.booking.com/b.ics"))
                .andExpect(flash().attribute("errorMessage", containsString("đã có link Booking.com")));
        mockMvc.perform(get("/admin/ota").with(as(staff))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Đồng bộ Booking.com / Airbnb")));
    }
}
