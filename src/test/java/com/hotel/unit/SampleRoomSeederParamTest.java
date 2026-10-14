package com.hotel.unit;

import com.hotel.config.SampleRoomSeeder;
import com.hotel.entity.Room;
import com.hotel.entity.RoomImage;
import com.hotel.entity.RoomType;
import com.hotel.repository.ComboRepository;
import com.hotel.repository.DivingTourRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTypeRepository;
import com.hotel.tc.TcSteps;
import com.hotel.tc.TcSuite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TcSuite(level = "UT", module = "Phòng, Loại phòng, Combo & Mã giảm giá")
class SampleRoomSeederParamTest {

    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ComboRepository comboRepository;
    @Mock private DivingTourRepository divingTourRepository;

    private SampleRoomSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new SampleRoomSeeder(roomTypeRepository, roomRepository, comboRepository, divingTourRepository);
        when(divingTourRepository.count()).thenReturn(3L);
    }

    private Room roomWith(String... urls) {
        Room room = Room.builder().roomNumber("101").roomType(new RoomType()).build();
        Arrays.stream(urls).forEach(u -> room.getImages().add(RoomImage.builder().room(room).imageUrl(u).build()));
        return room;
    }

    @TcSteps("DB đã có dữ liệu mẫu cũ (mỗi phòng 1 ảnh), khởi động lại app với app.seed-sample-rooms=true")
    @ParameterizedTest(name = "Nâng cấp bộ ảnh phòng mẫu - ảnh cũ {0} ¦ room.images = [{0}] ¦ {1} ảnh, ảnh đầu = {2}")
    @CsvSource({"anhphonggiuongdoi.jpg,4,rooms/standard-1.jpg", "viewcuasogiuongdoi.jpg,5,rooms/deluxe-1.jpg",
            "anhphonggiuongdoi2.jpg,5,rooms/family-1.jpg", "anhphongVIP.jpg,6,rooms/suite-1.jpg"})
    void upgradesLegacySampleImages(String legacy, int count, String first) {
        Room room = roomWith("/page-images/" + legacy);
        when(roomTypeRepository.count()).thenReturn(4L);
        when(roomRepository.findAll()).thenReturn(List.of(room));

        seeder.run();

        assertThat(room.getImages()).hasSize(count).allMatch(i -> i.getRoom() == room);
        assertThat(room.getImages().get(0).getImageUrl()).isEqualTo("/page-images/" + first);
        verify(roomRepository).save(room);
    }

    @TcSteps("Phòng đã được admin đổi ảnh (ảnh upload hoặc nhiều ảnh), khởi động lại app")
    @ParameterizedTest(name = "Không đụng tới ảnh admin đã đổi - {0} ¦ room.images = {0} ¦ giữ nguyên, không save")
    @CsvSource(value = {"/uploads/rooms/abc.jpg", "/page-images/anhphongVIP.jpg;/uploads/rooms/x.jpg"})
    void keepsCustomImages(String urls) {
        Room room = roomWith(urls.split(";"));
        when(roomTypeRepository.count()).thenReturn(4L);
        when(roomRepository.findAll()).thenReturn(List.of(room));

        seeder.run();

        assertThat(room.getImages()).extracting(RoomImage::getImageUrl).containsExactly(urls.split(";"));
        verify(roomRepository, never()).save(any());
    }

    @TcSteps("DB trống, chạy seeder tạo dữ liệu mẫu")
    @ParameterizedTest(name = "Phòng mẫu {0} có bộ {1} ảnh ¦ seed lần đầu ¦ ảnh = rooms/{2}-1..{1}")
    @CsvSource({"101,4,standard", "201,5,deluxe", "301,5,family", "501,6,suite"})
    void freshSeedHasGalleries(String number, int count, String name) {
        when(roomTypeRepository.count()).thenReturn(0L);
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Room> saved = ArgumentCaptor.forClass(Room.class);

        seeder.run();

        verify(roomRepository, atLeastOnce()).save(saved.capture());
        Room room = saved.getAllValues().stream().filter(r -> r.getRoomNumber().equals(number)).findFirst().orElseThrow();
        assertThat(room.getImages()).extracting(RoomImage::getImageUrl)
                .hasSize(count).first().isEqualTo("/page-images/rooms/" + name + "-1.jpg");
    }
}
