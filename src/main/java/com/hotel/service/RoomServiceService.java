package com.hotel.service;

import com.hotel.entity.*;
import com.hotel.exception.BusinessException;
import com.hotel.repository.BookingChargeRepository;
import com.hotel.repository.PaymentRepository;
import com.hotel.repository.RoomServiceItemRepository;
import com.hotel.repository.RoomServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Goi dich vu len phong: khach dang o chon mon -> le tan giao -> tien cong vao hoa don tra phong (theo loai phu phi)
@Service
public class RoomServiceService {

    private com.hotel.service.StaffNotificationService staffNotifier;

    @org.springframework.beans.factory.annotation.Autowired
    void setStaffNotifier(com.hotel.service.StaffNotificationService staffNotifier) {
        this.staffNotifier = staffNotifier;
    }

    private void notifyStaff(String kind, com.hotel.service.StaffNotificationService.Level level, String title, String message, String url) {
        if (staffNotifier != null) {
            staffNotifier.notify(kind, level, title, message, url);
        }
    }

    public static final int MAX_QUANTITY = 20;
    public static final BigDecimal MAX_PRICE = new BigDecimal("10000000");

    private final RoomServiceItemRepository itemRepository;
    private final RoomServiceOrderRepository orderRepository;
    private final BookingChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;

