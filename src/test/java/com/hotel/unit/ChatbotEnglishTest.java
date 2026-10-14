package com.hotel.unit;

import com.hotel.entity.*;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DiscountCodeRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.service.ChatbotService;
import com.hotel.service.PricingService;
import com.hotel.util.Texts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

// Chatbot "May" o che do tieng Anh: hieu cau hoi tieng Anh va tra loi hoan toan bang tieng Anh
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatbotEnglishTest {

    // Chu co dau tieng Viet (bo qua ten rieng "Sóng"/"Sóng Xanh" va du lieu admin nhap nhu ten phong/combo)
    private static final Pattern VIETNAMESE = Pattern.compile("[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]");

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
        when(pricingService.getSettings()).thenReturn(PricingSettings.builder().id(1L)
                .weekendSurchargePercent(30).depositPercent(30).depositDeadlineHours(24).build());
        RoomType vip = RoomType.builder().id(2L).name("VIP").basePrice(BigDecimal.valueOf(500000)).maxGuests(2).build();
        when(roomTypeRepository.findAll()).thenReturn(List.of(vip));
        Room room = Room.builder().roomNumber("R").roomType(vip).price(BigDecimal.valueOf(1500000)).status(RoomStatus.AVAILABLE).build();
        when(roomRepository.findByRoomTypeId(2L)).thenReturn(List.of(room));
        when(roomRepository.findAvailableRooms(any(), any(), anyLong(), any())).thenReturn(List.of(room));
        when(comboRepository.findByActiveTrue()).thenReturn(List.of(
                Combo.builder().name("BBQ").price(BigDecimal.valueOf(299000)).active(true).build()));
        when(discountCodeRepository.findByActiveTrue()).thenReturn(List.of(DiscountCode.builder().code("WELCOME10")
                .discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN)
                .applicableCustomerType(CustomerType.NEW).active(true).build()));
    }

    private String ask(String question) {
        return chatbotService.reply(question, Locale.ENGLISH).text();
    }

    // Moi nut goi y / chu de tieng Anh khi bam deu phai duoc hieu (khong roi vao cau "chua hieu")
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "chat.sg.price      | Room prices at Sóng Xanh",
            "chat.sg.available  | Availability for the next 7 days",
            "chat.sg.directions | **150 km**",
            "chat.sg.deposit    | **30%** deposit within **24 hours**",
            "chat.sg.howToBook  | Booking at Sóng Xanh is easy",
            "chat.sg.payment    | **cash** or **bank transfer**",
            "chat.sg.cancel     | **Cancel booking**",
            "chat.sg.combo      | Food combos for an evening by the sea",
            "chat.sg.explore    | **Sung Sot Cave**",
            "chat.sg.food       | squid cakes",
            "chat.sg.weather    | jacket"})
    void englishSuggestionChips_areUnderstood(String chipKey, String expected) {
        String answer = ask(Texts.get(Locale.ENGLISH, chipKey));
        assertThat(answer).contains(expected).doesNotContain("didn't quite get that");
    }

    @Test
    void englishAnswers_containNoVietnamese() {
        for (String q : new String[]{"hello", "price", "deposit", "where is it", "contact", "check in time",
                "discount for first time guests", "thanks bye", "blah blah"}) {
            String answer = ask(q).replace("Sóng Xanh", "").replace("Sóng", "");
            assertThat(VIETNAMESE.matcher(answer).find()).as("Answer to '%s': %s", q, answer).isFalse();
        }
    }

    @Test
    void englishPrices_useEnglishNumberFormat() {
        assertThat(ask("How much is the VIP room?")).contains("**1,500,000 VND**/night").contains("**+30%**");
    }

    @Test
    void englishActionsAndSuggestions_areTranslated() {
        ChatbotService.Reply reply = chatbotService.reply("What are the room prices?", Locale.ENGLISH);
        assertThat(reply.actions()).extracting(ChatbotService.Action::label).containsExactly("🛏️ View rooms & book");
        assertThat(reply.suggestions()).containsExactly("Are there rooms available?", "Do I need to pay a deposit?");
    }

    // Khach go tieng Viet trong che do tieng Anh van hieu, nhung tra loi bang tieng Anh
    @Test
    void vietnameseQuestion_inEnglishMode_answeredInEnglish() {
        assertThat(ask("Giá phòng thế nào")).contains("Room prices at Sóng Xanh");
    }

    // Mac dinh (va moi ngon ngu khac tieng Anh) van tra loi tieng Viet nhu cu
    @Test
    void defaultLocale_staysVietnamese() {
        assertThat(chatbotService.reply("price", Locale.FRENCH).text()).contains("Giá phòng ở Sóng Xanh nè");
        assertThat(chatbotService.answer("Giá phòng thế nào")).contains("**1.500.000đ**/đêm");
    }
}
