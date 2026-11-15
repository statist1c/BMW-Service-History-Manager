package dev.mgu.service;

import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Standalone tests: javac -d work/test-classes
 * app/src/main/java/dev/mgu/service/{ServiceRecord,Hsfz,Cbs}.java tests/CoreTest.java
 */
public final class CoreTest {
  static int checks;

  static void eq(Object a, Object b) {
    checks++;
    if (!Objects.equals(a, b)) throw new AssertionError(a + " != " + b);
  }

  static void reject(Runnable r) {
    checks++;
    try {
      r.run();
    } catch (IllegalArgumentException e) {
      return;
    }
    throw new AssertionError("Expected invalid input rejection");
  }

  static ServiceRecord fixture() {
    ServiceRecord r = new ServiceRecord();
    r.date = LocalDate.of(2026, 10, 13);
    r.mileage = 85000;
    r.bmw = true;
    r.dealer = "1234567";
    r.items.add(new ServiceRecord.Item(1));
    ServiceRecord.Item i = new ServiceRecord.Item(3);
    i.status = 2;
    r.items.add(i);
    return r;
  }

  static byte[] hex(String s) {
    s = s.replace(" ", "");
    byte[] b = new byte[s.length() / 2];
    for (int i = 0; i < b.length; i++)
      b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    return b;
  }

  static void frame(DataOutputStream out, int control, byte[] payload) throws Exception {
    out.writeInt(payload.length);
    out.writeShort(control);
    out.write(payload);
    out.flush();
  }

  static void transport(boolean badEcho) throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ExecutorService e = Executors.newSingleThreadExecutor();
      Future<?> f =
          e.submit(
              () -> {
                try (Socket s = server.accept()) {
                  DataInputStream in = new DataInputStream(s.getInputStream());
                  DataOutputStream out = new DataOutputStream(s.getOutputStream());
                  int n = in.readInt();
                  eq(in.readUnsignedShort(), 1);
                  byte[] request = new byte[n];
                  in.readFully(request);
                  eq(ServiceRecord.hex(request), "f4 63 31 01 a0 81");
                  frame(out, 2, new byte[0]);
                  frame(out, 0x12, new byte[0]);
                  eq(in.readInt(), 2);
                  eq(in.readUnsignedShort(), 0x13);
                  eq(in.readUnsignedByte(), 0);
                  eq(in.readUnsignedByte(), 244);
                  frame(out, 1, hex("63 f4 7f 31 78"));
                  frame(out, 1, hex(badEcho ? "63 f4 71 01 a0 80 01" : "63 f4 71 01 a0 81 01"));
                } catch (Exception ex) {
                  throw new RuntimeException(ex);
                }
              });
      try (Hsfz h = new Hsfz(new Socket(), "127.0.0.1", server.getLocalPort())) {
        if (badEcho) {
          try {
            h.request(0x63, hex("31 01 a0 81"));
            throw new AssertionError("Echo accepted");
          } catch (IOException expected) {
            checks++;
          }
        } else eq(ServiceRecord.hex(h.request(0x63, hex("31 01 a0 81"))), "71 01 a0 81 01");
      }
      f.get(5, TimeUnit.SECONDS);
      e.shutdownNow();
    }
  }

  public static void main(String[] args) throws Exception {
    ServiceRecord r = fixture();
    eq(
        ServiceRecord.hex(r.encode()),
        ServiceRecord.hex(
            hex(
                "0d 0a 07ea 00 00014c08 01 31323334353637 01 02 01 01 00000000 0000 03 02 00000000"
                    + " 0000")));
    eq(r.encode().length, 35);
    eq(r.args(), "13;10;2026;0;85000;1;1234567;0x1;2;1;0x1;0;0;3;0x2;0;0");
    eq(ServiceRecord.fromArgs(r.args()).args(), r.args());
    String tst = ServiceRecord.tst(Arrays.asList(r), true);
    ServiceRecord.Imported parsed = ServiceRecord.parseTst(tst);
    eq(parsed.erase, true);
    eq(parsed.records.get(0).args(), r.args());
    eq(
        ServiceRecord.fromArgs("08;03;2019;0;151690;0;00181;0x1;1;1;0x1;0;0").date,
        LocalDate.of(2019, 3, 8));
    r.dealer = "181";
    eq(ServiceRecord.hex(Arrays.copyOfRange(r.encode(), 10, 17)), "31 38 31 00 00 00 00");
    r.mileage = 0xffffffffL;
    r.items.get(0).distance = 0xffffffffL;
    r.items.get(0).months = 65535;
    eq(ServiceRecord.hex(Arrays.copyOfRange(r.encode(), 21, 27)), "ff ff ff ff ff ff");
    r.dealer = "12345678";
    reject(r::validate);
    r.dealer = "é";
    reject(r::validate);
    r.dealer = "181";
    r.status = 4;
    reject(r::validate);
    r.status = 1;
    r.items.get(0).distance = -1;
    reject(r::validate);
    r.items.get(0).distance = 0;
    r.items.clear();
    reject(r::validate);
    reject(() -> ServiceRecord.parseTst("1|g_mmi|steuern_servicehistory_add||1;2"));
    Cbs c = Cbs.decode(hex("0001 00 06 000f 000c fed4 0019 000c"));
    eq(c.code, 1);
    eq(c.remainingKm, -3000);
    eq(c.due, "12/2025");
    eq(c.intervalKm, 15000);
    eq(c.resets, 6);
    eq(Cbs.owner(1), 0x12);
    eq(Cbs.owner(2), 0x29);
    eq(Cbs.owner(3), 0x60);
    eq(ServiceRecord.hex(Cbs.reset(1)), "2e 10 01 01 01 64 1f 80 00 0f ff 0f 3f ff 00");
    eq(Cbs.decode(new byte[5]), null);
    eq(Cbs.decode(hex("ffff ffff ffff ffff ffff ffff ffff")), null);
    transport(false);
    transport(true);
    System.out.println(
        "PASS: "
            + checks
            + " core checks (upstream record fixture, TST, CBS, HSFZ ACK/alive/pending/echo).");
  }
}
