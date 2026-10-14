package com.hotel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.base-dir}")
    private String baseUploadDir;

    // Thu muc anh ngoai project (tuy chon) - neu co, anh trong do duoc uu tien hon anh di kem project
    @Value("${app.page-images.dir:}")
    private String pageImagesDir;

    public static final Locale VIETNAMESE = new Locale("vi", "VN");

    // Chu song ngu cua trang khach: messages.properties (tieng Viet, mac dinh) + messages_en.properties.
    // Khai bao trong code thay vi spring.messages.* de khong phu thuoc file cau hinh: neu de Spring tu cau hinh,
    // may chu chay he dieu hanh tieng Anh se lay nham messages_en cho khach Viet (fallback theo locale may chu)
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    // Ngon ngu khach chon (nut VI/EN tren header) luu trong cookie 1 nam, mac dinh tieng Viet
    // (khong doan theo trinh duyet: khach Viet dung trinh duyet tieng Anh van thay tieng Viet)
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("lang");
        resolver.setDefaultLocale(VIETNAMESE);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        return resolver;
    }

    // Them ?lang=en hoac ?lang=vi vao bat ky URL nao de doi ngon ngu
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Chi nhan vi/en - gia tri khac (?lang=fr) bi bo qua, giu nguyen ngon ngu dang dung
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor() {
            @Override
            protected Locale parseLocaleValue(String value) {
                return switch (value) {
                    case "en" -> Locale.ENGLISH;
                    case "vi" -> VIETNAMESE;
                    default -> throw new IllegalArgumentException("Unsupported language: " + value);
                };
            }
        };
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        registry.addInterceptor(interceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get(baseUploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");

        // Anh nen tung trang: tim trong thu muc ngoai (neu cau hinh) truoc, khong co thi lay anh
        // di kem project (src/main/resources/static/page-images) - clone ve la chay duoc, khong can chep anh
        var pageImages = registry.addResourceHandler("/page-images/**");
        if (pageImagesDir != null && !pageImagesDir.isBlank()) {
            String pageImagesPath = Paths.get(pageImagesDir).toAbsolutePath().normalize().toString();
            pageImages.addResourceLocations("file:" + pageImagesPath + "/");
        }
        pageImages.addResourceLocations("classpath:/static/page-images/");
    }
}
