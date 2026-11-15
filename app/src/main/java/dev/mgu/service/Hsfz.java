package dev.mgu.service;

import java.io.*;
import java.net.*;
import java.util.*;

/** Native HSFZ port of upstream hsfz.py. No automatic retries of mutations. */
public final class Hsfz implements Closeable {
  final Socket socket;
  final DataInputStream in;
  final DataOutputStream out;

  public static final class Negative extends IOException {
    public final int nrc;

    Negative(int sid, int code) {
      super(String.format(Locale.ROOT, "ECU refused service %02X: NRC %02X", sid, code));
      nrc = code;
    }
  }

  public Hsfz(Socket s, String host, int port) throws IOException {
    socket = s;
    try {
      s.connect(new InetSocketAddress(host, port), 4000);
      s.setTcpNoDelay(true);
      in = new DataInputStream(s.getInputStream());
      out = new DataOutputStream(s.getOutputStream());
    } catch (IOException e) {
      s.close();
      throw e;
    }
  }

  void send(int control, byte[] p) throws IOException {
    out.writeInt(p.length);
    out.writeShort(control);
    out.write(p);
    out.flush();
  }

  byte[] exact(int length, long deadline) throws IOException {
    byte[] data = new byte[length];
    int offset = 0;
    while (offset < length) {
      long left = deadline - System.nanoTime();
      if (left <= 0) throw new SocketTimeoutException("Gateway response deadline exceeded");
      socket.setSoTimeout((int) Math.max(1, left / 1_000_000));
      int count = in.read(data, offset, length - offset);
      if (count < 0) throw new EOFException("Gateway closed the connection");
      offset += count;
    }
    return data;
  }

  public byte[] request(int target, byte[] uds) throws IOException {
    byte[] p = new byte[uds.length + 2];
    p[0] = (byte) 0xf4;
    p[1] = (byte) target;
    System.arraycopy(uds, 0, p, 2, uds.length);
    send(1, p);
    long deadline = System.nanoTime() + 5_000_000_000L,
        absolute = System.nanoTime() + 35_000_000_000L;
    while (true) {
      long remaining = Math.min(deadline, absolute) - System.nanoTime();
      if (remaining <= 0) throw new SocketTimeoutException("Gateway response deadline exceeded");
      java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(exact(6, Math.min(deadline, absolute)));
      int length = header.getInt(), control = header.getShort() & 65535;
      if (length < 0 || length > 1048576) throw new IOException("Invalid HSFZ frame length");
      byte[] data = exact(length, Math.min(deadline, absolute));
      if (control == 2) continue;
      if (control == 0x12) {
        send(0x13, new byte[] {0, (byte) 0xf4});
        continue;
      }
      if (control >= 0x40)
        throw new IOException(String.format(Locale.ROOT, "Gateway error %04X", control));
      if (control != 1 || length < 3 || (data[0] & 255) != target || (data[1] & 255) != 0xf4)
        continue;
      byte[] r = Arrays.copyOfRange(data, 2, length);
      if ((r[0] & 255) == 0x7f && r.length >= 3) {
        if ((r[1] & 255) != (uds[0] & 255)) throw new IOException("Mismatched negative response");
        if ((r[2] & 255) == 0x78) {
          deadline = absolute;
          continue;
        }
        throw new Negative(r[1] & 255, r[2] & 255);
      }
      if ((r[0] & 255) != ((uds[0] & 255) + 0x40))
        throw new IOException("Unexpected UDS response: " + ServiceRecord.hex(r));
      int echo =
          uds[0] == 0x31
              ? 4
              : uds[0] == 0x22 || uds[0] == 0x2e ? 3 : uds[0] == 0x10 || uds[0] == 0x11 ? 2 : 1;
      if (r.length < echo) throw new IOException("Truncated positive response");
      for (int i = 1; i < echo; i++)
        if (r[i] != uds[i]) throw new IOException("Response does not match request");
      return r;
    }
  }

  public void close() throws IOException {
    socket.close();
  }

  public static String identify(DatagramSocket s, String host) throws IOException {
    return identify(s, host, false);
  }

  public static String identify(DatagramSocket s, String host, boolean broadcast)
      throws IOException {
    s.setSoTimeout(1800);
    byte[] req = {0, 0, 0, 0, 0, 0x11};
    s.send(new DatagramPacket(req, 6, InetAddress.getByName(host), 6811));
    long end = System.nanoTime() + 1_800_000_000L;
    while (System.nanoTime() < end) {
      byte[] b = new byte[4096];
      DatagramPacket p = new DatagramPacket(b, b.length);
      s.setSoTimeout((int) Math.max(1, (end - System.nanoTime()) / 1_000_000));
      s.receive(p);
      if (!broadcast && !p.getAddress().equals(InetAddress.getByName(host))) continue;
      if (p.getLength() > 6 && b[4] == 0 && b[5] == 0x11) {
        String text =
            new String(b, 6, p.getLength() - 6, java.nio.charset.StandardCharsets.US_ASCII);
        int v = text.indexOf("BMWVIN");
        return p.getAddress().getHostAddress()
            + " · "
            + (v >= 0 && text.length() >= v + 23
                ? text.substring(v + 6, v + 23)
                : "VIN unavailable");
      }
    }
    throw new SocketTimeoutException("No vehicle identity reply");
  }
}
