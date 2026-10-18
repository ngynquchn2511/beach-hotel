package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingGroupRepository;
import com.hotel.repository.CashShiftRepository;
import com.hotel.repository.PaymentRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Bao cao ke toan: so quy thang (trang + Excel) va doi soat VNPay theo ngay voi file sao ke
class AccountingIT extends ItFixtures {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BookingGroupRepository groupRepository;
    @Autowired private CashShiftRepository shiftRepository;

    // Thang cu co dinh de khong lan voi du lieu khac
    private static final LocalDateTime DAY = LocalDateTime.of(2025, 3, 14, 10, 0);

    private User accountant;
    private Booking single;
    private Booking g1;
    private Booking g2;
    private Booking refunded;

    @BeforeEach
    void seed() {
        accountant = persistUser(UserRole.ACCOUNTANT, null);
        User customer = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        RoomType type = persistRoomType("Hang KT IT", 1_000_000, 2);
        // Thanh toan tien mat 1.000.000 khi tra phong
        Booking cash = persistBooking(customer, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CHECKED_OUT, 1, 1);
        paymentRepository.save(Payment.builder().booking(cash).amount(BigDecimal.valueOf(1_000_000)).paymentMethod(PaymentMethod.CASH)
                .status(PaymentStatus.PAID).paymentDate(DAY.plusHours(1)).build());
        // Coc VNPay don le 300.000
        single = deposit(persistBooking(customer, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CONFIRMED, 5, 1),
                300_000, "14000001", DAY);
        // Coc doan: 2 phong chung 1 ma, 200.000 + 250.000
        BookingGroup group = groupRepository.save(BookingGroup.builder().customer(customer).guestName("Doan KT").build());
        g1 = deposit(persistBooking(customer, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CONFIRMED, 6, 1),
                200_000, "14000002", DAY.plusMinutes(5));
        g2 = deposit(persistBooking(customer, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CONFIRMED, 6, 1),
                250_000, "14000002", DAY.plusMinutes(5));
        g1.setGroup(group);
        g2.setGroup(group);
        bookingRepository.save(g1);
        bookingRepository.save(g2);
        // Coc VNPay 400.000 cua don khac, huy va hoan 400.000 qua VNPay cung ngay
        refunded = deposit(persistBooking(customer, persistRoom(type, 1_000_000, RoomStatus.AVAILABLE), BookingStatus.CANCELLED, 7, 1),
                400_000, "14000003", DAY.minusDays(2));
        refunded.setRefundAmount(BigDecimal.valueOf(400_000));
        refunded.setRefundedAt(DAY.plusHours(3));
        refunded.setRefundOnlineRef("14000099");
        bookingRepository.save(refunded);
        shiftRepository.save(CashShift.builder().status(CashShift.Status.CLOSED).openedBy(accountant).openedAt(DAY.minusHours(2))
                .openingCash(BigDecimal.valueOf(500_000)).closedBy(accountant).closedAt(DAY.plusHours(6))
                .cashIn(BigDecimal.valueOf(1_000_000)).countedCash(BigDecimal.valueOf(1_450_000)).cashToSafe(BigDecimal.valueOf(1_000_000))
                .handoverNote("Thieu 50k").build());
    }

    private Booking deposit(Booking b, long amount, String ref, LocalDateTime at) {
        b.setDepositAmount(BigDecimal.valueOf(amount));
        b.setDepositPaidAt(at);
        b.setDepositOnlineRef(ref);
        return bookingRepository.save(b);
    }

