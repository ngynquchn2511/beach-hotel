package com.hotel.tc;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

// Ghi ket qua TUNG test case (ke ca tung bo du lieu cua @ParameterizedTest) ra target/tc-results/results.tsv
// de sinh bang test case trong bao cao tu ket qua chay that, khong dien tay.
// Duoc dang ky tu dong cho moi lop test qua META-INF/services + junit-platform.properties.
public class TcRecorder implements TestWatcher {

    private static final Path OUT = Path.of("target", "tc-results", "results.tsv");

    @Override
    public void testSuccessful(ExtensionContext context) {
        write(context, "PASS", null);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        write(context, "FAIL", cause);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        write(context, "ABORTED", cause);
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        write(context, "DISABLED", null);
    }

    private static synchronized void write(ExtensionContext context, String status, Throwable cause) {
        Class<?> testClass = context.getRequiredTestClass();
        TcSuite suite = testClass.getAnnotation(TcSuite.class);
        String steps = context.getTestMethod()
                .map(m -> m.getAnnotation(TcSteps.class))
                .map(TcSteps::value)
                .orElse("");
        String line = String.join("\t",
                suite != null ? suite.level() : "",
                suite != null ? suite.module() : "",
                testClass.getName(),
                context.getTestMethod().map(m -> m.getName()).orElse(""),
                clean(steps),
                clean(context.getDisplayName()),
                status,
                cause != null ? clean(cause.getClass().getSimpleName() + ": " + cause.getMessage()) : "");
        try {
            Files.createDirectories(OUT.getParent());
            Files.writeString(OUT, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Khong de loi ghi file lam hong ket qua test
        }
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        return oneLine.length() > 400 ? oneLine.substring(0, 400) : oneLine;
    }
}
