package com.hotel.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoomEntityTest {

    @Test
    void getCoverImageUrl_noImages_returnsNull() {
        Room room = Room.builder().id(1L).roomNumber("101").build();
        assertNull(room.getCoverImageUrl());
    }

    @Test
    void getCoverImageUrl_withImages_returnsFirstImageUrl() {
        Room room = Room.builder().id(1L).roomNumber("101").build();
        RoomImage img1 = RoomImage.builder().id(1L).room(room).imageUrl("/uploads/rooms/a.jpg").build();
        RoomImage img2 = RoomImage.builder().id(2L).room(room).imageUrl("/uploads/rooms/b.jpg").build();
        room.setImages(new java.util.ArrayList<>(List.of(img1, img2)));

        assertEquals("/uploads/rooms/a.jpg", room.getCoverImageUrl());
    }
}
