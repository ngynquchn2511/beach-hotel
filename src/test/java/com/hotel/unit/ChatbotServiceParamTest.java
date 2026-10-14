package com.hotel.unit;

import com.hotel.entity.*;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.service.ChatbotService;
import com.hotel.service.PricingService;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TcSuite(level = "UT", module = "Chatbot tư vấn (ChatbotService)")
class ChatbotServiceParamTest {

    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ComboRepository comboRepository;
    @Mock private DiscountCodeRepository discountCodeRepository;
    @Mock private PricingService pricingService;
    @Mock private com.hotel.repository.DivingTourRepository divingTourRepository;
    @Mock private com.hotel.repository.TransferRateRepository transferRateRepository;

    @InjectMocks private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        // Homestay dang bat dat coc 30% trong 24 gio, phu thu cuoi tuan 30%
        when(pricingService.getSettings()).thenReturn(PricingSettings.builder().id(1L)
                .weekendSurchargePercent(30).depositPercent(30).depositDeadlineHours(24).build());
        RoomType normal = RoomType.builder().id(1L).name("Phòng thường").basePrice(bd(200000)).maxGuests(2).amenities("Wi-Fi, Tivi, tủ lạnh").build();
        RoomType vip = RoomType.builder().id(2L).name("Phòng VIP").basePrice(bd(500000)).maxGuests(2).amenities("Bồn tắm, view sông").build();
        when(roomTypeRepository.findAll()).thenReturn(List.of(normal, vip));
        when(roomRepository.findByRoomTypeId(1L)).thenReturn(List.of(room(200000, normal)));
        when(roomRepository.findByRoomTypeId(2L)).thenReturn(List.of(room(500000, vip), room(600000, vip)));
        when(roomRepository.findAvailableRooms(any(), any(), anyLong(), any())).thenReturn(List.of(room(200000, normal)));
        when(comboRepository.findByActiveTrue()).thenReturn(List.of(
                Combo.builder().name("Lẩu").price(bd(399000)).description("Lẩu thái 2 người").active(true).build(),
                Combo.builder().name("Nướng BBQ").price(bd(299000)).active(true).build()));
        when(discountCodeRepository.findByActiveTrue()).thenReturn(List.of(
                code("WELCOME10", DiscountType.PERCENTAGE, 10, CustomerType.NEW),
                code("VIP20", DiscountType.PERCENTAGE, 20, CustomerType.VIP),
                code("SUMMER50K", DiscountType.FIXED_AMOUNT, 50000, null)));
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static Room room(long price, RoomType type) {
        return Room.builder().roomNumber("R").roomType(type).price(bd(price)).status(RoomStatus.AVAILABLE).build();
    }

    private static DiscountCode code(String c, DiscountType t, long v, CustomerType ct) {
        return DiscountCode.builder().code(c).discountType(t).discountValue(bd(v)).applicableCustomerType(ct).active(true).build();
    }

