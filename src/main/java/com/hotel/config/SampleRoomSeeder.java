package com.hotel.config;

import com.hotel.entity.Combo;
import com.hotel.entity.Room;
import com.hotel.entity.RoomImage;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.RoomType;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// Tao san hang phong, phong va combo hai san cua resort bien de chay thu ngay sau khi clone.
// Chi chay khi DB chua co hang phong nao (lan dau) va app.seed-sample-rooms=true (may dev).
// Chay truoc TestDataSeeder (@Order(1)) de TestDataSeeder co phong ma sinh don gia lap.
@Component
@Order(1)
@ConditionalOnProperty(name = "app.seed-sample-rooms", havingValue = "true")
public class SampleRoomSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleRoomSeeder.class);

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ComboRepository comboRepository;

    public SampleRoomSeeder(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository,
                            ComboRepository comboRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
        this.comboRepository = comboRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (roomTypeRepository.count() > 0) {
            return;
        }

        RoomType standard = roomTypeRepository.save(RoomType.builder()
                .name("Phòng Tiêu chuẩn hướng phố").nameEn("Standard City View")
                .basePrice(BigDecimal.valueOf(650000)).maxGuests(2).area(BigDecimal.valueOf(25))
                .description("Phòng ấm cúng hướng phố, cách bãi biển Trần Phú chỉ vài bước chân.")
                .descriptionEn("A cosy city-view room just steps from Tran Phu beach.")
                .amenities("Giường đôi, điều hòa, Wi-Fi, TV, tủ lạnh mini")
                .amenitiesEn("Double bed, air conditioning, Wi-Fi, TV, mini fridge")
                .build());
        RoomType deluxe = roomTypeRepository.save(RoomType.builder()
                .name("Phòng Deluxe view biển").nameEn("Deluxe Sea View")
                .basePrice(BigDecimal.valueOf(1100000)).maxGuests(2).area(BigDecimal.valueOf(32))
                .description("Ban công riêng nhìn thẳng ra vịnh Nha Trang, đón bình minh trên biển mỗi sáng.")
                .descriptionEn("A private balcony facing Nha Trang Bay — watch the sunrise over the sea every morning.")
                .amenities("Ban công hướng biển, bồn tắm, điều hòa, Wi-Fi, minibar")
                .amenitiesEn("Sea-view balcony, bathtub, air conditioning, Wi-Fi, minibar")
                .build());
        RoomType family = roomTypeRepository.save(RoomType.builder()
                .name("Phòng Gia đình hướng biển").nameEn("Family Ocean Room")
                .basePrice(BigDecimal.valueOf(1600000)).maxGuests(4).area(BigDecimal.valueOf(45))
                .description("Rộng rãi cho cả nhà với hai giường lớn và ban công hướng biển.")
                .descriptionEn("Spacious for the whole family with two large beds and a sea-view balcony.")
                .amenities("2 giường đôi, ban công hướng biển, sofa, điều hòa, Wi-Fi")
                .amenitiesEn("2 double beds, sea-view balcony, sofa, air conditioning, Wi-Fi")
                .build());
        RoomType suite = roomTypeRepository.save(RoomType.builder()
                .name("Suite Hoàng hôn").nameEn("Sunset Suite")
                .basePrice(BigDecimal.valueOf(2800000)).maxGuests(2).area(BigDecimal.valueOf(60))
                .description("Căn suite tầng cao với phòng khách riêng và bồn tắm ngắm hoàng hôn trên vịnh.")
                .descriptionEn("A high-floor suite with a private lounge and a bathtub overlooking the sunset on the bay.")
                .amenities("Phòng khách riêng, bồn tắm view biển, ban công lớn, máy pha cà phê, minibar")
                .amenitiesEn("Private lounge, sea-view bathtub, large balcony, coffee machine, minibar")
                .build());

        addRooms(standard, "/page-images/anhphonggiuongdoi.jpg", "101", "102", "103");
        addRooms(deluxe, "/page-images/viewcuasogiuongdoi.jpg", "201", "202", "203", "204");
        addRooms(family, "/page-images/anhphonggiuongdoi2.jpg", "301", "302");
        addRooms(suite, "/page-images/anhphongVIP.jpg", "501");

        addCombo("Combo BBQ hải sản bên biển", "Seafood BBQ by the beach",
                "Tôm, mực, sò điệp, cá nướng tươi rói phục vụ ngay trên bãi biển của resort.",
                "Fresh grilled prawns, squid, scallops and fish served right on the resort's beach.",
                1200000, 4, "/page-images/donuong.jpg");
        addCombo("Combo lẩu hải sản", "Seafood hotpot",
                "Lẩu chua cay với cá, tôm, mực và rau tươi cho nhóm bạn.",
                "Spicy and sour hotpot with fish, prawns, squid and fresh greens for a group.",
                850000, 4, null);
        addCombo("Bữa sáng buffet view biển", "Sea-view breakfast buffet",
                "Buffet sáng Á – Âu tại nhà hàng tầng thượng nhìn ra vịnh.",
                "Asian and Western breakfast buffet at the rooftop restaurant overlooking the bay.",
                180000, 1, null);

        log.info("Da tao du lieu mau: 4 hang phong, 10 phong, 3 combo hai san");
    }

    private void addRooms(RoomType type, String imageUrl, String... numbers) {
        for (String number : numbers) {
            Room room = Room.builder()
                    .roomNumber(number)
                    .roomType(type)
                    .price(type.getBasePrice())
                    .status(RoomStatus.AVAILABLE)
                    .description(type.getDescription())
                    .descriptionEn(type.getDescriptionEn())
                    .build();
            room.getImages().add(RoomImage.builder().room(room).imageUrl(imageUrl).build());
            roomRepository.save(room);
        }
    }

    private void addCombo(String name, String nameEn, String description, String descriptionEn,
                          long price, int maxGuests, String imageUrl) {
        comboRepository.save(Combo.builder()
                .name(name).nameEn(nameEn)
                .description(description).descriptionEn(descriptionEn)
                .price(BigDecimal.valueOf(price)).maxGuests(maxGuests)
                .imageUrl(imageUrl).active(true)
                .build());
    }
}
