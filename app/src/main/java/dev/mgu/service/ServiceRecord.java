package dev.mgu.service;

import java.io.*;
import java.time.LocalDate;
import java.util.*;

/** Android port of devttys1/bmw-service-manager records.py. See LICENSE.md. */
public final class ServiceRecord {
  public static final int[] CODES = {1, 2, 3, 4, 5, 6, 10, 11, 12, 100, 20, 21, 32, 33};
  public static final String[] NAMES = {
    "Engine oil",
    "Front brakes",
    "Brake fluid",
    "Micro-filter",
    "Recirculating air filter",
    "Rear brakes",
    "Spark plugs",
    "Air filter element",
    "Fuel filter",
    "Vehicle check",
    "Pre-delivery inspection",
    "Running-in check",
    "Statutory vehicle inspection",
    "Statutory emissions test"
  };
  public LocalDate date = LocalDate.now();
  public long mileage;
  public String localId = UUID.randomUUID().toString();
  public boolean bmw;
  public String dealer = "";
  public int status = 1, correction;
  public final List<Item> items = new ArrayList<>();

  public static final class Item {
    public int code, status = 1, months;
    public long distance;

    public Item(int c) {
      code = c;
    }
  }

  public static String name(int code) {
    for (int i = 0; i < CODES.length; i++) if (CODES[i] == code) return NAMES[i];
    return "Unknown (" + code + ")";
  }

  static void range(long n, long max, String field) {
    if (n < 0 || n > max) throw new IllegalArgumentException(field + " must be 0–" + max);
  }

