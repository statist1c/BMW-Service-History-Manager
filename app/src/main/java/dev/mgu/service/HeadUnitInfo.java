package dev.mgu.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Read-only standardized identification DIDs; never infers head-unit compatibility. */
public final class HeadUnitInfo {
  public static final String READER_REVISION = "BMW-HU-ID/2";

  public static final class Result {
    public final String report;
    public final int supported;
    public final String variant;
    public final byte[] software;
    public final boolean complete;

    Result(String text, int count, String variant, byte[] software) {
      this(text, count, variant, software, true);
    }

    Result(String text, int count, String variant, byte[] software, boolean complete) {
      this.complete = complete;
      report = text;
      supported = count;
      this.variant = variant;
      this.software = software.clone();
    }
  }

  public static final int[] DIDS = {
    0xf150, 0xf101, 0xf197, 0xf187, 0xf188, 0xf189, 0xf18c, 0xf190, 0xf191
  };
  public static final String[] NAMES = {
    "BMW diagnostic variant",
    "BMW software fingerprint",
    "System name / engine type",
    "Manufacturer spare-part number",
    "Manufacturer ECU software number",
    "Manufacturer ECU software version",
    "ECU serial number",
    "ECU VIN",
    "Manufacturer ECU hardware number"
  };

  public interface Output {
    void accept(String line);
  }

  public static String variant(byte[] data) {
    if (data.length < 3) return "";
    String id =
        String.format(Locale.ROOT, "%02X%02X%02X", data[0] & 255, data[1] & 255, data[2] & 255);
    return id.equals("000000") || id.equals("FFFFFF") ? "" : id;
  }

  public static String family(String variant) {
    switch (variant) {
      case "0F12D0":
        return "CIC";
      case "0F1A30":
        return "NBT";
      case "0F2230":
        return "NBT Evo";
      case "0F1820":
        return "ENTRY";
      case "0F1580":
        return "CHAMP2";
      case "0F25B0":
      case "0F2E60":
      case "0F2E50":
        return "MGU · experimental";
      default:
        return "Unknown · read only";
    }
  }

  public static void requireMatch(Result result, String profile) throws java.io.IOException {
    if (result == null
        || !result.complete
        || result.variant.isEmpty()
        || result.software.length == 0)
      throw new java.io.IOException("Read exact head-unit identification (F150 and F101) first");
    if (!family(result.variant).equals(profile) || profile.startsWith("Unknown"))
      throw new java.io.IOException(
          "Selected family does not match the identified head unit (" + result.variant + ")");
  }

  public static void recheck(Hsfz client, Result expected) throws Exception {
    byte[] variant = client.request(0x63, new byte[] {0x22, (byte) 0xf1, 0x50});
    byte[] software = client.request(0x63, new byte[] {0x22, (byte) 0xf1, 1});
    if (!variant(Arrays.copyOfRange(variant, 3, variant.length)).equals(expected.variant)
        || !Arrays.equals(Arrays.copyOfRange(software, 3, software.length), expected.software))
      throw new java.io.IOException("Head-unit variant or software changed. No mutation was sent");
  }

  public static String display(byte[] data) {
    boolean printable = false;
    for (byte b : data) {
      int n = b & 255;
      if (n != 0 && n != 255 && (n < 32 || n > 126)) return "Binary value · see raw bytes";
      if (n >= 32 && n <= 126) printable = true;
    }
    if (!printable) return "Empty / unavailable value";
    return new String(data, StandardCharsets.US_ASCII)
        .replace("\u0000", "")
        .replace("\ufffd", "")
        .trim();
  }

  public static String read(Hsfz client, Output output) throws Exception {
    return readDetailed(client, output).report;
  }

  public static Result readDetailed(Hsfz client, Output output) throws Exception {
    StringBuilder result =
        new StringBuilder(
            "Reader: "
                + READER_REVISION
                + "\n"
                + "Planned DIDs: F150 F101 F197 F187 F188 F189 F18C F190 F191\n"
                + "Head-unit diagnostic address 0x63\n\n");
    int supported = 0;
    int attempted = 0;
    boolean complete = true;
    String variant = "";
    byte[] software = new byte[0];
    for (int n = 0; n < DIDS.length; n++) {
      int did = DIDS[n];
      byte[] request = {0x22, (byte) (did >> 8), (byte) did};
      String title = String.format(Locale.ROOT, "%s · 0x%04X", NAMES[n], did);
      attempted++;
      result.append("TX 63: ").append(ServiceRecord.hex(request)).append("\n");
      output.accept(title + " TX " + ServiceRecord.hex(request));
      try {
        byte[] response = client.request(0x63, request);
        byte[] value = Arrays.copyOfRange(response, 3, response.length);
        if (did == 0xf150) variant = variant(value);
        if (did == 0xf101) software = value;
        output.accept(title + " RX " + ServiceRecord.hex(response));
        result
            .append(title)
            .append("\n")
            .append(did == 0xf150 ? variant + " · " + family(variant) : display(value))
            .append("\nRX: ")
            .append(ServiceRecord.hex(response))
            .append("\nRaw: ")
            .append(ServiceRecord.hex(value))
            .append("\n\n");
        if (value.length > 0 && (did != 0xf150 || !variant.isEmpty())) supported++;
      } catch (Hsfz.Negative e) {
        result.append(title).append("\nNot available: ").append(e.getMessage()).append("\n\n");
        output.accept(title + " · " + e.getMessage());
        result.append(String.format(Locale.ROOT, "RX: 7F 22 %02X\n\n", e.nrc));
      } catch (java.io.IOException e) {
        complete = false;
        String error =
            "Scan stopped at "
                + title
                + ": "
                + e.getClass().getSimpleName()
                + ": "
                + e.getMessage();
        result.append(error).append("\nPartial results retained; no further requests sent.\n\n");
        output.accept(error);
        break;
      }
    }
    return new Result(
        result
            + "Scan: "
            + (complete ? "complete" : "INCOMPLETE")
            + " · attempted "
            + attempted
            + " / "
            + DIDS.length
            + "\n"
            + "Returned identifiers: "
            + supported
            + " / "
            + DIDS.length
            + "\n"
            + "Variant mapping is a diagnostic hint, not proof of firmware write permission or"
            + " vehicle validation. Unknown variants remain read only. F101 is retained as a raw"
            + " software fingerprint.",
        supported,
        variant,
        software,
        complete);
  }
}
