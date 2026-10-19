package com.hotel.config;

import com.hotel.entity.Room;
import com.hotel.entity.RoomImage;
import com.hotel.entity.RoomStatus;
import com.hotel.entity.RoomType;
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
import java.util.List;
import java.util.stream.IntStream;

// Bo sung 50 phong cho resort (tong 60 phong): them phong cho 4 hang phong mau + 6 hang phong moi voi view khac nhau
// (vuon, ho boi, bien/dao, bungalow sat bien, penthouse). Chay sau SampleRoomSeeder (@Order(2)) va truoc DemoDataSeeder.
// An toan khi chay lai: hang phong tim theo ten tieng Anh, phong nao da co so phong thi bo qua (khong dung toi phong admin da sua)
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed-sample-rooms", havingValue = "true")
public class MoreRoomsSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MoreRoomsSeeder.class);

    // Phong tang cao / goc dep dat hon gia hang phong mot chut
    private static final long PREMIUM_STEP = 50000;

    // Hang phong moi (ten tieng Anh dung lam khoa de khong tao trung khi khoi dong lai)
    record TypeSpec(String name, String nameEn, long basePrice, int maxGuests, int area,
                    String description, String descriptionEn, String amenities, String amenitiesEn) {
    }

    // Mot nhom phong cung hang: so phong, bo anh, cac goc view (xoay vong theo tung phong)
    record RoomGroup(String typeNameEn, TypeSpec newType, String imageSet, int imageCount,
                     List<String> numbers, List<String> views, List<String> viewsEn) {
    }

    static final TypeSpec SUPERIOR_GARDEN = new TypeSpec(
            "Phòng Superior hướng vườn", "Superior Garden View", 850000, 2, 28,
            "Phòng yên tĩnh nhìn ra khu vườn nhiệt đới và hồ sen của resort, cách bãi biển 2 phút đi bộ.",
            "A quiet room overlooking the resort's tropical garden and lotus pond, a 2-minute walk to the beach.",
            "Giường đôi, cửa sổ hướng vườn, vòi sen, điều hòa, Wi-Fi, TV",
            "Double bed, garden-view window, rain shower, air conditioning, Wi-Fi, TV");
    static final TypeSpec TWIN_POOL = new TypeSpec(
            "Phòng Deluxe 2 giường hướng hồ bơi", "Deluxe Twin Pool View", 1250000, 3, 34,
            "Hai giường đơn rộng, ban công nhìn xuống hồ bơi vô cực - hợp cho bạn bè hoặc bố mẹ đi cùng con lớn.",
            "Two large single beds and a balcony above the infinity pool - ideal for friends or parents with an older child.",
            "2 giường đơn, ban công hướng hồ bơi, bồn tắm, điều hòa, Wi-Fi, minibar",
            "2 single beds, pool-view balcony, bathtub, air conditioning, Wi-Fi, minibar");
    static final TypeSpec PREMIER_OCEAN = new TypeSpec(
            "Phòng Premier view đảo", "Premier Island View", 1450000, 2, 38,
            "Tầng cao với cửa kính lớn nhìn thẳng ra các đảo đá vịnh Hạ Long, có góc ngồi thư giãn bên cửa sổ.",
            "A high-floor room with floor-to-ceiling windows facing the limestone islands of Ha Long Bay and a window lounge.",
            "Giường King, cửa kính toàn cảnh, bồn tắm, máy pha cà phê, Wi-Fi, minibar",
            "King bed, panoramic windows, bathtub, coffee machine, Wi-Fi, minibar");
    static final TypeSpec FAMILY_GARDEN = new TypeSpec(
            "Phòng Gia đình hướng vườn", "Family Garden Room", 1400000, 4, 48,
            "Phòng rộng cho gia đình với sân hiên riêng mở ra vườn, gần hồ bơi trẻ em và khu vui chơi.",
            "A spacious family room with a private terrace opening onto the garden, close to the kids' pool and play area.",
            "1 giường King + 2 giường đơn, sân hiên riêng, sofa, điều hòa, Wi-Fi",
            "1 king bed + 2 single beds, private terrace, sofa, air conditioning, Wi-Fi");
    static final TypeSpec BUNGALOW = new TypeSpec(
            "Bungalow sát biển", "Beachfront Bungalow", 3200000, 3, 70,
            "Bungalow mái lá riêng biệt ngay trên bãi cát, có bể ngâm riêng và chòi nghỉ nhìn ra biển.",
            "A private thatched bungalow right on the sand with its own plunge pool and a beach-facing pavilion.",
            "Giường King có màn, bể ngâm riêng, chòi nghỉ, phòng tắm ngoài trời, minibar",
            "Canopy king bed, private plunge pool, pavilion, outdoor bathroom, minibar");
    static final TypeSpec PENTHOUSE = new TypeSpec(
            "Penthouse toàn cảnh vịnh", "Bay Panorama Penthouse", 5500000, 4, 120,
            "Căn penthouse tầng thượng với phòng khách riêng, 2 phòng ngủ và sân thượng nhìn toàn cảnh vịnh.",
            "A top-floor penthouse with a private living room, 2 bedrooms and a terrace overlooking the whole bay.",
            "2 phòng ngủ, phòng khách riêng, sân thượng, bồn tắm view biển, quản gia riêng",
            "2 bedrooms, private living room, rooftop terrace, sea-view bathtub, personal butler");

    static final List<RoomGroup> GROUPS = List.of(
            // Them phong cho 4 hang phong mau (bo anh cu cua SampleRoomSeeder)
            new RoomGroup("Standard City View", null, "standard", 4, numbers(104, 110),
                    List.of("Hướng phố Bãi Cháy", "Hướng đồi thông", "Hướng phố, gần thang máy"),
                    List.of("Bai Chay street view", "Pine hill view", "Street view, near the lift")),
            new RoomGroup("Deluxe Sea View", null, "deluxe", 5, numbers(205, 212),
                    List.of("Nhìn thẳng ra vịnh", "Góc nhìn cầu Bãi Cháy", "Góc nhìn bình minh trên đảo"),
                    List.of("Straight bay view", "Bai Chay Bridge view", "Sunrise-over-the-islands view")),
            new RoomGroup("Family Ocean Room", null, "family", 5, numbers(303, 306),
                    List.of("Ban công hướng biển", "Ban công góc, đón gió biển"),
                    List.of("Sea-view balcony", "Corner balcony with sea breeze")),
            new RoomGroup("Sunset Suite", null, "suite", 6, List.of("502"),
                    List.of("Góc ngắm hoàng hôn trên vịnh"), List.of("Sunset-over-the-bay corner")),
            // 6 hang phong moi
            new RoomGroup(SUPERIOR_GARDEN.nameEn(), SUPERIOR_GARDEN, "superior-garden", 4, numbers(111, 118),
                    List.of("Hướng vườn nhiệt đới", "Hướng hồ sen", "Hướng vườn, tầng trệt có hiên"),
                    List.of("Tropical garden view", "Lotus pond view", "Garden view, ground floor with porch")),
            new RoomGroup(TWIN_POOL.nameEn(), TWIN_POOL, "twin-pool", 4, numbers(213, 220),
                    List.of("Hướng hồ bơi vô cực", "Hướng hồ bơi và vườn dừa"),
                    List.of("Infinity pool view", "Pool and coconut grove view")),
            new RoomGroup(PREMIER_OCEAN.nameEn(), PREMIER_OCEAN, "premier-ocean", 3, numbers(401, 407),
                    List.of("View đảo Đầu Gỗ", "View hòn Trống Mái", "View toàn cảnh đảo đá"),
                    List.of("Dau Go Island view", "Fighting Cocks Islet view", "Limestone islands panorama")),
            new RoomGroup(FAMILY_GARDEN.nameEn(), FAMILY_GARDEN, "family-garden", 5, numbers(307, 310),
                    List.of("Sân hiên hướng vườn", "Sân hiên gần hồ bơi trẻ em"),
                    List.of("Garden-facing terrace", "Terrace near the kids' pool")),
            new RoomGroup(BUNGALOW.nameEn(), BUNGALOW, "bungalow", 5, List.of("B01", "B02"),
                    List.of("Ngay trên bãi cát", "Sát mép nước, đón hoàng hôn"),
                    List.of("Right on the sand", "At the water's edge, sunset side")),
            new RoomGroup(PENTHOUSE.nameEn(), PENTHOUSE, "penthouse", 3, List.of("601"),
                    List.of("Toàn cảnh vịnh 270°"), List.of("270° bay panorama")));

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;

    public MoreRoomsSeeder(RoomTypeRepository roomTypeRepository, RoomRepository roomRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<RoomType> types = roomTypeRepository.findAll();
        int added = 0;
        for (RoomGroup group : GROUPS) {
            RoomType type = types.stream()
                    .filter(t -> group.typeNameEn().equalsIgnoreCase(t.getNameEn()))
                    .findFirst()
                    .orElse(null);
            if (type == null) {
                if (group.newType() == null) {
                    // Hang phong mau da bi admin doi ten / xoa -> khong tu tao lai
                    log.info("Bo qua phong them cho hang '{}' (khong con hang phong nay)", group.typeNameEn());
                    continue;
                }
                type = roomTypeRepository.save(toRoomType(group.newType()));
                types = new java.util.ArrayList<>(types);
                types.add(type);
            }
            added += addRooms(type, group);
        }
        if (added > 0) {
            log.info("Da them {} phong mau moi (cac hang phong va view khac nhau)", added);
        }
    }

    private int addRooms(RoomType type, RoomGroup group) {
        int added = 0;
        for (int i = 0; i < group.numbers().size(); i++) {
            String number = group.numbers().get(i);
            if (roomRepository.existsByRoomNumber(number)) {
                continue;
            }
            String view = group.views().get(i % group.views().size());
            String viewEn = group.viewsEn().get(i % group.viewsEn().size());
            Room room = Room.builder()
                    .roomNumber(number)
                    .roomType(type)
                    .price(type.getBasePrice().add(BigDecimal.valueOf(PREMIUM_STEP * (i % 3))))
                    .status(RoomStatus.AVAILABLE)
                    .description(view + ". " + type.getDescription())
                    .descriptionEn(viewEn + ". " + type.getDescriptionEn())
                    .build();
            // Moi phong bat dau bo anh o mot vi tri khac nhau -> anh dai dien cac phong cung hang khong giong het nhau
            for (int k = 0; k < group.imageCount(); k++) {
                int n = (i + k) % group.imageCount() + 1;
                room.getImages().add(RoomImage.builder().room(room)
                        .imageUrl("/page-images/rooms/" + group.imageSet() + "-" + n + ".jpg").build());
            }
            roomRepository.save(room);
            added++;
        }
        return added;
    }

    private static RoomType toRoomType(TypeSpec spec) {
        return RoomType.builder()
                .name(spec.name()).nameEn(spec.nameEn())
                .basePrice(BigDecimal.valueOf(spec.basePrice())).maxGuests(spec.maxGuests())
                .area(BigDecimal.valueOf(spec.area()))
                .description(spec.description()).descriptionEn(spec.descriptionEn())
                .amenities(spec.amenities()).amenitiesEn(spec.amenitiesEn())
                .build();
    }

    private static List<String> numbers(int from, int to) {
        return IntStream.rangeClosed(from, to).mapToObj(String::valueOf).toList();
    }
}
