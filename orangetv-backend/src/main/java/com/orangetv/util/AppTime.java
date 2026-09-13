package com.orangetv.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

/** 数据库的无时区日期时间统一按上海时间解释，传输时间戳保持真实的 Unix 毫秒值。 */
public final class AppTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private AppTime() {}

    public static void initializeDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static Long toEpochMillis(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toInstant().toEpochMilli();
    }
}
