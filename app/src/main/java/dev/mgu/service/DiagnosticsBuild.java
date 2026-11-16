package dev.mgu.service;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Build identity is collected on the worker, directly from the installed APK. */
final class DiagnosticsBuild {
  static String header(Context context) {
    StringBuilder report = new StringBuilder("BMW Service Manager · car diagnostics\n");
    report
        .append("Compiled version: ")
        .append(BuildConfig.VERSION_NAME)
        .append(" (")
        .append(BuildConfig.VERSION_CODE)
        .append(")\n");
    try {
      PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      report
          .append("Installed version: ")
          .append(info.versionName)
          .append(" (")
          .append(
              android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode)
          .append(")\n");
      report.append("Package: ").append(context.getPackageName()).append("\n");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (FileInputStream in = new FileInputStream(context.getApplicationInfo().sourceDir)) {
        byte[] buffer = new byte[32768];
        int count;
        while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
      }
      StringBuilder hex = new StringBuilder();
      for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
      report.append("Installed APK SHA-256: ").append(hex).append("\n");
    } catch (Exception e) {
      report.append("Build identity collection failed: ").append(e).append("\n");
    }
    return report
        .append("Reader revision: ")
        .append(HeadUnitInfo.READER_REVISION)
        .append("\n\n")
        .toString();
  }
}
