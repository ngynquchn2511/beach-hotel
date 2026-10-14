package com.hotel.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Nut VI/EN tren trang khach: ?lang= doi ngon ngu, lua chon duoc nho qua cookie "lang"
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class I18nIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private com.hotel.repository.RoomTypeRepository roomTypeRepository;

    private MvcResult fetch(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private static String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void defaultIsVietnamese() throws Exception {
        String page = html(fetch(get("/")));
        assertThat(page).contains("Xem danh sách phòng").contains("<html lang=\"vi\"");
    }

    @Test
    void langParamSwitchesToEnglish_andRemembersInCookie() throws Exception {
        MvcResult result = fetch(get("/").param("lang", "en"));
        assertThat(html(result)).contains("Browse rooms").doesNotContain("Xem danh sách phòng").contains("<html lang=\"en\"");
        assertThat(result.getResponse().getCookie("lang")).isNotNull()
                .extracting(Cookie::getValue).isEqualTo("en");
    }

    @Test
    void cookieKeepsEnglishOnNextPage() throws Exception {
        assertThat(html(fetch(get("/").cookie(new Cookie("lang", "en"))))).contains("Browse rooms");
    }

    @Test
    void unsupportedLanguage_isIgnored() throws Exception {
        MvcResult result = fetch(get("/").param("lang", "fr"));
        assertThat(html(result)).contains("Xem danh sách phòng");
        assertThat(result.getResponse().getCookie("lang")).isNull();
    }

    // Trang gioi thieu co du lieu hang phong (nhanh co/khong co mo ta) render duoc o ca 2 ngon ngu
    @Test
    void aboutPage_withRoomTypes_rendersInBothLanguages() throws Exception {
        roomTypeRepository.save(com.hotel.entity.RoomType.builder().name("Suite I18n")
                .basePrice(java.math.BigDecimal.valueOf(900000)).maxGuests(3).build());
        assertThat(html(fetch(get("/about").param("lang", "en"))))
                .contains("Suite I18n").contains("A comfortable, fully equipped place to relax.").contains("3 guests");
        assertThat(html(fetch(get("/about").param("lang", "vi"))))
                .contains("Không gian nghỉ ngơi thoải mái, đầy đủ tiện nghi.").contains("3 khách");
    }

    // Noi dung admin nhap 2 ban: EN hien ban tieng Anh, VI hien ban tieng Viet; bo trong ban tieng Anh thi dung ban tieng Viet
    @Test
    void adminContent_followsSelectedLanguage_withVietnameseFallback() throws Exception {
        roomTypeRepository.save(com.hotel.entity.RoomType.builder().name("Phòng Mây Trắng").nameEn("White Cloud Room")
                .description("Phòng có ban công ngắm mây").descriptionEn("Room with a cloud-watching balcony")
                .basePrice(java.math.BigDecimal.valueOf(900000)).maxGuests(2).build());
        roomTypeRepository.save(com.hotel.entity.RoomType.builder().name("Phòng Chưa Dịch")
                .description("Chỉ có bản tiếng Việt").basePrice(java.math.BigDecimal.valueOf(800000)).maxGuests(2).build());

        assertThat(html(fetch(get("/about").param("lang", "en"))))
                .contains("White Cloud Room").contains("Room with a cloud-watching balcony")
                .doesNotContain("Phòng Mây Trắng")
                .contains("Phòng Chưa Dịch").contains("Chỉ có bản tiếng Việt");
        assertThat(html(fetch(get("/about").param("lang", "vi"))))
                .contains("Phòng Mây Trắng").contains("Phòng có ban công ngắm mây").doesNotContain("White Cloud Room");
    }

    // Loi nghiep vu tu service (BusinessException.of) hien theo ngon ngu dang chon
    @Test
    void businessErrors_followSelectedLanguage() throws Exception {
        String en = html(fetch(get("/customer/rooms").param("checkIn", "2020-01-01").param("checkOut", "2020-01-03")
                .param("lang", "en")));
        assertThat(en).contains("The check-in date can&#39;t be in the past").doesNotContain("Ngày nhận phòng không được");

        String vi = html(fetch(get("/customer/rooms").param("checkIn", "2020-01-01").param("checkOut", "2020-01-03")));
        assertThat(vi).contains("Ngày nhận phòng không được ở trong quá khứ");
    }

    // Thong bao kiem tra form (message = "{val...}" trong DTO) cung song ngu
    @Test
    void validationMessages_followSelectedLanguage() throws Exception {
        String en = html(fetch(post("/register").with(csrf()).param("lang", "en")
                .param("fullName", "").param("email", "x@y.com").param("phoneNumber", "123")
                .param("password", "secret1").param("confirmPassword", "secret1")));
        assertThat(en).contains("Please enter your full name").contains("Phone number must have 10 digits");
    }

    // getMessage() cua loi co ma van la tieng Viet (log, test cu khong doi)
    @Test
    void translatableException_keepsVietnameseMessage() {
        var ex = com.hotel.exception.BusinessException.of("err.editTimesClosed", 6);
        assertThat(ex.getMessage()).isEqualTo("Không thể sửa giờ nhận/trả phòng — chỉ được sửa trước 6 tiếng so với giờ nhận phòng, và đơn phải chưa check-in/hủy");
        assertThat(ex.render((code, args) -> code)).isEqualTo("err.editTimesClosed");
    }

    // Moi trang khach render duoc o ca 2 ngon ngu va khong thieu khoa (thieu thi Thymeleaf hien ??key_en??)
    @ParameterizedTest
    @ValueSource(strings = {"/", "/about", "/rooms", "/customer/rooms", "/login", "/register", "/forgot-password"})
    void guestPages_haveNoMissingTranslations(String path) throws Exception {
        for (String lang : new String[]{"vi", "en"}) {
            assertThat(html(fetch(get(path).param("lang", lang))))
                    .as("%s?lang=%s", path, lang)
                    .doesNotContainPattern("\\?\\?[\\w.]+_(vi|en)\\w*\\?\\?");
        }
    }
}
