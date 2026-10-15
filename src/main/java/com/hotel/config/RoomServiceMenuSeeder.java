package com.hotel.config;

import com.hotel.entity.RoomServiceItem;
import com.hotel.repository.RoomServiceItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

// Thuc don goi len phong mau (may dev, app.seed-sample-rooms=true) - chi tao khi chua co mon nao
@Component
@Order(3)
@ConditionalOnProperty(name = "app.seed-sample-rooms", havingValue = "true")
public class RoomServiceMenuSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoomServiceMenuSeeder.class);

    private final RoomServiceItemRepository repository;

    public RoomServiceMenuSeeder(RoomServiceItemRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        item("Phở bò tái", "Rare beef pho", RoomServiceItem.Category.FOOD, 85000);
        item("Cơm rang hải sản", "Seafood fried rice", RoomServiceItem.Category.FOOD, 120000);
        item("Mực nướng sa tế", "Grilled squid with chilli", RoomServiceItem.Category.FOOD, 165000);
        item("Bánh mì trứng ốp la", "Banh mi with fried egg", RoomServiceItem.Category.FOOD, 45000);
        item("Nước cam ép", "Fresh orange juice", RoomServiceItem.Category.DRINK, 55000);
        item("Cà phê sữa đá", "Iced milk coffee", RoomServiceItem.Category.DRINK, 40000);
        item("Bia Hạ Long (lon)", "Ha Long beer (can)", RoomServiceItem.Category.MINIBAR, 30000);
        item("Nước suối", "Mineral water", RoomServiceItem.Category.MINIBAR, 15000);
        item("Snack khoai tây", "Potato chips", RoomServiceItem.Category.MINIBAR, 35000);
        item("Giặt ủi (1 kg)", "Laundry (per kg)", RoomServiceItem.Category.LAUNDRY, 60000);
        item("Ủi áo sơ mi", "Shirt pressing", RoomServiceItem.Category.LAUNDRY, 25000);
        log.info("Da tao thuc don goi len phong mau");
    }

    private void item(String name, String nameEn, RoomServiceItem.Category category, long price) {
        repository.save(RoomServiceItem.builder().name(name).nameEn(nameEn).category(category)
                .price(BigDecimal.valueOf(price)).active(true).build());
    }
}
