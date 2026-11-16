package dev.mgu.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class V042CoreTest {
  static int checks;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static byte[] response(int slot) {
    byte[] r = new byte[23];
    r[0] = 0x62;
    r[1] = 0x41;
    r[2] = (byte) slot;
    if (slot == 1) {
      byte[] data = {0, 1, 100, 10, 0, 24, 0, 24, 9, 96, 0, 28, 0, 11, 35, 3, 0, 0, 0, 0};
      System.arraycopy(data, 0, r, 3, 20);
    } else Arrays.fill(r, 3, r.length, (byte) 255);
    return r;
  }

  public static void main(String[] args) throws Exception {
    for (int mode = 0; mode < 4; mode++) {
      final int scenario = mode;
      final int[] busy = {0}, calls = {0};
      List<Long> pauses = new ArrayList<>();
      List<String> log = new ArrayList<>();
      CbsScan.Result r =
          CbsScan.read(
              (target, request) -> {
                calls[0]++;
                check(
                    target == 0x60
                        && request.length == 3
                        && request[0] == 0x22
                        && request[1] == 0x41,
                    "Read-only CBS request");
                int slot = request[2] & 255;
                if (slot == 126 && scenario <= 1 && busy[0]++ < (scenario == 0 ? 1 : 99))
                  throw new Hsfz.Negative(0x22, 0x21);
                if (slot == 2 && scenario == 2) throw new EOFException("Recorded disconnect");
                if (slot == 2 && scenario == 3) return new byte[] {0x62, 0x41, 2, 0};
                return response(slot);
              },
              pauses::add,
              log::add);
      check(
          r.items.size() == 1 && r.items.get(0).remainingKm == 24000, "Earlier CBS item retained");
      if (mode == 0) {
        check(
            r.complete && r.attempted == 127 && calls[0] == 128,
            "Busy response recovers without whole-scan retry");
        check(pauses.contains(300L), "Delayed retry");
        check(
            log.stream().anyMatch(s -> s.contains("retrying same read")), "Retry exported to log");
      } else if (mode == 1) {
        check(
            !r.complete && r.attempted == 126 && calls[0] == 128,
            "Busy retry bounded, no subsequent slot sent");
        check(pauses.contains(300L) && pauses.contains(600L), "Bounded backoff");
        check(
            r.summary().contains("INCOMPLETE") && r.summary().contains("NRC 21"),
            "Partial reason explicit");
      } else
        check(
            !r.complete && calls[0] == 2,
            "Transport or malformed response stops with partial data");
    }
    System.out.println("PASS: " + checks + " CBS busy/partial-read regression checks");
  }
}
