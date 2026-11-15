package dev.mgu.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Read-only standardized identification DIDs; never infers head-unit compatibility. */
public final class HeadUnitInfo {
  public static final class Result {
    public final String report;
    public final int supported;

    Result(String text, int count) {
      report = text;
      supported = count;
    }
  }

  public static final int[] DIDS = {0xf197, 0xf187, 0xf188, 0xf189, 0xf18c, 0xf190, 0xf191};
  public static final String[] NAMES = {
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
    StringBuilder result = new StringBuilder("Head-unit diagnostic address 0x63\n\n");
    int supported = 0;
    for (int n = 0; n < DIDS.length; n++) {
      int did = DIDS[n];
      byte[] request = {0x22, (byte) (did >> 8), (byte) did};
      String title = String.format(Locale.ROOT, "%s · 0x%04X", NAMES[n], did);
      output.accept(title + " TX " + ServiceRecord.hex(request));
      try {
        byte[] response = client.request(0x63, request);
        byte[] value = Arrays.copyOfRange(response, 3, response.length);
        output.accept(title + " RX " + ServiceRecord.hex(response));
        result
            .append(title)
            .append("\n")
            .append(display(value))
            .append("\nRaw: ")
            .append(ServiceRecord.hex(value))
            .append("\n\n");
        supported++;
      } catch (Hsfz.Negative e) {
        result.append(title).append("\nNot available: ").append(e.getMessage()).append("\n\n");
        output.accept(title + " · " + e.getMessage());
      }
    }
    return new Result(
        result
            + "Returned identifiers: "
            + supported
            + " / "
            + DIDS.length
            + "\n"
            + "These are raw ECU identity fields. They may be unsupported, binary or"
            + " manufacturer-specific. This does not automatically identify a supported family or"
            + " validate MGU compatibility.",
        supported);
  }
}
