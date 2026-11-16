package dev.mgu.service;

import java.io.IOException;
import java.util.Locale;

/** Inspected head-unit descriptions use zero for success and one for memory failure. */
public final class HeadUnitProtocol {
  public static void validateForProfile(java.util.List<ServiceRecord> records, String profile)
      throws IOException {
    for (ServiceRecord record : records) {
      record.validate();
      if (isMgu(profile))
        for (ServiceRecord.Item item : record.items)
          if (item.distance > Integer.MAX_VALUE)
            throw new IOException(
                "MGU remaining-distance values above signed 32-bit range are not supported. Use a"
                    + " verified value.");
    }
  }

  public static boolean isMgu(String profile) {
    return profile.startsWith("MGU");
  }

  public static void requireSuccess(byte[] response, String profile) throws IOException {
    if (response.length < 5)
      throw new IOException("Routine response has no result status; outcome is unverified");
    int status = response[4] & 255;
    if (status == 0) return;
    String meaning =
        status == 1
            ? "Out of memory / no free space"
            : status == 2
                ? "Data inconsistency"
                : status == 4
                    ? "No writing permission"
                    : status == 5 ? "Unknown ECU error" : "Unrecognized result";
    throw new IOException(
        String.format(Locale.ROOT, "%s routine result 0x%02X: %s", profile, status, meaning));
  }
}
