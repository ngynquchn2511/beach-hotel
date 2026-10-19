package com.hotel.config.demo;

import com.hotel.entity.Gender;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Nguoi gia lap: CCCD dung cau truc (ma tinh + gioi tinh/the ky + nam sinh), SDT 10 so, email khong trung va chi o ten mien du phong
class DemoPeopleTest {

    private final LocalDate today = LocalDate.of(2026, 10, 19);

    @Test
    void realisticAndSafePeople() {
        DemoPeople people = new DemoPeople(new Random(1));
        Set<String> emails = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            DemoPeople.Person p = people.person(today, i % 10 == 0);
            assertThat(emails.add(p.email())).isTrue();
            assertThat(p.email()).matches("[a-z0-9.]+@example\\.(com|net|org)");
            if (!p.foreigner()) {
                assertThat(p.phone()).matches("0\\d{9}");
                assertThat(p.idNumber()).hasSize(12);
                int digit = p.idNumber().charAt(3) - '0';
                assertThat(digit % 2 == 1).isEqualTo(p.gender() == Gender.FEMALE);
                assertThat(p.idNumber().substring(4, 6)).isEqualTo(String.format("%02d", p.birthDate().getYear() % 100));
                assertThat(p.fullName().split(" ").length).isBetween(3, 4);
            }
        }
    }

    @Test
    void companionsShareNationality() {
        DemoPeople people = new DemoPeople(new Random(2));
        for (int i = 0; i < 300; i++) {
            DemoPeople.Person main = people.person(today, true);
            DemoPeople.Person spouse = people.companion(main, today, false);
            DemoPeople.Person child = people.companion(main, today, true);
            assertThat(spouse.nationality()).isEqualTo(main.nationality());
            assertThat(child.nationality()).isEqualTo(main.nationality());
            assertThat(child.birthDate()).isAfter(today.minusYears(14));
        }
        DemoPeople.Person vn = people.person(today, false);
        DemoPeople.Person kid = people.companion(vn, today, true);
        assertThat(kid.idNumber()).isNotEqualTo(vn.idNumber());
    }
}