  public void validate() {
    if (localId == null
        || !localId.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new IllegalArgumentException("Invalid local draft identity");
    if (date == null || date.getYear() < 1 || date.getYear() > 65535)
      throw new IllegalArgumentException("Invalid date");
    range(mileage, 0xffffffffL, "Mileage");
    range(correction, 255, "Correction");
    if (dealer.length() > 7 || !dealer.chars().allMatch(c -> c >= 32 && c <= 126))
      throw new IllegalArgumentException("Dealer must be at most 7 printable ASCII characters");
    if (status < 1 || status > 3) throw new IllegalArgumentException("Record status must be 1–3");
    if (items.size() < 1 || items.size() > 13)
      throw new IllegalArgumentException("Select 1–13 service items");
    for (Item i : items) {
      range(i.code, 255, "Item code");
      if (i.status < 1 || i.status > 3)
        throw new IllegalArgumentException("Item status must be 1–3");
      if (i.distance < Integer.MIN_VALUE || i.distance > Integer.MAX_VALUE)
        throw new IllegalArgumentException("Remaining distance must fit a signed 32-bit value");
      range(i.months, 65535, "Remaining months");
    }
  }

  public byte[] encode() {
    validate();
    try {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      DataOutputStream o = new DataOutputStream(b);
      o.writeByte(date.getDayOfMonth());
      o.writeByte(date.getMonthValue());
      o.writeShort(date.getYear());
      o.writeByte(correction);
      o.writeInt((int) mileage);
      o.writeByte(bmw ? 1 : 0);
      for (int i = 0; i < 7; i++) o.writeByte(i < dealer.length() ? dealer.charAt(i) : 0);
      o.writeByte(status);
      o.writeByte(items.size());
      for (Item i : items) {
        o.writeByte(i.code);
        o.writeByte(i.status);
        o.writeInt((int) i.distance);
        o.writeShort(i.months);
      }
      return b.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public String args() {
    validate();
    StringBuilder s =
        new StringBuilder(
            date.getDayOfMonth()
                + ";"
                + date.getMonthValue()
                + ";"
                + date.getYear()
                + ";"
                + correction
                + ";"
                + mileage
                + ";"
                + (bmw ? 1 : 0)
                + ";"
                + dealer
                + ";0x"
                + status
                + ";"
                + items.size());
    for (Item i : items)
      s.append(";")
          .append(i.code)
          .append(";0x")
          .append(i.status)
          .append(";")
          .append(i.distance)
          .append(";")
          .append(i.months);
    return s.toString();
  }

  static long number(String s) {
    s = s.trim();
    return s.startsWith("0x") || s.startsWith("0X")
        ? Long.parseLong(s.substring(2), 16)
        : Long.parseLong(s);
  }

  public static ServiceRecord fromArgs(String text) {
    String[] p = text.trim().split(";", -1);
    if (p.length < 9) throw new IllegalArgumentException("Too few TST arguments");
    ServiceRecord r = new ServiceRecord();
    r.date =
        LocalDate.of(
            Math.toIntExact(number(p[2])),
            Math.toIntExact(number(p[1])),
            Math.toIntExact(number(p[0])));
    r.correction = Math.toIntExact(number(p[3]));
    r.mileage = number(p[4]);
    r.bmw = number(p[5]) == 1;
    r.dealer = p[6].trim();
    r.status = Math.toIntExact(number(p[7]));
    long count = number(p[8]);
    if (count < 1 || count > 13) throw new IllegalArgumentException("TST item count must be 1–13");
    for (int i = 0; i < count; i++) {
      int b = 9 + i * 4;
      if (b >= p.length) throw new IllegalArgumentException("Truncated TST item");
      Item it = new Item(Math.toIntExact(number(p[b])));
      if (b + 1 < p.length) it.status = Math.toIntExact(number(p[b + 1]));
      if (b + 2 < p.length) it.distance = signedDistance(number(p[b + 2]));
      if (b + 3 < p.length) it.months = Math.toIntExact(number(p[b + 3]));
      r.items.add(it);
    }
    r.validate();
    return r;
  }

  /**
   * Strict editor validation; the legacy TST importer retains upstream's optional-field tolerance.
   */
  public static ServiceRecord fromEditorArgs(String text) {
    String args = text.trim();
    if (args.endsWith(";")) args = args.substring(0, args.length() - 1);
    String[] fields = args.split(";", -1);
    if (fields.length < 9)
      throw new IllegalArgumentException("A record needs its 9 header fields and service items");
    long count = number(fields[8]);
    if (count < 1 || count > 13 || fields.length != 9 + count * 4)
      throw new IllegalArgumentException(
          "Item count must match exactly 4 fields per item (1–13 items)");
    long dealerFlag = number(fields[5]);
    if (dealerFlag != 0 && dealerFlag != 1)
      throw new IllegalArgumentException("BMW-dealer flag must be 0 or 1");
    return fromArgs(args);
  }

  public static long signedDistance(long value) {
    if (value >= 0x80000000L && value <= 0xffffffffL) return (int) value;
    return value;
  }

  public static final class Imported {
    public final List<ServiceRecord> records = new ArrayList<>();
    public boolean erase;
  }

  public static Imported parseTst(String text) {
    Imported out = new Imported();
    for (String line : text.split("\\r?\\n")) {
      String[] c = line.trim().split("\\|", -1);
      if (c.length < 3) continue;
      if (c[2].trim().equalsIgnoreCase("steuern_servicehistory_erase")) out.erase = true;
      if (c[2].trim().equalsIgnoreCase("steuern_servicehistory_add")) {
        if (c.length < 5) throw new IllegalArgumentException("Missing TST record arguments");
        out.records.add(fromArgs(c[4]));
        if (out.records.size() > 2000) throw new IllegalArgumentException("Maximum 2000 records");
      }
    }
    if (out.records.isEmpty()) throw new IllegalArgumentException("No service records in TST file");
    return out;
  }

  public static String tst(List<ServiceRecord> records, boolean erase) {
    StringBuilder s = new StringBuilder();
    int n = 1;
    if (erase) s.append(n++).append("|g_mmi|steuern_servicehistory_erase\r\n");
    for (ServiceRecord r : records)
      s.append(n++).append("|g_mmi|steuern_servicehistory_add||").append(r.args()).append("\r\n");
    return s.toString();
  }

  public static byte[] add(ServiceRecord r) {
    byte[] b = r.encode(), out = new byte[b.length + 4];
    out[0] = 0x31;
    out[1] = 1;
    out[2] = (byte) 0xa0;
    out[3] = (byte) 0x81;
    System.arraycopy(b, 0, out, 4, b.length);
    return out;
  }

  public static String hex(byte[] b) {
    StringBuilder s = new StringBuilder();
    for (byte v : b) {
      if (s.length() > 0) s.append(' ');
      s.append(String.format(Locale.ROOT, "%02x", v & 255));
    }
    return s.toString();
  }
}
