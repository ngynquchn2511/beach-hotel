package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TcSuite(level = "IT", module = "Quản trị: vòng đời đơn, phòng, danh mục, khách hàng")
class AdminOperationsParamIT extends ItFixtures {

    private User admin;
    private User staff;
    private User customer;
    private RoomType type;
    private Room room;

    @BeforeEach
    void seed() {
        admin = persistUser(UserRole.ADMIN, CustomerType.NEW);
        staff = persistUser(UserRole.STAFF, CustomerType.NEW);
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        type = persistRoomType("Phòng IT", 300000, 2);
        room = persistRoom(type, 300000, RoomStatus.BOOKED);
    }

    private User actor(String who) {
        return who.equals("ADMIN") ? admin : staff;
    }

    // ===================== Vòng đời đơn qua controller thật =====================

    @TcSteps("Tạo đơn ở trạng thái S trong DB, nhân viên POST /admin/bookings/{id}/<thao tác> (có CSRF), đọc lại đơn và phòng từ DB")
    @ParameterizedTest(name = "{1} khi đơn đang {0} (bởi {2}) ¦ status={0}, POST .../{1} ¦ Trạng thái sau = {3}; luôn redirect về trang chi tiết đơn")
    @CsvSource({
            "PENDING,confirm,STAFF,CONFIRMED", "CONFIRMED,confirm,STAFF,CONFIRMED", "CHECKED_IN,confirm,ADMIN,CHECKED_IN", "CHECKED_OUT,confirm,ADMIN,CHECKED_OUT", "CANCELLED,confirm,STAFF,CANCELLED",
            "PENDING,check-in,STAFF,PENDING", "CONFIRMED,check-in,STAFF,CHECKED_IN", "CHECKED_IN,check-in,ADMIN,CHECKED_IN", "CHECKED_OUT,check-in,ADMIN,CHECKED_OUT", "CANCELLED,check-in,STAFF,CANCELLED",
            "PENDING,check-out,STAFF,PENDING", "CONFIRMED,check-out,ADMIN,CONFIRMED", "CHECKED_IN,check-out,STAFF,CHECKED_OUT", "CHECKED_OUT,check-out,ADMIN,CHECKED_OUT", "CANCELLED,check-out,STAFF,CANCELLED",
            "PENDING,cancel,STAFF,CANCELLED", "CONFIRMED,cancel,ADMIN,CANCELLED", "CHECKED_IN,cancel,STAFF,CHECKED_IN", "CHECKED_OUT,cancel,ADMIN,CHECKED_OUT", "CANCELLED,cancel,STAFF,CANCELLED"})
    void lifecycle(BookingStatus from, String action, String who, BookingStatus to) throws Exception {
        Booking b = persistBooking(customer, room, from, 3, 2);
        declareGuest(b);
        mockMvc.perform(post("/admin/bookings/{id}/" + action, b.getId()).with(csrf()).with(as(actor(who))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/bookings/" + b.getId()));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(to);
        if (action.equals("check-in") && to == BookingStatus.CHECKED_IN && from == BookingStatus.CONFIRMED) {
            assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.OCCUPIED);
        }
        if (action.equals("check-out") && to == BookingStatus.CHECKED_OUT && from == BookingStatus.CHECKED_IN) {
            assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(RoomStatus.NOT_READY);
        }
    }

    @TcSteps("Tạo 1 đơn cho mỗi trạng thái, nhân viên GET /admin/bookings?status=S")
    @ParameterizedTest(name = "Lọc danh sách đơn theo trạng thái {0} ¦ GET /admin/bookings?status={0} ¦ View admin/bookings/list, model chỉ chứa đơn {0}")
    @EnumSource(BookingStatus.class)
    void listFilter(BookingStatus status) throws Exception {
        for (BookingStatus s : BookingStatus.values()) {
            persistBooking(customer, persistRoom(type, 300000, RoomStatus.AVAILABLE), s, 5, 1);
        }
        var mv = mockMvc.perform(get("/admin/bookings").param("status", status.name()).with(as(staff)))
                .andExpect(status().isOk()).andExpect(view().name("admin/bookings/list")).andReturn().getModelAndView();
        @SuppressWarnings("unchecked")
        var list = (java.util.List<Booking>) mv.getModel().get("bookings");
        assertThat(list).isNotEmpty().allMatch(b -> b.getStatus() == status);
    }

    @TcSteps("Tạo đơn của khách IT, nhân viên GET /admin/bookings?q=<từ khóa>")
    @ParameterizedTest(name = "Tìm đơn theo {0} ¦ GET /admin/bookings?q=<{0} của khách> ¦ Kết quả có chứa đơn của khách đó")
    @ValueSource(strings = {"họ tên", "số điện thoại", "email", "email viết hoa"})
    void listSearch(String by) throws Exception {
        Booking b = persistBooking(customer, room, BookingStatus.PENDING, 4, 1);
        String q = switch (by) {
            case "họ tên" -> customer.getFullName();
            case "số điện thoại" -> customer.getPhoneNumber();
            case "email" -> customer.getEmail();
            default -> customer.getEmail().toUpperCase();
        };
        var mv = mockMvc.perform(get("/admin/bookings").param("q", q).with(as(staff))).andExpect(status().isOk()).andReturn().getModelAndView();
        @SuppressWarnings("unchecked")
        var list = (java.util.List<Booking>) mv.getModel().get("bookings");
        assertThat(list).extracting(Booking::getId).contains(b.getId());
    }

    @TcSteps("Tạo đơn có cờ đơn mới, nhân viên GET /admin/bookings/{id}, đọc lại cờ từ DB")
    @ParameterizedTest(name = "Mở chi tiết đơn tắt cờ cảnh báo - {0} ¦ GET /admin/bookings/<id> bởi {0} ¦ newBooking = false sau khi xem")
    @ValueSource(strings = {"STAFF", "ADMIN"})
    void detailClearsNewFlag(String who) throws Exception {
        Booking b = persistBooking(customer, room, BookingStatus.PENDING, 4, 1);
        assertThat(b.isNewBooking()).isTrue();
        mockMvc.perform(get("/admin/bookings/{id}", b.getId()).with(as(actor(who))))
                .andExpect(status().isOk()).andExpect(view().name("admin/bookings/detail"));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().isNewBooking()).isFalse();
    }

