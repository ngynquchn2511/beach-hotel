package com.hotel.unit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Gia tri inline JS kieu /*[[...]]*/ 'mac dinh' phai dung cuoi dong (chi con ; , hoac ) phia sau).
// Thymeleaf nuot luon phan code viet tiep sau gia tri mac dinh (VD 'a'.split(';') hay x ? '' : '')
// -> sinh JS sai cu phap, ca script cua trang ngung chay (trang Gioi thieu tung trang tron vi loi nay)
class TemplateInlineJsTest {

    private static final Pattern INLINE = Pattern.compile("/\\*\\[\\[.*?]]\\*/\\s*('[^']*'|\"[^\"]*\"|\\[]|\\{}|true|false|null|-?\\d+)(.*)$");
    private static final Pattern SAFE_TAIL = Pattern.compile("[\\s;,)]*(//.*)?");

    @Test
    void inlineJsDefaults_areLastOnTheirLine() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    var m = INLINE.matcher(lines.get(i));
                    if (m.find() && !SAFE_TAIL.matcher(m.group(2)).matches()) {
                        problems.add(file + ":" + (i + 1) + "  " + lines.get(i).trim());
                    }
                }
            }
        }
        assertThat(problems).as("Tach gia tri inline ra 1 dong rieng: const x = /*[[...]]*/ '';").isEmpty();
    }
}
