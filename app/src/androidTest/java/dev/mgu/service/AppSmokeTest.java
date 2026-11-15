package dev.mgu.service;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import java.util.*;
import org.json.*;

/**
 * Platform-only instrumentation; run with adb shell am instrument -w
 * dev.mgu.service.test/dev.mgu.service.AppSmokeTest.
 */
public class AppSmokeTest extends Instrumentation {
  int checks;
  MainActivity app;
  Bundle args;

  void ok(boolean b, String message) {
    checks++;
    if (!b) throw new AssertionError(message);
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    args = b;
    start();
  }

  void ui(Runnable r) {
    runOnMainSync(r);
    waitForIdleSync();
  }

  AccessibilityNodeInfo node(String text) throws Exception {
    for (int n = 0; n < 30; n++) {
      AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
      if (root != null)
        for (AccessibilityNodeInfo x : root.findAccessibilityNodeInfosByText(text))
          if (text.equalsIgnoreCase(x.getText() == null ? "" : x.getText().toString())) return x;
      Thread.sleep(100);
    }
    throw new AssertionError("Missing UI text " + text);
  }

  void click(String text) throws Exception {
    AccessibilityNodeInfo n = node(text);
    if (!n.performAction(AccessibilityNodeInfo.ACTION_CLICK) && n.getParent() != null)
      n.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
    waitForIdleSync();
    Thread.sleep(200);
  }

