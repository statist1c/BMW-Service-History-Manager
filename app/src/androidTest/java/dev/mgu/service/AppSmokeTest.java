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
    throw new AssertionError(
        "Missing UI text " + text + ": " + allText(getUiAutomation().getRootInActiveWindow()));
  }

  void click(String text) throws Exception {
    AccessibilityNodeInfo n = node(text);
    if (!n.performAction(AccessibilityNodeInfo.ACTION_CLICK) && n.getParent() != null)
      n.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
    waitForIdleSync();
    Thread.sleep(200);
  }

  public void onStart() {
    if (args != null && args.containsKey("migration")) {
      migrationCheck(args.getString("migration"));
      return;
    }
    if (args != null && "storage".equals(args.getString("focus"))) {
      new V040Checks(this).run();
      return;
    }
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
      String source = "statist1c/BMW-Service-History-Manager";
      String sha = "sha256:" + String.join("", Collections.nCopies(64, "a"));
      for (String name : Arrays.asList("BMW-Service-Manager-v0.4.0.apk", "Any New Name.APK")) {
        ok(
            Updater.choose(
                    new JSONArray().put(release("v0.4.0", false, name, source, sha)),
                    "0.2.1",
                    source)
                != null,
            "Accept arbitrary APK filename: " + name);
      }
      JSONObject multiple = release("v0.4.0", false, "one.apk", source, sha);
      multiple
          .getJSONArray("assets")
          .put(
              release("v0.4.0", false, "two.apk", source, sha)
                  .getJSONArray("assets")
                  .getJSONObject(0));
      ok(
          Updater.choose(new JSONArray().put(multiple), "0.2.1", source) == null,
          "Reject ambiguous multiple APKs");
      ok(
          Updater.choose(
                  new JSONArray().put(release("v0.4.0", false, "source.zip", source, sha)),
                  "0.2.1",
                  source)
              == null,
          "Ignore non-APK assets");
      JSONObject newest = release("v0.5.0", false, "renamed.apk", source, sha);
      ok(
          Updater.choose(
                  new JSONArray()
                      .put(newest)
                      .put(release("v0.4.0", false, "older.apk", source, sha)),
                  "0.2.1",
                  source)
              .tag
              .equals("v0.5.0"),
          "Choose newest version independent of release ordering");
      JSONObject draft = release("v0.6.0", false, "draft.apk", source, sha).put("draft", true);
      ok(Updater.choose(new JSONArray().put(draft), "0.2.1", source) == null, "Ignore draft");
      JSONObject preview = release("v0.3.0", true, "BMW-Service-Manager-v0.3.0.apk", source, sha);
      Updater.Release previewUpdate =
          Updater.choose(new JSONArray().put(preview), "0.2.2", source, true);
      ok(
          previewUpdate != null && previewUpdate.preview,
          "Include GitHub preview with normal version tag");
      ok(
          Updater.choose(new JSONArray().put(preview), "0.2.2", source, false) == null,
          "Stable-only channel excludes previews");
      ok(
          Updater.choose(new JSONArray().put(draft), "0.2.1", source, true) == null,
          "Preview channel still excludes drafts");
      ok(
          Updater.choose(new JSONArray().put(preview), "0.3.0", source, true) == null,
          "Preview channel rejects same version");
      ok(
          Updater.choose(
                  new JSONArray()
                      .put(preview)
                      .put(release("v0.3.1", false, "renamed.apk", source, sha)),
                  "0.2.2",
                  source,
                  true)
              .tag
              .equals("v0.3.1"),
          "Newest stable wins over older preview");
      ok(new Updater.Release(previewUpdate.json()).preview, "Preview metadata survives caching");
      if (args != null && "true".equals(args.getString("live"))) {
        java.net.HttpURLConnection connection =
            Updater.connect("https://api.github.com/repos/" + source + "/releases?per_page=100");
        JSONArray published;
        try {
          published =
              new JSONArray(
                  new String(
                      Updater.read(connection.getInputStream(), 2 * 1024 * 1024),
                      java.nio.charset.StandardCharsets.UTF_8));
        } finally {
          connection.disconnect();
        }
        Updater.Release publishedUpdate = Updater.choose(published, "0.2.1", source, true);
        ok(
            publishedUpdate != null
                && ReleaseVersion.parse(publishedUpdate.tag)
                        .compareTo(ReleaseVersion.parse("0.2.2"))
                    >= 0,
            "Detect actual published GitHub release from v0.2.1");
      }
      if (args != null && "updater".equals(args.getString("focus"))) {
        result.putString("stream", "PASS: " + checks + " updater/JSON checks\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
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
      getTargetContext()
          .getSharedPreferences("settings", 0)
          .edit()
          .putString("theme", "Light")
          .apply();
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
      editorChecks();
      workspaceChecks();
      if (args != null && "true".equals(args.getString("upgrade"))) updateFlowChecks();
      ui(
          () -> {
            app.reset.setChecked(false);
            app.previewSelected(0);
          });
      node("Selected record · request preview");
      AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
      String previewText = allText(root);
      ok(
          previewText.contains("31 01 a0 81") && !previewText.contains("31 01 a0 80"),
          "Selected record preview never erases history");
      ok(
          previewText.indexOf("31 01 a0 81") == previewText.lastIndexOf("31 01 a0 81"),
          "Exactly one selected-record add request");
      click("Close");
      ui(() -> app.editArguments(0));
      setDescriptionText("EDIABAS arguments", "29;2;2024;0;123456;0;00181;0x1;1;1;0x1;0;0");
      click("Save locally");
      ui(
          () ->
              ok(
                  app.records.get(0).mileage == 123456
                      && app.records.get(0).date.toString().equals("2024-02-29"),
                  "EDIABAS editor saves validated local record"));
      ui(() -> app.editArguments(0));
      setDescriptionText("EDIABAS arguments", "invalid");
      click("Save locally");
      ui(() -> ok(app.records.get(0).mileage == 123456, "Invalid arguments do not change record"));
      click("Cancel");
      final android.widget.EditText[] date = new android.widget.EditText[1];
      ui(
          () -> {
            date[0] = new android.widget.EditText(app);
            date[0].setText("2024-02-29");
            app.chooseDate(date[0]);
          });
      waitForIdleSync();
      ok(
          allText(getUiAutomation().getRootInActiveWindow()).contains("2024"),
          "Calendar shows selected service year");
      click("OK");
      ui(
          () ->
              ok(
                  date[0].getText().toString().equals("2024-02-29"),
                  "Calendar uses selected leap-day date"));
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
      node("Select a head-unit family first. Unknown vehicle changes are disabled.");
      click("OK");
      ui(() -> app.independentControl(false));
      node("Select a head-unit family first. Unknown vehicle changes are disabled.");
      click("OK");
      ui(() -> app.independentControl(true));
      node("Select a head-unit family first. Unknown vehicle changes are disabled.");
      click("OK");
      ui(() -> ok(!app.busy, "Standalone controls send nothing for unsupported family"));
      ui(
          () -> {
            app.family.setSelection(6);
            app.route.setSelection(1);
            try {
              MainActivity.Target target = app.new Target();
              app.identified = target.key() + "|" + target.ip + " · WBA12345678901234";
              deniedTarget("Acknowledge experimental MGU");
              app.mguConsent.setChecked(true);
              deniedTarget("read head-unit identification");
              app.headUnitIdentity = app.identified;
              ok(
                  app.mutationTarget().key().equals(target.key()),
                  "MGU gate permits only explicit scoped identity acknowledgement");
              app.headUnitIdentity = app.identified + "stale";
              deniedTarget("read head-unit identification");
              Cbs item = new Cbs();
              item.code = 1;
              app.resetCbs(item);
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
      node(
          "CBS resets on MGU-equipped vehicles remain disabled pending cluster validation. MGU mode"
              + " currently supports head-unit history operations only.");
      click("OK");
      ui(
          () -> {
            ok(!app.busy, "MGU CBS reset sends no requests");
            app.family.setSelection(0);
            app.identified = "";
            app.headUnitIdentity = "";
          });
      ui(
          () -> {
            app.select(3);
            app.save();
          });
      node("Appearance");
      ok(app.workspaces.file(app.workspaceKey).exists(), "History persisted in selected workspace");
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

  void migrationCheck(String mode) {
    Bundle result = new Bundle();
    try {
      getTargetContext()
          .getSharedPreferences("updates", 0)
          .edit()
          .putBoolean("automatic", false)
          .commit();
      app =
          (MainActivity)
              startActivitySync(
                  new Intent(getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      java.io.File legacy = new java.io.File(app.getFilesDir(), "history.json");
      if (mode.equals("seed")) {
        if (!app.getPackageManager()
            .getPackageInfo(app.getPackageName(), 0)
            .versionName
            .equals("0.3.0"))
          throw new IllegalStateException("Seed mode requires the old v0.3.0 fixture");
        ui(
            () -> {
              app.records.clear();
              ServiceRecord record = CoreFixture.record();
              record.items.get(0).distance = 4294967295L;
              app.records.add(record);
              app.vinInput.setText("WBA12345678901234");
              app.changed();
              app.finish();
            });
        String hash =
            java.util.Base64.getEncoder()
                .encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(java.nio.file.Files.readAllBytes(legacy.toPath())));
        getTargetContext()
            .getSharedPreferences("migration-fixture", 0)
            .edit()
            .putString("hash", hash)
            .commit();
        ok(legacy.exists(), "Seed old release with legacy history");
      } else {
        ui(
            () -> {
              ok(
                  app.workspaceKey.equals("WBA12345678901234"),
                  "Upgrade selects the migrated VIN workspace");
              ok(
                  app.records.size() == 1 && app.records.get(0).mileage == 85000,
                  "Upgrade preserves legacy records");
              ok(
                  app.records.get(0).items.get(0).distance == -1,
                  "Upgrade preserves legacy distance bits");
            });
        String hash =
            java.util.Base64.getEncoder()
                .encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(java.nio.file.Files.readAllBytes(legacy.toPath())));
        ok(
            hash.equals(
                getTargetContext()
                    .getSharedPreferences("migration-fixture", 0)
                    .getString("hash", "")),
            "Signed upgrade leaves the original history file byte-for-byte unchanged");
      }
      result.putString(
          "stream", "PASS: " + checks + " signed-release migration checks (" + mode + ")\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", "FAIL: " + e + "\n" + android.util.Log.getStackTraceString(e));
      finish(0, result);
    }
  }

  void deniedTarget(String message) throws Exception {
    try {
      app.mutationTarget();
      throw new AssertionError("Expected MGU gate rejection");
    } catch (java.io.IOException e) {
      ok(e.getMessage().contains(message), "MGU guard: " + message);
    }
  }

  String allText(AccessibilityNodeInfo node) {
    if (node == null) return "";
    StringBuilder text = new StringBuilder(node.getText() == null ? "" : node.getText());
    for (int n = 0; n < node.getChildCount(); n++)
      text.append('\n').append(allText(node.getChild(n)));
    return text.toString();
  }

  AccessibilityNodeInfo description(AccessibilityNodeInfo node, String desc) {
    if (node == null) return null;
    if (desc.contentEquals(
        node.getContentDescription() == null ? "" : node.getContentDescription())) return node;
    for (int n = 0; n < node.getChildCount(); n++) {
      AccessibilityNodeInfo found = description(node.getChild(n), desc);
      if (found != null) return found;
    }
    return null;
  }

  void clickDescription(String desc) throws Exception {
    AccessibilityNodeInfo field = description(getUiAutomation().getRootInActiveWindow(), desc);
    if (field == null || !field.performAction(AccessibilityNodeInfo.ACTION_CLICK))
      throw new AssertionError("Cannot click " + desc);
    waitForIdleSync();
    for (int n = 0; n < 4; n++) {
      scrollBack(getUiAutomation().getRootInActiveWindow());
      waitForIdleSync();
    }
  }

  boolean scrollBack(AccessibilityNodeInfo node) {
    if (node == null) return false;
    if (node.isScrollable())
      return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    for (int n = 0; n < node.getChildCount(); n++) if (scrollBack(node.getChild(n))) return true;
    return false;
  }

  void editorChecks() throws Exception {
    ui(() -> app.editRecord(0));
    click("Add service item");
    click("Brake fluid");
    click("Move item up");
    click("Save");
    ui(() -> ok(app.records.get(0).items.get(0).code == 3, "Reorder items in record"));
    ui(() -> app.editRecord(0));
    click("Edit Brake fluid");
    clickDescription("Service type");
    click("Engine oil");
    click("Save");
    click("Cancel");
    ui(
        () ->
            ok(
                app.records.get(0).items.get(0).code == 3,
                "Cancel record discards nested item edits"));
    ui(() -> app.editRecord(0));
    click("Edit Brake fluid");
    clickDescription("Service type");
    click("Front brakes");
    click("Save");
    click("Save");
    ui(() -> ok(app.records.get(0).items.get(0).code == 2, "Change service type directly"));
    ui(() -> app.editRecord(0));
    click("Insert new items at the top");
    click("Add service item");
    click("Fuel filter");
    click("Save");
    ui(() -> ok(app.records.get(0).items.get(0).code == 12, "Insert item at top"));
    ServiceRecord original = app.records.get(0);
    ui(() -> app.editRecord(-1, true));
    click("Add service item");
    click("Engine oil");
    click("Save");
    ui(() -> ok(app.records.get(1) == original, "Insert record at top"));
    ui(() -> app.editRecord(-1, false));
    click("Add service item");
    click("Brake fluid");
    click("Save");
    ui(() -> ok(app.records.get(2).items.get(0).code == 3, "Insert record at bottom"));
    ui(
        () -> {
          app.records.clear();
          app.records.add(original);
          app.changed();
        });
    java.io.File file = new java.io.File(app.getFilesDir(), "save-back-test.tst");
    java.nio.file.Files.write(file.toPath(), new byte[4096]);
    ui(
        () -> {
          app.erase.setChecked(false);
          app.prefs()
              .edit()
              .putString(app.openedTstKey(), android.net.Uri.fromFile(file).toString())
              .apply();
          app.saveOpenedTst();
        });
    node("Replace opened TST?");
    click("Cancel");
    ok(file.length() == 4096, "Cancel overwrite leaves original file intact");
    ui(() -> app.saveOpenedTst());
    click("Continue");
    node("Opened TST saved.");
    click("OK");
    String saved =
        new String(
            java.nio.file.Files.readAllBytes(file.toPath()),
            java.nio.charset.StandardCharsets.UTF_8);
    ok(
        saved.equals(ServiceRecord.tst(app.records, false)),
        "Save back truncates and writes exact TST");
    ok(ServiceRecord.parseTst(saved).records.size() == 1, "Saved TST imports successfully");
    ui(() -> app.prefs().edit().remove(app.openedTstKey()).apply());
  }

  void updateFlowChecks() throws Exception {
    java.io.File next = new java.io.File(app.getFilesDir(), "update-flow.apk");
    Updater.Release release =
        new Updater.Release(metadata(next, "v0.4.1").json().put("preview", true));
    Updater.validate(app, next, release);
    ok(true, "Validate newer same-signer preview APK");
    java.nio.file.Files.copy(
        next.toPath(),
        Updater.apk(app).toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    Updater.prefs(app)
        .edit()
        .putBoolean("previews", false)
        .putString("release", release.json().toString())
        .commit();
    ok(Updater.cached(app) == null, "Stable-only channel ignores cached preview");
    Updater.prefs(app).edit().putBoolean("previews", true).commit();
    ok(Updater.cached(app) != null, "Preview channel restores eligible cached update");
    ui(
        () -> {
          app.select(3);
          app.reviewUpdate();
        });
    node("Update to v0.4.1?");
    click("Continue");
    node("Install verified update?");
    click("Continue");
    node("Do you want to update this app?");
    ok(true, "In-app verified preview opens Android update installer without redownload");
    click("Cancel");
    Updater.prefs(app).edit().remove("release").apply();
  }

  void workspaceChecks() throws Exception {
    final String originalKey = app.workspaceKey;
    final String first = "WBA12345678901234", second = "WBA12345678901235";
    final int originalCount = app.records.size();
    ui(
        () -> {
          try {
            app.switchWorkspace(first, true);
            ok(
                app.records.size() == originalCount
                    && app.vinInput.getText().toString().equals(first),
                "Copy records into a VIN workspace");
            app.headUnitIdentity = "fixture";
            app.headUnitDetails = new HeadUnitInfo.Result("", 2, "0F25B0", new byte[] {1});
            app.workspaces.save(second, Collections.emptyList());
            app.switchWorkspace(second, false);
            ok(app.records.isEmpty(), "Another VIN starts with a separate history");
            ok(
                app.headUnitDetails == null && app.headUnitIdentity.isEmpty(),
                "Workspace switch invalidates head-unit identity");
            app.records.add(CoreFixture.record());
            app.changed();
            app.switchWorkspace(first, false);
            ok(app.records.size() == originalCount, "Switching VIN restores its saved history");
            app.identified = "fixture · " + first;
            app.headUnitIdentity = app.identified;
            app.headUnitDetails = new HeadUnitInfo.Result("", 2, "0F25B0", new byte[] {1});
            app.requireExactHeadUnit("MGU · experimental");
            ok(true, "Matching exact identity accepted in bound VIN workspace");
            boolean blocked = false;
            try {
              app.requireExactHeadUnit("NBT");
            } catch (Exception expected) {
              blocked = true;
            }
            ok(blocked, "Wrong manual family cannot override variant mapping");
            app.switchWorkspace(originalKey, false);
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
  }

  void setDescriptionText(String desc, String value) throws Exception {
    AccessibilityNodeInfo field = description(getUiAutomation().getRootInActiveWindow(), desc);
    if (field == null) throw new AssertionError("Missing field " + desc);
    Bundle args = new Bundle();
    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
    if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
      throw new AssertionError("Cannot edit " + desc);
    waitForIdleSync();
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
                                + java.net.URLEncoder.encode(asset, "UTF-8").replace("+", "%20"))));
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
