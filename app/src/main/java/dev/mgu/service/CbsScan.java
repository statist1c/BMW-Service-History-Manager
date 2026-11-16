package dev.mgu.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Read-only cluster scan. Busy retries are deliberately scoped to service 22 here. */
public final class CbsScan {
  public interface Reader {
    byte[] read(int target, byte[] request) throws IOException;
  }

  public interface Pause {
    void waitMs(long delay) throws InterruptedException;
  }

  public static final class Result {
    public final List<Cbs> items = new ArrayList<>();
    public boolean complete;
    public int attempted;
    public String issue = "";

    public String summary() {
      return (complete ? "CBS scan complete" : "CBS scan INCOMPLETE")
          + " · "
          + items.size()
          + " populated slots · "
          + attempted
          + " / 127 slots attempted"
          + (issue.isEmpty()
              ? ""
              : "\n" + issue + "\nPartial results retained. Read CBS again to refresh.");
    }
  }

  public static Result read(Reader reader, Pause pause, HeadUnitInfo.Output log)
      throws InterruptedException {
    Result result = new Result();
    for (int slot = 1; slot <= 127; slot++) {
      result.attempted = slot;
      byte[] request = {0x22, 0x41, (byte) slot};
      try {
        byte[] raw = null;
        for (int attempt = 0; attempt < 3; attempt++) {
          log.accept("CBS TX 60: " + ServiceRecord.hex(request));
          try {
            raw = reader.read(0x60, request);
            break;
          } catch (Hsfz.Negative e) {
            log.accept(String.format(Locale.ROOT, "CBS slot %02X RX: 7F 22 %02X", slot, e.nrc));
            if (e.nrc == 0x21 && attempt < 2) {
              long delay = 300L * (attempt + 1);
              log.accept(
                  "Cluster busy · retrying same read in "
                      + delay
                      + " ms ("
                      + (attempt + 1)
                      + "/2)");
              pause.waitMs(delay);
            } else throw e;
          }
        }
        log.accept("CBS RX: " + ServiceRecord.hex(raw));
        if (raw.length < 3 || raw[0] != 0x62 || raw[1] != 0x41 || raw[2] != (byte) slot)
          throw new IOException("Unexpected CBS response identifier");
        byte[] data = Arrays.copyOfRange(raw, 3, raw.length);
        Cbs c = Cbs.decode(data);
        if (c != null) {
          if (c.code != slot) throw new IOException("CBS payload identifier does not match slot");
          result.items.add(c);
        } else if (data.length < 14) throw new IOException("Truncated CBS payload");
      } catch (Hsfz.Negative e) {
        if (e.nrc != 0x31 && e.nrc != 0x11 && e.nrc != 0x12) {
          result.issue =
              String.format(Locale.ROOT, "Stopped at slot %02X: %s", slot, e.getMessage());
          log.accept(result.summary());
          return result;
        }
      } catch (IOException e) {
        result.issue = String.format(Locale.ROOT, "Stopped at slot %02X: %s", slot, e.getMessage());
        log.accept(result.summary());
        return result;
      }
      if (slot < 127) pause.waitMs(75);
    }
    result.complete = true;
    log.accept(result.summary());
    return result;
  }
}
