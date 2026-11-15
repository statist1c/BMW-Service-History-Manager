package dev.mgu.service;

import java.nio.*;
import java.util.*;

/** CBS layout ported from upstream cbs.py; real-vehicle Android validation pending. */
public final class Cbs {
  public int code, availability, resets, intervalKm, intervalMonths;
  public Integer remainingKm;
  public String due;
  public byte[] raw;

  public static Cbs decode(byte[] data) {
    if (data.length < 14 || (data[0] == -1 && data[1] == -1)) return null;
    ByteBuffer b = ByteBuffer.wrap(data);
    Cbs c = new Cbs();
    c.raw = data;
    c.code = b.getShort() & 65535;
    c.availability = b.get() & 255;
    c.resets = (b.get() & 127);
    c.intervalKm = (b.getShort() & 65535) * 1000;
    c.intervalMonths = b.getShort() & 65535;
    int dist = b.getShort(), year = b.getShort() & 65535, month = b.getShort() & 65535;
    c.remainingKm = dist == 32000 || (c.intervalKm == 0 && dist == 0) ? null : dist * 10;
    c.due =
        year > 0 && month >= 1 && month <= 12
            ? String.format(Locale.ROOT, "%02d/%d", month, 2000 + year)
            : null;
    return c;
  }

  public String name() {
    switch (code) {
      case 2:
        return "Front brake pads";
      case 5:
        return "Service Inclusive";
      case 6:
        return "Rear brake pads";
      case 13:
        return "NOx additive";
      case 17:
      case 100:
        return "Vehicle check";
      case 32:
        return "Statutory inspection";
      case 33:
        return "Emissions test";
      default:
        return ServiceRecord.name(code);
    }
  }

  public static int owner(int code) {
    return code == 1 || code == 13 ? 0x12 : code == 2 || code == 6 ? 0x29 : 0x60;
  }

  public static byte[] reset(int code) {
    return new byte[] {
      0x2e, 0x10, 1, 1, (byte) code, 100, 31, (byte) 0x80, 0, 15, (byte) 255, 15, 63, (byte) 255, 0
    };
  }
}