    @TcSteps("Gọi ChatbotService.answer(câu hỏi) với dữ liệu phòng/combo/mã giảm giá giả lập, kiểm tra nội dung trả lời")
    @ParameterizedTest(name = "Chatbot hiểu ý định [{1}] ¦ câu hỏi: \"{0}\" ¦ Câu trả lời chứa \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "Giá phòng bao nhiêu?|Hỏi giá|Giá phòng ở Sóng Xanh nè",
            "gia phong bao nhieu tien|Hỏi giá (không dấu)|Giá phòng ở Sóng Xanh nè",
            "GIÁ PHÒNG VIP|Hỏi giá theo loại phòng (chữ hoa)|Phòng VIP: **500.000 – 600.000đ**/đêm",
            "phòng thường bao nhiêu 1 đêm|Hỏi giá phòng thường|Phòng thường: **200.000đ**/đêm",
            "Còn phòng trống không?|Hỏi phòng trống|Lịch trống 7 ngày tới",
            "con phong khong ban|Hỏi phòng trống (không dấu)|Lịch trống 7 ngày tới",
            "Ngày nào trống phòng VIP|Hỏi phòng trống theo loại|• Phòng VIP:",
            "Có cần đặt cọc không?|Hỏi đặt cọc|chuyển cọc **30%** tổng tiền trong vòng **24 giờ**",
            "dat coc bao nhieu|Hỏi đặt cọc (không dấu)|mã QR chuyển khoản",
            "ĐẶT CỌC TRƯỚC KHÔNG|Hỏi đặt cọc (chữ hoa)|**30%**",
            "Thanh toán bằng hình thức nào?|Hỏi thanh toán|**tiền mặt** hoặc **chuyển khoản**",
            "tra tien the nao|Hỏi thanh toán (không dấu)|trừ luôn tiền cọc",
            "PHƯƠNG THỨC THANH TOÁN|Hỏi thanh toán (chữ hoa)|tiền mặt",
            "Resort có hồ bơi không?|Hỏi tiện ích|Hồ bơi vô cực",
            "co ho boi khong|Hỏi tiện ích (không dấu)|Hồ bơi vô cực",
            "Tiện ích phòng VIP có gì|Hỏi tiện ích riêng của loại phòng|Tiện ích riêng của Phòng VIP: Bồn tắm",
            "Có bãi biển riêng không|Hỏi bãi biển|Bãi biển riêng",
            "Có combo ăn uống gì?|Hỏi combo|Lẩu: **399.000đ** (Lẩu thái 2 người)",
            "co buffet khong|Hỏi combo (không dấu)|Nướng BBQ: **299.000đ**",
            "ĐỒ ĂN CÓ GÌ|Hỏi combo (chữ hoa)|Combo ăn uống cho tối bên biển",
            "Có mã giảm giá không?|Hỏi khuyến mãi|Mã ưu đãi đang có nè",
            "khuyen mai gi khong|Hỏi khuyến mãi (không dấu)|**VIP20**: giảm 20%",
            "Có voucher nào không|Hỏi voucher|**SUMMER50K**: giảm 50.000đ",
            "Resort ở đâu?|Hỏi địa chỉ|Bãi Cháy",
            "dia chi resort|Hỏi địa chỉ (không dấu)|Hạ Long, Quảng Ninh",
            "VỊ TRÍ RESORT|Hỏi vị trí (chữ hoa)|Sóng Xanh ở **Đường Hạ Long",
            "Đường tới Hạ Long đi thế nào?|Hỏi đường đi|**150 km**",
            "tu ha noi toi bao xa|Hỏi khoảng cách (không dấu)|**2 – 2,5 tiếng**",
            "Số điện thoại liên hệ là gì?|Hỏi liên hệ|0338932368",
            "hotline khach san|Hỏi hotline (không dấu)|0338932368",
            "cho xin email|Hỏi email|songxanhresort@gmail.com",
            "có zalo không|Hỏi Zalo|Zalo: **0337196258**",
            "Mấy giờ nhận phòng?|Hỏi giờ nhận phòng|Giờ nhận phòng: **14:00**",
            "may gio tra phong|Hỏi giờ trả phòng (không dấu)|Giờ trả phòng: **12:00**",
            "CHECK IN LÚC NÀO|Hỏi check-in (chữ hoa)|Giờ nhận phòng",
            "Thời tiết Hạ Long thế nào|Hỏi thời tiết|mang áo khoác",
            "Gần đây có chỗ nào đi chơi|Hỏi điểm tham quan|**Hang Sửng Sốt**",
            "ở hạ long ăn gì ngon|Hỏi đặc sản|chả mực giã tay",
            "Cách đặt phòng thế nào|Hỏi cách đặt|Đặt phòng ở Sóng Xanh dễ lắm",
            "muốn hủy phòng thì sao|Hỏi hủy phòng|**Hủy đặt phòng**",
            "cảm ơn nha|Cảm ơn|Không có gì đâu nè",
            "tạm biệt|Tạm biệt|Hẹn gặp bạn ở vịnh Hạ Long",
            "Xin chào|Chào hỏi|Mình là **Sóng**",
            "hello|Chào hỏi tiếng Anh|trợ lý nhỏ của Sóng Xanh Resort",
            "hi|Chào hỏi ngắn|Mình là **Sóng**",
            "Phòng VIP|Chỉ nhắc tên loại phòng|Phòng VIP: **500.000 – 600.000đ**/đêm",
            "phong thuong|Chỉ nhắc tên loại phòng (không dấu)|• Phòng thường:",
            "abc xyz|Câu hỏi không hiểu|Sóng chưa hiểu lắm",
            "dự báo chứng khoán|Câu hỏi ngoài phạm vi|\"Phòng view biển giá bao nhiêu?\"",
            "Giá phòng và địa chỉ ở đâu|Hỏi kết hợp nhiều ý|Đường Hạ Long"})
    void answer_detectsIntent(String question, String intent, String expected) {
        assertThat(chatbotService.answer(question)).contains(expected);
    }

    @TcSteps("Gọi ChatbotService.answer() với câu hỏi rỗng")
    @ParameterizedTest(name = "Câu hỏi rỗng ¦ message=[{0}] ¦ Trả lời gợi ý \"Bạn muốn hỏi Sóng...\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void answer_blank(String q) {
        assertThat(chatbotService.answer(q)).startsWith("Bạn muốn hỏi Sóng");
    }

    @TcSteps("Gọi answer() hỏi ưu đãi cho khách mới, dữ liệu có mã NEW, VIP và mã dùng chung")
    @ParameterizedTest(name = "Hỏi ưu đãi cho khách mới - kiểm tra mã {1} ¦ câu hỏi: \"{0}\" ¦ Có chứa {1} = {2}")
    @CsvSource(delimiter = '|', value = {
            "Có ưu đãi gì cho người mới không|WELCOME10|true",
            "Có ưu đãi gì cho người mới không|SUMMER50K|true",
            "Có ưu đãi gì cho người mới không|VIP20|false",
            "khuyen mai cho khach moi lan dau|VIP20|false"})
    void answer_promotionForNewCustomers(String q, String code, boolean present) {
        String reply = chatbotService.answer(q);
        assertThat(reply).contains("Ưu đãi cho khách mới");
        assertThat(reply.contains(code)).isEqualTo(present);
    }

    @TcSteps("Giả lập kho dữ liệu trống (không có loại phòng/combo/mã giảm giá), gọi answer()")
    @ParameterizedTest(name = "Dữ liệu trống - hỏi {0} ¦ câu hỏi: \"{1}\" ¦ Trả lời \"{2}\"")
    @CsvSource(delimiter = '|', value = {
            "giá|Giá phòng bao nhiêu|Sóng chưa cập nhật giá phòng",
            "combo|Có combo gì|chưa mở combo ăn uống nào",
            "mã giảm giá|Có mã giảm giá không|chưa có mã giảm giá nào",
            "ưu đãi khách mới|Ưu đãi cho người mới|chưa có mã riêng cho khách mới"})
    void answer_emptyData(String topic, String q, String expected) {
        when(roomTypeRepository.findAll()).thenReturn(List.of());
        when(comboRepository.findByActiveTrue()).thenReturn(List.of());
        when(discountCodeRepository.findByActiveTrue()).thenReturn(List.of());
        assertThat(chatbotService.answer(q)).contains(expected);
    }

    @TcSteps("Giả lập loại phòng VIP không còn phòng trống trong 7 ngày, gọi answer()")
    @ParameterizedTest(name = "Hết phòng trong 7 ngày tới ¦ câu hỏi: \"{0}\" ¦ Trả lời \"kín phòng mất rồi\"")
    @ValueSource(strings = {"Phòng VIP còn phòng không", "het phong chua"})
    void answer_noAvailability(String q) {
        when(roomRepository.findAvailableRooms(any(), any(), anyLong(), any())).thenReturn(List.of());
        assertThat(chatbotService.answer(q)).contains("kín phòng mất rồi");
    }

    @TcSteps("Gọi reply() hỏi giá / đường đi / liên hệ, kiểm tra nút hành động và gợi ý câu hỏi tiếp theo")
    @ParameterizedTest(name = "Chatbot kèm nút hành động ¦ câu hỏi: \"{0}\" ¦ Có nút \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Giá phòng bao nhiêu|/rooms",
            "Đường tới Hạ Long đi thế nào|https://www.google.com/maps/dir/",
            "có zalo không|https://zalo.me/0337196258",
            "abc xyz|https://zalo.me/0337196258"})
    void reply_hasActions(String q, String urlPrefix) {
        ChatbotService.Reply reply = chatbotService.reply(q);
        assertThat(reply.actions()).anyMatch(a -> a.url().startsWith(urlPrefix));
        assertThat(reply.suggestions().size()).isLessThanOrEqualTo(3);
    }

    @TcSteps("Tắt đặt cọc trong cài đặt (0%), hỏi chatbot về đặt cọc")
    @ParameterizedTest(name = "Chatbot theo đúng cài đặt cọc ¦ cọc 0% ¦ Trả lời \"không yêu cầu đặt cọc\"")
    @ValueSource(strings = {"Có cần đặt cọc không", "dat coc"})
    void answer_depositDisabled(String q) {
        when(pricingService.getSettings()).thenReturn(PricingSettings.defaults());
        assertThat(chatbotService.answer(q)).contains("không yêu cầu đặt cọc");
    }

    @TcSteps("Hỏi về hang Sửng Sốt - không được nhầm thành hỏi combo")
    @ParameterizedTest(name = "Không nhận nhầm ý định ¦ câu hỏi: \"{0}\" ¦ Không trả lời combo")
    @ValueSource(strings = {"Hang Sửng Sốt có đẹp không", "đi chơi hang sửng sốt"})
    void answer_noFalseComboMatch(String q) {
        assertThat(chatbotService.answer(q)).doesNotContain("Combo ăn uống").contains("Hang Sửng Sốt");
    }
}
