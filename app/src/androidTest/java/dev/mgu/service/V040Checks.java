package dev.mgu.service;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Storage and failure-injection checks in isolated emulator directories, never a vehicle. */
final class V040Checks {
  final Instrumentation instrumentation;

  V040Checks(Instrumentation instrumentation) {
    this.instrumentation = instrumentation;
  }

  Context getTargetContext() {
    return instrumentation.getTargetContext();
  }

  void finish(int code, Bundle result) {
    instrumentation.finish(code, result);
  }

  int checks;

  interface Action {
    void run() throws Exception;
  }

  void ok(boolean b, String message) {
    checks++;
    if (!b) throw new AssertionError(message);
  }

  void rejects(Action action, String message) throws Exception {
    boolean failed = false;
    try {
      action.run();
    } catch (Exception e) {
      failed = true;
    }
    ok(failed, message);
  }

  ServiceRecord record(int day) {
    ServiceRecord r = new ServiceRecord();
    r.date = LocalDate.of(2024, 2, day);
    r.dealer = "00181";
    r.items.add(new ServiceRecord.Item(1));
    return r;
  }

  Context isolated(String name) {
    String prefix = "v040-" + name + "-" + System.nanoTime();
    File directory = new File(getTargetContext().getFilesDir(), prefix);
    directory.mkdirs();
    return new ContextWrapper(getTargetContext()) {
      public File getFilesDir() {
        return directory;
      }

      public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences(prefix + name, mode);
      }
    };
  }

  WriteJournal journal(File root, String vin, List<ServiceRecord> records, boolean restart)
      throws Exception {
    return WriteJournal.begin(
        root, vin, "MGU · experimental", "0F25B0", "01 02", records, false, restart);
  }

  void run() {
    Bundle result = new Bundle();
    try {
      String a = "WBA12345678901234", b = "WBA12345678901235";
      Context context = isolated("migration");
      ServiceRecord legacy = record(1);
      String raw =
          HistoryJson.dump(Collections.singletonList(legacy), a)
              .replace("\"remaining_distance\": 0", "\"remaining_distance\": 4294967295");
      File legacyFile = new File(context.getFilesDir(), "history.json");
      Files.write(legacyFile.toPath(), raw.getBytes(StandardCharsets.UTF_8));
      WorkspaceStore store = new WorkspaceStore(context);
      store.initialize();
      ok(store.active().equals(a), "Migrate legacy VIN workspace");
      ok(
          store.load(a).get(0).items.get(0).distance == -1,
          "Migrate legacy unsigned distance without changing bits");
      ok(
          new String(Files.readAllBytes(legacyFile.toPath()), StandardCharsets.UTF_8).equals(raw),
          "Preserve original legacy history");
      store.save(b, Arrays.asList(record(2), record(3)));
      store.activate(b);
      WorkspaceStore reopened = new WorkspaceStore(context);
      reopened.initialize();
      ok(reopened.active().equals(b), "Workspace selection survives restart");
      ok(
          reopened.load(a).size() == 1 && reopened.load(b).size() == 2,
          "Per-VIN histories remain separate");
      ok(reopened.keys().containsAll(Arrays.asList("offline", a, b)), "Workspace list");
      rejects(() -> store.file("../history"), "Reject invalid path keys");
      Files.write(
          store.file(a).toPath(),
          HistoryJson.dump(Arrays.asList(record(4)), b).getBytes(StandardCharsets.UTF_8));
      rejects(() -> store.load(a), "Reject a workspace with mismatched VIN");
      Context corrupt = isolated("corrupt-legacy");
      Files.write(
          new File(corrupt.getFilesDir(), "history.json").toPath(),
          "invalid".getBytes(StandardCharsets.UTF_8));
      WorkspaceStore broken = new WorkspaceStore(corrupt);
      rejects(broken::initialize, "Corrupt migration fails without replacing legacy data");
      ok(!broken.prefs.getBoolean("migrated", false), "Failed migration is not marked complete");
      File root = new File(context.getFilesDir(), "journals-accepted");
      ServiceRecord entry = record(5);
      WriteJournal accepted = journal(root, a, Collections.singletonList(entry), false);
      WriteJournal.guard(root, a, Collections.singletonList(entry));
      ok(true, "Prepared but unsent intent does not block append");
      int[] sends = {0};
      accepted.send(
          0,
          request -> {
            sends[0]++;
            ok(
                WorkspaceStore.read(accepted.file)
                    .getJSONArray("steps")
                    .getJSONObject(0)
                    .getString("state")
                    .equals("sending"),
                "Persist intent before network send");
            return new byte[] {0x71, 1, (byte) 0xa0, (byte) 0x81, 0};
          },
          "MGU · experimental");
      accepted.finish(true, "fixture");
      ok(
          WorkspaceStore.read(accepted.file).getString("state").equals("response-confirmed"),
          "Persist final outcome");
      rejects(
          () -> WriteJournal.guard(root, a, Collections.singletonList(entry)),
          "Block accepted append after reload");
      entry.mileage = 12345;
      rejects(
          () -> WriteJournal.guard(root, a, Collections.singletonList(entry)),
          "Edited mileage cannot bypass date guard");
      entry.date = LocalDate.of(2024, 3, 5);
      rejects(
          () -> WriteJournal.guard(root, a, Collections.singletonList(entry)),
          "Edited date and mileage cannot bypass draft identity");
      ok(
          HistoryJson.record(HistoryJson.record(entry)).localId.equals(entry.localId),
          "Local draft identity survives JSON interchange");
      WriteJournal.guard(root, b, Collections.singletonList(entry));
      ok(true, "Duplicate guard is VIN scoped");
      rejects(
          () ->
              accepted.send(
                  0,
                  request -> {
                    sends[0]++;
                    return new byte[0];
                  },
                  "MGU · experimental"),
          "Never replay an attempted step");
      ok(sends[0] == 1, "Repeated invocation sends nothing");
      File timeoutRoot = new File(context.getFilesDir(), "journals-timeout");
      WriteJournal timeout = journal(timeoutRoot, a, Collections.singletonList(record(6)), false);
      rejects(
          () ->
              timeout.send(
                  0,
                  request -> {
                    throw new java.net.SocketTimeoutException("fixture timeout");
                  },
                  "MGU · experimental"),
          "Timeout stops operation");
      timeout.finish(false, "timeout");
      ok(
          WorkspaceStore.read(timeout.file)
              .getJSONArray("steps")
              .getJSONObject(0)
              .getString("state")
              .equals("uncertain"),
          "Persist uncertain timeout");
      rejects(
          () -> journal(timeoutRoot, a, Collections.singletonList(record(6)), false),
          "Block uncertain append after restart");
      File crashRoot = new File(context.getFilesDir(), "journals-crash");
      WriteJournal crashed = journal(crashRoot, a, Collections.singletonList(record(7)), false);
      crashed.data.getJSONArray("steps").getJSONObject(0).put("state", "sending");
      crashed.persist();
      rejects(
          () -> WriteJournal.guard(crashRoot, a, Collections.singletonList(record(7))),
          "Pending send after process death blocks replay");
      File rejectedRoot = new File(context.getFilesDir(), "journals-rejected");
      WriteJournal rejected = journal(rejectedRoot, a, Collections.singletonList(record(8)), false);
      rejects(
          () ->
              rejected.send(
                  0,
                  request -> new byte[] {0x71, 1, (byte) 0xa0, (byte) 0x81, 1},
                  "MGU · experimental"),
          "Memory-error result rejected");
      WriteJournal.guard(rejectedRoot, a, Collections.singletonList(record(8)));
      ok(true, "Definite rejection is not marked accepted");
      File malformedRoot = new File(context.getFilesDir(), "journals-malformed");
      WriteJournal malformed =
          journal(malformedRoot, a, Collections.singletonList(record(9)), false);
      rejects(
          () ->
              malformed.send(
                  0,
                  request -> new byte[] {0x71, 1, (byte) 0xa0, (byte) 0x80, 0},
                  "MGU · experimental"),
          "Wrong routine echo is uncertain");
      rejects(
          () -> WriteJournal.guard(malformedRoot, a, Collections.singletonList(record(9))),
          "Malformed positive response cannot bypass duplicate guard");
      File partialRoot = new File(context.getFilesDir(), "journals-partial");
      WriteJournal partial = journal(partialRoot, a, Arrays.asList(record(10), record(11)), true);
      partial.send(
          0, request -> new byte[] {0x71, 1, (byte) 0xa0, (byte) 0x81, 0}, "MGU · experimental");
      rejects(
          () ->
              partial.send(
                  1,
                  request -> {
                    throw new Hsfz.Negative(0x31, 0x22);
                  },
                  "MGU · experimental"),
          "Stop after negative reply");
      partial.finish(false, "fixture partial");
      JSONObject stored = WorkspaceStore.read(partial.file);
      ok(
          stored.getJSONArray("steps").getJSONObject(1).getString("response").equals("7f 31 22"),
          "Retain the checked negative response bytes");
      ok(
          stored.getString("state").equals("partial-or-uncertain"),
          "Partial completion survives restart");
      ok(
          stored.getJSONArray("steps").getJSONObject(2).getString("state").equals("unattempted"),
          "Restart is not sent after failure");
      File diskRoot = new File(context.getFilesDir(), "journals-disk");
      WriteJournal disk = journal(diskRoot, a, Collections.singletonList(record(12)), false);
      new File(disk.file + ".new").mkdir();
      int[] diskSends = {0};
      rejects(
          () ->
              disk.send(
                  0,
                  request -> {
                    diskSends[0]++;
                    return new byte[0];
                  },
                  "MGU · experimental"),
          "Disk error prevents send");
      ok(diskSends[0] == 0, "No vehicle request without durable write-ahead evidence");
      ok(
          WriteJournal.report(crashRoot, a).contains("UNCERTAIN"),
          "Reports explain interrupted sends");
      ok(
          WorkspaceStore.read(accepted.file).isNull("vehicle_history_before"),
          "Journal does not fabricate vehicle backup");
      result.putString("stream", "PASS: " + checks + " v0.4.0 Android storage/failure checks\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", "FAIL: " + e + "\n" + android.util.Log.getStackTraceString(e));
      finish(0, result);
    }
  }
}
