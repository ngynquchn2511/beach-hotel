package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TcSuite(level = "IT", module = "Khách hàng: tìm phòng, đặt phòng, quản lý đơn, chatbot")
class CustomerFlowParamIT extends ItFixtures {

    private RoomType type;
    private Room room;
    private User customer;

    @BeforeEach
    void seed() {
        type = persistRoomType("Phòng Flow " + seq(), 400000, 2);
        room = persistRoom(type, 400000, RoomStatus.AVAILABLE);
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
    }

    private MockHttpServletRequestBuilder bookingPost(LocalDate in, LocalDate out, String name, String phone, String email) {
        return post("/customer/bookings/new").with(csrf())
                .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", out.toString())
                .param("guests", "2").param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", name).param("guestPhone", phone).param("guestEmail", email);
    }

    @SuppressWarnings("unchecked")
    private List<Room> searchResult(String... params) throws Exception {
        var req = get("/customer/rooms");
        for (int i = 0; i < params.length; i += 2) {
            req.param(params[i], params[i + 1]);
        }
        return (List<Room>) mockMvc.perform(req).andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("rooms");
    }

    // ===================== Tìm phòng =====================

    @TcSteps("Tạo đơn trạng thái S trên phòng trong khoảng ngày tìm kiếm, khách GET /customer/rooms?checkIn&checkOut")
    @ParameterizedTest(name = "Phòng có đơn {0} trùng ngày có hiện trong kết quả tìm kiếm ¦ booking.status={0} trùng ngày ¦ Phòng xuất hiện = {1}")
    @CsvSource({"PENDING,false", "CONFIRMED,false", "CHECKED_IN,false", "CHECKED_OUT,true", "CANCELLED,true"})
    void search_overlapByStatus(BookingStatus status, boolean visible) throws Exception {
        persistBooking(customer, room, status, 10, 3);
        List<Room> rooms = searchResult("checkIn", LocalDate.now().plusDays(11).toString(), "checkOut", LocalDate.now().plusDays(12).toString());
        assertThat(rooms.stream().anyMatch(r -> r.getId().equals(room.getId()))).isEqualTo(visible);
    }

    @TcSteps("Có đơn CONFIRMED từ ngày +10 đến +13, khách tìm phòng với khoảng ngày theo bộ dữ liệu")
    @ParameterizedTest(name = "Kiểm tra trùng lịch theo khoảng ngày ¦ tìm từ +{0} đến +{1} (đơn có sẵn +10 → +13) ¦ Phòng còn trống = {2}")
    @CsvSource({"7,10,true", "8,11,false", "10,13,false", "11,12,false", "12,15,false", "13,15,true", "9,14,false", "14,16,true"})
    void search_intervalOverlap(int in, int out, boolean free) throws Exception {
        persistBooking(customer, room, BookingStatus.CONFIRMED, 10, 3);
        List<Room> rooms = searchResult("checkIn", LocalDate.now().plusDays(in).toString(), "checkOut", LocalDate.now().plusDays(out).toString());
        assertThat(rooms.stream().anyMatch(r -> r.getId().equals(room.getId()))).isEqualTo(free);
    }

    @TcSteps("Đặt phòng ở trạng thái S, khách tìm phòng trống cho ngày mai")
    @ParameterizedTest(name = "Phòng ở trạng thái {0} trong kết quả tìm kiếm ¦ room.status={0} ¦ Xuất hiện = {1}")
    @CsvSource({"AVAILABLE,true", "MAINTENANCE,false"})
    void search_roomStatus(RoomStatus status, boolean visible) throws Exception {
        room.setStatus(status);
        roomRepository.save(room);
        List<Room> rooms = searchResult("checkIn", LocalDate.now().plusDays(1).toString(), "checkOut", LocalDate.now().plusDays(2).toString());
        assertThat(rooms.stream().anyMatch(r -> r.getId().equals(room.getId()))).isEqualTo(visible);
    }

