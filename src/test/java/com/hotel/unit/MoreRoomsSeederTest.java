package com.hotel.unit;

import com.hotel.config.MoreRoomsSeeder;
import com.hotel.entity.Room;
import com.hotel.entity.RoomImage;
import com.hotel.entity.RoomType;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Bo sung 50 phong mau voi nhieu hang phong / view khac nhau
@ExtendWith(MockitoExtension.class)
class MoreRoomsSeederTest {

    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomRepository roomRepository;

    private List<RoomType> sampleTypes() {
        long id = 1;
        List<RoomType> types = new ArrayList<>();
        for (String en : List.of("Standard City View", "Deluxe Sea View", "Family Ocean Room", "Sunset Suite")) {
            types.add(RoomType.builder().id(id++).name(en).nameEn(en).basePrice(BigDecimal.valueOf(1_000_000))
                    .maxGuests(2).description("Mo ta").descriptionEn("Desc").build());
        }
        return types;
    }

    private List<Room> runWith(List<RoomType> types) {
        when(roomTypeRepository.findAll()).thenReturn(types);
        lenient().when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(inv -> inv.getArgument(0));
        new MoreRoomsSeeder(roomTypeRepository, roomRepository).run();
        ArgumentCaptor<Room> saved = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository, atLeast(0)).save(saved.capture());
        return saved.getAllValues();
    }

    @Test
    void adds50RoomsAcross10TypesWithExistingImages() {
        List<Room> rooms = runWith(sampleTypes());

        assertThat(rooms).hasSize(50);
        assertThat(rooms).extracting(Room::getRoomNumber).doesNotHaveDuplicates();
        Map<String, Long> perType = rooms.stream()
                .collect(Collectors.groupingBy(r -> r.getRoomType().getNameEn(), Collectors.counting()));
        assertThat(perType).hasSize(10);
        verify(roomTypeRepository, times(6)).save(any(RoomType.class));

        for (Room room : rooms) {
            assertThat(room.getImages()).isNotEmpty().allMatch(i -> i.getRoom() == room);
            for (RoomImage image : room.getImages()) {
                assertThat(new ClassPathResource("static" + image.getImageUrl()).exists())
                        .as(image.getImageUrl()).isTrue();
            }
            assertThat(room.getPrice()).isGreaterThanOrEqualTo(room.getRoomType().getBasePrice());
        }
        // Cung hang nhung view / anh dai dien khac nhau
        Map<String, Room> byNumber = rooms.stream().collect(Collectors.toMap(Room::getRoomNumber, Function.identity()));
        assertThat(byNumber.get("401").getDescription()).isNotEqualTo(byNumber.get("402").getDescription());
        assertThat(byNumber.get("401").getCoverImageUrl()).isNotEqualTo(byNumber.get("402").getCoverImageUrl());
    }

    @Test
    void rerun_skipsExistingRoomsAndReusesTypes() {
        List<RoomType> types = sampleTypes();
        types.add(RoomType.builder().id(9L).name("Bungalow").nameEn("Beachfront Bungalow")
                .basePrice(BigDecimal.valueOf(3_200_000)).maxGuests(3).description("x").descriptionEn("x").build());
        when(roomRepository.existsByRoomNumber(anyString())).thenAnswer(inv -> !inv.getArgument(0, String.class).equals("B02"));

        List<Room> rooms = runWith(types);

        assertThat(rooms).extracting(Room::getRoomNumber).containsExactly("B02");
        assertThat(rooms.get(0).getRoomType().getId()).isEqualTo(9L);
        verify(roomTypeRepository, times(5)).save(any(RoomType.class));
    }

    @Test
    void renamedSampleType_isNotRecreated() {
        List<RoomType> types = sampleTypes();
        types.removeIf(t -> t.getNameEn().equals("Sunset Suite"));

        List<Room> rooms = runWith(types);

        assertThat(rooms).hasSize(49).noneMatch(r -> r.getRoomNumber().equals("502"));
    }
}
