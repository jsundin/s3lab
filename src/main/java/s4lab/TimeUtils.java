package s4lab;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

  private TimeUtils to(ZoneId zoneOffset) {
    zdt = zdt.withZoneSameInstant(zoneOffset);
    return this;
  }
}