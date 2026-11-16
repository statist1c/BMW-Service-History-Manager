package dev.mgu.service;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public final class V041CoreTest {
  public static void main(String[] args) throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      ExecutorService worker = Executors.newSingleThreadExecutor();
      Future<?> fake =
          worker.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  DataInputStream in = new DataInputStream(socket.getInputStream());
                  DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                  for (int n = 0; n < 3; n++) {
                    int length = in.readInt();
                    if (in.readUnsignedShort() != 1 || length != 5)
                      throw new AssertionError("Unexpected request");
                    byte[] p = new byte[length];
                    in.readFully(p);
                    if (p[2] != 0x22 || (p[1] & 255) != 0x63)
                      throw new AssertionError("Only identification reads allowed");
                    if (n == 2) break;
                    byte[] value =
                        n == 0 ? new byte[] {0x0f, 0x25, (byte) 0xb0} : new byte[] {1, 2, 3};
                    byte[] response = {
                      0x63, (byte) 0xf4, 0x62, p[3], p[4], value[0], value[1], value[2]
                    };
                    out.writeInt(response.length);
                    out.writeShort(1);
                    out.write(response);
                    out.flush();
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      try (Hsfz client = new Hsfz(new Socket(), "127.0.0.1", server.getLocalPort())) {
        HeadUnitInfo.Result result = HeadUnitInfo.readDetailed(client, line -> {});
        if (result.complete) throw new AssertionError("Dropped scan marked complete");
        if (result.supported != 2 || !result.variant.equals("0F25B0"))
          throw new AssertionError("Partial results lost");
        if (!result.report.contains("INCOMPLETE")
            || !result.report.contains("attempted 3 / 9")
            || !result.report.contains("TX 63: 22 f1 50")
            || !result.report.contains("BMW-HU-ID/2")) throw new AssertionError(result.report);
        boolean blocked = false;
        try {
          HeadUnitInfo.requireMatch(result, "MGU · experimental");
        } catch (IOException expected) {
          blocked = true;
        }
        if (!blocked) throw new AssertionError("Incomplete scan permits changes");
      }
      fake.get(5, TimeUnit.SECONDS);
      worker.shutdownNow();
    }
    System.out.println("PASS: 5 interrupted-identification regression checks");
  }
}