    @Test
    void monthLedger_pageAndExcel() throws Exception {
        // Thu: 1.000.000 tien mat + 300k + 450k (thang 3) ; coc 400k nhan ngay 12/03 ; hoan 400k -> thuc thu 1.750.000
        mockMvc.perform(get("/admin/accounting/ledger").with(as(accountant)).param("month", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sổ quỹ tháng")))
                .andExpect(content().string(containsString("1.750.000")))
                .andExpect(content().string(containsString("Hoàn cọc đơn #" + refunded.getId())));

        byte[] xlsx = mockMvc.perform(get("/admin/accounting/ledger.xlsx").with(as(accountant)).param("month", "2025-03"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("so-quy-2025-03.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            Sheet summary = wb.getSheet("Tong hop theo ngay");
            // 2 ngay co phat sinh (12/03 va 14/03) + dong cong
            assertThat(summary.getLastRowNum()).isEqualTo(3);
            assertThat(summary.getRow(3).getCell(5).getNumericCellValue()).isEqualTo(1_750_000d);
            assertThat(summary.getRow(3).getCell(4).getNumericCellValue()).isEqualTo(400_000d);
            assertThat(wb.getSheet("Chi tiet").getLastRowNum()).isEqualTo(6);
            Sheet shifts = wb.getSheet("Ca truc");
            assertThat(shifts.getRow(1).getCell(10).getNumericCellValue()).isEqualTo(-50_000d);
        }
    }

    @Test
    void vnpayDay_groupsGroupDeposit_andReconcilesStatement() throws Exception {
        mockMvc.perform(get("/admin/accounting/vnpay").with(as(accountant)).param("day", "2025-03-14"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cọc đoàn")))
                .andExpect(content().string(containsString("450.000")))
                .andExpect(content().string(containsString("14000099")))
                // coc nhan ngay 12/03 khong thuoc ngay 14/03
                .andExpect(content().string(not(containsString("14000003"))));

        // Sao ke: 1 khop, 1 lech tien, hoan tien ghi so am, 1 ma he thong khong co
        String csv = "﻿Ma GD;So tien\n14000001;300.000\n\"14000002\";\"440,000\"\n14000099;-400000\n15550000;120000\n";
        MockMultipartFile file = new MockMultipartFile("statement", "saoke.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/admin/accounting/vnpay").file(file).param("day", "2025-03-14").with(csrf()).with(as(accountant)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 giao dịch khớp, 2 giao dịch cần kiểm tra")))
                .andExpect(content().string(containsString("Lệch số tiền")))
                .andExpect(content().string(containsString("Không có trong hệ thống")));

        // Cung file -> Excel ket qua
        byte[] xlsx = mockMvc.perform(multipart("/admin/accounting/vnpay/export").file(file).param("day", "2025-03-14")
                        .with(csrf()).with(as(accountant)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(4);
            assertThat(wb.getSheetAt(0).getRow(1).getCell(7).getStringCellValue()).isNotEqualTo("Khớp");
        }
    }

    @Test
    void statementAsXlsx_andBadFile() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet();
            s.createRow(0).createCell(0).setCellValue("Mã giao dịch");
            var r1 = s.createRow(1);
            r1.createCell(0).setCellValue("14000001");
            r1.createCell(1).setCellValue(300000d);
            var r2 = s.createRow(2);
            r2.createCell(0).setCellValue("14000002");
            r2.createCell(1).setCellValue(450000d);
            var r3 = s.createRow(3);
            r3.createCell(0).setCellValue("14000099");
            r3.createCell(1).setCellValue(400000d);
            wb.write(out);
        }
        mockMvc.perform(multipart("/admin/accounting/vnpay").file(new MockMultipartFile("statement", "vnpay.xlsx", null, out.toByteArray()))
                        .param("day", "2025-03-14").with(csrf()).with(as(accountant)))
                .andExpect(content().string(containsString("Khớp toàn bộ 3 giao dịch")));

        mockMvc.perform(multipart("/admin/accounting/vnpay").file(new MockMultipartFile("statement", "rac.csv", "text/csv",
                                "xin chao\nkhong co gi".getBytes(StandardCharsets.UTF_8)))
                        .param("day", "2025-03-14").with(csrf()).with(as(accountant)))
                .andExpect(content().string(containsString("Không đọc được giao dịch nào")));
    }

    @Test
    void access_adminAndAccountantOnly() throws Exception {
        mockMvc.perform(get("/admin/accounting/ledger").with(as(persistUser(UserRole.ADMIN, null)))).andExpect(status().isOk());
        mockMvc.perform(get("/admin/accounting/vnpay").with(as(persistUser(UserRole.STAFF, null)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/accounting/ledger.xlsx").with(as(persistUser(UserRole.HOUSEKEEPER, null)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/home").with(as(accountant))).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/company-invoices").with(as(accountant)))
                .andExpect(content().string(containsString("Đối soát VNPay")));
    }
}
