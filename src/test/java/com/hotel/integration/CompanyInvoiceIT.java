package com.hotel.integration;

import com.hotel.entity.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Hoa don cong ty (VAT): nhap khi dat phong / tren trang don, in len hoa don, ke toan ghi so HDDT + Excel
class CompanyInvoiceIT extends ItFixtures {

    @org.springframework.beans.factory.annotation.Autowired
    private com.hotel.repository.PaymentRepository paymentRepository;

    private User customer;
    private User staff;
    private Room room;

    @BeforeEach
    void seed() {
        customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        staff = persistUser(UserRole.STAFF, null);
        room = persistRoom(persistRoomType("Hang HD IT", 1_000_000, 2), 1_000_000, RoomStatus.AVAILABLE);
    }

    private org.springframework.test.web.servlet.ResultActions book(String taxCode) throws Exception {
        LocalDate in = LocalDate.now().plusDays(20);
        return mockMvc.perform(post("/customer/bookings/new").with(csrf()).with(as(customer))
                .param("roomId", room.getId().toString()).param("checkIn", in.toString()).param("checkOut", in.plusDays(2).toString())
                .param("checkInTime", "14:00").param("checkOutTime", "12:00")
                .param("guestName", customer.getFullName()).param("guestPhone", customer.getPhoneNumber())
                .param("guestEmail", customer.getEmail())
                .param("wantCompanyInvoice", "true").param("companyName", "Cong ty TNHH Bien Xanh")
                .param("taxCode", taxCode).param("companyAddress", "12 Ha Long, Quang Ninh").param("invoiceEmail", "ketoan@bienxanh.vn"));
    }

    @Test
    void bookWithCompanyInfo_shownOnInvoice_thenAccountantMarksIssued() throws Exception {
        long before = bookingRepository.count();
        book("0101 234 567").andExpect(status().is3xxRedirection());
        assertThat(bookingRepository.count()).isEqualTo(before + 1);
        Booking b = bookingRepository.findAll().stream().max(java.util.Comparator.comparing(Booking::getId)).orElseThrow();
        assertThat(b.getInvoiceTaxCode()).isEqualTo("0101234567");
        assertThat(b.getInvoiceEmail()).isEqualTo("ketoan@bienxanh.vn");

        mockMvc.perform(get("/customer/bookings/{id}", b.getId()).with(as(customer)))
                .andExpect(content().string(containsString("Cong ty TNHH Bien Xanh")));

        // Chua tra phong -> chua xuat duoc
        mockMvc.perform(post("/admin/company-invoices/{id}/issued", b.getId()).with(csrf()).with(as(staff)).param("invoiceNumber", "0000123"))
                .andExpect(flash().attribute("errorMessage", containsString("trả phòng")));
        b.setStatus(BookingStatus.CHECKED_OUT);
        bookingRepository.save(b);
        paymentRepository.save(Payment.builder().booking(b).amount(b.getTotalAmount()).paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PAID).paymentDate(java.time.LocalDateTime.now()).build());
        mockMvc.perform(get("/admin/bookings/{id}/invoice", b.getId()).with(as(staff)))
                .andExpect(content().string(containsString("MST: 0101234567")));
        mockMvc.perform(post("/admin/company-invoices/{id}/issued", b.getId()).with(csrf()).with(as(staff)).param("invoiceNumber", "0000123"))
                .andExpect(flash().attribute("successMessage", containsString("#" + b.getId())));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getEInvoiceNumber()).isEqualTo("0000123");

        // Da xuat -> le tan khong sua duoc nua
        mockMvc.perform(post("/admin/bookings/{id}/company-invoice", b.getId()).with(csrf()).with(as(staff))
                        .param("companyName", "Khac").param("taxCode", "0101234567").param("companyAddress", "X"))
                .andExpect(flash().attribute("ciError", containsString("Không sửa được")));

        byte[] xlsx = mockMvc.perform(get("/admin/company-invoices/export").with(as(staff)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getRow(1).getCell(4).getStringCellValue()).isEqualTo("0101234567");
            assertThat(wb.getSheetAt(0).getRow(1).getCell(8).getStringCellValue()).isEqualTo("0000123");
        }
    }

    @Test
    void invalidTaxCode_noBookingCreated() throws Exception {
        long before = bookingRepository.count();
        book("12345").andExpect(model().attribute("errorMessage", containsString("Mã số thuế")));
        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    @Test
    void customerAddsThenRemovesOnBookingPage() throws Exception {
        Booking b = persistBooking(customer, room, BookingStatus.CONFIRMED, 10, 2);
        mockMvc.perform(post("/customer/bookings/{id}/company-invoice", b.getId()).with(csrf()).with(as(customer))
                        .param("companyName", "Chi nhanh A").param("taxCode", "0101234567-001").param("companyAddress", "HN"))
                .andExpect(flash().attribute("ciSuccess", containsString("Đã lưu")));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().getInvoiceTaxCode()).isEqualTo("0101234567-001");

        // Nguoi khac khong sua duoc don nay
        User other = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        mockMvc.perform(post("/customer/bookings/{id}/company-invoice", b.getId()).with(csrf()).with(as(other))
                .param("action", "remove"));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().isCompanyInvoice()).isTrue();

        mockMvc.perform(post("/customer/bookings/{id}/company-invoice", b.getId()).with(csrf()).with(as(customer)).param("action", "remove"));
        assertThat(bookingRepository.findById(b.getId()).orElseThrow().isCompanyInvoice()).isFalse();

        mockMvc.perform(get("/admin/company-invoices").with(as(customer))).andExpect(status().isForbidden());
    }
}
