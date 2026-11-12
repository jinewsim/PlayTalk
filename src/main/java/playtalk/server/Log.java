package playtalk.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 서버 콘솔 로그. */
public final class Log {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Log() {
    }

    public static void info(String format, Object... args) {
        System.out.printf("[%s] %s%n", LocalTime.now().format(FMT), String.format(format, args));
    }

    public static void error(String format, Object... args) {
        System.err.printf("[%s] ERROR %s%n", LocalTime.now().format(FMT), String.format(format, args));
    }
}