    @TcSteps("Loại phòng có sức chứa 2 người, khách tìm với số khách G")
    @ParameterizedTest(name = "Lọc theo số khách {0} (phòng chứa tối đa 2) ¦ guests={0} ¦ Phòng xuất hiện = {1}")
    @CsvSource({"1,true", "2,true", "3,false", "6,false"})
    void search_capacity(int guests, boolean visible) throws Exception {
        List<Room> rooms = searchResult("checkIn", LocalDate.now().plusDays(1).toString(), "checkOut", LocalDate.now().plusDays(2).toString(),
                "guests", String.valueOf(guests));
        assertThat(rooms.stream().anyMatch(r -> r.getId().equals(room.getId()))).isEqualTo(visible);
    }

    @TcSteps("Khách GET /customer/rooms với ngày không hợp lệ")
    @ParameterizedTest(name = "Tìm phòng với ngày không hợp lệ - {0} ¦ checkIn=+{1}, checkOut=+{2} ¦ Trang 200, hiển thị errorMessage, không có kết quả")
    @CsvSource({"ngày nhận ở quá khứ,-1,2", "ngày trả bằng ngày nhận,3,3", "ngày trả trước ngày nhận,5,2"})
    void search_invalidDates(String label, int in, int out) throws Exception {
        mockMvc.perform(get("/customer/rooms").param("checkIn", LocalDate.now().plusDays(in).toString())
                        .param("checkOut", LocalDate.now().plusDays(out).toString()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage")).andExpect(model().attributeDoesNotExist("rooms"));
    }

    @TcSteps("Tạo phòng thuộc 2 loại phòng, GET /rooms?roomTypeId=<id>")
    @ParameterizedTest(name = "Danh sách phòng công khai lọc theo loại - {0} ¦ GET /rooms {0} ¦ Chỉ chứa phòng đúng loại")
    @ValueSource(strings = {"lọc theo loại phòng Flow", "không lọc"})
    void publicRooms_filterByType(String mode) throws Exception {
        RoomType other = persistRoomType("Loại khác " + seq(), 900000, 4);
        persistRoom(other, 900000, RoomStatus.AVAILABLE);
        var req = get("/rooms");
        if (mode.startsWith("lọc")) {
            req.param("roomTypeId", type.getId().toString());
        }
        @SuppressWarnings("unchecked")
        var rooms = (List<Room>) mockMvc.perform(req).andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("rooms");
        if (mode.startsWith("lọc")) {
            assertThat(rooms).isNotEmpty().allMatch(r -> r.getRoomType().getId().equals(type.getId()));
        } else {
            assertThat(rooms.stream().map(r -> r.getRoomType().getId()).distinct().count()).isGreaterThanOrEqualTo(2);
        }
    }

    @TcSteps("Có đơn CONFIRMED N đêm, khách GET /customer/rooms/{id} (trang chi tiết phòng)")
    @ParameterizedTest(name = "Lịch phòng khóa đúng ngày đã có khách - đơn {0} đêm ¦ đơn từ +5, {0} đêm ¦ unavailableDates có {0} ngày, không gồm ngày trả")
    @ValueSource(ints = {1, 2, 4})
    void roomDetail_unavailableDates(int nights) throws Exception {
        persistBooking(customer, room, BookingStatus.CONFIRMED, 5, nights);
        @SuppressWarnings("unchecked")
        var dates = (List<String>) mockMvc.perform(get("/customer/rooms/{id}", room.getId())).andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("unavailableDates");
        assertThat(dates).hasSize(nights).doesNotContain(LocalDate.now().plusDays(5 + nights).toString());
    }

    @TcSteps("Khách GET /customer/rooms/{id}?checkIn&checkOut&comboId (có/không combo)")
    @ParameterizedTest(name = "Chi tiết phòng tính tiền {0} đêm, combo {1} ¦ 400.000/đêm × {0} + combo {1} ¦ totalAmount = {2}, available = true")
    @CsvSource({"1,0,400000", "3,0,1200000", "2,399000,1199000", "7,299000,3099000"})
    void roomDetail_total(int nights, long combo, long total) throws Exception {
        var req = get("/customer/rooms/{id}", room.getId()).param("checkIn", LocalDate.now().plusDays(2).toString())
                .param("checkOut", LocalDate.now().plusDays(2 + nights).toString());
        if (combo > 0) {
            req.param("comboId", persistCombo("Combo CT " + seq(), combo, true).getId().toString());
        }
        var model = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat(new BigDecimal(model.get("totalAmount").toString())).isEqualByComparingTo(BigDecimal.valueOf(total));
        assertThat(model.get("available")).isEqualTo(true);
    }

    @TcSteps("Truy cập trang chi tiết với id không tồn tại")
    @ParameterizedTest(name = "Mở {0} không tồn tại ¦ GET {1} ¦ HTTP 404 với trang lỗi thân thiện error/business-error")
    @CsvSource({"phòng,/customer/rooms/987654", "phòng (trang quản trị),/admin/rooms/987654/view", "đơn đặt phòng,/customer/bookings/987654"})
    void notFoundFriendly(String what, String path) throws Exception {
        var req = get(path);
        if (path.startsWith("/admin")) {
            req.with(as(persistUser(UserRole.STAFF, CustomerType.NEW)));
        }
        mockMvc.perform(req).andExpect(status().isNotFound()).andExpect(view().name("error/business-error"));
    }

    // ===================== Đặt phòng =====================

    @TcSteps("Khách (chưa đăng nhập hoặc đã đăng nhập) POST /customer/bookings/new với dữ liệu hợp lệ")
    @ParameterizedTest(name = "Đặt phòng thành công - {0} ¦ {0}, ngày +3 → +{1} ¦ Redirect /customer/bookings/<id>, đơn PENDING, tổng tiền = {2}")
    @CsvSource({"khách vãng lai chưa đăng nhập,4,400000", "khách vãng lai chưa đăng nhập,6,1200000", "khách đã đăng nhập,5,800000"})
    void booking_success(String who, int out, long total) throws Exception {
        var req = bookingPost(LocalDate.now().plusDays(3), LocalDate.now().plusDays(out), "Nguyễn Khách", "0912340000", "khach" + seq() + "@gmail.com");
        if (who.contains("đã đăng nhập")) {
            req.with(as(customer));
        }
        mockMvc.perform(req).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/customer/bookings/*"));
        Booking b = bookingRepository.findAll().stream().filter(x -> x.getRoom().getId().equals(room.getId())).findFirst().orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(b.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(total));
        if (who.contains("đã đăng nhập")) {
            assertThat(b.getCustomer().getId()).isEqualTo(customer.getId());
        }
    }

    @TcSteps("Khách vãng lai đặt phòng bằng email đã/chưa có tài khoản")
    @ParameterizedTest(name = "Gắn đơn của khách vãng lai vào tài khoản - email {0} ¦ email {0} ¦ {1}")
    @CsvSource({"chưa có tài khoản,Tự tạo tài khoản CUSTOMER mới theo email", "đã có tài khoản,Dùng lại tài khoản sẵn có, không tạo thêm"})
    void booking_guestAccount(String label, String expected) throws Exception {
        String email = label.startsWith("đã") ? customer.getEmail() : "vanglai" + seq() + "@gmail.com";
        long usersBefore = userRepository.count();
        mockMvc.perform(bookingPost(LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), "Khách", "0912340001", email))
                .andExpect(status().is3xxRedirection());
        assertThat(userRepository.count()).isEqualTo(label.startsWith("đã") ? usersBefore : usersBefore + 1);
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @TcSteps("Khách POST /customer/bookings/new với dữ liệu sai (validation phía controller)")
    @ParameterizedTest(name = "Đặt phòng bị từ chối - {0} ¦ guestName=\"{1}\", guestPhone=\"{2}\", guestEmail=\"{3}\" ¦ Ở lại trang xác nhận, báo lỗi trường, không tạo đơn")
    @CsvSource({"họ tên trống,'',0912345678,a@gmail.com", "SĐT 9 số,Khách,091234567,a@gmail.com", "SĐT có chữ,Khách,09123abcde,a@gmail.com",
            "SĐT không bắt đầu bằng 0,Khách,1912345678,a@gmail.com", "SĐT trống,Khách,'',a@gmail.com", "email sai,Khách,0912345678,khongphaiemail",
            "email trống,Khách,0912345678,''", "email thiếu tên miền,Khách,0912345678,a@"})
    void booking_validation(String label, String name, String phone, String email) throws Exception {
        long before = bookingRepository.count();
        mockMvc.perform(bookingPost(LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), name, phone, email))
                .andExpect(status().isOk()).andExpect(view().name("customer/booking-confirm"))
                .andExpect(model().hasErrors());
        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    @TcSteps("Khách POST /customer/bookings/new vi phạm quy tắc nghiệp vụ")
    @ParameterizedTest(name = "Đặt phòng vi phạm nghiệp vụ - {0} ¦ {0} ¦ Ở lại trang xác nhận kèm errorMessage, không tạo đơn")
    @ValueSource(strings = {"trùng lịch với đơn PENDING", "trùng lịch với đơn CONFIRMED", "trùng lịch với đơn CHECKED_IN", "phòng đang bảo trì",
            "ngày nhận ở quá khứ", "ngày trả bằng ngày nhận", "mã giảm giá không tồn tại", "mã giảm giá không đúng loại khách"})
    void booking_businessRules(String scenario) throws Exception {
        LocalDate in = LocalDate.now().plusDays(6);
        LocalDate out = in.plusDays(2);
        var req = bookingPost(in, out, "Khách", "0912345678", "kh" + seq() + "@gmail.com");
        switch (scenario) {
            case "trùng lịch với đơn PENDING" -> persistBooking(customer, room, BookingStatus.PENDING, 7, 2);
            case "trùng lịch với đơn CONFIRMED" -> persistBooking(customer, room, BookingStatus.CONFIRMED, 5, 2);
            case "trùng lịch với đơn CHECKED_IN" -> persistBooking(customer, room, BookingStatus.CHECKED_IN, 6, 1);
            case "phòng đang bảo trì" -> {
                room.setStatus(RoomStatus.MAINTENANCE);
                roomRepository.save(room);
            }
            case "ngày nhận ở quá khứ" -> req = bookingPost(LocalDate.now().minusDays(1), out, "Khách", "0912345678", "kh@gmail.com");
            case "ngày trả bằng ngày nhận" -> req = bookingPost(in, in, "Khách", "0912345678", "kh@gmail.com");
            case "mã giảm giá không tồn tại" -> req.param("discountCode", "KHONGTONTAI");
            default -> {
                persistCode("CHIVIP", DiscountType.PERCENTAGE, 20, CustomerType.VIP, true);
                req.param("discountCode", "CHIVIP").with(as(customer));
            }
        }
        long before = bookingRepository.count();
        mockMvc.perform(req).andExpect(status().isOk()).andExpect(view().name("customer/booking-confirm"))
                .andExpect(model().attributeExists("errorMessage"));
        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    @TcSteps("Khách đã đăng nhập loại T đặt phòng 2 đêm (800.000) kèm mã giảm giá")
    @ParameterizedTest(name = "Đặt phòng kèm mã {0} - khách {1} ¦ mã {0} ({2}), khách {1} ¦ Tổng tiền lưu = {3}")
    @CsvSource({"WELCOME10,NEW,10% cho khách mới,720000", "QUEN50K,REGULAR,50.000 cho khách quen,750000",
            "VIP25,VIP,25% cho khách VIP,600000", "TATCA100K,VIP,100.000 cho mọi khách,700000"})
    void booking_withDiscount(String code, CustomerType ct, String desc, long total) throws Exception {
        persistCode("WELCOME10", DiscountType.PERCENTAGE, 10, CustomerType.NEW, true);
        persistCode("QUEN50K", DiscountType.FIXED_AMOUNT, 50000, CustomerType.REGULAR, true);
        persistCode("VIP25", DiscountType.PERCENTAGE, 25, CustomerType.VIP, true);
        persistCode("TATCA100K", DiscountType.FIXED_AMOUNT, 100000, null, true);
        customer.setCustomerType(ct);
        userRepository.save(customer);
        mockMvc.perform(bookingPost(LocalDate.now().plusDays(3), LocalDate.now().plusDays(5), "Khách", "0912345678", customer.getEmail())
                .param("discountCode", code.toLowerCase()).with(as(customer))).andExpect(status().is3xxRedirection());
        Booking b = bookingRepository.findAll().stream().filter(x -> x.getRoom().getId().equals(room.getId())).findFirst().orElseThrow();
        assertThat(b.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(total));
    }

    @TcSteps("Gọi AJAX GET /customer/bookings/check-discount với mã theo bộ dữ liệu (phòng 400.000 × 2 đêm)")
    @ParameterizedTest(name = "Kiểm tra mã giảm giá qua AJAX - {0} ¦ code=\"{1}\", người gọi: {2} ¦ valid = {3}, discountAmount = {4}")
    @CsvSource({"mã % hợp lệ,SALE10,khách chưa đăng nhập (coi là NEW),true,80000", "khách gõ mã chữ thường,sale10,khách chưa đăng nhập (coi là NEW),true,80000",
            "mã cố định,GIAM50K,khách chưa đăng nhập (coi là NEW),true,50000", "mã đã tắt,DATAT,khách chưa đăng nhập (coi là NEW),false,-",
            "mã không tồn tại,ABCXYZ,khách chưa đăng nhập (coi là NEW),false,-", "mã VIP với khách mới,VIPONLY,khách chưa đăng nhập (coi là NEW),false,-",
            "mã VIP với khách VIP,VIPONLY,khách VIP đã đăng nhập,true,160000", "mã cố định lớn hơn tạm tính,GIAM5TR,khách chưa đăng nhập (coi là NEW),true,800000"})
    void checkDiscountAjax(String label, String code, String caller, boolean valid, String amount) throws Exception {
        persistCode("SALE10", DiscountType.PERCENTAGE, 10, null, true);
        persistCode("GIAM50K", DiscountType.FIXED_AMOUNT, 50000, null, true);
        persistCode("DATAT", DiscountType.PERCENTAGE, 10, null, false);
        persistCode("VIPONLY", DiscountType.PERCENTAGE, 20, CustomerType.VIP, true);
        persistCode("GIAM5TR", DiscountType.FIXED_AMOUNT, 5000000, null, true);
        var req = get("/customer/bookings/check-discount").param("code", code).param("roomId", room.getId().toString())
                .param("checkIn", LocalDate.now().plusDays(3).toString()).param("checkOut", LocalDate.now().plusDays(5).toString());
        if (caller.contains("VIP")) {
            customer.setCustomerType(CustomerType.VIP);
            userRepository.save(customer);
            req.with(as(customer));
        }
        String body = mockMvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(valid))
                .andExpect(jsonPath("$.message").isNotEmpty()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if (valid) {
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            assertThat(new BigDecimal(json.get("discountAmount").asText())).isEqualByComparingTo(new BigDecimal(amount));
        }
    }

    @TcSteps("Khách đã đăng nhập GET /customer/bookings/new (trang xác nhận)")
    @ParameterizedTest(name = "Trang xác nhận điền sẵn thông tin - {0} ¦ GET /customer/bookings/new bởi {0} ¦ guestName/guestEmail/guestPhone điền sẵn = {1}, giờ mặc định 14:00/12:00")
    @CsvSource({"khách đã đăng nhập,true", "khách chưa đăng nhập,false"})
    void confirmForm_prefill(String who, boolean prefilled) throws Exception {
        var req = get("/customer/bookings/new").param("roomId", room.getId().toString())
                .param("checkIn", LocalDate.now().plusDays(3).toString()).param("checkOut", LocalDate.now().plusDays(4).toString());
        if (prefilled) {
            req.with(as(customer));
        }
        var model = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        var form = (com.hotel.dto.BookingConfirmRequest) model.get("bookingConfirmRequest");
        assertThat(form.getGuestEmail() != null).isEqualTo(prefilled);
        assertThat(form.getCheckInTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(form.getCheckOutTime()).isEqualTo(LocalTime.of(12, 0));
    }

    // ===================== Quản lý đơn của khách =====================

    private Booking bookingStartingIn(BookingStatus status, long minutes) {
        LocalDateTime start = LocalDateTime.now().plusMinutes(minutes);
        return bookingRepository.save(Booking.builder().customer(customer).room(room)
                .checkInDate(start.toLocalDate()).checkInTime(start.toLocalTime().withNano(0))
                .checkOutDate(start.toLocalDate().plusDays(2)).checkOutTime(LocalTime.of(12, 0)).numberOfGuests(1)
                .guestName("Khách").guestPhone("0912345678").guestEmail(customer.getEmail())
                .totalAmount(BigDecimal.valueOf(800000)).discountAmount(BigDecimal.ZERO).status(status).build());
    }

    @TcSteps("Khách đăng nhập POST /customer/bookings/{id}/cancel với đơn ở trạng thái S")
    @ParameterizedTest(name = "Khách hủy đơn đang {0} ¦ POST .../cancel ¦ Trạng thái sau = {1}")
    @CsvSource({"PENDING,CANCELLED", "CONFIRMED,CANCELLED", "CHECKED_IN,CHECKED_IN", "CHECKED_OUT,CHECKED_OUT", "CANCELLED,CANCELLED"})
    void customerCancel(BookingStatus from, BookingStatus to) throws Exception {
        Booking b = bookingStartingIn(from, 3 * 24 * 60);
        mockMvc.perform(post("/customer/bookings/{id}/cancel", b.getId()).with(csrf()).with(as(customer)))
                .andExpect(redirectedUrl("/customer/bookings/" + b.getId()));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(to);
    }

    @TcSteps("Khách đăng nhập POST /customer/bookings/{id}/edit-time (15:00/11:00), đơn PENDING còn M phút tới giờ nhận")
    @ParameterizedTest(name = "Khách đổi giờ nhận/trả, còn {0} phút ¦ POST .../edit-time ¦ Đổi được = {1}")
    @CsvSource({"120,false", "330,false", "400,true", "2880,true"})
    void customerEditTime(long minutes, boolean ok) throws Exception {
        Booking b = bookingStartingIn(BookingStatus.PENDING, minutes);
        mockMvc.perform(post("/customer/bookings/{id}/edit-time", b.getId()).param("checkInTime", "15:00").param("checkOutTime", "11:00")
                .with(csrf()).with(as(customer))).andExpect(redirectedUrl("/customer/bookings/" + b.getId()));
        Booking after = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(after.getCheckOutTime().equals(LocalTime.of(11, 0))).isEqualTo(ok);
        assertThat(after.isCheckInTimeChanged()).isEqualTo(ok);
    }

    @TcSteps("Khách đăng nhập POST /customer/bookings/{id}/edit-combo, đơn 800.000 còn 3 ngày")
    @ParameterizedTest(name = "Khách đổi combo sang {0} ¦ POST .../edit-combo comboId={0} ¦ Tổng tiền mới = {1}")
    @CsvSource({"không combo,800000", "Lẩu 399.000,1199000", "Nướng 299.000,1099000"})
    void customerEditCombo(String combo, long total) throws Exception {
        Booking b = bookingStartingIn(BookingStatus.CONFIRMED, 3 * 24 * 60);
        var req = post("/customer/bookings/{id}/edit-combo", b.getId()).with(csrf()).with(as(customer));
        if (combo.startsWith("Lẩu")) {
            req.param("comboId", persistCombo("Lẩu", 399000, true).getId().toString());
        } else if (combo.startsWith("Nướng")) {
            req.param("comboId", persistCombo("Nướng", 299000, true).getId().toString());
        }
        mockMvc.perform(req).andExpect(redirectedUrl("/customer/bookings/" + b.getId()));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(total));
    }

    @TcSteps("Tạo đơn của khách A, người dùng B (đã đăng nhập) thao tác trên đơn đó")
    @ParameterizedTest(name = "Khách khác thao tác trên đơn không phải của mình - {0} ¦ {0} bởi khách B ¦ Bị từ chối, đơn không đổi")
    @ValueSource(strings = {"xem chi tiết", "hủy đơn", "đổi giờ"})
    void otherCustomerBlocked(String action) throws Exception {
        Booking b = bookingStartingIn(BookingStatus.PENDING, 3 * 24 * 60);
        User intruder = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        switch (action) {
            case "xem chi tiết" -> mockMvc.perform(get("/customer/bookings/{id}", b.getId()).with(as(intruder))).andExpect(status().isNotFound());
            case "hủy đơn" -> mockMvc.perform(post("/customer/bookings/{id}/cancel", b.getId()).with(csrf()).with(as(intruder)));
            default -> mockMvc.perform(post("/customer/bookings/{id}/edit-time", b.getId()).param("checkInTime", "15:00")
                    .param("checkOutTime", "11:00").with(csrf()).with(as(intruder)));
        }
        Booking after = bookingRepository.findById(b.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(after.getCheckOutTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @TcSteps("Tạo N đơn của khách, GET /customer/bookings?page=P")
    @ParameterizedTest(name = "Lịch sử đặt phòng phân trang - {0} đơn, trang {1} ¦ {0} đơn, page={1} ¦ Hiển thị {2} đơn, totalPages = {3}")
    @CsvSource({"3,1,3,1", "12,1,10,2", "12,2,2,2", "12,5,2,2"})
    void history_pagination(int count, int page, int shown, int pages) throws Exception {
        for (int i = 0; i < count; i++) {
            persistBooking(customer, persistRoom(type, 400000, RoomStatus.AVAILABLE), BookingStatus.PENDING, 3, 1);
        }
        var model = mockMvc.perform(get("/customer/bookings").param("page", String.valueOf(page)).with(as(customer)))
                .andExpect(status().isOk()).andExpect(view().name("customer/booking-history")).andReturn().getModelAndView().getModel();
        assertThat((List<?>) model.get("bookings")).hasSize(shown);
        assertThat(model.get("totalPages")).isEqualTo(pages);
    }

    @TcSteps("Khách đã đăng nhập xem chi tiết đơn ở trạng thái S (còn 3 ngày tới giờ nhận)")
    @ParameterizedTest(name = "Trang chi tiết đơn {0} - quyền sửa ¦ GET /customer/bookings/<id> ¦ canModify = {1}")
    @EnumSource(BookingStatus.class)
    void detail_canModify(BookingStatus status) throws Exception {
        Booking b = bookingStartingIn(status, 3 * 24 * 60);
        boolean expected = status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).with(as(customer)))
                .andExpect(status().isOk()).andExpect(model().attribute("canModify", expected));
    }

    // ===================== Chatbot API =====================

    @TcSteps("POST /api/chatbot/ask với JSON {\"message\": câu hỏi} (có CSRF, không đăng nhập)")
    @ParameterizedTest(name = "API chatbot trả lời ¦ message=\"{0}\" ¦ HTTP 200, JSON reply chứa \"{1}\"")
    @CsvSource(delimiter = '|', value = {"Resort ở đâu?|Trần Phú", "Số điện thoại liên hệ|0338932368", "Có cần đặt cọc không|đặt cọc",
            "Thanh toán thế nào|tiền mặt", "Mấy giờ nhận phòng|Giờ nhận phòng", "xin chào|Mình là **Sóng**", "abcxyz|Sóng chưa hiểu lắm"})
    void chatbotApi(String message, String expected) throws Exception {
        mockMvc.perform(post("/api/chatbot/ask").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reply", containsString(expected)));
    }
}
