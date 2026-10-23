package com.hotel.integration;

import com.hotel.entity.*;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.RoomServiceItemRepository;
import com.hotel.repository.RoomServiceOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Goi dich vu len phong: khach dang o chon mon -> le tan giao -> tien vao hoa don theo loai phu phi
class RoomServiceIT extends ItFixtures {

    @Autowired private RoomServiceItemRepository itemRepository;
    @Autowired private RoomServiceOrderRepository orderRepository;
    @Autowired private BookingChargeRepository chargeRepository;

    private User staff;
    private Booking booking;
    private String token;
    private RoomServiceItem pho;
    private RoomServiceItem beer;

    @BeforeEach
    void seed() {
        staff = persistUser(UserRole.STAFF, null);
        User guest = persistUser(UserRole.CUSTOMER, CustomerType.NEW);
        Room room = persistRoom(persistRoomType("Hang Goi IT", 900_000, 2), 900_000, RoomStatus.OCCUPIED);
        booking = persistBooking(guest, room, BookingStatus.CHECKED_IN, -1, 2);
        token = booking.getAccessToken();
        pho = itemRepository.save(RoomServiceItem.builder().name("Phở bò IT").category(RoomServiceItem.Category.FOOD)
                .price(BigDecimal.valueOf(85000)).active(true).build());
        beer = itemRepository.save(RoomServiceItem.builder().name("Bia IT").category(RoomServiceItem.Category.MINIBAR)
                .price(BigDecimal.valueOf(30000)).active(true).build());
    }

    private ResultActions order(String... qtyPairs) throws Exception {
        MockHttpServletRequestBuilder req = post("/customer/bookings/{id}/room-service", booking.getId()).with(csrf())
                .param("token", token).with(as(booking.getCustomer())).param("note", "ít cay");
        for (int i = 0; i < qtyPairs.length; i += 2) {
            req.param("qty_" + qtyPairs[i], qtyPairs[i + 1]);
        }
        return mockMvc.perform(req);
    }

    private RoomServiceOrder latestOrder() {
        return orderRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).get(0);
    }

    @Test
    void guestOrders_staffDelivers_chargesAddedByType() throws Exception {
        mockMvc.perform(get("/customer/bookings/{id}", booking.getId()).param("token", token).with(as(booking.getCustomer())))
                .andExpect(content().string(containsString("Gọi dịch vụ lên phòng")))
                .andExpect(content().string(containsString("Phở bò IT")));

        order(pho.getId().toString(), "2", beer.getId().toString(), "3")
                .andExpect(flash().attribute("roomServiceSuccess", containsString("Đã gửi yêu cầu")));
        RoomServiceOrder placed = latestOrder();
        assertThat(placed.getTotalAmount()).isEqualByComparingTo("260000");
        assertThat(placed.getNote()).isEqualTo("ít cay");

        mockMvc.perform(get("/admin/room-service").with(as(staff)))
                .andExpect(content().string(containsString("Phở bò IT x2, Bia IT x3")));

        mockMvc.perform(post("/admin/room-service/orders/{id}/deliver", placed.getId()).with(csrf()).with(as(staff)))
                .andExpect(flash().attribute("successMessage", containsString("Đã giao")));
        assertThat(latestOrder().getStatus()).isEqualTo(RoomServiceOrder.Status.DELIVERED);
        assertThat(latestOrder().getHandledBy()).isEqualTo(staff.getFullName());
        assertThat(chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()))
                .extracting(BookingCharge::getType, c -> c.getAmount().intValue())
                .containsExactlyInAnyOrder(tuple(ChargeType.ROOM_SERVICE, 170000), tuple(ChargeType.MINIBAR, 90000));

        // Da giao -> khach khong huy duoc
        mockMvc.perform(post("/customer/bookings/{id}/room-service/{oid}/cancel", booking.getId(), placed.getId())
                        .with(csrf()).param("token", token).with(as(booking.getCustomer())))
                .andExpect(flash().attribute("roomServiceError", containsString("đã được xử lý")));
    }

    @Test
    void guestCancelsPending_staffCancel_noCharges() throws Exception {
        order(pho.getId().toString(), "1");
        RoomServiceOrder first = latestOrder();
        mockMvc.perform(post("/customer/bookings/{id}/room-service/{oid}/cancel", booking.getId(), first.getId())
                .with(csrf()).param("token", token).with(as(booking.getCustomer())));
        assertThat(orderRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(RoomServiceOrder.Status.CANCELLED);

        order(beer.getId().toString(), "1");
        mockMvc.perform(post("/admin/room-service/orders/{id}/cancel", latestOrder().getId()).with(csrf()).with(as(staff)));
        assertThat(latestOrder().getStatus()).isEqualTo(RoomServiceOrder.Status.CANCELLED);
        assertThat(chargeRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId())).isEmpty();
    }

    @Test
    void validation_notInHouse_empty_tooMany_inactiveItem() throws Exception {
        order(pho.getId().toString(), "0").andExpect(flash().attribute("roomServiceError", containsString("ít nhất 1 món")));
        order(pho.getId().toString(), "25").andExpect(flash().attribute("roomServiceError", containsString("tối đa 20")));

        beer.setActive(false);
        itemRepository.save(beer);
        order(beer.getId().toString(), "2").andExpect(flash().attribute("roomServiceError", containsString("ít nhất 1 món")));

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        order(pho.getId().toString(), "1").andExpect(flash().attribute("roomServiceError", containsString("đang ở")));
        assertThat(orderRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId())).isEmpty();
    }

    @Test
    void menu_onlyAdminCanEdit() throws Exception {
        User admin = persistUser(UserRole.ADMIN, null);
        mockMvc.perform(post("/admin/room-service/menu").with(csrf()).with(as(admin))
                        .param("name", "Trà đào IT").param("category", "DRINK").param("price", "45000"))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm món")));
        mockMvc.perform(post("/admin/room-service/menu").with(csrf()).with(as(staff))
                        .param("name", "Lén thêm").param("category", "DRINK").param("price", "1000"))
                .andExpect(status().isForbidden());
        assertThat(itemRepository.findAll()).extracting(RoomServiceItem::getName).contains("Trà đào IT").doesNotContain("Lén thêm");
    }
}
