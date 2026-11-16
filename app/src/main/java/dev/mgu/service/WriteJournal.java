package dev.mgu.service;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.json.*;

/** Write-ahead operation evidence. A pending send is uncertain after a process stop. */
final class WriteJournal {
  final File file;
  final JSONObject data;

  interface Sender {
    byte[] send(byte[] request) throws Exception;
  }

  private WriteJournal(File file, JSONObject data) {
    this.file = file;
    this.data = data;
  }

  static String fingerprint(ServiceRecord record) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(record.encode());
    return ServiceRecord.hex(digest).replace(" ", "");
  }

  static File[] files(File root) throws IOException {
    if (!root.exists()) return new File[0];
    File[] files =
        root.listFiles((d, name) -> name.endsWith(".json") || name.endsWith(".json.bak"));
    if (files == null) throw new IOException("Cannot read operation journals");
    TreeMap<String, File> unique = new TreeMap<>();
    for (File f : files) {
      String name = f.getName().replaceFirst("\\.bak$", "");
      unique.put(name, new File(root, name));
    }
    return unique.values().toArray(new File[0]);
  }

  static void guard(File root, String vin, List<ServiceRecord> records) throws Exception {
    for (File file : files(root)) {
      JSONObject op = WorkspaceStore.read(file);
      if (op.optInt("schema") != 1 || !RecordChecks.vin(op.optString("vin")))
        throw new IOException("Invalid operation journal; inspect stored evidence before writing");
      if (!vin.equals(op.getString("vin"))) continue;
      JSONArray steps = op.getJSONArray("steps");
      for (int n = 0; n < steps.length(); n++) {
        JSONObject step = steps.getJSONObject(n);
        String state = step.getString("state");
        if (!Arrays.asList("unattempted", "sending", "accepted", "rejected", "uncertain")
                .contains(state)
            || !Arrays.asList("add", "erase", "restart").contains(step.getString("kind")))
          throw new IOException("Invalid journal step; duplicate protection cannot be checked");
        if (!step.getString("kind").equals("add")) continue;
        if (!state.equals("sending") && !state.equals("accepted") && !state.equals("uncertain"))
          continue;
        for (ServiceRecord record : records)
          if (step.optString("draft_id").equals(record.localId)
              || step.getString("date").equals(record.date.toString())
              || step.getString("fingerprint").equals(fingerprint(record)))
            throw new IOException(
                "A record on "
                    + record.date
                    + " was accepted or may already have been sent. Review the persistent write"
                    + " journal and iDrive before a new action. No automatic replay is allowed.");
      }
    }
  }

  static WriteJournal begin(
      File root,
      String vin,
      String profile,
      String variant,
      String software,
      List<ServiceRecord> records,
      boolean erase,
      boolean restart)
      throws Exception {
    if (!RecordChecks.vin(vin)) throw new IOException("A VIN-bound workspace is required");
    RecordChecks.requireWritable(records);
    if (!root.isDirectory() && !root.mkdirs())
      throw new IOException("Cannot create write journal storage");
    if (files(root).length >= 2000)
      throw new IOException("Journal storage limit reached; export your operation evidence");
    if (!erase) guard(root, vin, records);
    JSONArray steps = new JSONArray();
    if (erase) steps.put(step("erase", new byte[] {0x31, 1, (byte) 0xa0, (byte) 0x80}));
    for (ServiceRecord record : records)
      steps.put(
          step("add", ServiceRecord.add(record))
              .put("date", record.date.toString())
              .put("fingerprint", fingerprint(record))
              .put("draft_id", record.localId));
    if (restart) steps.put(step("restart", new byte[] {0x11, 1}));
    JSONObject data =
        new JSONObject()
            .put("schema", 1)
            .put("created", Instant.now().toString())
            .put("vin", vin)
            .put("profile", profile)
            .put("variant", variant)
            .put("software", software)
            .put("mode", erase ? "replace-or-erase" : "append-or-restart")
            .put("vehicle_history_before", JSONObject.NULL)
            .put("local_snapshot", new JSONObject(HistoryJson.dump(records, vin)))
            .put("state", "prepared")
            .put("steps", steps);
    WriteJournal journal = new WriteJournal(new File(root, UUID.randomUUID() + ".json"), data);
    journal.persist();
    return journal;
  }

  static JSONObject step(String kind, byte[] request) throws JSONException {
    return new JSONObject()
        .put("kind", kind)
        .put("request", ServiceRecord.hex(request))
        .put("state", "unattempted");
  }

  void persist() throws Exception {
    byte[] bytes = data.toString(2).getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 8 * 1024 * 1024) throw new IOException("Journal exceeds storage limit");
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream out = null;
    try {
      out = atomic.startWrite();
      out.write(bytes);
      out.getFD().sync();
      atomic.finishWrite(out);
      if (!Arrays.equals(atomic.readFully(), bytes))
        throw new IOException("Journal commit could not be verified");
    } catch (Exception e) {
      if (out != null) atomic.failWrite(out);
      throw e;
    }
  }

  byte[] send(int index, Sender sender, String profile) throws Exception {
    JSONObject step = data.getJSONArray("steps").getJSONObject(index);
    if (!step.getString("state").equals("unattempted"))
      throw new IOException("This step cannot be replayed");
    byte[] request = decode(step.getString("request"));
    step.put("state", "sending").put("sent_at", Instant.now().toString());
    persist(); // Failure here prevents the send. A crash after this point is treated as uncertain.
    byte[] response;
    try {
      response = sender.send(request);
    } catch (Exception e) {
      if (e instanceof Hsfz.Negative)
        step.put(
            "response",
            ServiceRecord.hex(new byte[] {0x7f, request[0], (byte) ((Hsfz.Negative) e).nrc}));
      step.put("state", e instanceof Hsfz.Negative ? "rejected" : "uncertain")
          .put("detail", e.toString());
      persist();
      throw e;
    }
    step.put("response", ServiceRecord.hex(response));
    try {
      if (request[0] == 0x31) {
        if (response.length != 5
            || response[0] != 0x71
            || response[1] != request[1]
            || response[2] != request[2]
            || response[3] != request[3])
          throw new IOException("Invalid routine echo/status; outcome is uncertain");
        HeadUnitProtocol.requireSuccess(response, profile);
      } else if (!Arrays.equals(response, new byte[] {0x51, 1}))
        throw new IOException("Restart was not response-confirmed");
    } catch (Exception e) {
      boolean rejected =
          response.length == 5
              && response[0] == 0x71
              && response[1] == request[1]
              && response[2] == request[2]
              && response[3] == request[3]
              && (response[4] == 1 || response[4] == 2 || response[4] == 4);
      step.put("state", rejected ? "rejected" : "uncertain").put("detail", e.toString());
      persist();
      throw e;
    }
    step.put("state", "accepted");
    persist();
    return response;
  }

  void finish(boolean complete, String detail) throws Exception {
    String state = complete ? "response-confirmed" : "stopped";
    JSONArray steps = data.getJSONArray("steps");
    if (!complete)
      for (int n = 0; n < steps.length(); n++) {
        String s = steps.getJSONObject(n).getString("state");
        if (s.equals("accepted") || s.equals("sending") || s.equals("uncertain"))
          state = "partial-or-uncertain";
      }
    data.put("state", state).put("detail", detail).put("finished", Instant.now().toString());
    persist();
  }

  static byte[] decode(String hex) {
    String[] parts = hex.split(" ");
    byte[] bytes = new byte[parts.length];
    for (int n = 0; n < bytes.length; n++) bytes[n] = (byte) Integer.parseInt(parts[n], 16);
    return bytes;
  }

  static String report(File root, String vin) throws Exception {
    StringBuilder text =
        new StringBuilder(
            "Local operation evidence, not a backup or read-back of iDrive.\n"
                + "A step left in 'sending' after interruption is UNCERTAIN. Never replay it"
                + " automatically.\n\n");
    for (File file : files(root)) {
      JSONObject json = WorkspaceStore.read(file);
      if (vin.isEmpty() || vin.equals(json.optString("vin"))) {
        String entry = json.toString(2);
        if (text.length() + entry.length() > 2 * 1024 * 1024) {
          text.append(
              "Report size limit reached. Earlier journal files remain stored on this phone.\n");
          break;
        }
        text.append(entry).append("\n\n");
      }
    }
    return text.toString();
  }
}
