package s4lab;

import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class TimeUtils {
  private ZonedDateTime zdt;

  private TimeUtils(ZonedDateTime zdt) {
    this.zdt = zdt;
  }

  public static TimeUtils at(long millis) {
    return at(millis, ZoneId.systemDefault());
  }

  public static TimeUtils at(long millis, ZoneId zoneId) {
    ZonedDateTime zdt = Instant.ofEpochMilli(millis).atZone(zoneId);
    return new TimeUtils(zdt);
  }

  public static TimeUtils at(Timestamp ts) {
    return at(ts, ZoneId.systemDefault());
  }

  public static TimeUtils at(Timestamp ts, ZoneId zoneId) {
    ZonedDateTime zdt = ts.toLocalDateTime().atZone(zoneId);
    return new TimeUtils(zdt);
  }

  public static TimeUtils at(LocalDateTime ldt) {
    return at(ldt, ZoneId.systemDefault());
  }

  public static TimeUtils at(LocalDateTime ldt, ZoneId zoneId) {
    ZonedDateTime zdt = ldt.atZone(zoneId);
    return new TimeUtils(zdt);
  }

  public static TimeUtils at(ZonedDateTime zdt) {
    return new TimeUtils(zdt);
  }

  public static TimeUtils at(ZonedDateTime zdt, ZoneId zoneId) {
    return new TimeUtils(zdt.withZoneSameLocal(zoneId));
  }

  public static TimeUtils at(FileTime fileTime) {
    return at(fileTime, ZoneId.systemDefault());
  }

  public static TimeUtils at(FileTime fileTime, ZoneId zoneId) {
    return new TimeUtils(fileTime.toInstant().atZone(zoneId));
  }

  public LocalDateTime toLocalDateTime() {
    return zdt.toLocalDateTime();
  }

  public LocalDateTime toLocalDateTime(ZoneId zoneId) {
    to(zoneId);
    return toLocalDateTime();
  }

  public ZonedDateTime toZonedDateTime() {
    return zdt;
  }

  public ZonedDateTime toZonedDateTime(ZoneId zoneId) {
    to(zoneId);
    return toZonedDateTime();
  }

  public long toEpochMilli() {
    return zdt.toInstant().toEpochMilli();
  }

  public long toEpochMilli(ZoneId zoneId) {
    to(zoneId);
    return toEpochMilli();
  }

  public Timestamp toTimestamp() {
    return Timestamp.valueOf(toLocalDateTime());
  }

  public Timestamp toTimestamp(ZoneId zoneId) {
    to(zoneId);
    return toTimestamp();
  }

  public Date toDate() {
    return Date.from(zdt.toInstant());
  }

  public Date toDate(ZoneId zoneId) {
    to(zoneId);
    return toDate();
  }

  private TimeUtils to(ZoneId zoneOffset) {
    zdt = zdt.withZoneSameInstant(zoneOffset);
    return this;
  }

  public static String formatMillis(long millis) {
    long h = millis / 1000 / 60 / 60;
    long m = millis / 1000 / 60 - (h * 60);
    long s = millis / 1000 - (m * 60) - (h * 60 * 60);
    if (h > 0) {
      return String.format("%dh%dm%ds", h, m, s);
    }
    if (m > 0) {
      return String.format("%dm%ds", m, s);
    }
    if (s > 0) {
      return String.format("%ds", s);
    }
    return String.format("%dms", millis);
  }
}
