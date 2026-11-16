package dev.mgu.service;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Uses only the Android platform. Network work runs outside the UI thread. */
final class Updater {
  static String repo(Context c) {
    String r = prefs(c).getString("repo", BuildConfig.UPDATE_REPOSITORY);
    if (!r.matches("[A-Za-z0-9-]+/[A-Za-z0-9._-]+"))
      throw new IllegalArgumentException("Set the GitHub owner/repository in Settings first.");
    return r;
  }

  static final String ASSET = "BMW-Service-Manager.apk";
  static final int JOB = 9011, MAX = 16 * 1024 * 1024;

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("updates", Context.MODE_PRIVATE);
  }

  static final class Release {
    final String tag, url, digest, notes;
    final boolean preview;
    final long size;

    Release(JSONObject j) throws JSONException {
      tag = j.getString("tag");
      url = j.getString("url");
      digest = j.getString("digest");
      notes = j.optString("notes");
      preview = j.optBoolean("preview", false);
      size = j.getLong("size");
    }

    JSONObject json() throws JSONException {
      return new JSONObject()
          .put("tag", tag)
          .put("url", url)
          .put("digest", digest)
          .put("notes", notes)
          .put("preview", preview)
          .put("size", size);
    }
  }

  static void schedule(Context c) {
    JobScheduler scheduler = c.getSystemService(JobScheduler.class);
    if (!prefs(c).getBoolean("automatic", true)) {
      scheduler.cancel(JOB);
      return;
    }
    if (scheduler.getPendingJob(JOB) == null)
      scheduler.schedule(
          new JobInfo.Builder(JOB, new ComponentName(c, UpdateJob.class))
              .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
              .setPeriodic(24 * 60 * 60 * 1000L)
              .build());
  }

  static boolean due(Context c) {
    return !prefs(c).getString("repo", BuildConfig.UPDATE_REPOSITORY).isEmpty()
        && prefs(c).getBoolean("automatic", true)
        && System.currentTimeMillis() - prefs(c).getLong("last_check", 0) > 24 * 60 * 60 * 1000L;
  }

  static byte[] read(InputStream in, int limit) throws IOException {
    try (InputStream input = in;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = input.read(b)) != -1) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        if (out.size() + n > limit) throw new IOException("File exceeds size limit");
        out.write(b, 0, n);
      }
      return out.toByteArray();
    }
  }

  static HttpURLConnection connect(String address) throws Exception {
    URL url = new URL(address);
    for (int n = 0; n < 6; n++) {
      String host = url.getHost();
      if (!url.getProtocol().equals("https")
          || !(host.equals("api.github.com")
              || host.equals("github.com")
              || host.equals("release-assets.githubusercontent.com")
              || host.equals("objects.githubusercontent.com")))
        throw new IOException("Refused update download host.");
      HttpURLConnection c = (HttpURLConnection) url.openConnection();
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(15000);
      c.setReadTimeout(20000);
      c.setRequestProperty("User-Agent", "BMW-Service-Manager/" + BuildConfig.VERSION_NAME);
      c.setRequestProperty(
          "Accept",
          host.equals("api.github.com")
              ? "application/vnd.github+json"
              : "application/octet-stream");
      int status = c.getResponseCode();
      if (status >= 300 && status < 400) {
        String location = c.getHeaderField("Location");
        c.disconnect();
        if (location == null) throw new IOException("Missing redirect destination.");
        url = new URL(url, location);
        continue;
      }
      if (status != 200) {
        c.disconnect();
        throw new IOException(
            status == 403 || status == 429
                ? "GitHub request limit reached. Try later."
                : status == 404
                    ? "No published releases found yet."
                    : "GitHub returned HTTP " + status);
      }
      return c;
    }
    throw new IOException("Too many update redirects.");
  }

  static Release choose(JSONArray releases, String installed, String repo) throws Exception {
    return choose(releases, installed, repo, false);
  }

  static Release choose(JSONArray releases, String installed, String repo, boolean previews)
      throws Exception {
    Release best = null;
    ReleaseVersion current = ReleaseVersion.parse(installed.replaceFirst("-beta$", ""));
    for (int i = 0; i < releases.length(); i++) {
      JSONObject r = releases.getJSONObject(i);
      if (r.optBoolean("draft") || (!previews && r.optBoolean("prerelease"))) continue;
      String tag = r.optString("tag_name");
      ReleaseVersion version;
      try {
        version = ReleaseVersion.parse(tag);
      } catch (RuntimeException e) {
        continue;
      }
      if (version.compareTo(current) <= 0
          || (best != null && version.compareTo(ReleaseVersion.parse(best.tag)) <= 0)) continue;
      JSONArray assets = r.optJSONArray("assets");
      if (assets == null) continue;
      int apkCount = 0;
      for (int j = 0; j < assets.length(); j++) {
        JSONObject asset = assets.optJSONObject(j);
        if (asset != null && asset.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk"))
          apkCount++;
      }
      // A release must identify one installable APK without guessing between variants.
      if (apkCount != 1) continue;
      for (int j = 0; j < assets.length(); j++) {
        JSONObject a = assets.optJSONObject(j);
        if (a == null) continue;
        String url = a.optString("browser_download_url"), digest = a.optString("digest");
        long size = a.optLong("size");
        if (!a.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")
            || !digest.matches("sha256:[0-9a-fA-F]{64}")
            || size <= 0
            || size > MAX) continue;
        URI uri;
        try {
          uri = new URI(url);
        } catch (URISyntaxException malformed) {
          continue;
        }
        String expected = "/" + repo + "/releases/download/";
        if (!"https".equals(uri.getScheme())
            || !"github.com".equals(uri.getHost())
            || !uri.getPath().startsWith(expected)
            || uri.getUserInfo() != null
            || uri.getFragment() != null
            || uri.getPort() != -1) continue;
        best =
            new Release(
                new JSONObject()
                    .put("tag", tag)
                    .put("url", url)
                    .put("digest", digest.substring(7).toLowerCase(Locale.ROOT))
                    .put("size", size)
                    .put("notes", r.optString("body"))
                    .put("preview", r.optBoolean("prerelease")));
        break;
      }
    }
    return best;
  }

  static Release check(Context c) throws Exception {
    String source = repo(c);
    boolean previews = prefs(c).getBoolean("previews", true);
    HttpURLConnection connection =
        connect("https://api.github.com/repos/" + source + "/releases?per_page=100");
    byte[] bytes;
    try {
      bytes = read(connection.getInputStream(), 2 * 1024 * 1024);
    } finally {
      connection.disconnect();
    }
    Release release =
        choose(
            new JSONArray(new String(bytes, StandardCharsets.UTF_8)),
            BuildConfig.VERSION_NAME,
            source,
            previews);
    if (!source.equals(repo(c))) throw new IOException("Repository changed; check again.");
    if (previews != prefs(c).getBoolean("previews", true))
      throw new IOException("Update channel changed; check again.");
    SharedPreferences.Editor editor =
        prefs(c)
            .edit()
            .putLong("last_check", System.currentTimeMillis())
            .putString(
                "status",
                release == null
                    ? "No newer compatible APK · v" + BuildConfig.VERSION_NAME
                    : "Update available · " + release.tag);
    if (release == null) editor.remove("release");
    else editor.putString("release", release.json().toString());
    editor.apply();
    return release;
  }

  static Release cached(Context c) {
    try {
      String json = prefs(c).getString("release", "");
      if (json.isEmpty()) return null;
      Release r = new Release(new JSONObject(json));
      if (r.preview && !prefs(c).getBoolean("previews", true)) return null;
      return ReleaseVersion.parse(r.tag)
                  .compareTo(
                      ReleaseVersion.parse(BuildConfig.VERSION_NAME.replaceFirst("-beta$", "")))
              > 0
          ? r
          : null;
    } catch (Exception e) {
      return null;
    }
  }

  static File apk(Context c) {
    return new File(c.getCacheDir(), "update.apk");
  }

  static byte[][] signatures(PackageInfo info) {
    android.content.pm.Signature[] signatures;
    if (Build.VERSION.SDK_INT >= 28) {
      if (info.signingInfo == null)
        throw new IllegalArgumentException("APK has no signing information.");
      signatures = info.signingInfo.getApkContentsSigners();
    } else signatures = info.signatures;
    if (signatures == null || signatures.length == 0)
      throw new IllegalArgumentException("APK is not signed.");
    byte[][] result = new byte[signatures.length][];
    for (int i = 0; i < result.length; i++) result[i] = signatures[i].toByteArray();
    Arrays.sort(
        result,
        (a, b) ->
            Base64.getEncoder().encodeToString(a).compareTo(Base64.getEncoder().encodeToString(b)));
    return result;
  }

  static long code(PackageInfo info) {
    return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
  }

  static void validate(Context c, File file, Release r) throws Exception {
    if (file.length() != r.size) throw new IOException("Update size mismatch.");
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) sha.update(b, 0, n);
    }
    StringBuilder hash = new StringBuilder();
    for (byte b : sha.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
    if (!hash.toString().equals(r.digest)) throw new IOException("Update SHA-256 mismatch.");
    PackageManager pm = c.getPackageManager();
    int flags =
        Build.VERSION.SDK_INT >= 28
            ? PackageManager.GET_SIGNING_CERTIFICATES
            : PackageManager.GET_SIGNATURES;
    PackageInfo next = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags),
        current = pm.getPackageInfo(c.getPackageName(), flags);
    if (next == null || !c.getPackageName().equals(next.packageName))
      throw new IOException("Update belongs to a different app.");
    if (!Arrays.deepEquals(signatures(current), signatures(next)))
      throw new IOException("Update signing certificate does not match this installation.");
    if (code(next) <= code(current)
        || ReleaseVersion.parse(next.versionName).compareTo(ReleaseVersion.parse(r.tag)) != 0)
      throw new IOException("Update version does not match the release, or is not newer.");
  }

  static void download(Context c, Release r) throws Exception {
    File part = new File(c.getCacheDir(), "update.part");
    HttpURLConnection connection = connect(r.url);
    try {
      try (InputStream in = connection.getInputStream();
          OutputStream out = new FileOutputStream(part)) {
        byte[] b = new byte[8192];
        long count = 0;
        int n;
        while ((n = in.read(b)) != -1) {
          count += n;
          if (count > MAX || count > r.size)
            throw new IOException("Update exceeds its declared size.");
          out.write(b, 0, n);
        }
      }
      validate(c, part, r);
      File target = apk(c);
      if (target.exists() && !target.delete())
        throw new IOException("Cannot replace previous update.");
      if (!part.renameTo(target)) throw new IOException("Cannot finish update download.");
    } finally {
      connection.disconnect();
      if (part.exists()) part.delete();
    }
  }

  static void install(Activity activity, Release r) throws Exception {
    validate(activity, apk(activity), r);
    if (!activity.getPackageManager().canRequestPackageInstalls()) {
      activity.startActivity(
          new Intent(
              Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
              Uri.parse("package:" + activity.getPackageName())));
      return;
    }
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(
        Uri.parse("content://" + activity.getPackageName() + ".updates/update.apk"),
        "application/vnd.android.package-archive");
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    activity.startActivity(intent);
  }
}
