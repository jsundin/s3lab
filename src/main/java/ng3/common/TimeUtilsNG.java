package ng3.common;

import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class TimeUtilsNG {
  private ZonedDateTime zdt;

  private TimeUtilsNG(ZonedDateTime zdt) {
    this.zdt = zdt;
  }

  public static TimeUtilsNG at(ZonedDateTime zdt) {
    return new TimeUtilsNG(zdt);
  }

  public static TimeUtilsNG at(long millis) {
    return at(millis, ZoneId.systemDefault());
  }

  public static TimeUtilsNG at(long millis, ZoneId zoneId) {
    return new TimeUtilsNG(Instant.ofEpochMilli(millis).atZone(zoneId));
  }

  public static TimeUtilsNG at(FileTime fileTime) {
    return at(fileTime, ZoneId.systemDefault());
  }

  public static TimeUtilsNG at(FileTime fileTime, ZoneId zoneId) {
    return new TimeUtilsNG(fileTime.toInstant().atZone(zoneId));
  }

  public static TimeUtilsNG at(Timestamp timestamp) {
    return at(timestamp, ZoneId.systemDefault());
  }

  public static TimeUtilsNG at(Timestamp timestamp, ZoneId zoneId) {
    return new TimeUtilsNG(timestamp.toLocalDateTime().atZone(zoneId));
  }

  public static TimeUtilsNG at(String isoString) {
    return new TimeUtilsNG(ZonedDateTime.from(DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(isoString)));
  }

  public TimeUtilsNG to(ZoneId zoneId) {
    return new TimeUtilsNG(zdt.withZoneSameInstant(zoneId));
  }

  public ZonedDateTime toZonedDateTime() {
    return zdt;
  }

  public long toEpochMilli() {
    return zdt.toInstant().toEpochMilli();
  }

  public Timestamp toTimestamp() {
    return Timestamp.valueOf(zdt.toLocalDateTime());
  }

  public Date toDate() {
    return Date.from(zdt.toInstant());
  }

  public String toISOString() {
    return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(zdt);
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

  public static void main(String[] args) {
    TimeUtilsNG tu = TimeUtilsNG
            .at(ZonedDateTime.now())
            .to(ZoneOffset.UTC);

    System.out.println("ZDT:  " + tu.toZonedDateTime());
    System.out.println("TS:   " + tu.toTimestamp());
    System.out.println("MILLIS:  " + tu.toEpochMilli());
    String str = tu.toISOString();
    System.out.println("STR:  " + str);
    System.out.println("FROM STR: " + TimeUtilsNG.at(str).to(ZoneOffset.ofHours(1)).toZonedDateTime());
  }
}