  public void onStart() {
    Bundle result = new Bundle();
    try {
      ServiceRecord r = CoreFixture.record();
      String json = HistoryJson.dump(Arrays.asList(r), "WBA12345678901234");
      List<ServiceRecord> rs = HistoryJson.parse(new JSONObject(json));
      ok(rs.get(0).args().equals(r.args()), "JSON round trip");
      ok(
          HistoryJson.parse(new JSONArray(new JSONObject(json).getJSONArray("records").toString()))
                  .size()
              == 1,
          "Array import");
      JSONArray releases = new JSONArray();
      releases.put(
          release(
              "v0.2.0",
              false,
              "BMW-Service-Manager.apk",
              "statist1c/BMW-Service-History-Manager",
              "sha256:" + String.join("", Collections.nCopies(64, "a"))));
      releases.put(
          release(
              "v0.3.0",
              true,
              "BMW-Service-Manager.apk",
              "statist1c/BMW-Service-History-Manager",
              "sha256:" + String.join("", Collections.nCopies(64, "a"))));
      Updater.Release chosen =
          Updater.choose(releases, "0.1.0", "statist1c/BMW-Service-History-Manager");
      ok(chosen != null && chosen.tag.equals("v0.2.0"), "Stable update selection");
      ok(
          Updater.choose(releases, "0.2.0", "statist1c/BMW-Service-History-Manager") == null,
          "Reject same/older version");
      ok(
          Updater.choose(releases, "0.1.0", "someone/else") == null,
          "Reject other repository asset");
      ok(
          Updater.choose(
                  new JSONArray()
                      .put(
                          release(
                              "v0.4.0",
                              false,
                              "BMW-Service-Manager.apk",
                              "statist1c/BMW-Service-History-Manager",
                              "")),
                  "0.1.0",
                  "statist1c/BMW-Service-History-Manager")
              == null,
          "Reject missing SHA256");
      JSONObject invalid = HistoryJson.record(r).put("correction", 4294967296L);
      boolean rejected = false;
      try {
        HistoryJson.record(invalid);
      } catch (Exception expected) {
        rejected = true;
      }
      ok(rejected, "Reject integer overflow in JSON");
      if (args != null && "true".equals(args.getString("fixtures"))) fixtureChecks();
      Updater.prefs(getTargetContext()).edit().putBoolean("automatic", false).apply();
      app =
          (MainActivity)
              startActivitySync(
                  new Intent(getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      ui(
          () -> {
            app.records.clear();
            app.changed();
            app.editRecord(-1);
          });
      click("Add service item");
      click("Engine oil");
      click("Save");
      ui(() -> ok(app.records.size() == 1, "Save record through native dialog"));
      ui(() -> app.preview());
      node("Exact UDS request preview");
      click("Close");
      ui(() -> ok(!app.planned.isEmpty(), "Preview state"));
      ui(
          () -> {
            app.select(1);
            app.experimental.setChecked(true);
            app.backup.setChecked(true);
            app.write();
          });
      node(
          "Select a supported upstream head-unit family. MGU / unknown vehicle changes are"
              + " disabled.");
      click("OK");
      ui(
          () -> {
            app.select(3);
            app.save();
          });
      node("Appearance");
      ok(new java.io.File(app.getFilesDir(), "history.json").exists(), "History persisted");
      ui(
          () -> {
            app.records.clear();
            app.changed();
            app.select(0);
          });
      if (args != null && "true".equals(args.getString("screenshots"))) {
        screenshot("history-light.png");
        ui(() -> app.select(1));
        screenshot("vehicle-light.png");
        ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
        ui(
            () -> {
              app.prefs().edit().putString("theme", "Dark").apply();
              app.recreate();
            });
        app = (MainActivity) waitForMonitorWithTimeout(monitor, 5000);
        ok(app != null, "Dark theme recreation");
        waitForIdleSync();
        ui(() -> app.select(0));
        screenshot("history-dark.png");
        ui(() -> app.select(3));
        screenshot("settings-dark.png");
        removeMonitor(monitor);
      }
      result.putString(
          "stream",
          "PASS: "
              + checks
              + " Android checks (JSON, updater filtering, native record dialog, preview, MGU"
              + " mutation guard, persistence).\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString(
          "stream", "FAIL: " + e.toString() + "\n" + android.util.Log.getStackTraceString(e));
      finish(0, result);
    }
  }

  interface Checked {
    void run() throws Exception;
  }

  void screenshot(String name) throws Exception {
    Thread.sleep(300);
    java.io.File dir = new java.io.File(getTargetContext().getFilesDir(), "screenshots");
    dir.mkdirs();
    try (java.io.OutputStream out = new java.io.FileOutputStream(new java.io.File(dir, name))) {
      getUiAutomation()
          .takeScreenshot()
          .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
    }
  }

  void fails(Checked test, String message) throws Exception {
    boolean rejected = false;
    try {
      test.run();
    } catch (Exception expected) {
      rejected = true;
    }
    ok(rejected, message);
  }

  Updater.Release metadata(java.io.File file, String tag) throws Exception {
    java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
    try (java.io.InputStream in = new java.io.FileInputStream(file)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) sha.update(b, 0, n);
    }
    StringBuilder digest = new StringBuilder();
    for (byte b : sha.digest())
      digest.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return new Updater.Release(
        new JSONObject()
            .put("tag", tag)
            .put("size", file.length())
            .put("digest", digest.toString())
            .put(
                "url",
                "https://github.com/statist1c/BMW-Service-History-Manager/releases/download/"
                    + tag
                    + "/BMW-Service-Manager.apk"));
  }

  void fixtureChecks() throws Exception {
    java.io.File dir = new java.io.File(getTargetContext().getFilesDir(), "update-tests");
    java.io.File valid = new java.io.File(dir, "valid.apk"),
        otherSigner = new java.io.File(dir, "other-signer.apk"),
        otherPackage = new java.io.File(dir, "other-package.apk"),
        old = new java.io.File(dir, "old.apk");
    Updater.Release release = metadata(valid, "v0.1.1");
    Updater.validate(getTargetContext(), valid, release);
    ok(true, "Accept newer same-signer APK");
    fails(
        () ->
            Updater.validate(
                getTargetContext(),
                valid,
                new Updater.Release(release.json().put("size", release.size + 1))),
        "Reject size mismatch");
    fails(
        () ->
            Updater.validate(
                getTargetContext(),
                valid,
                new Updater.Release(
                    release.json().put("digest", String.join("", Collections.nCopies(64, "0"))))),
        "Reject SHA mismatch");
    fails(
        () -> Updater.validate(getTargetContext(), otherSigner, metadata(otherSigner, "v0.1.1")),
        "Reject other signing certificate");
    fails(
        () -> Updater.validate(getTargetContext(), otherPackage, metadata(otherPackage, "v0.1.1")),
        "Reject other package");
    fails(
        () -> Updater.validate(getTargetContext(), old, metadata(old, "v0.1.0")),
        "Reject same-version APK");
    fails(
        () -> Updater.validate(getTargetContext(), valid, metadata(valid, "v0.1.2")),
        "Reject tag/version mismatch");
    fails(
        () ->
            getTargetContext()
                .getContentResolver()
                .openFileDescriptor(
                    android.net.Uri.parse("content://dev.mgu.service.updates/anything-else"), "r"),
        "Reject arbitrary provider path");
  }

  JSONObject release(String tag, boolean pre, String asset, String repo, String digest)
      throws Exception {
    return new JSONObject()
        .put("tag_name", tag)
        .put("prerelease", pre)
        .put(
            "assets",
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("name", asset)
                        .put("size", 1000)
                        .put("digest", digest)
                        .put(
                            "browser_download_url",
                            "https://github.com/"
                                + repo
                                + "/releases/download/"
                                + tag
                                + "/"
                                + asset)));
  }

  static class CoreFixture {
    static ServiceRecord record() {
      ServiceRecord r = new ServiceRecord();
      r.mileage = 85000;
      r.dealer = "00181";
      r.items.add(new ServiceRecord.Item(1));
      return r;
    }
  }
}
