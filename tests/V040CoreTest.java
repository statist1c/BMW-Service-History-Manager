package dev.mgu.service;

import java.time.LocalDate;
import java.util.*;

/** Independent boundary checks: no vehicle, imported runtime or proprietary assets. */
public final class V040CoreTest {
  static int checks;

  static void ok(boolean b) {
    checks++;
    if (!b) throw new AssertionError("Check " + checks);
  }

  interface Action {
    void run() throws Exception;
  }

  static void reject(Action action) throws Exception {
    boolean failed = false;
    try {
      action.run();
    } catch (Exception e) {
      failed = true;
    }
    ok(failed);
  }

  static ServiceRecord record() {
    ServiceRecord r = new ServiceRecord();
    r.date = LocalDate.of(2024, 2, 29);
    r.dealer = "00181";
    r.items.add(new ServiceRecord.Item(1));
    return r;
  }

  public static void main(String[] args) throws Exception {
    ServiceRecord r = record();
    for (int value : new int[] {Integer.MIN_VALUE, -65536, -256, -1, 0, Integer.MAX_VALUE}) {
      r.items.get(0).distance = value;
      byte[] bytes = r.encode();
      ok(java.nio.ByteBuffer.wrap(bytes, 21, 4).getInt() == value);
      ok(ServiceRecord.fromArgs(r.args()).items.get(0).distance == value);
    }
    ok(ServiceRecord.signedDistance(4294967295L) == -1);
    ok(ServiceRecord.signedDistance(2147483648L) == Integer.MIN_VALUE);
    r.items.get(0).distance = 2147483648L;
    reject(r::validate);
    r.items.get(0).distance = (long) Integer.MIN_VALUE - 1;
    reject(r::validate);
    r.items.get(0).distance = 0;
    ok(RecordChecks.issues(Collections.singletonList(r)).isEmpty());
    reject(() -> RecordChecks.requireWritable(Arrays.asList(r, r)));
    reject(
        () ->
            RecordChecks.requireUnambiguousDates(
                Collections.singletonList(r), Arrays.asList(r, r)));
    r.date = LocalDate.now().plusDays(1);
    reject(() -> RecordChecks.requireWritable(Collections.singletonList(r)));
    r.date = LocalDate.of(2024, 2, 29);
    r.items.get(0).code = 254;
    r.validate();
    reject(() -> RecordChecks.requireWritable(Collections.singletonList(r)));
    ok(RecordChecks.vin("WBA12345678901234"));
    ok(!RecordChecks.vin("00000000000000000"));
    ok(!RecordChecks.vin("WBA1234567890I234"));
    for (String variant : new String[] {"0F25B0", "0F2E60", "0F2E50"}) {
      HeadUnitInfo.Result result = new HeadUnitInfo.Result("", 2, variant, new byte[] {1, 2});
      HeadUnitInfo.requireMatch(result, "MGU · experimental");
      ok(true);
      reject(() -> HeadUnitInfo.requireMatch(result, "NBT"));
    }
    reject(
        () ->
            HeadUnitInfo.requireMatch(
                new HeadUnitInfo.Result("", 1, "0F25B0", new byte[0]), "MGU · experimental"));
    reject(
        () ->
            HeadUnitInfo.requireMatch(
                new HeadUnitInfo.Result("", 2, "010203", new byte[] {1}), "MGU · experimental"));
    ok(HeadUnitInfo.variant(new byte[] {0x0f, 0x25, (byte) 0xb0}).equals("0F25B0"));
    ok(HeadUnitInfo.variant(new byte[] {0, 0}).isEmpty());
    ok(HeadUnitInfo.variant(new byte[] {-1, -1, -1}).isEmpty());
    System.out.println(
        "PASS: "
            + checks
            + " v0.4.0 core checks (signed distance, record guards, exact variant matching).");
  }
}
