package dev.mgu.service;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
  int BG, CARD, INK, MUTED, BLUE, PALE, LINE, ON, AMBER, selected;
  boolean dark, busy, updateBusy, saveBlocked;
  String theme,
      vin = "",
      identified = "",
      headUnitIdentity = "",
      planned = "",
      cbsTarget = "",
      session = "No vehicle operations yet.\n",
      updateMessage = "";
  final List<ServiceRecord> records = new ArrayList<>();
  final List<Cbs> cbsItems = new ArrayList<>();
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Handler handler = new Handler(Looper.getMainLooper());
  LinearLayout shell, body, historyList, cbsList;
  ScrollView[] pages = new ScrollView[4];
  TextView[] navText = new TextView[4];
  FrameLayout[] pills = new FrameLayout[4];
  Icon[] icons = new Icon[4];
  TextView status, log, summary, identity, updates;
  EditText host, port, vinInput;
  Spinner route, family;
  CheckBox erase, reset, experimental, backup, mguConsent;
  String operationState = "Offline workspace · vehicle not checked";
  WorkspaceStore workspaces;
  String workspaceKey = "offline";
  HeadUnitInfo.Result headUnitDetails;

  SharedPreferences prefs() {
    return getSharedPreferences("settings", 0);
  }

  @Override
  public void onCreate(Bundle state) {
    theme = prefs().getString("theme", "System");
    dark =
        theme.equals("Dark")
            || (theme.equals("System")
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
    setTheme(dark ? R.style.Theme_RootCompanion_Dark : R.style.Theme_RootCompanion);
    super.onCreate(state);
    BG = dark ? 0xff11141c : 0xfffaf8ff;
    CARD = dark ? 0xff1e2330 : 0xffefedf5;
    INK = dark ? 0xffeef1fa : 0xff1d1c22;
    MUTED = dark ? 0xffb1b9ca : 0xff5a5d6b;
    BLUE = dark ? 0xffafc3ff : 0xff30477f;
    PALE = dark ? 0xff28385b : 0xffdfe5f6;
    LINE = dark ? 0xff3c4558 : 0xffd6d5df;
    ON = dark ? 0xff10254b : Color.WHITE;
    AMBER = dark ? 0xffffd68c : 0xff8b5700;
    try {
      workspaces = new WorkspaceStore(this);
      workspaces.initialize();
      workspaceKey = workspaces.active();
      records.addAll(workspaces.load(workspaceKey));
      vin = workspaceKey.equals("offline") ? "" : workspaceKey;
    } catch (Exception e) {
      saveBlocked = true;
      operationState = "Saved history could not be loaded · import a backup";
      session += "Load error: " + e.getMessage() + "\n";
    }
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(CARD);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            dark
                ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    shell = new LinearLayout(this);
    shell.setOrientation(LinearLayout.VERTICAL);
    shell.setBackgroundColor(BG);
    setContentView(shell);
    shell.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            Insets b =
                insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            shell.setPadding(b.left, b.top, b.right, b.bottom);
          } else
            shell.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
          return insets;
        });
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(16), dp(12), dp(16), dp(10));
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.ic_service);
    header.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));
    TextView title = text("BMW Service\nHistory Manager", 20, INK);
    title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
    tl.leftMargin = dp(10);
    header.addView(title, tl);
    shell.addView(header);
    status = text(operationState, 12, MUTED);
    status.setPadding(dp(14), dp(10), dp(14), dp(10));
    status.setBackground(shape(CARD, 14));
    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
    sp.setMargins(dp(16), 0, dp(16), dp(6));
    shell.addView(status, sp);
    FrameLayout content = new FrameLayout(this);
    shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    for (int i = 0; i < 4; i++) {
      pages[i] = new ScrollView(this);
      pages[i].setFillViewport(true);
      content.addView(pages[i], new FrameLayout.LayoutParams(-1, -1));
    }
    history();
    vehicle();
    monitor();
    settings();
    LinearLayout nav = new LinearLayout(this);
    nav.setPadding(dp(8), dp(8), dp(8), dp(8));
    nav.setBackgroundColor(CARD);
    String[] labels = {"History", "Vehicle", "Monitor", "Settings"};
    for (int i = 0; i < 4; i++) {
      final int index = i;
      LinearLayout item = new LinearLayout(this);
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(Gravity.CENTER);
      item.setContentDescription(labels[i] + " tab");
      item.setFocusable(true);
      item.setOnClickListener(v -> select(index));
      pills[i] = new FrameLayout(this);
      icons[i] = new Icon(this, i);
      pills[i].addView(icons[i], new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
      item.addView(pills[i], new LinearLayout.LayoutParams(dp(64), dp(32)));
      navText[i] = text(labels[i], 12, MUTED);
      navText[i].setGravity(Gravity.CENTER);
      navText[i].setPadding(0, dp(4), 0, 0);
      item.addView(navText[i], new LinearLayout.LayoutParams(-1, -2));
      nav.addView(item, new LinearLayout.LayoutParams(0, dp(64), 1));
    }
    shell.addView(nav);
    select(state == null ? 0 : state.getInt("tab", 0));
    shell.requestApplyInsets();
    Updater.schedule(this);
    if (Updater.due(this)) checkUpdate(false);
  }

  int dp(float x) {
    return Math.round(x * getResources().getDisplayMetrics().density);
  }

  GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setLineSpacing(dp(2), 1);
    return t;
  }

  LinearLayout page(int n, String title, String subtitle) {
    LinearLayout p = new LinearLayout(this);
    p.setOrientation(LinearLayout.VERTICAL);
    p.setPadding(dp(16), dp(14), dp(16), dp(24));
    pages[n].addView(p);
    TextView t = text(title, 25, INK);
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    p.addView(t);
    TextView s = text(subtitle, 14, MUTED);
    s.setPadding(0, dp(6), 0, dp(18));
    p.addView(s);
    return p;
  }

  void card(LinearLayout parent, String title) {
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(16), dp(16), dp(16), dp(12));
    body.setBackground(shape(CARD, 22));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(14);
    parent.addView(body, lp);
    TextView t = text(title, 18, INK);
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    t.setPadding(0, 0, 0, dp(8));
    body.addView(t);
  }

  TextView label(String s) {
    TextView t = text(s, 14, MUTED);
    t.setPadding(0, 0, 0, dp(10));
    body.addView(t);
    return t;
  }

  EditText input(String caption, String value, boolean number) {
    label(caption);
    EditText e = new EditText(this);
    e.setText(value);
    e.setSingleLine(true);
    e.setTextSize(15);
    e.setTextColor(INK);
    e.setHintTextColor(MUTED);
    e.setContentDescription(caption);
    e.setPadding(dp(14), dp(12), dp(14), dp(12));
    GradientDrawable bg = shape(BG, 13);
    bg.setStroke(dp(1), LINE);
    e.setBackground(bg);
    e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(10);
    body.addView(e, lp);
    return e;
  }

  Button button(String s, boolean primary, Runnable run) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTextColor(primary ? ON : BLUE);
    GradientDrawable d = shape(primary ? BLUE : Color.TRANSPARENT, 24);
    if (!primary) d.setStroke(dp(1), BLUE);
    b.setBackground(d);
    b.setMinHeight(dp(48));
    b.setPadding(dp(14), dp(10), dp(14), dp(10));
    b.setStateListAnimator(null);
    b.setOnClickListener(
        v -> {
          try {
            run.run();
          } catch (Exception e) {
            message(e.getMessage());
          }
        });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(4), 0, dp(6));
    body.addView(b, lp);
    return b;
  }

  CheckBox check(String s, boolean value) {
    CheckBox c = new CheckBox(this);
    c.setText(s);
    c.setTextColor(INK);
    c.setChecked(value);
    body.addView(c);
    return c;
  }

  Spinner spinner(String caption, String[] choices, int selected) {
    label(caption);
    Spinner s = new Spinner(this);
    s.setContentDescription(caption);
    s.setAdapter(
        new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, choices));
    s.setSelection(selected);
    s.setMinimumHeight(dp(48));
    body.addView(s);
    return s;
  }

  void select(int n) {
    selected = n;
    for (int i = 0; i < 4; i++) {
      pages[i].setVisibility(i == n ? View.VISIBLE : View.GONE);
      if (navText[i] != null) {
        navText[i].setTextColor(i == n ? BLUE : MUTED);
        pills[i].setBackground(shape(i == n ? PALE : Color.TRANSPARENT, 22));
        icons[i].color = i == n ? BLUE : MUTED;
        icons[i].invalidate();
      }
    }
  }

  boolean available() {
    if (busy) {
      message("Wait for the vehicle operation to finish.");
      return false;
    }
    return true;
  }

  void history() {
    LinearLayout p =
        page(0, "Your service history", "Create a local record of the work completed.");
    card(p, "History workspace");
    summary = label("");
    vinInput = input("Workspace VIN · use Vehicle workspaces to switch", vin, false);
    vinInput.setKeyListener(null);
    vinInput.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(17)});
    button("Vehicle workspaces…", false, this::chooseWorkspace);
    button(
        "Check local history",
        false,
        () -> {
          try {
            List<String> issues = RecordChecks.issues(records);
            showText(
                "Local history checks",
                issues.isEmpty()
                    ? "No local conflicts found. This does not check the car's existing history."
                    : String.join("\n", issues));
          } catch (Exception e) {
            message(e.getMessage());
          }
        });
    button(
        "Add service record",
        true,
        () -> {
          if (available())
            new AlertDialog.Builder(this)
                .setTitle("Insert service record")
                .setItems(
                    new String[] {"At the top", "At the bottom"},
                    (d, position) -> editRecord(-1, position == 0))
                .show();
        });
    button(
        "Add from EDIABAS arguments…",
        false,
        () -> {
          if (available()) editArguments(-1);
        });
    button(
        "Sort by mileage ↓",
        false,
        () -> {
          if (available()) {
            records.sort((a, b) -> Long.compare(b.mileage, a.mileage));
            changed();
          }
        });
    card(p, "Import & backup");
    label(
        "Compatible with upstream JSON, Stampbook history JSON and Toolset32 .tst files. Files stay"
            + " on your phone.");
    button(
        "Import JSON / TST",
        false,
        () -> {
          if (available()) {
            Intent i =
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("*/*")
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, 10);
          }
        });
    button("Export JSON", false, () -> export(false));
    button("Export Toolset32 .tst", false, () -> export(true));
    button("Save back to opened TST…", false, this::saveOpenedTst);
    historyList = new LinearLayout(this);
    historyList.setOrientation(LinearLayout.VERTICAL);
    p.addView(historyList);
    renderHistory();
  }

  void renderHistory() {
    summary.setText(
        records.size()
            + " service record"
            + (records.size() == 1 ? "" : "s")
            + " · "
            + workspaceKey
            + " · saved on this phone");
    historyList.removeAllViews();
    LinearLayout old = body;
    if (records.isEmpty()) {
      card(historyList, "Ready for your first service");
      label(
          "Add a genuine service record or import your existing history. No sample records are"
              + " mixed with your data.");
    }
    for (int n = 0; n < records.size(); n++) {
      final int at = n;
      ServiceRecord r = records.get(n);
      card(historyList, r.date + "  ·  " + String.format(Locale.ROOT, "%,d", r.mileage) + " km");
      StringBuilder names = new StringBuilder();
      for (ServiceRecord.Item i : r.items) {
        if (names.length() > 0) names.append(" · ");
        names.append(ServiceRecord.name(i.code));
      }
      label(names.toString());
      label(
          (r.bmw ? "BMW dealer" : "Independent service")
              + " · "
              + (r.dealer.isEmpty() ? "No dealer number" : r.dealer)
              + "\n"
              + new String[] {"Carried out", "Carried out late", "Incomplete"}[r.status - 1]);
      button(
          "Edit record",
          false,
          () -> {
            if (available()) editRecord(at);
          });
      button(
          "EDIABAS arguments…",
          false,
          () -> {
            if (available()) editArguments(at);
          });
      button(
          "Write selected record…",
          false,
          () -> {
            if (available()) previewSelected(at);
          });
      if (n > 0)
        button(
            "Move up",
            false,
            () -> {
              if (available()) {
                Collections.swap(records, at, at - 1);
                changed();
              }
            });
      if (n < records.size() - 1)
        button(
            "Move down",
            false,
            () -> {
              if (available()) {
                Collections.swap(records, at, at + 1);
                changed();
              }
            });
      button(
          "Delete record…",
          false,
          () -> {
            if (available())
              confirm(
                  "Delete this record?",
                  "This removes the phone’s copy. Export a backup first if needed.",
                  () -> {
                    records.remove(at);
                    changed();
                  });
          });
    }
    body = old;
  }

  void changed() {
    planned = "";
    if (saveBlocked) {
      try {
        File damaged = workspaces.file(workspaceKey);
        if (!damaged.exists() && !new File(damaged + ".bak").exists())
          damaged = new File(getFilesDir(), "history.json");
        if (damaged.exists())
          java.nio.file.Files.copy(
              damaged.toPath(),
              new File(getFilesDir(), "history-unreadable-" + System.currentTimeMillis() + ".json")
                  .toPath());
        File oldBackup = new File(damaged + ".bak");
        if (oldBackup.exists())
          java.nio.file.Files.copy(
              oldBackup.toPath(),
              new File(getFilesDir(), "history-unreadable-backup-" + System.nanoTime() + ".json")
                  .toPath());
        if (!workspaces
            .prefs
            .edit()
            .putString("active", workspaceKey)
            .putBoolean("migrated", true)
            .commit()) throw new IOException("Could not finish workspace recovery");
        saveBlocked = false;
      } catch (IOException e) {
        message("Cannot preserve the unreadable history. Saving is blocked: " + e.getMessage());
        renderHistory();
        return;
      }
    }
    save();
    renderHistory();
  }

  void save() {
    saveWorkspace();
  }

  boolean saveWorkspace() {
    if (saveBlocked) return false;
    vin = workspaceKey.equals("offline") ? "" : workspaceKey;
    try {
      workspaces.save(workspaceKey, records);
      return true;
    } catch (Exception e) {
      message("Could not save history: " + e.getMessage());
      return false;
    }
  }

  String openedTstKey() {
    return "opened_tst_" + workspaceKey;
  }

  void chooseWorkspace() {
    if (!available()) return;
    List<String> keys = workspaces.keys();
    String[] names = new String[keys.size() + 1];
    for (int n = 0; n < keys.size(); n++)
      names[n] = keys.get(n).equals("offline") ? "Offline workspace" : keys.get(n);
    names[names.length - 1] = "Open another VIN…";
    new AlertDialog.Builder(this)
        .setTitle("Vehicle workspaces")
        .setItems(
            names,
            (d, n) -> {
              if (n < keys.size()) {
                try {
                  switchWorkspace(keys.get(n), false);
                } catch (Exception e) {
                  message(e.getMessage());
                }
                return;
              }
              LinearLayout view = new LinearLayout(this);
              view.setOrientation(LinearLayout.VERTICAL);
              EditText entry = new EditText(this);
              entry.setHint("17-character VIN");
              view.addView(entry);
              CheckBox copy = new CheckBox(this);
              copy.setText("Copy current records to this VIN");
              view.addView(copy);
              AlertDialog dialog =
                  new AlertDialog.Builder(this)
                      .setTitle("Open vehicle workspace")
                      .setView(view)
                      .setNegativeButton("Cancel", null)
                      .setPositiveButton("Open", null)
                      .create();
              dialog.setOnShowListener(
                  x ->
                      dialog
                          .getButton(-1)
                          .setOnClickListener(
                              v -> {
                                try {
                                  String key = WorkspaceStore.key(val(entry));
                                  if (key.equals("offline"))
                                    throw new IllegalArgumentException("Enter a VIN");
                                  if (copy.isChecked())
                                    confirm(
                                        "Copy history to " + key + "?",
                                        "This replaces that VIN workspace's local records. Other"
                                            + " workspaces stay saved.",
                                        () -> {
                                          try {
                                            switchWorkspace(key, true);
                                            dialog.dismiss();
                                          } catch (Exception e) {
                                            message(e.getMessage());
                                          }
                                        });
                                  else {
                                    switchWorkspace(key, false);
                                    dialog.dismiss();
                                  }
                                } catch (Exception e) {
                                  entry.setError(e.getMessage());
                                }
                              }));
              dialog.show();
            })
        .show();
  }

  void switchWorkspace(String key, boolean copy) throws Exception {
    key = WorkspaceStore.key(key);
    if (!saveWorkspace())
      throw new IOException("Current workspace could not be saved; switch cancelled");
    List<ServiceRecord> next =
        copy
            ? HistoryJson.parse(
                new JSONObject(HistoryJson.dump(records, key.equals("offline") ? "" : key)))
            : workspaces.load(key);
    if (copy) workspaces.save(key, next);
    workspaces.activate(key);
    workspaceKey = key;
    vin = key.equals("offline") ? "" : key;
    records.clear();
    records.addAll(next);
    vinInput.setText(vin);
    identified = "";
    headUnitIdentity = "";
    headUnitDetails = null;
    cbsTarget = "";
    planned = "";
    erase.setChecked(false);
    experimental.setChecked(false);
    changed();
  }

  String val(EditText e) {
    return e.getText().toString().trim();
  }

  void chooseDate(EditText field) {
    LocalDate selected;
    try {
      selected = LocalDate.parse(val(field));
    } catch (Exception e) {
      selected = LocalDate.now();
    }
    DatePickerDialog dialog =
        new DatePickerDialog(
            this,
            (picker, year, month, day) ->
                field.setText(LocalDate.of(year, month + 1, day).toString()),
            selected.getYear(),
            selected.getMonthValue() - 1,
            selected.getDayOfMonth());
    dialog.setTitle("Service date");
    dialog.show();
  }

  void editArguments(int index) {
    if (index < 0 && records.size() >= 2000) {
      message("Maximum 2000 records");
      return;
    }
    LinearLayout view = new LinearLayout(this);
    view.setOrientation(LinearLayout.VERTICAL);
    view.setPadding(dp(20), dp(8), dp(20), dp(16));
    TextView help =
        text(
            "Edit the semicolon-separated arguments for one STEUERN_SERVICEHISTORY_ADD record."
                + " Saving updates the local workspace only; it sends nothing to the vehicle.\n\n"
                + "Day;month;year;correction;km;BMW-dealer flag;dealer;status;item count; then"
                + " code;status;distance;months for each item.",
            13,
            MUTED);
    view.addView(help);
    EditText args = new EditText(this);
    args.setTextColor(INK);
    args.setTextSize(14);
    args.setTypeface(Typeface.MONOSPACE);
    args.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    args.setMinLines(4);
    args.setMaxLines(8);
    args.setGravity(Gravity.TOP);
    args.setContentDescription("EDIABAS arguments");
    args.setText(index < 0 ? "" : records.get(index).args());
    view.addView(args, new LinearLayout.LayoutParams(-1, -2));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(view);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("EDIABAS record arguments")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Copy", null)
            .setPositiveButton("Save locally", null)
            .create();
    dialog.setOnShowListener(
        d -> {
          dialog
              .getButton(AlertDialog.BUTTON_NEUTRAL)
              .setOnClickListener(
                  v -> {
                    getSystemService(ClipboardManager.class)
                        .setPrimaryClip(
                            ClipData.newPlainText(
                                "EDIABAS service arguments", args.getText().toString()));
                    Toast.makeText(this, "Arguments copied", Toast.LENGTH_SHORT).show();
                  });
          dialog
              .getButton(AlertDialog.BUTTON_POSITIVE)
              .setOnClickListener(
                  v -> {
                    try {
                      if (!available()) return;
                      ServiceRecord record = ServiceRecord.fromEditorArgs(val(args));
                      if (index >= 0) record.localId = records.get(index).localId;
                      if (index < 0) records.add(record);
                      else records.set(index, record);
                      changed();
                      dialog.dismiss();
                    } catch (Exception e) {
                      args.setError("Invalid service arguments: " + e.getMessage());
                    }
                  });
        });
    dialog.show();
  }

  void identifyHeadUnit() {
    if (!available()) return;
    try {
      Target target = new Target();
      final String checked = identified.startsWith(target.key() + "|") ? identified : "";
      headUnitIdentity = "";
      headUnitDetails = null;
      work(
          "Head-unit identification",
          () -> {
            try (Hsfz client = client(target)) {
              HeadUnitInfo.Result result = HeadUnitInfo.readDetailed(client, this::append);
              handler.post(
                  () -> {
                    if (!checked.isEmpty() && result.supported > 0 && identified.equals(checked)) {
                      headUnitIdentity = checked;
                      headUnitDetails = result;
                    }
                    showText("Head-unit identification", target.ip + "\n\n" + result.report);
                  });
            }
          });
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void independentControl(boolean eraseOnly) {
    if (!available()) return;
    try {
      Target target = mutationTarget();
      requireExactHeadUnit(family.getSelectedItem().toString());
      if (eraseOnly && !backup.isChecked())
        throw new IOException(
            "Back up the current iDrive history and tick the backup confirmation.");
      final String expected = identified;
      final String profile = family.getSelectedItem().toString();
      final HeadUnitInfo.Result exactHeadUnit = headUnitDetails;
      final byte[] request =
          eraseOnly ? new byte[] {0x31, 1, (byte) 0xa0, (byte) 0x80} : new byte[] {0x11, 1};
      String name = eraseOnly ? "Erase vehicle history only" : "Restart head unit only";
      confirm(
          name + "?",
          expected
              + "\n\n"
              + (eraseOnly
                  ? "PERMANENTLY ERASES all service history stored in the head unit. No replacement"
                      + " records will be written. This app cannot restore it."
                  : "Restarts the head unit without changing service-history records. The screen"
                      + " and connection may go offline temporarily.")
              + "\n\nTarget 0x63 · TX "
              + ServiceRecord.hex(request)
              + "\n"
              + "Park the vehicle, keep ignition on and stable battery power. Android real-car"
              + " validation is pending.",
          () ->
              confirm(
                  "Send this request now?",
                  name + "\n" + expected + "\n\nKeep this app open until finished.",
                  () -> {
                    work(
                        name,
                        () -> {
                          executeHistory(
                              target,
                              expected,
                              profile,
                              exactHeadUnit,
                              Collections.emptyList(),
                              eraseOnly,
                              !eraseOnly);
                          append(
                              "Request accepted. Verify the outcome in iDrive. No automatic retries"
                                  + " or rollback.");
                        });
                    select(2);
                  }));
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void previewSelected(int index) {
    if (!available()) return;
    try {
      final ServiceRecord record = HistoryJson.record(HistoryJson.record(records.get(index)));
      final boolean restart = reset.isChecked();
      final String exact = plan(Collections.singletonList(record), false, restart);
      ScrollView scroll = new ScrollView(this);
      TextView text =
          text(
              "Only this record will be added. Existing history is NOT erased. A duplicate can be"
                  + " created if this entry is already in the vehicle.\n\n"
                  + exact,
              13,
              INK);
      text.setTypeface(Typeface.MONOSPACE);
      text.setTextIsSelectable(true);
      text.setPadding(dp(20), dp(12), dp(20), dp(16));
      scroll.addView(text);
      new AlertDialog.Builder(this)
          .setTitle("Selected record · request preview")
          .setView(scroll)
          .setNegativeButton("Close", null)
          .setPositiveButton(
              "Continue to write…",
              (d, w) -> {
                planned = exact;
                writeRecords(Collections.singletonList(record), false, restart);
              })
          .show();
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void editRecord(int index) {
    editRecord(index, false);
  }

  void editRecord(int index, boolean atTop) {
    if (index < 0 && records.size() >= 2000) {
      message("Maximum 2000 records");
      return;
    }
    ServiceRecord r;
    try {
      r =
          index < 0
              ? new ServiceRecord()
              : HistoryJson.record(HistoryJson.record(records.get(index)));
    } catch (Exception e) {
      message(e.getMessage());
      return;
    }
    final ServiceRecord draft = r;
    LinearLayout old = body;
    ScrollView scroll = new ScrollView(this);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(20), dp(10), dp(20), dp(16));
    scroll.addView(body);
    EditText date = input("Date · YYYY-MM-DD", r.date.toString(), false);
    button("Choose date…", false, () -> chooseDate(date));
    EditText km = input("Mileage · km", Long.toString(r.mileage), true);
    EditText dealer = input("Dealer number · up to 7 ASCII characters", r.dealer, false);
    CheckBox bmw = check("BMW dealer", r.bmw);
    Spinner state =
        spinner(
            "Record status",
            new String[] {"Carried out", "Carried out late", "Incomplete"},
            r.status - 1);
    EditText corr = input("Correction counter · 0–255", Integer.toString(r.correction), true);
    label("Service items · 1–13 per record");
    CheckBox insertTop = check("Insert new items at the top", false);
    LinearLayout itemList = new LinearLayout(this);
    itemList.setOrientation(LinearLayout.VERTICAL);
    body.addView(itemList);
    Runnable render = () -> renderDraftItems(itemList, draft);
    render.run();
    button(
        "Add service item",
        false,
        () -> {
          if (draft.items.size() >= 13) {
            message("A record supports up to 13 items.");
            return;
          }
          String[] options = new String[ServiceRecord.NAMES.length + 1];
          System.arraycopy(ServiceRecord.NAMES, 0, options, 0, ServiceRecord.NAMES.length);
          options[options.length - 1] = "Custom item code…";
          new AlertDialog.Builder(this)
              .setTitle("Service item")
              .setItems(
                  options,
                  (d, w) -> {
                    if (w < ServiceRecord.CODES.length) {
                      draft.items.add(
                          insertTop.isChecked() ? 0 : draft.items.size(),
                          new ServiceRecord.Item(ServiceRecord.CODES[w]));
                      render.run();
                    } else customItem(draft, render, insertTop.isChecked());
                  })
              .show();
        });
    body = old;
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(index < 0 ? "Add service record" : "Edit service record")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        draft.date = LocalDate.parse(val(date));
                        draft.mileage = Long.parseLong(val(km));
                        draft.dealer = val(dealer);
                        draft.bmw = bmw.isChecked();
                        draft.status = state.getSelectedItemPosition() + 1;
                        draft.correction = Integer.parseInt(val(corr));
                        draft.validate();
                        if (index < 0) records.add(atTop ? 0 : records.size(), draft);
                        else records.set(index, draft);
                        changed();
                        dialog.dismiss();
                      } catch (Exception e) {
                        message("Check the record: " + e.getMessage());
                      }
                    }));
    dialog.show();
  }

  void customItem(ServiceRecord r, Runnable after, boolean atTop) {
    EditText e = new EditText(this);
    e.setInputType(InputType.TYPE_CLASS_NUMBER);
    e.setHint("0–255");
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Custom service item code")
            .setView(e)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create();
    d.setOnShowListener(
        x ->
            d.getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        int c = Integer.parseInt(val(e));
                        ServiceRecord.range(c, 255, "Item code");
                        r.items.add(atTop ? 0 : r.items.size(), new ServiceRecord.Item(c));
                        after.run();
                        d.dismiss();
                      } catch (Exception ex) {
                        e.setError("Enter 0–255");
                      }
                    }));
    d.show();
  }

  void renderDraftItems(LinearLayout list, ServiceRecord r) {
    list.removeAllViews();
    LinearLayout old = body;
    body = list;
    for (ServiceRecord.Item i : new ArrayList<>(r.items)) {
      label(
          ServiceRecord.name(i.code)
              + " · "
              + new String[] {"Carried out", "Carried out late", "Overdue"}[i.status - 1]
              + "\n"
              + i.distance
              + " km · "
              + i.months
              + " months");
      button(
          "Edit " + ServiceRecord.name(i.code),
          false,
          () -> editItem(i, () -> renderDraftItems(list, r)));
      button(
          "Remove " + ServiceRecord.name(i.code),
          false,
          () -> {
            r.items.remove(i);
            renderDraftItems(list, r);
          });
      int position = r.items.indexOf(i);
      if (position > 0)
        button(
            "Move item up",
            false,
            () -> {
              Collections.swap(r.items, r.items.indexOf(i), r.items.indexOf(i) - 1);
              renderDraftItems(list, r);
            });
      if (position < r.items.size() - 1)
        button(
            "Move item down",
            false,
            () -> {
              Collections.swap(r.items, r.items.indexOf(i), r.items.indexOf(i) + 1);
              renderDraftItems(list, r);
            });
    }
    body = old;
  }

  void editItem(ServiceRecord.Item item, Runnable after) {
    LinearLayout old = body;
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(20), 0, dp(20), dp(12));
    int selectedType = -1;
    for (int n = 0; n < ServiceRecord.CODES.length; n++)
      if (ServiceRecord.CODES[n] == item.code) selectedType = n;
    String[] types = Arrays.copyOf(ServiceRecord.NAMES, ServiceRecord.NAMES.length + 1);
    types[types.length - 1] = "Custom code (use field below)";
    Spinner type =
        spinner("Service type", types, selectedType < 0 ? types.length - 1 : selectedType);
    EditText customCode = input("Custom item code · 0–255", Integer.toString(item.code), true);
    Spinner st =
        spinner(
            "Item status",
            new String[] {"Carried out", "Carried out late", "Overdue"},
            item.status - 1);
    EditText dist =
        input(
            "Remaining distance · signed km (negative = overdue)",
            Long.toString(item.distance),
            true);
    dist.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
    EditText months = input("Remaining time · months", Integer.toString(item.months), true);
    LinearLayout view = body;
    body = old;
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(ServiceRecord.name(item.code))
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create();
    d.setOnShowListener(
        x ->
            d.getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        long km = Long.parseLong(val(dist));
                        int mo = Integer.parseInt(val(months));
                        int code =
                            type.getSelectedItemPosition() < ServiceRecord.CODES.length
                                ? ServiceRecord.CODES[type.getSelectedItemPosition()]
                                : Integer.parseInt(val(customCode));
                        ServiceRecord.range(code, 255, "Item code");
                        if (km < Integer.MIN_VALUE || km > Integer.MAX_VALUE)
                          throw new IllegalArgumentException(
                              "Distance must fit a signed 32-bit value");
                        ServiceRecord.range(mo, 65535, "Months");
                        item.code = code;
                        item.status = st.getSelectedItemPosition() + 1;
                        item.distance = km;
                        item.months = mo;
                        after.run();
                        d.dismiss();
                      } catch (Exception e) {
                        message(e.getMessage());
                      }
                    }));
    d.show();
  }

  void export(boolean tst) {
    try {
      save();
      exportText(
          tst ? ServiceRecord.tst(records, erase.isChecked()) : HistoryJson.dump(records, vin),
          "service-history." + (tst ? "tst" : "json"),
          tst ? "text/plain" : "application/json");
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void saveOpenedTst() {
    if (!available()) return;
    String address = prefs().getString(openedTstKey(), "");
    if (address.isEmpty()) {
      message("Import a TST file first, or use Export Toolset32 .tst to save a new file.");
      return;
    }
    final String text;
    try {
      text = ServiceRecord.tst(records, erase.isChecked());
    } catch (Exception e) {
      message("Check the history: " + e.getMessage());
      return;
    }
    Uri document = Uri.parse(address);
    String name = "the opened TST file";
    try (android.database.Cursor cursor =
        getContentResolver()
            .query(
                document,
                new String[] {android.provider.OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
      if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
    } catch (Exception ignored) {
    }
    confirm(
        "Replace opened TST?",
        "Replace "
            + name
            + " with "
            + records.size()
            + " records? Export a separate backup first if needed."
            + (erase.isChecked() ? "\nThe file will include an ERASE instruction for Tool32." : ""),
        () -> {
          try {
            writeTstDocument(document, text);
            message("Opened TST saved.");
          } catch (Exception e) {
            message(
                "Could not overwrite the file: "
                    + e.getMessage()
                    + ". Use Export Toolset32 .tst to save a new copy.");
          }
        });
  }

  void writeTstDocument(Uri document, String text) throws IOException {
    try (OutputStream out = getContentResolver().openOutputStream(document, "wt")) {
      if (out == null) throw new IOException("The file provider did not open the document");
      out.write(text.getBytes(StandardCharsets.UTF_8));
    }
  }

  void exportText(String text, String name, String mime) {
    try {
      try (OutputStream out = new FileOutputStream(new File(getCacheDir(), "pending-export.txt"))) {
        out.write(text.getBytes(StandardCharsets.UTF_8));
      }
      startActivityForResult(
          new Intent(Intent.ACTION_CREATE_DOCUMENT)
              .setType(mime)
              .addCategory(Intent.CATEGORY_OPENABLE)
              .putExtra(Intent.EXTRA_TITLE, name),
          11);
    } catch (Exception e) {
      message("Could not prepare export: " + e.getMessage());
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null) return;
    try {
      if (request == 11) {
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
          out.write(
              Updater.read(
                  new FileInputStream(new File(getCacheDir(), "pending-export.txt")),
                  8 * 1024 * 1024));
        }
        new File(getCacheDir(), "pending-export.txt").delete();
        message("File exported.");
      }
      if (request == 10) {
        if (!available()) return;
        String text =
            new String(
                    Updater.read(
                        getContentResolver().openInputStream(data.getData()), 4 * 1024 * 1024),
                    StandardCharsets.UTF_8)
                .replace("\ufeff", "")
                .trim();
        final List<ServiceRecord> imported;
        final String newVin;
        final boolean hasErase;
        if (text.startsWith("{") || text.startsWith("[")) {
          Object j = new JSONTokener(text).nextValue();
          imported = HistoryJson.parse(j);
          newVin = j instanceof JSONObject ? ((JSONObject) j).optString("vin", "") : "";
          hasErase = false;
        } else {
          ServiceRecord.Imported i = ServiceRecord.parseTst(text);
          imported = i.records;
          newVin = "";
          hasErase = i.erase;
        }
        final String importKey = WorkspaceStore.key(newVin.isEmpty() ? vin : newVin);
        confirm(
            "Import " + imported.size() + " records?",
            "This replaces the local records in "
                + importKey
                + ". Other workspaces stay saved. The vehicle is unchanged."
                + (hasErase
                    ? "\n"
                        + "The TST contains an erase instruction. It will stay OFF here; review"
                        + " it separately."
                    : ""),
            () -> {
              if (saveBlocked) {
                changed();
                if (saveBlocked) return;
              }
              if (!importKey.equals(workspaceKey) && !saveWorkspace()) return;
              try {
                workspaces.save(importKey, imported);
                workspaces.activate(importKey);
              } catch (Exception e) {
                message("Import not completed: " + e.getMessage());
                return;
              }
              workspaceKey = importKey;
              records.clear();
              records.addAll(imported);
              vin = importKey.equals("offline") ? "" : importKey;
              vinInput.setText(vin);
              identified = "";
              headUnitIdentity = "";
              headUnitDetails = null;
              erase.setChecked(false);
              boolean tst = !(text.startsWith("{") || text.startsWith("["));
              if (tst) {
                try {
                  if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)
                    getContentResolver()
                        .takePersistableUriPermission(
                            data.getData(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                  else
                    getContentResolver()
                        .takePersistableUriPermission(
                            data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }
              }
              prefs()
                  .edit()
                  .putString(openedTstKey(), tst ? data.getData().toString() : "")
                  .apply();
              changed();
            });
      }
    } catch (Exception e) {
      message("File operation failed: " + e.getMessage());
    }
  }

  void vehicle() {
    LinearLayout p =
        page(1, "Vehicle connection", "Native ENET diagnostics · experimental Android port");
    card(p, "Compatibility first");
    label(
        "Upstream lists CIC, NBT, NBT Evo, ENTRY and CHAMP2. It was developed on an F48 with NBT"
            + " Evo. MGU service-history commands are implemented from inspected diagnostic"
            + " definitions, but remain experimental and unvalidated on a real vehicle.");
    family =
        spinner(
            "Head-unit family",
            new String[] {
              "Unknown · read only",
              "CIC",
              "NBT",
              "NBT Evo",
              "ENTRY",
              "CHAMP2",
              "MGU · experimental"
            },
            0);
    mguConsent =
        check("I understand MGU history support is experimental and not vehicle-validated", false);
    TextView mguHelp =
        label(
            "For MGU changes: check vehicle identity, then read head-unit identification on the"
                + " same connection. The family must match the F150 variant. CBS resets on"
                + " MGU-equipped vehicles remain disabled pending cluster validation.");
    mguConsent.setVisibility(View.GONE);
    mguHelp.setVisibility(View.GONE);
    family.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> parent) {}

          public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
            boolean mgu = index == 6;
            mguConsent.setVisibility(mgu ? View.VISIBLE : View.GONE);
            mguHelp.setVisibility(mgu ? View.VISIBLE : View.GONE);
            if (!mgu) mguConsent.setChecked(false);
          }
        });
    card(p, "1   Connect over ENET");
    label(
        "Use an ENET cable and compatible USB-C Ethernet adapter, or an ENET Wi-Fi adapter. Phone"
            + " routing must reach the vehicle gateway; the MGU SSH address is not the diagnostic"
            + " gateway.");
    host = input("Gateway IPv4 address", prefs().getString("host", "169.254.199.99"), false);
    port = input("HSFZ port", prefs().getString("port", "6801"), true);
    route =
        spinner(
            "Use phone network", new String[] {"Ethernet", "Wi-Fi"}, prefs().getInt("route", 0));
    button(
        "Open network settings",
        false,
        () -> startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)));
    button("Find vehicle on selected network", false, () -> discover());
    button("Check vehicle identity", true, () -> identify());
    identity = label("No vehicle identity checked this session.");
    button("Read head-unit identification", false, () -> identifyHeadUnit());
    card(p, "2   Preview the write");
    erase = check("Erase existing vehicle history first", false);
    reset = check("Restart head unit after successful write", true);
    label(
        "History cannot be read back with the known upstream protocol. Photograph the current"
            + " iDrive history and export your records before replacing it.");
    button("Preview exact requests", false, () -> preview());
    experimental = check("Enable experimental vehicle changes this session", false);
    backup = check("I have backed up the history currently in iDrive", false);
    button("Write service history…", true, () -> write());
    card(p, "Independent head-unit controls");
    label(
        "These controls work without writing any service records. They require the same vehicle"
            + " identity and supported-family checks. Erasing is permanent and cannot be undone by"
            + " this app.");
    button("Restart head unit only…", false, () -> independentControl(false));
    button("Erase vehicle history only…", false, () -> independentControl(true));
    card(p, "Service due · CBS");
    label(
        "Read-only scan of cluster slots 0x4101–0x417F. Each live response is shown with its raw"
            + " bytes. Reset controls require a known head-unit family and experimental changes"
            + " enabled.");
    button("Read CBS from vehicle", false, () -> readCbs());
    cbsList = new LinearLayout(this);
    cbsList.setOrientation(LinearLayout.VERTICAL);
    body.addView(cbsList);
  }

  static boolean localAddress(String host) {
    try {
      String[] p = host.split("\\.", -1);
      if (p.length != 4) return false;
      int[] a = new int[4];
      for (int i = 0; i < 4; i++) {
        if (!p[i].matches("[0-9]{1,3}")) return false;
        a[i] = Integer.parseInt(p[i]);
        if (a[i] > 255) return false;
      }
      return a[0] == 10
          || a[0] == 172 && a[1] >= 16 && a[1] <= 31
          || a[0] == 192 && a[1] == 168
          || a[0] == 169 && a[1] == 254
          || BuildConfig.DEBUG && a[0] == 127;
    } catch (Exception e) {
      return false;
    }
  }

  final class Target {
    String ip;
    int port;
    Network network;

    Target() throws Exception {
      ip = val(host);
      if (!localAddress(ip))
        throw new IOException("Enter a private or link-local IPv4 gateway address.");
      port = Integer.parseInt(val(MainActivity.this.port));
      if (port < 1 || port > 65535) throw new IOException("Port must be 1–65535");
      network = network();
    }

    String key() {
      return ip + ":" + port + " / " + network.getNetworkHandle();
    }
  }

  Network network() throws IOException {
    ConnectivityManager cm = getSystemService(ConnectivityManager.class);
    int type =
        route.getSelectedItemPosition() == 0
            ? NetworkCapabilities.TRANSPORT_ETHERNET
            : NetworkCapabilities.TRANSPORT_WIFI;
    for (Network n : cm.getAllNetworks()) {
      NetworkCapabilities cap = cm.getNetworkCapabilities(n);
      if (cap != null && cap.hasTransport(type)) return n;
    }
    throw new IOException(
        "Connect the selected " + (type == 3 ? "Ethernet" : "Wi-Fi") + " network first.");
  }

  Hsfz client(Target t) throws Exception {
    return new Hsfz(t.network.getSocketFactory().createSocket(), t.ip, t.port);
  }

  String identifyTarget(Target t) throws Exception {
    try (DatagramSocket s = new DatagramSocket(null)) {
      t.network.bindSocket(s);
      s.bind(new InetSocketAddress(0));
      return Hsfz.identify(s, t.ip);
    }
  }

  interface Work {
    void run() throws Exception;
  }

  void work(String name, Work task) {
    if (!available()) return;
    busy = true;
    status.setText(name + "… Keep the app open.");
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    worker.execute(
        () -> {
          try {
            task.run();
            handler.post(
                () -> {
                  status.setText(name + " finished · review Monitor");
                });
          } catch (Exception e) {
            append(
                "STOPPED: "
                    + e.getMessage()
                    + "\n"
                    + "Do not assume the vehicle was unchanged. Check the result before"
                    + " retrying.");
            handler.post(
                () -> {
                  identified = "";
                  identity.setText("Vehicle identity must be checked again.");
                  status.setText(name + " stopped · review Monitor");
                  message(name + " stopped: " + e.getMessage());
                });
          } finally {
            handler.post(
                () -> {
                  busy = false;
                  getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
          }
        });
  }

  void append(String text) {
    handler.post(
        () -> {
          session += LocalTime.now().withNano(0) + "  " + text + "\n";
          if (session.length() > 160000) session = session.substring(session.length() - 160000);
          if (log != null) log.setText(session);
        });
  }

  void identify() {
    try {
      Target t = new Target();
      work(
          "Vehicle identity",
          () -> {
            String result = identifyTarget(t);
            append(result);
            handler.post(
                () -> {
                  identified = t.key() + "|" + result;
                  identity.setText(
                      result
                          + "\n"
                          + "Identity checked this session. This does not validate model"
                          + " compatibility.");
                });
          });
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void discover() {
    try {
      Network net = network();
      ConnectivityManager cm = getSystemService(ConnectivityManager.class);
      LinkProperties lp = cm.getLinkProperties(net);
      String destination = "255.255.255.255";
      if (lp != null)
        for (LinkAddress a : lp.getLinkAddresses())
          if (a.getAddress() instanceof Inet4Address && a.getPrefixLength() < 31) {
            byte[] ip = a.getAddress().getAddress();
            int v = java.nio.ByteBuffer.wrap(ip).getInt(), mask = -1 << (32 - a.getPrefixLength());
            destination =
                InetAddress.getByAddress(java.nio.ByteBuffer.allocate(4).putInt(v | ~mask).array())
                    .getHostAddress();
            break;
          }
      final String broadcast = destination;
      work(
          "Vehicle discovery",
          () -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
              net.bindSocket(socket);
              socket.bind(new InetSocketAddress(0));
              socket.setBroadcast(true);
              String result = Hsfz.identify(socket, broadcast, true);
              append("Discovery: " + result);
              handler.post(
                  () -> {
                    host.setText(result.split(" · ")[0]);
                    identified = "";
                    identity.setText(
                        result + "\nTap Check vehicle identity to select this gateway.");
                  });
            }
          });
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  String plan(List<ServiceRecord> rs, boolean eraseFirst, boolean restart) {
    StringBuilder s = new StringBuilder("Target diagnostic address: 0x63\n");
    if (eraseFirst) s.append("Erase existing history\n31 01 a0 80\n\n");
    for (ServiceRecord r : rs)
      s.append(r.date)
          .append(" / ")
          .append(r.mileage)
          .append(" km\n")
          .append(ServiceRecord.hex(ServiceRecord.add(r)))
          .append("\n\n");
    if (restart) s.append("Restart head unit\n11 01\n");
    return s.toString();
  }

  void preview() {
    if (!available()) return;
    if (records.isEmpty()) {
      message("Add or import at least one record first.");
      return;
    }
    try {
      String value = plan(records, erase.isChecked(), reset.isChecked());
      planned = value;
      showText(
          "Exact UDS request preview",
          value + "\nOffline preview only. No vehicle requests have been sent.");
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  Target mutationTarget() throws Exception {
    if (family.getSelectedItemPosition() == 0)
      throw new IOException(
          "Select a head-unit family first. Unknown vehicle changes are disabled.");
    if (!experimental.isChecked())
      throw new IOException("Enable experimental vehicle changes for this session first.");
    Target t = new Target();
    if (!identified.startsWith(t.key() + "|"))
      throw new IOException("Check vehicle identity on this gateway and network first.");
    String found = identified.substring(identified.lastIndexOf(" · ") + 3);
    if (!found.matches("[A-HJ-NPR-Z0-9]{17}"))
      throw new IOException("A valid vehicle VIN is required before changes.");
    if (!val(vinInput).isEmpty() && !val(vinInput).equalsIgnoreCase(found))
      throw new IOException("Workspace VIN does not match the connected vehicle.");
    if (HeadUnitProtocol.isMgu(family.getSelectedItem().toString())) {
      if (!mguConsent.isChecked())
        throw new IOException("Acknowledge experimental MGU history support first.");
      if (!identified.equals(headUnitIdentity))
        throw new IOException(
            "For MGU, check vehicle identity then read head-unit identification on this connection"
                + " first.");
    }
    return t;
  }

  void verifyIdentity(Target t, String expected) throws Exception {
    String now = identifyTarget(t);
    if (!(t.key() + "|" + now).equals(expected))
      throw new IOException("Vehicle identity changed. No mutation was sent.");
  }

  void write() {
    writeRecords(records, erase.isChecked(), reset.isChecked());
  }

  File journalRoot() {
    return new File(getFilesDir(), "write-journals");
  }

  void requireExactHeadUnit(String profile) throws Exception {
    if (!RecordChecks.vin(vin) || !vin.equals(workspaceKey))
      throw new IOException("Open or copy your records into the connected VIN's workspace first");
    if (identified.isEmpty()
        || !identified.equals(headUnitIdentity)
        || !identified.endsWith(" · " + vin))
      throw new IOException("Read exact head-unit identification on this connection first");
    HeadUnitInfo.requireMatch(headUnitDetails, profile);
  }

  void executeHistory(
      Target target,
      String expected,
      String profile,
      HeadUnitInfo.Result exact,
      List<ServiceRecord> snapshot,
      boolean erase,
      boolean restart)
      throws Exception {
    synchronized (WriteJournal.class) {
      executeHistoryLocked(target, expected, profile, exact, snapshot, erase, restart);
    }
  }

  void executeHistoryLocked(
      Target target,
      String expected,
      String profile,
      HeadUnitInfo.Result exact,
      List<ServiceRecord> snapshot,
      boolean erase,
      boolean restart)
      throws Exception {
    String checkedVin = expected.substring(expected.lastIndexOf(" · ") + 3);
    HeadUnitInfo.requireMatch(exact, profile);
    WriteJournal journal =
        WriteJournal.begin(
            journalRoot(),
            checkedVin,
            profile,
            exact.variant,
            ServiceRecord.hex(exact.software),
            snapshot,
            erase,
            restart);
    journal.data.put("connection", target.key()).put("checked_gateway", expected);
    journal.persist();
    append("Persistent operation journal: " + journal.file.getName());
    boolean complete = false;
    try {
      verifyIdentity(target, expected);
      try (Hsfz h = client(target)) {
        HeadUnitInfo.recheck(h, exact);
        JSONArray steps = journal.data.getJSONArray("steps");
        for (int n = 0; n < steps.length(); n++) {
          JSONObject step = steps.getJSONObject(n);
          append(step.getString("kind") + " TX " + step.getString("request"));
          byte[] response = journal.send(n, request -> h.request(0x63, request), profile);
          append(step.getString("kind") + " RX " + ServiceRecord.hex(response));
          if (step.getString("kind").equals("erase")) Thread.sleep(200);
          if (step.getString("kind").equals("add")) Thread.sleep(100);
        }
      }
      complete = true;
    } finally {
      journal.finish(
          complete,
          complete
              ? "Responses confirmed; inspect iDrive. No vehicle read-back."
              : "Stopped. A pending send may have taken effect. Inspect iDrive; do not replay"
                    + " automatically.");
    }
  }

  void viewJournals(boolean export) {
    if (!available()) return;
    try {
      String report = WriteJournal.report(journalRoot(), vin);
      if (export) exportText(report, "BMW-service-write-journals.txt", "text/plain");
      else showText("Persistent write journals", report);
    } catch (Exception e) {
      message("Journal unavailable: " + e.getMessage());
    }
  }

  void writeRecords(List<ServiceRecord> selectedRecords, boolean er, boolean re) {
    if (!available()) return;
    try {
      if (selectedRecords.isEmpty()) throw new IOException("No records to write");
      if (!backup.isChecked())
        throw new IOException(
            "Back up the current iDrive history and tick the backup confirmation.");
      Target t = mutationTarget();
      final String profile = family.getSelectedItem().toString();
      requireExactHeadUnit(profile);
      final HeadUnitInfo.Result exactHeadUnit = headUnitDetails;
      RecordChecks.requireWritable(selectedRecords);
      RecordChecks.requireUnambiguousDates(selectedRecords, records);
      if (!er) WriteJournal.guard(journalRoot(), vin, selectedRecords);
      HeadUnitProtocol.validateForProfile(selectedRecords, profile);
      String exact = plan(selectedRecords, er, re);
      if (!exact.equals(planned))
        throw new IOException("Preview the exact requests after your latest changes first.");
      final List<ServiceRecord> snapshot =
          HistoryJson.parse(new JSONObject(HistoryJson.dump(selectedRecords, val(vinInput))));
      String id = identified;
      confirm(
          "Review vehicle write",
          id
              + "\n\n"
              + snapshot.size()
              + " record(s) · "
              + family.getSelectedItem()
              + "\n"
              + (er ? "PERMANENTLY ERASES existing history. " : "Adds records without erasing. ")
              + "Keep ignition on and stable battery power. Android real-car validation is pending."
              + " A connection failure can leave partial history.",
          () ->
              confirm(
                  "Send these requests now?",
                  "The head unit will receive the previewed records"
                      + (re ? " and restart after success." : ".")
                      + "\nKeep this app in the foreground until finished.",
                  () -> {
                    if (!available()) return;
                    work(
                        "Service-history write",
                        () -> {
                          executeHistory(t, id, profile, exactHeadUnit, snapshot, er, re);
                          append(
                              "All steps accepted by the ECU. Verify each record in iDrive; this is"
                                  + " not a read-back verification.");
                        });
                    select(2);
                  }));
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void routine(Hsfz h, byte[] request, String title, String profile) throws Exception {
    append(title + " TX " + ServiceRecord.hex(request));
    byte[] r = h.request(0x63, request);
    append(title + " RX " + ServiceRecord.hex(r));
    HeadUnitProtocol.requireSuccess(r, profile);
  }

  void readCbs() {
    try {
      Target t = new Target();
      final String checked = identified.startsWith(t.key() + "|") ? identified : "";
      work(
          "CBS read",
          () -> {
            List<Cbs> result = new ArrayList<>();
            try (Hsfz h = client(t)) {
              for (int slot = 1; slot <= 127; slot++) {
                try {
                  byte[] raw = h.request(0x60, new byte[] {0x22, 0x41, (byte) slot});
                  Cbs c = Cbs.decode(Arrays.copyOfRange(raw, 3, raw.length));
                  if (c != null && c.code == slot) {
                    result.add(c);
                    append(c.name() + " RX " + ServiceRecord.hex(c.raw));
                  }
                } catch (Hsfz.Negative e) {
                  if (e.nrc != 0x31 && e.nrc != 0x11 && e.nrc != 0x12) throw e;
                }
              }
            }
            handler.post(
                () -> {
                  cbsItems.clear();
                  cbsItems.addAll(result);
                  cbsTarget = checked;
                  renderCbs();
                });
            append("CBS read: " + result.size() + " populated slots.");
          });
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void renderCbs() {
    cbsList.removeAllViews();
    LinearLayout old = body;
    body = cbsList;
    label(
        cbsItems.isEmpty()
            ? "No supported CBS items returned."
            : "Live snapshot · refresh after any service reset.");
    for (Cbs c : cbsItems) {
      label(
          c.name()
              + " · "
              + (c.availability <= 100 ? c.availability + "%" : "Availability unknown")
              + "\n"
              + (c.remainingKm == null ? "No distance reported" : c.remainingKm + " km remaining")
              + " · Due "
              + (c.due == null ? "not reported" : c.due)
              + "\nResets: "
              + c.resets
              + " · Interval: "
              + c.intervalKm
              + " km / "
              + c.intervalMonths
              + " months\nRaw: "
              + ServiceRecord.hex(c.raw));
      button("Reset " + c.name() + "…", false, () -> resetCbs(c));
    }
    body = old;
  }

  void resetCbs(Cbs c) {
    if (!available()) return;
    try {
      if (HeadUnitProtocol.isMgu(family.getSelectedItem().toString()))
        throw new IOException(
            "CBS resets on MGU-equipped vehicles remain disabled pending cluster validation. MGU"
                + " mode currently supports head-unit history operations only.");
      Target t = mutationTarget();
      String id = identified;
      if (!id.equals(cbsTarget))
        throw new IOException(
            "Check this vehicle’s identity and read CBS again before resetting an item.");
      confirm(
          "Reset " + c.name() + "?",
          id
              + "\n\n"
              + "Only after completing the service. Requests 100% availability and increments the"
              + " service count in ECU 0x"
              + Integer.toHexString(Cbs.owner(c.code))
              + ". Android real-car validation is pending.\n\nTX "
              + ServiceRecord.hex(Cbs.reset(c.code)),
          () ->
              work(
                  "CBS reset",
                  () -> {
                    verifyIdentity(t, id);
                    try (Hsfz h = client(t)) {
                      append("CBS reset TX " + ServiceRecord.hex(Cbs.reset(c.code)));
                      append(
                          "CBS reset RX "
                              + ServiceRecord.hex(h.request(Cbs.owner(c.code), Cbs.reset(c.code))));
                    }
                    append("Reset accepted. Read CBS again and verify on the instrument cluster.");
                    handler.post(
                        () -> {
                          cbsItems.clear();
                          renderCbs();
                        });
                  }));
    } catch (Exception e) {
      message(e.getMessage());
    }
  }

  void monitor() {
    LinearLayout p = page(2, "Activity monitor", "Real requests, responses and operation results.");
    card(p, "Session output");
    log = label(session);
    log.setTypeface(Typeface.MONOSPACE);
    log.setTextSize(12);
    log.setTextIsSelectable(true);
    button(
        "Export session log",
        false,
        () -> exportText(session, "bmw-service-session.txt", "text/plain"));
    button("View persistent write journals", false, () -> viewJournals(false));
    button("Export write journals", false, () -> viewJournals(true));
    button(
        "Clear completed session log",
        false,
        () -> {
          if (available()) {
            session = "No vehicle operations yet.\n";
            log.setText(session);
          }
        });
    label(
        "Logs may contain the vehicle VIN and address. Review before sharing. A partial write must"
            + " be checked in iDrive before retrying.");
  }

  void settings() {
    LinearLayout p = page(3, "Settings", "Appearance, app updates and project credits.");
    card(p, "Appearance");
    String[] modes = {"System", "Light", "Dark"};
    Spinner mode = spinner("Theme", modes, Arrays.asList(modes).indexOf(theme));
    mode.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(AdapterView<?> a) {}

          public void onItemSelected(AdapterView<?> a, View v, int i, long id) {
            if (!modes[i].equals(theme)) {
              if (busy || updateBusy) {
                mode.setSelection(Arrays.asList(modes).indexOf(theme));
                message("Wait for the current operation to finish.");
                return;
              }
              save();
              prefs().edit().putString("theme", modes[i]).apply();
              recreate();
            }
          }
        });
    card(p, "GitHub app updates");
    label(
        "Updates from statist1c/BMW-Service-History-Manager. Preview releases are included by"
            + " default. Automatic checks run approximately daily when Android allows; download and"
            + " install require your action.");
    CheckBox automatic =
        check("Automatically check for updates", Updater.prefs(this).getBoolean("automatic", true));
    automatic.setOnCheckedChangeListener(
        (b, value) -> {
          Updater.prefs(this).edit().putBoolean("automatic", value).apply();
          Updater.schedule(this);
        });
    CheckBox previews =
        check("Include preview releases", Updater.prefs(this).getBoolean("previews", true));
    previews.setOnCheckedChangeListener(
        (button, enabled) -> {
          Updater.prefs(this)
              .edit()
              .putBoolean("previews", enabled)
              .remove("release")
              .putLong("last_check", 0)
              .putString("status", "Update channel changed · check for updates")
              .apply();
          if (updates != null) updates.setText("Update channel changed · check for updates");
        });
    updates =
        label(
            Updater.prefs(this)
                .getString(
                    "status", "Version " + BuildConfig.VERSION_NAME + " · no update check yet"));
    button("Check for updates", false, () -> checkUpdate(true));
    button("Review available update", true, () -> reviewUpdate());
    button(
        "Open GitHub releases",
        false,
        () -> open("https://github.com/" + BuildConfig.UPDATE_REPOSITORY + "/releases"));
    card(p, "Supported in this Android preview");
    label(
        "Native service record editor, item statuses and remaining values; JSON / TST import and"
            + " export; mileage sorting and manual ordering; exact UDS previews; experimental"
            + " native HSFZ discovery, identity, CBS read, CBS reset and history write.");
    label(
        "Requires vehicle validation: Android network routing, ECU acceptance, model coverage and"
            + " CBS ownership. MGU history changes require explicit experimental opt-in and a"
            + " scoped head-unit identity read; MGU CBS resets remain disabled. No history"
            + " read-back, rooting, firmware flashing or cloud service-book changes.");
    label(
        "Not ported: Stampbook QR transfer, 3D drive replay and experimental DID probes. Use"
            + " upstream desktop BMW Service Manager for these features.");
    card(p, "Licenses & credits");
    label(
        "BMW Service History Manager · "
            + BuildConfig.VERSION_NAME
            + "\n"
            + "Android adaptation for statist1c.\n"
            + "Based on devttys1/bmw-service-manager.\n"
            + "Required Notice: Copyright 2026 devttys1"
            + " (https://github.com/devttys1/bmw-service-manager)");
    label(
        "PolyForm Noncommercial License 1.0.0. Personal and other permitted noncommercial uses"
            + " only. Commercial use, paid service-business use and selling this derived app are"
            + " not permitted by this license.");
    button(
        "Read full license & notices",
        false,
        () -> {
          try {
            showText(
                "License & notices",
                new String(
                        Updater.read(getAssets().open("LICENSE.md"), 64000), StandardCharsets.UTF_8)
                    + "\n\n"
                    + "Android changes: native Java UI, record codec, ENET/CBS transport, local"
                    + " storage and GitHub updater. No upstream Python runtime, web libraries,"
                    + " fonts or 3D assets are bundled.\n\n"
                    + "BMW, iDrive and ISTA are trademarks of BMW AG. Independent project, no BMW"
                    + " affiliation or endorsement.");
          } catch (Exception e) {
            message(e.getMessage());
          }
        });
    button(
        "Open upstream project",
        false,
        () -> open("https://github.com/devttys1/bmw-service-manager"));
    label(
        "Privacy: records stay in private phone storage unless exported. Vehicle operations use"
            + " only the selected local network. GitHub receives update-check/download requests. No"
            + " telemetry. Android backup is disabled; keep your own exports.");
  }

  void checkUpdate(boolean manual) {
    if (updateBusy || busy) {
      if (manual) message("Wait for the current operation.");
      return;
    }
    updateBusy = true;
    if (updates != null) updates.setText("Checking GitHub…");
    worker.execute(
        () -> {
          try {
            Updater.Release r = Updater.check(this);
            handler.post(
                () -> {
                  updates.setText(Updater.prefs(this).getString("status", ""));
                  if (manual && r == null)
                    message("No newer compatible release found for the selected update channel.");
                });
          } catch (Exception e) {
            handler.post(
                () -> {
                  updates.setText("Update check: " + e.getMessage());
                  if (manual) message(e.getMessage());
                });
          } finally {
            handler.post(() -> updateBusy = false);
          }
        });
  }

  void reviewUpdate() {
    if (!available() || updateBusy) return;
    Updater.Release r = Updater.cached(this);
    if (r == null) {
      message(
          "Check for updates first. Publish a newer GitHub release with"
              + " one APK attached to offer an update.");
      return;
    }
    confirm(
        "Update to " + r.tag + "?",
        (r.preview ? "Preview release\n\n" : "")
            + r.notes
            + "\n\nDownload "
            + r.size
            + " bytes. SHA-256, app identity, newer version and signing certificate are checked"
            + " before installation.",
        () -> {
          if (!available() || updateBusy) return;
          updateBusy = true;
          updates.setText("Downloading and verifying…");
          worker.execute(
              () -> {
                try {
                  File cached = Updater.apk(this);
                  boolean ready = false;
                  if (cached.exists()) {
                    try {
                      Updater.validate(this, cached, r);
                      ready = true;
                    } catch (Exception ignored) {
                    }
                  }
                  if (!ready) Updater.download(this, r);
                  handler.post(
                      () -> {
                        updates.setText(
                            "Verified update ready. Android will ask before installing.");
                        confirm(
                            "Install verified update?",
                            "If Android asks to allow installs from this app, enable it, then tap"
                                + " Review available update again.",
                            () -> {
                              try {
                                Updater.install(this, r);
                              } catch (Exception e) {
                                message(e.getMessage());
                              }
                            });
                      });
                } catch (Exception e) {
                  handler.post(
                      () -> {
                        updates.setText("Update rejected: " + e.getMessage());
                        message(e.getMessage());
                      });
                } finally {
                  handler.post(() -> updateBusy = false);
                }
              });
        });
  }

  void open(String url) {
    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
  }

  void confirm(String title, String detail, Runnable action) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(detail)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Continue",
            (d, w) -> {
              try {
                action.run();
              } catch (Exception e) {
                message(e.getMessage());
              }
            })
        .show();
  }

  void showText(String title, String value) {
    ScrollView s = new ScrollView(this);
    TextView t = text(value, 13, INK);
    t.setTypeface(Typeface.MONOSPACE);
    t.setTextIsSelectable(true);
    t.setPadding(dp(20), dp(12), dp(20), dp(16));
    s.addView(t);
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setView(s)
        .setPositiveButton("Close", null)
        .show();
  }

  void message(String message) {
    if (isFinishing() || isDestroyed()) return;
    new AlertDialog.Builder(this)
        .setTitle("BMW Service History Manager")
        .setMessage(message == null ? "Operation failed" : message)
        .setPositiveButton("OK", null)
        .show();
  }

  @Override
  protected void onPause() {
    super.onPause();
    save();
    prefs()
        .edit()
        .putString("host", val(host))
        .putString("port", val(port))
        .putInt("route", route.getSelectedItemPosition())
        .apply();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putInt("tab", selected);
  }

  @Override
  public void onBackPressed() {
    if (busy || updateBusy) {
      message("Keep the app open until the current operation finishes.");
      return;
    }
    super.onBackPressed();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    worker.shutdown();
  }

  final class Icon extends View {
    int color;
    final int kind;
    final Paint p = new Paint(3);

    Icon(Context c, int k) {
      super(c);
      kind = k;
      setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    protected void onDraw(Canvas c) {
      c.save();
      c.scale(getWidth() / 24f, getHeight() / 24f);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.8f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      if (kind == 0) {
        c.drawRoundRect(4, 3, 20, 21, 2, 2, p);
        c.drawLine(8, 8, 16, 8, p);
        c.drawLine(8, 12, 16, 12, p);
        c.drawLine(8, 16, 13, 16, p);
      }
      if (kind == 1) {
        c.drawRoundRect(3, 10, 21, 18, 2, 2, p);
        c.drawLine(5, 10, 8, 5, p);
        c.drawLine(8, 5, 16, 5, p);
        c.drawLine(16, 5, 19, 10, p);
        c.drawLine(6, 18, 6, 21, p);
        c.drawLine(18, 18, 18, 21, p);
        c.drawLine(6, 14, 8, 14, p);
        c.drawLine(16, 14, 18, 14, p);
      }
      if (kind == 2) {
        c.drawRoundRect(2, 4, 22, 20, 2, 2, p);
        Path line = new Path();
        line.moveTo(4, 13);
        line.lineTo(8, 13);
        line.lineTo(10, 8);
        line.lineTo(14, 17);
        line.lineTo(16, 12);
        line.lineTo(20, 12);
        c.drawPath(line, p);
      }
      if (kind == 3) {
        c.drawCircle(12, 12, 6, p);
        c.drawCircle(12, 12, 2, p);
        for (int i = 0; i < 8; i++) {
          double a = i * Math.PI / 4;
          c.drawLine(
              12 + (float) Math.cos(a) * 7,
              12 + (float) Math.sin(a) * 7,
              12 + (float) Math.cos(a) * 9,
              12 + (float) Math.sin(a) * 9,
              p);
        }
      }
      c.restore();
    }
  }
}