    public RoomServiceService(RoomServiceItemRepository itemRepository, RoomServiceOrderRepository orderRepository,
                              BookingChargeRepository chargeRepository, PaymentRepository paymentRepository) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
    }

    // Chi khach dang o (da nhan phong, chua thanh toan) moi goi duoc
    public boolean canOrder(Booking booking) {
        return booking.getStatus() == BookingStatus.CHECKED_IN && !isPaid(booking);
    }

    public List<RoomServiceItem> menu() {
        return itemRepository.findByActiveTrueOrderByCategoryAscNameAsc();
    }

    public Map<RoomServiceItem.Category, List<RoomServiceItem>> menuByCategory() {
        return menu().stream().collect(Collectors.groupingBy(RoomServiceItem::getCategory, LinkedHashMap::new, Collectors.toList()));
    }

    public List<RoomServiceOrder> ordersOf(Long bookingId) {
        return orderRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    // quantities: id mon -> so luong (0 / am bo qua)
    @Transactional
    public RoomServiceOrder place(Booking booking, Map<Long, Integer> quantities, String note) {
        if (!canOrder(booking)) {
            throw BusinessException.of("rs.error.notInHouse");
        }
        RoomServiceOrder order = RoomServiceOrder.builder().booking(booking).status(RoomServiceOrder.Status.PENDING)
                .note(cleanNote(note)).totalAmount(BigDecimal.ZERO).build();
        BigDecimal total = BigDecimal.ZERO;
        for (RoomServiceItem item : menu()) {
            int qty = quantities.getOrDefault(item.getId(), 0);
            if (qty <= 0) {
                continue;
            }
            if (qty > MAX_QUANTITY) {
                throw BusinessException.of("rs.error.quantity", MAX_QUANTITY);
            }
            RoomServiceOrderLine line = RoomServiceOrderLine.builder().order(order).itemName(item.getName())
                    .category(item.getCategory()).unitPrice(item.getPrice()).quantity(qty).build();
            order.getLines().add(line);
            total = total.add(line.getAmount());
        }
        if (order.getLines().isEmpty()) {
            throw BusinessException.of("rs.error.empty");
        }
        order.setTotalAmount(total);
        RoomServiceOrder saved = orderRepository.save(order);
        notifyStaff("room-service", com.hotel.service.StaffNotificationService.Level.INFO, "Gọi lên phòng " + booking.getRoom().getRoomNumber(),
                saved.getLines().size() + " món · " + com.hotel.util.Money.format(total) + " VND", "/admin/room-service");
        return saved;
    }

    // Khach huy khi le tan chua giao
    @Transactional
    public void cancelByGuest(Booking booking, Long orderId) {
        RoomServiceOrder order = orderRepository.findById(orderId)
                .filter(o -> o.getBooking().getId().equals(booking.getId()))
                .orElseThrow(() -> BusinessException.of("rs.error.notFound"));
        if (!order.isPending()) {
            throw BusinessException.of("rs.error.notPending");
        }
        order.setStatus(RoomServiceOrder.Status.CANCELLED);
        orderRepository.save(order);
    }

    // ===== Le tan =====

    public List<RoomServiceOrder> pending() {
        return orderRepository.findPending();
    }

    public List<RoomServiceOrder> deliveredToday() {
        return orderRepository.findDeliveredSince(LocalDate.now().atStartOfDay());
    }

    public long countPending() {
        return orderRepository.countByStatus(RoomServiceOrder.Status.PENDING);
    }

    // Da giao -> cong tien vao hoa don: moi loai phu phi (do an / minibar / giat ui) 1 dong
    @Transactional
    public RoomServiceOrder deliver(Long orderId, String staffName) {
        RoomServiceOrder order = requirePending(orderId);
        Booking booking = order.getBooking();
        if (!canOrder(booking)) {
            throw new BusinessException("Đơn phòng không còn ở trạng thái đang ở / đã thanh toán - không cộng tiền được, hãy hủy yêu cầu này");
        }
        Map<ChargeType, List<RoomServiceOrderLine>> byType = order.getLines().stream()
                .collect(Collectors.groupingBy(l -> l.getCategory().chargeType(), LinkedHashMap::new, Collectors.toList()));
        byType.forEach((type, lines) -> {
            String text = "Gọi lên phòng #" + order.getId() + ": " + lines.stream()
                    .map(l -> l.getItemName() + " x" + l.getQuantity()).collect(Collectors.joining(", "));
            chargeRepository.save(BookingCharge.builder().booking(booking).type(type)
                    .description(text.length() > 200 ? text.substring(0, 197) + "..." : text)
                    .amount(lines.stream().map(RoomServiceOrderLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .build());
        });
        order.setStatus(RoomServiceOrder.Status.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        order.setHandledBy(staffName);
        return orderRepository.save(order);
    }

    @Transactional
    public RoomServiceOrder cancelByStaff(Long orderId, String staffName) {
        RoomServiceOrder order = requirePending(orderId);
        order.setStatus(RoomServiceOrder.Status.CANCELLED);
        order.setHandledBy(staffName);
        return orderRepository.save(order);
    }

    // ===== Thuc don (ADMIN) =====

    public List<RoomServiceItem> allItems() {
        return itemRepository.findAllByOrderByCategoryAscNameAsc();
    }

    @Transactional
    public RoomServiceItem createItem(String name, String nameEn, RoomServiceItem.Category category, BigDecimal price) {
        String text = name == null ? "" : name.trim();
        if (text.isEmpty() || text.length() > 100) {
            throw new BusinessException("Vui lòng nhập tên món (tối đa 100 ký tự)");
        }
        if (category == null) {
            throw new BusinessException("Vui lòng chọn nhóm");
        }
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new BusinessException("Giá phải lớn hơn 0 và không quá 10.000.000 VND");
        }
        String en = nameEn == null || nameEn.isBlank() ? null : nameEn.trim();
        return itemRepository.save(RoomServiceItem.builder().name(text).nameEn(en).category(category)
                .price(price.setScale(0, java.math.RoundingMode.HALF_UP)).active(true).build());
    }

    @Transactional
    public void toggleItem(Long id) {
        RoomServiceItem item = itemRepository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy món"));
        item.setActive(!Boolean.TRUE.equals(item.getActive()));
        itemRepository.save(item);
    }

    private RoomServiceOrder requirePending(Long orderId) {
        RoomServiceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu gọi lên phòng"));
        if (!order.isPending()) {
            throw new BusinessException("Yêu cầu #" + orderId + " đã được xử lý");
        }
        return order;
    }

    private boolean isPaid(Booking booking) {
        return paymentRepository.findByBookingId(booking.getId())
                .map(p -> p.getStatus() == PaymentStatus.PAID).orElse(false);
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String text = note.trim();
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
