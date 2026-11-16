package dev.mgu.service;

import android.content.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Independent per-VIN storage. Legacy history is preserved during migration. */
final class WorkspaceStore {
  final File root;
  final Context context;
  final SharedPreferences prefs;

  WorkspaceStore(Context c) {
    context = c;
    root = new File(c.getFilesDir(), "workspaces");
    prefs = c.getSharedPreferences("workspaces", 0);
  }

  static String key(String vin) {
    String value = vin.trim().toUpperCase(Locale.ROOT);
    if (value.isEmpty() || value.equals("OFFLINE")) return "offline";
    if (!RecordChecks.vin(value))
      throw new IllegalArgumentException("Enter a valid 17-character VIN");
    return value;
  }

  File file(String key) {
    if (!key.equals("offline") && !RecordChecks.vin(key))
      throw new IllegalArgumentException("Invalid workspace");
    return new File(root, key + ".json");
  }

  String active() {
    return key(prefs.getString("active", "offline"));
  }

  void activate(String key) throws IOException {
    file(key);
    if (!prefs.edit().putString("active", key).commit())
      throw new IOException("Could not select workspace");
  }

  void initialize() throws Exception {
    if (prefs.getBoolean("migrated", false)) return;
    File legacy = new File(context.getFilesDir(), "history.json");
    String key = "offline";
    if (legacy.exists() || new File(legacy + ".bak").exists()) {
      JSONObject json = read(legacy);
      String oldVin = json.optString("vin", "").toUpperCase(Locale.ROOT);
      if (RecordChecks.vin(oldVin)) key = oldVin;
      // Write before committing the migration; keep the original legacy file untouched.
      save(key, HistoryJson.parse(json));
    }
    SharedPreferences settings = context.getSharedPreferences("settings", 0);
    if (!settings.getString("opened_tst", "").isEmpty() && !settings.contains("opened_tst_" + key))
      if (!settings
          .edit()
          .putString("opened_tst_" + key, settings.getString("opened_tst", ""))
          .commit()) throw new IOException("Could not migrate opened TST destination");
    if (!prefs.edit().putString("active", key).putBoolean("migrated", true).commit())
      throw new IOException("Could not finish workspace migration");
  }

  static JSONObject read(File file) throws Exception {
    return new JSONObject(
        new String(
            Updater.read(new AtomicFile(file).openRead(), 8 * 1024 * 1024),
            StandardCharsets.UTF_8));
  }

  List<ServiceRecord> load(String key) throws Exception {
    File file = file(key);
    if (!file.exists() && !new File(file + ".bak").exists()) return new ArrayList<>();
    JSONObject json = read(file);
    if (!json.optString("vin", "").equals(key.equals("offline") ? "" : key))
      throw new IOException("Workspace VIN does not match its file");
    return HistoryJson.parse(json);
  }

  void save(String key, List<ServiceRecord> records) throws Exception {
    byte[] bytes =
        HistoryJson.dump(records, key.equals("offline") ? "" : key)
            .getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 8 * 1024 * 1024) throw new IOException("Workspace exceeds storage limit");
    if (!root.isDirectory() && !root.mkdirs())
      throw new IOException("Cannot create workspace storage");
    AtomicFile file = new AtomicFile(file(key));
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      stream.write(bytes);
      stream.getFD().sync();
      file.finishWrite(stream);
      if (!Arrays.equals(file.readFully(), bytes))
        throw new IOException("Workspace commit could not be verified");
    } catch (Exception e) {
      if (stream != null) file.failWrite(stream);
      throw e;
    }
  }

  List<String> keys() {
    TreeSet<String> keys = new TreeSet<>();
    keys.add("offline");
    File[] files = root.listFiles();
    if (files != null)
      for (File f : files) {
        String name = f.getName().replaceFirst("\\.json(?:\\.bak)?$", "");
        if (RecordChecks.vin(name)) keys.add(name);
      }
    return new ArrayList<>(keys);
  }
}