    // ===================== Phòng =====================

    @TcSteps("Nhân viên POST /admin/rooms/{id}/status?status=S, đọc lại phòng")
    @ParameterizedTest(name = "Đổi trạng thái phòng sang {0} qua giao diện ¦ POST /admin/rooms/<id>/status, status={0} ¦ Redirect /admin/rooms, DB lưu {0}")
    @EnumSource(RoomStatus.class)
    void roomChangeStatus(RoomStatus s) throws Exception {
        mockMvc.perform(post("/admin/rooms/{id}/status", room.getId()).param("status", s.name()).with(csrf()).with(as(staff)))
                .andExpect(redirectedUrl("/admin/rooms"));
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(s);
    }

    @TcSteps("Đặt phòng về trạng thái S, nhân viên POST /admin/rooms/{id}/mark-ready")
    @ParameterizedTest(name = "Xác nhận sẵn sàng khi phòng đang {0} ¦ POST /admin/rooms/<id>/mark-ready ¦ Trạng thái sau = {1}")
    @CsvSource({"NOT_READY,AVAILABLE", "AVAILABLE,AVAILABLE", "BOOKED,BOOKED", "OCCUPIED,OCCUPIED", "MAINTENANCE,MAINTENANCE"})
    void roomMarkReady(RoomStatus from, RoomStatus to) throws Exception {
        room.setStatus(from);
        roomRepository.save(room);
        mockMvc.perform(post("/admin/rooms/{id}/mark-ready", room.getId()).with(csrf()).with(as(staff)))
                .andExpect(redirectedUrl("/admin/rooms"));
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus()).isEqualTo(to);
    }

    @TcSteps("ADMIN POST /admin/rooms/new với dữ liệu theo bộ test (multipart, có CSRF)")
    @ParameterizedTest(name = "Thêm phòng qua form - {0} ¦ roomNumber=\"{1}\", price={2}, có chọn loại = {3} ¦ Lưu thành công = {4}")
    @CsvSource({"dữ liệu hợp lệ,NEW-501,450000,true,true", "giá bằng 0,NEW-502,0,true,false", "giá âm,NEW-503,-1,true,false",
            "bỏ trống số phòng,'',450000,true,false", "không chọn loại phòng,NEW-504,450000,false,false", "trùng số phòng đã có,DUP,450000,true,false"})
    void roomCreate(String label, String number, String price, boolean withType, boolean ok) throws Exception {
        if (number.equals("DUP")) {
            number = room.getRoomNumber();
        }
        long before = roomRepository.count();
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/admin/rooms/new")
                .param("roomNumber", number).param("price", price).param("description", "Phòng thử nghiệm");
        if (withType) {
            req.param("roomTypeId", type.getId().toString());
        }
        var result = mockMvc.perform(req.with(csrf()).with(as(admin)));
        if (ok) {
            result.andExpect(redirectedUrl("/admin/rooms"));
            assertThat(roomRepository.count()).isEqualTo(before + 1);
        } else {
            result.andExpect(status().isOk()).andExpect(view().name("admin/rooms/form"));
            assertThat(roomRepository.count()).isEqualTo(before);
        }
    }

    @TcSteps("Tạo phòng có/không có đơn đặt phòng, ADMIN POST /admin/rooms/{id}/delete")
    @ParameterizedTest(name = "Xóa phòng {0} ¦ POST /admin/rooms/<id>/delete ¦ Phòng còn trong DB = {1}")
    @CsvSource({"chưa có đơn,false", "đã có đơn PENDING,true", "đã có đơn CHECKED_OUT,true"})
    void roomDelete(String label, boolean kept) throws Exception {
        Room r = persistRoom(type, 300000, RoomStatus.AVAILABLE);
        if (label.contains("PENDING")) {
            persistBooking(customer, r, BookingStatus.PENDING, 3, 1);
        } else if (label.contains("CHECKED_OUT")) {
            persistBooking(customer, r, BookingStatus.CHECKED_OUT, 3, 1);
        }
        mockMvc.perform(post("/admin/rooms/{id}/delete", r.getId()).with(csrf()).with(as(admin))).andExpect(redirectedUrl("/admin/rooms"));
        assertThat(roomRepository.existsById(r.getId())).isEqualTo(kept);
    }

    @TcSteps("Nhân viên GET /admin/rooms/{id}/view với ngày nhận/trả theo bộ dữ liệu")
    @ParameterizedTest(name = "Xem phòng & tính tiền tại quầy - {0} đêm ¦ GET /admin/rooms/<id>/view?checkIn=+3&checkOut=+{1} ¦ totalAmount = {2}")
    @CsvSource({"1,4,300000", "2,5,600000", "5,8,1500000"})
    void roomViewCalculates(int nights, int out, long total) throws Exception {
        var mv = mockMvc.perform(get("/admin/rooms/{id}/view", room.getId())
                        .param("checkIn", LocalDate.now().plusDays(3).toString())
                        .param("checkOut", LocalDate.now().plusDays(out).toString()).with(as(staff)))
                .andExpect(status().isOk()).andReturn().getModelAndView();
        assertThat(new java.math.BigDecimal(mv.getModel().get("totalAmount").toString())).isEqualByComparingTo(java.math.BigDecimal.valueOf(total));
    }

    // ===================== Loại phòng =====================

    @TcSteps("ADMIN POST /admin/room-types/new với dữ liệu theo bộ test")
    @ParameterizedTest(name = "Thêm loại phòng - {0} ¦ name=\"{1}\", basePrice={2}, maxGuests={3} ¦ Lưu thành công = {4}")
    @CsvSource({"hợp lệ,Phòng gia đình,900000,4,true", "tên trống,'',900000,4,false", "giá 0,Phòng A,0,2,false",
            "sức chứa 0,Phòng B,500000,0,false", "giá không phải số,Phòng C,abc,2,false"})
    void roomTypeCreate(String label, String name, String price, String guests, boolean ok) throws Exception {
        long before = roomTypeRepository.count();
        var result = mockMvc.perform(post("/admin/room-types/new").param("name", name).param("basePrice", price)
                .param("maxGuests", guests).with(csrf()).with(as(admin)));
        if (ok) {
            result.andExpect(redirectedUrl("/admin/room-types"));
            assertThat(roomTypeRepository.count()).isEqualTo(before + 1);
        } else {
            result.andExpect(view().name("admin/room-types/form"));
            assertThat(roomTypeRepository.count()).isEqualTo(before);
        }
    }

    @TcSteps("Tạo loại phòng có/không có phòng, ADMIN POST /admin/room-types/{id}/delete")
    @ParameterizedTest(name = "Xóa loại phòng {0} ¦ POST /admin/room-types/<id>/delete ¦ Loại phòng còn trong DB = {1}")
    @CsvSource({"chưa có phòng nào,false", "đang có phòng,true"})
    void roomTypeDelete(String label, boolean kept) throws Exception {
        RoomType t = persistRoomType("Loại xóa " + seq(), 100000, 1);
        if (kept) {
            persistRoom(t, 100000, RoomStatus.AVAILABLE);
        }
        mockMvc.perform(post("/admin/room-types/{id}/delete", t.getId()).with(csrf()).with(as(admin))).andExpect(redirectedUrl("/admin/room-types"));
        assertThat(roomTypeRepository.existsById(t.getId())).isEqualTo(kept);
    }

    @TcSteps("ADMIN POST /admin/room-types/{id}/edit đổi giá cơ bản")
    @ParameterizedTest(name = "Sửa loại phòng - giá mới {0} ¦ POST .../edit basePrice={0} ¦ Cập nhật = {1}")
    @CsvSource({"350000,true", "1,true", "0,false"})
    void roomTypeEdit(String price, boolean ok) throws Exception {
        mockMvc.perform(post("/admin/room-types/{id}/edit", type.getId()).param("name", type.getName()).param("basePrice", price)
                .param("maxGuests", "2").with(csrf()).with(as(admin)));
        boolean updated = roomTypeRepository.findById(type.getId()).orElseThrow().getBasePrice().compareTo(new java.math.BigDecimal(price)) == 0;
        assertThat(updated).isEqualTo(ok);
    }

    @Test
    @TcSteps("Nhân viên POST /admin/room-types/{id}/edit gửi giá mới kèm mô tả mới")
    void staffRoomTypeEdit_keepsPriceButUpdatesInfo() throws Exception {
        java.math.BigDecimal oldPrice = roomTypeRepository.findById(type.getId()).orElseThrow().getBasePrice();
        mockMvc.perform(post("/admin/room-types/{id}/edit", type.getId()).param("name", type.getName()).param("basePrice", "1")
                .param("maxGuests", "2").param("description", "Mô tả do nhân viên sửa").with(csrf()).with(as(staff)))
                .andExpect(redirectedUrl("/admin/room-types"));
        RoomType saved = roomTypeRepository.findById(type.getId()).orElseThrow();
        assertThat(saved.getBasePrice()).isEqualByComparingTo(oldPrice);
        assertThat(saved.getDescription()).isEqualTo("Mô tả do nhân viên sửa");
    }

    // ===================== Combo =====================

    @TcSteps("ADMIN POST /admin/combos/new (multipart) với dữ liệu theo bộ test")
    @ParameterizedTest(name = "Thêm combo - {0} ¦ name=\"{1}\", price={2}, maxGuests=[{3}] ¦ Lưu thành công = {4}")
    @CsvSource({"hợp lệ,Nướng Than Hoa,799000,2,true", "phòng thường giá 0,Phòng thường,0,'',true", "tên trống,'',100000,2,false",
            "giá âm,Lẩu,-1,2,false", "số người 0,Lẩu,100000,0,false"})
    void comboCreate(String label, String name, String price, String guests, boolean ok) throws Exception {
        long before = comboRepository.count();
        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/admin/combos/new")
                .param("name", name).param("price", price).param("maxGuests", guests).with(csrf()).with(as(admin)));
        if (ok) {
            result.andExpect(redirectedUrl("/admin/combos"));
            assertThat(comboRepository.count()).isEqualTo(before + 1);
        } else {
            result.andExpect(view().name("admin/combos/form"));
            assertThat(comboRepository.count()).isEqualTo(before);
        }
    }

    @TcSteps("Tạo combo active=A, nhân viên POST /admin/combos/{id}/toggle-active, kiểm tra combo có hiện ở trang chi tiết phòng phía khách")
    @ParameterizedTest(name = "Ẩn/Hiện combo đang active={0} ¦ POST /admin/combos/<id>/toggle-active ¦ active = {1}; combo hiển thị cho khách = {1}")
    @CsvSource({"true,false", "false,true"})
    void comboToggleVisibleToCustomer(boolean initial, boolean expected) throws Exception {
        Combo c = persistCombo("Combo thử " + seq(), 199000, initial);
        mockMvc.perform(post("/admin/combos/{id}/toggle-active", c.getId()).with(csrf()).with(as(staff))).andExpect(redirectedUrl("/admin/combos"));
        assertThat(comboRepository.findById(c.getId()).orElseThrow().isActive()).isEqualTo(expected);
        var mv = mockMvc.perform(get("/customer/rooms/{id}", room.getId())).andExpect(status().isOk()).andReturn().getModelAndView();
        @SuppressWarnings("unchecked")
        var combos = (java.util.List<Combo>) mv.getModel().get("combos");
        assertThat(combos.stream().anyMatch(x -> x.getId().equals(c.getId()))).isEqualTo(expected);
    }

    // ===================== Mã giảm giá =====================

    @TcSteps("ADMIN POST /admin/discount-codes/new với dữ liệu theo bộ test")
    @ParameterizedTest(name = "Thêm mã giảm giá - {0} ¦ code=\"{1}\", type={2}, value={3} ¦ Lưu thành công = {4}")
    @CsvSource({"phần trăm hợp lệ,SALE10,PERCENTAGE,10,true", "cố định hợp lệ,GIAM50K,FIXED_AMOUNT,50000,true", "chữ thường được chuẩn hóa,tet2027,PERCENTAGE,15,true",
            "phần trăm vượt 100,SALE150,PERCENTAGE,150,false", "giá trị 0,ZERO,FIXED_AMOUNT,0,false", "mã trống,'',PERCENTAGE,10,false",
            "trùng mã có sẵn (khác hoa thường),dupcode,PERCENTAGE,10,false"})
    void discountCreate(String label, String code, String type, String value, boolean ok) throws Exception {
        persistCode("DUPCODE", DiscountType.PERCENTAGE, 5, null, true);
        long before = discountCodeRepository.count();
        var result = mockMvc.perform(post("/admin/discount-codes/new").param("code", code).param("discountType", type)
                .param("discountValue", value).with(csrf()).with(as(admin)));
        if (ok) {
            result.andExpect(redirectedUrl("/admin/discount-codes"));
            assertThat(discountCodeRepository.count()).isEqualTo(before + 1);
            assertThat(discountCodeRepository.existsByCode(code.toUpperCase())).isTrue();
        } else {
            result.andExpect(view().name("admin/discount-codes/form"));
            assertThat(discountCodeRepository.count()).isEqualTo(before);
        }
    }

    @TcSteps("Tạo mã active=A, ADMIN POST /admin/discount-codes/{id}/toggle-active rồi khách kiểm tra mã qua /customer/bookings/check-discount")
    @ParameterizedTest(name = "Bật/Tắt mã đang active={0} ¦ toggle rồi check-discount ¦ Khách dùng được mã = {1}")
    @CsvSource({"true,false", "false,true"})
    void discountToggleAffectsCustomer(boolean initial, boolean usable) throws Exception {
        DiscountCode dc = persistCode("TGL" + seq(), DiscountType.PERCENTAGE, 10, null, initial);
        mockMvc.perform(post("/admin/discount-codes/{id}/toggle-active", dc.getId()).with(csrf()).with(as(admin)))
                .andExpect(redirectedUrl("/admin/discount-codes"));
        mockMvc.perform(get("/customer/bookings/check-discount").param("code", dc.getCode()).param("roomId", room.getId().toString())
                        .param("checkIn", LocalDate.now().plusDays(2).toString()).param("checkOut", LocalDate.now().plusDays(3).toString()))
                .andExpect(jsonPath("$.valid").value(usable));
    }

    // ===================== Khách hàng =====================

    @TcSteps("Nhân viên POST /admin/customers/{id}/update-type?customerType=T cho tài khoản vai trò R")
    @ParameterizedTest(name = "Đổi loại khách cho tài khoản {0} sang {1} ¦ POST /admin/customers/<id>/update-type ¦ {2}")
    @CsvSource({"CUSTOMER,REGULAR,Cập nhật thành công", "CUSTOMER,VIP,Cập nhật thành công", "CUSTOMER,NEW,Cập nhật thành công",
            "STAFF,VIP,Bị từ chối - trang lỗi thân thiện 404"})
    void customerUpdateType(UserRole role, CustomerType t, String expected) throws Exception {
        User target = role == UserRole.CUSTOMER ? customer : staff;
        var result = mockMvc.perform(post("/admin/customers/{id}/update-type", target.getId()).param("customerType", t.name())
                .with(csrf()).with(as(admin)));
        if (expected.startsWith("Cập nhật")) {
            result.andExpect(redirectedUrl("/admin/customers"));
            assertThat(userRepository.findById(target.getId()).orElseThrow().getCustomerType()).isEqualTo(t);
        } else {
            result.andExpect(status().isNotFound()).andExpect(view().name("error/business-error"));
        }
    }

    // ===================== Đặt phòng tại quầy =====================

    @TcSteps("Nhân viên POST /admin/bookings/walk-in/new với thông tin khách vãng lai, đọc đơn vừa tạo")
    @ParameterizedTest(name = "Đặt phòng tại quầy {0} đêm, combo = {1} ¦ khách vãng lai, {0} đêm ¦ Đơn CONFIRMED, không bật cờ đơn mới, tổng tiền = {2}")
    @CsvSource({"1,false,300000", "2,false,600000", "3,true,1099000"})
    void walkInCreatesConfirmed(int nights, boolean withCombo, long total) throws Exception {
        Room r = persistRoom(type, 300000, RoomStatus.AVAILABLE);
        Combo c = persistCombo("Combo quầy " + seq(), 199000, true);
        var req = post("/admin/bookings/walk-in/new").param("guestName", "Khách Lẻ").param("guestEmail", "le" + seq() + "@gmail.com")
                .param("guestPhone", String.format("08%08d", seq())).param("roomId", r.getId().toString())
                .param("checkIn", LocalDate.now().plusDays(1).toString()).param("checkOut", LocalDate.now().plusDays(1 + nights).toString())
                .param("checkInTime", "14:00").param("checkOutTime", "12:00");
        if (withCombo) {
            req.param("comboId", c.getId().toString());
        }
        mockMvc.perform(req.with(csrf()).with(as(staff))).andExpect(redirectedUrl("/admin/bookings"));
        Booking b = bookingRepository.findAll().stream().filter(x -> x.getRoom().getId().equals(r.getId())).findFirst().orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.isNewBooking()).isFalse();
        assertThat(b.getTotalAmount()).isEqualByComparingTo(java.math.BigDecimal.valueOf(total));
    }

    @TcSteps("Nhân viên POST /admin/bookings/walk-in/new với dữ liệu lỗi")
    @ParameterizedTest(name = "Đặt phòng tại quầy lỗi - {0} ¦ {0} ¦ Ở lại form walk-in kèm errorMessage, không tạo đơn")
    @ValueSource(strings = {"phòng đã có đơn trùng ngày", "ngày nhận ở quá khứ", "ngày trả trước ngày nhận", "email sai định dạng"})
    void walkInErrors(String scenario) throws Exception {
        Room r = persistRoom(type, 300000, RoomStatus.AVAILABLE);
        LocalDate in = LocalDate.now().plusDays(2);
        LocalDate out = in.plusDays(1);
        String email = "dung@gmail.com";
        switch (scenario) {
            case "phòng đã có đơn trùng ngày" -> persistBooking(customer, r, BookingStatus.CONFIRMED, 2, 1);
            case "ngày nhận ở quá khứ" -> in = LocalDate.now().minusDays(1);
            case "ngày trả trước ngày nhận" -> out = in.minusDays(1);
            default -> email = "sai-email";
        }
        long before = bookingRepository.count();
        mockMvc.perform(post("/admin/bookings/walk-in/new").param("guestName", "Khách").param("guestEmail", email)
                        .param("guestPhone", "0811112222").param("roomId", r.getId().toString())
                        .param("checkIn", in.toString()).param("checkOut", out.toString())
                        .param("checkInTime", "14:00").param("checkOutTime", "12:00").with(csrf()).with(as(staff)))
                .andExpect(status().isOk()).andExpect(view().name("admin/bookings/walk-in-form"))
                .andExpect(model().attributeExists("errorMessage"));
        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    // ===================== Dashboard =====================

    @TcSteps("Nhân viên GET /admin/dashboard với tham số ngày theo bộ dữ liệu")
    @ParameterizedTest(name = "Dashboard với khoảng ngày {0} ¦ fromDate={1}, toDate={2} ¦ Trang 200, model có đủ số liệu và 3 bộ dữ liệu biểu đồ; from ≤ to")
    @CsvSource({"mặc định,'',''", "1 tuần,2026-03-01,2026-03-07", "nhập ngược thứ tự (tự đảo),2026-03-31,2026-03-01", "cả năm,2026-01-01,2026-12-31"})
    void dashboard(String label, String from, String to) throws Exception {
        var req = get("/admin/dashboard").with(as(staff));
        if (from != null && !from.isEmpty()) {
            req.param("fromDate", from).param("toDate", to);
        }
        var mv = mockMvc.perform(req).andExpect(status().isOk())
                .andExpect(model().attributeExists("totalRooms", "availableRooms", "occupiedRooms", "notReadyRooms", "maintenanceRooms",
                        "pendingBookings", "bookingStatusLabels", "roomTypeLabels", "trendLabels", "kpi"))
                .andReturn().getModelAndView();
        LocalDate f = (LocalDate) mv.getModel().get("fromDate");
        LocalDate t = (LocalDate) mv.getModel().get("toDate");
        assertThat(f).isBeforeOrEqualTo(t);
    }

    @Test
    @TcSteps("Tạo đơn đã trả phòng hôm nay, nhân viên GET /admin/dashboard/export, đọc file Excel trả về bằng Apache POI")
    void exportRevenueExcel() throws Exception {
        Booking b = persistBooking(customer, room, BookingStatus.CHECKED_OUT, 0, 2);
        LocalDate today = LocalDate.now();
        var res = mockMvc.perform(get("/admin/dashboard/export").param("fromDate", today.minusDays(3).toString())
                        .param("toDate", today.toString()).with(as(staff)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn().getResponse();

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(res.getContentAsByteArray()))) {
            assertThat(wb.getSheet("Tổng quan")).isNotNull();
            var sheet = wb.getSheet("Đơn đặt phòng");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mã đơn");
            boolean found = false;
            for (var row : sheet) {
                if (row.getRowNum() > 0 && ("#" + b.getId()).equals(row.getCell(0).getStringCellValue())) {
                    found = true;
                    assertThat(row.getCell(4).getStringCellValue()).isEqualTo(room.getRoomNumber());
                    assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(b.getTotalAmount().doubleValue());
                }
            }
            assertThat(found).as("file Excel có dòng của đơn vừa tạo").isTrue();
        }
    }

    @Test
    @TcSteps("GET /admin/dashboard/export khi chưa đăng nhập và với vai trò CUSTOMER")
    void exportRevenueExcel_requiresStaffOrAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard/export")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/dashboard/export").with(as(customer))).andExpect(status().isForbidden());
    }
}
