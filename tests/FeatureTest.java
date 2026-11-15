package dev.mgu.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** New v0.2.0 features exercised against a read-only local gateway double. */
public final class FeatureTest {
  static int checks;

  static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  static void reject(String args) {
    boolean rejected = false;
    try {
      ServiceRecord.fromEditorArgs(args);
    } catch (Exception expected) {
      rejected = true;
    }
    check(rejected, "Invalid editor arguments accepted");
  }

  public static void main(String[] args) throws Exception {
    String valid = "29;2;2024;1;85000;1;00181;0x1;2;1;0x1;0;0;3;0x2;5000;5";
    ServiceRecord record = ServiceRecord.fromEditorArgs(valid);
    check(record.args().equals(valid), "Strict argument editor round trip");
    check(
        ServiceRecord.fromEditorArgs(valid + ";").args().equals(valid),
        "One optional final separator");
    reject(valid + ";99");
    reject(valid.replace(";2;1;0x1", ";3;1;0x1"));
    reject(valid.replace("85000;1;", "85000;2;"));
    reject(valid.replace("29;2;2024", "30;2;2024"));
    check(
        HeadUnitInfo.display(new byte[] {0, 72, 85, 95, 78, 66, 84, 50, 0}).equals("HU_NBT2"),
        "ASCII identification");
    check(
        HeadUnitInfo.display(new byte[] {1, 2}).startsWith("Binary"),
        "Binary identity is not guessed");
    HeadUnitProtocol.requireSuccess(
        new byte[] {0x71, 1, (byte) 0xa0, (byte) 0x81, 0}, "MGU · experimental");
    check(true, "MGU result zero accepted");
    boolean memoryRejected = false;
    try {
      HeadUnitProtocol.requireSuccess(
          new byte[] {0x71, 1, (byte) 0xa0, (byte) 0x81, 1}, "MGU · experimental");
    } catch (IOException e) {
      memoryRejected = e.getMessage().contains("memory");
    }
    check(memoryRejected, "MGU memory-error result must stop a write");
    HeadUnitProtocol.requireSuccess(new byte[] {0x71, 1, (byte) 0xa0, (byte) 0x81, 1}, "CIC");
    check(true, "CIC uses result one for success");
    boolean shortRejected = false;
    try {
      HeadUnitProtocol.requireSuccess(
          new byte[] {0x71, 1, (byte) 0xa0, (byte) 0x81}, "MGU · experimental");
    } catch (IOException e) {
      shortRejected = true;
    }
    check(shortRejected, "Missing routine status rejected");
    record.items.get(0).distance = 0xffffffffL;
    boolean rangeRejected = false;
    try {
      HeadUnitProtocol.validateForProfile(
          java.util.Collections.singletonList(record), "MGU · experimental");
    } catch (IOException e) {
      rangeRejected = true;
    }
    check(rangeRejected, "MGU signed-distance limit enforced");
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ExecutorService worker = Executors.newSingleThreadExecutor();
      Future<?> fake =
          worker.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  DataInputStream in = new DataInputStream(socket.getInputStream());
                  DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                  for (int n = 0; n < HeadUnitInfo.DIDS.length; n++) {
                    int size = in.readInt();
                    check(in.readUnsignedShort() == 1, "HSFZ request type");
                    byte[] p = new byte[size];
                    in.readFully(p);
                    check(
                        size == 5
                            && (p[0] & 255) == 244
                            && (p[1] & 255) == 99
                            && (p[2] & 255) == 0x22,
                        "Identification sends read requests only");
                    int did = ((p[3] & 255) << 8) | (p[4] & 255);
                    check(did == HeadUnitInfo.DIDS[n], "Expected DID order");
                    byte[] r;
                    if (n == 0) {
                      byte[] text = "HU_NBT2".getBytes(StandardCharsets.US_ASCII);
                      r = new byte[text.length + 5];
                      r[0] = 99;
                      r[1] = (byte) 244;
                      r[2] = 0x62;
                      r[3] = p[3];
                      r[4] = p[4];
                      System.arraycopy(text, 0, r, 5, text.length);
                    } else r = new byte[] {99, (byte) 244, 0x7f, 0x22, 0x31};
                    out.writeInt(r.length);
                    out.writeShort(1);
                    out.write(r);
                    out.flush();
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (Hsfz h = new Hsfz(new Socket(), "127.0.0.1", server.getLocalPort())) {
        String result = HeadUnitInfo.read(h, line -> {});
        check(result.contains("HU_NBT2"), "Returned head-unit identity displayed");
        check(
            result.contains("Returned identifiers: 1 / 7"),
            "Unsupported DIDs handled individually");
        check(result.contains("does not automatically"), "No compatibility inferred");
      }
      fake.get(5, TimeUnit.SECONDS);
      worker.shutdownNow();
    }
    System.out.println(
        "PASS: "
            + checks
            + " feature checks (strict arguments, read-only head-unit identification).");
  }
}
