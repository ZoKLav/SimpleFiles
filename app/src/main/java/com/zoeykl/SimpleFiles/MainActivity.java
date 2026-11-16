package com.zoeykl.SimpleFiles;

// Developed by ZoeyKL

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity {
  private LinearLayout root, toolbar, tabsBar, listHost, bottomBar, breadcrumbs;
  private TextView title, pathLabel, backButton, forwardButton, upButton;
  private LinearLayout actionRibbon;
  private final Map<String, View> actionButtons = new HashMap<>();
  private Switch rootSwitch;
  private String rootError;
  private Appearance appearance;
  private SharedPreferences prefs;
  private File current, base;
  private boolean homeView = true,
      clipboardCut = false,
      grid = false,
      dark = false,
      rootMode = false,
      showHidden = false,
      descending = false;
  private boolean pickerMode = false, pickerMultiple = false, pickerDocuments = false;
  private String[] pickerMimeTypes = {"*/*"};
  private int activeTab = 0, sort = 0, accent = 0xff4169e1;
  private final String[] sortNames = {"Name", "Date modified", "Size", "Type"};
  private final ArrayList<BrowseTab> tabs = new ArrayList<>();
  private final LinkedHashSet<File> selected = new LinkedHashSet<>();
  private final HashSet<String> rootDirectories = new HashSet<>();
  private ArrayList<File> clipboard = new ArrayList<>(), visibleFiles = new ArrayList<>();
  private Uri safTree;
  private final ArrayList<String> safStack = new ArrayList<>();
  private final int REQ_PERMISSION = 101, REQ_TREE = 102;

  /** Each tab owns its back/forward stack; visiting a new path discards forward history. */
  private static class BrowseTab {
    ArrayList<String> history = new ArrayList<>();
    int position = 0;

    BrowseTab(String path) {
      history.add(path);
    }

    String path() {
      return history.get(position);
    }

    void visit(String path) {
      if (path().equals(path)) return;
      while (history.size() > position + 1) history.remove(history.size() - 1);
      history.add(path);
      position++;
    }
  }

  /**
   * Restore browsing state separately from preferences; external pickers never inherit root or
   * clipboard.
   */
  @Override
  public void onCreate(Bundle state) {
    prefs = getSharedPreferences("prefs", 0);
    dark = prefs.getBoolean("dark", false);
    setTheme(
        dark
            ? android.R.style.Theme_Material_NoActionBar
            : android.R.style.Theme_Material_Light_NoActionBar);
    super.onCreate(state);
    String action = getIntent().getAction();
    pickerMode =
        this instanceof PickerActivity
            && (Intent.ACTION_GET_CONTENT.equals(action)
                || Intent.ACTION_OPEN_DOCUMENT.equals(action)
                || Intent.ACTION_PICK.equals(action));
    if (pickerMode) {
      pickerDocuments = Intent.ACTION_OPEN_DOCUMENT.equals(action);
      pickerMultiple = getIntent().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
      String[] requested = getIntent().getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
      String fallback = getIntent().getType();
      if (requested != null && requested.length > 0) pickerMimeTypes = requested;
      else pickerMimeTypes = new String[] {fallback == null ? "*/*" : fallback};
    }
    base = Environment.getExternalStorageDirectory();
    if (base == null) base = new File("/");
    current = base;
    loadPrefs();
    if (state != null) {
      int count = state.getInt("tabCount", 0);
      for (int i = 0; i < count; i++) {
        ArrayList<String> history = state.getStringArrayList("history" + i);
        if (history == null || history.isEmpty()) continue;
        BrowseTab t = new BrowseTab("");
        t.history = history;
        t.position = Math.min(state.getInt("position" + i), history.size() - 1);
        tabs.add(t);
      }
      activeTab = Math.max(0, Math.min(state.getInt("active"), tabs.size() - 1));
      ArrayList<String> clips = state.getStringArrayList("clipboard");
      if (clips != null) for (String path : clips) clipboard.add(new File(path));
      clipboardCut = state.getBoolean("cut");
      ArrayList<String> selection = state.getStringArrayList("selection");
      if (selection != null) for (String path : selection) selected.add(new File(path));
    }
    if (pickerMode) {
      rootMode = false;
      clipboard.clear();
      if (state == null) selected.clear();
    }
    if (tabs.isEmpty()) tabs.add(new BrowseTab(startupLocation()));
    restoreLocation();
    buildUi();
    render();
    requestStorage();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putInt("tabCount", tabs.size());
    state.putInt("active", activeTab);
    for (int i = 0; i < tabs.size(); i++) {
      state.putStringArrayList("history" + i, tabs.get(i).history);
      state.putInt("position" + i, tabs.get(i).position);
    }
    ArrayList<String> paths = new ArrayList<>();
    for (File f : clipboard) paths.add(f.getAbsolutePath());
    state.putStringArrayList("clipboard", paths);
    state.putBoolean("cut", clipboardCut);
    paths = new ArrayList<>();
    for (File f : selected) paths.add(f.getAbsolutePath());
    state.putStringArrayList("selection", paths);
  }

  private void loadPrefs() {
    rootMode = !pickerMode && prefs.getBoolean("rootMode", false);
    appearance = new Appearance(this);
    dark = appearance.dark;
    accent = appearance.color("accent");
    grid = prefs.getBoolean("grid", false);
    showHidden = prefs.getBoolean("hidden", false);
    sort = Math.max(0, Math.min(3, prefs.getInt("sort", 0)));
    descending = prefs.getBoolean("descending", false);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (root != null) {
      boolean previous = dark;
      loadPrefs();
      if (previous != dark)
        setTheme(
            dark
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
      buildUi();
      render();
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    render();
  }

  private int bg() {
    return appearance.color("background");
  }

  private int surface() {
    return appearance.color("surface");
  }

  private int fg() {
    return appearance.color("text");
  }

  private int muted() {
    return appearance.muted();
  }

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(radius);
    return d;
  }

  private TextView text(String s, int size, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(fg());
    if (bold) t.setTypeface(null, 1);
    return t;
  }

  private TextView button(LinearLayout parent, String label, String description, Runnable action) {
    TextView b = text(label, 24, true);
    b.setGravity(Gravity.CENTER);
    b.setTextColor(appearance.color("toolbarColor"));
    b.setContentDescription(description);
    parent.addView(b, new LinearLayout.LayoutParams(dp(48), dp(48)));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  /**
   * Portrait Explorer layout: tabs, navigation/address, view controls, files, then touch commands.
   */
  private void buildUi() {
    getWindow().setStatusBarColor(surface());
    getWindow().setNavigationBarColor(Appearance.blend(surface(), Color.BLACK, .75f));
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            Color.luminance(surface()) > 0.5 ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
    root = new LinearLayout(this);
    root.setOrientation(1);
    root.setBackgroundColor(bg());
    setContentView(root);
    toolbar = new LinearLayout(this);
    toolbar.setOrientation(1);
    toolbar.setBackgroundColor(surface());
    root.addView(toolbar);
    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(16), 0, dp(4), 0);
    toolbar.addView(top);
    title = text("SimpleFiles", 21, true);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    top.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
    title.setGravity(Gravity.CENTER_VERTICAL);
    button(
        top,
        pickerMode ? "×" : "⌕",
        pickerMode ? "Cancel selection" : "Search folder",
        () -> {
          if (pickerMode) {
            setResult(RESULT_CANCELED);
            finish();
          } else search();
        });
    button(top, "↻", "Refresh folder", () -> render());
    if (!pickerMode) button(top, "⚙", "Settings", () -> openSettings());
    HorizontalScrollView tabScroll = new HorizontalScrollView(this);
    tabScroll.setHorizontalScrollBarEnabled(false);
    tabsBar = new LinearLayout(this);
    tabsBar.setPadding(dp(8), 0, dp(8), 0);
    tabScroll.addView(tabsBar);
    toolbar.addView(tabScroll, new LinearLayout.LayoutParams(-1, dp(48)));
    LinearLayout navigation = new LinearLayout(this);
    navigation.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.addView(navigation);
    backButton = button(navigation, "←", "Back", () -> history(-1));
    forwardButton = button(navigation, "→", "Forward", () -> history(1));
    upButton = button(navigation, "↑", "Parent folder", () -> up());
    button(navigation, "⌂", "Home", () -> goHome());
    pathLabel = text("", 12, false);
    pathLabel.setTextColor(muted());
    pathLabel.setSingleLine(true);
    pathLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
    pathLabel.setGravity(Gravity.CENTER_VERTICAL);
    pathLabel.setPadding(dp(8), 0, dp(12), 0);
    pathLabel.setContentDescription("Address bar: tap to enter a folder path");
    navigation.addView(pathLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
    pathLabel.setOnClickListener(v -> address());
    HorizontalScrollView breadcrumbScroll = new HorizontalScrollView(this);
    breadcrumbScroll.setHorizontalScrollBarEnabled(false);
    breadcrumbs = new LinearLayout(this);
    breadcrumbs.setPadding(dp(8), 0, dp(8), 0);
    breadcrumbScroll.addView(breadcrumbs);
    toolbar.addView(breadcrumbScroll, new LinearLayout.LayoutParams(-1, dp(40)));
    LinearLayout viewBar = new LinearLayout(this);
    viewBar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.addView(viewBar);
    TextView view = text(grid ? "▦  Icons" : "☷  Details", 13, true);
    view.setGravity(Gravity.CENTER);
    viewBar.addView(view, new LinearLayout.LayoutParams(0, dp(48), 1));
    view.setOnClickListener(
        v -> {
          grid = !grid;
          savePrefs();
          buildUi();
          render();
        });
    TextView sortButton = text("↕  Sort", 13, true);
    sortButton.setGravity(Gravity.CENTER);
    viewBar.addView(sortButton, new LinearLayout.LayoutParams(0, dp(48), 1));
    sortButton.setOnClickListener(v -> chooseSort());
    if (!pickerMode) {
      button(viewBar, "▣", "Open Android folder provider", () -> pickTree());
      rootSwitch = new Switch(this);
      rootSwitch.setText("Root  ");
      rootSwitch.setTextColor(fg());
      rootSwitch.setChecked(rootMode);
      rootSwitch.setContentDescription("Root Mode");
      viewBar.addView(rootSwitch, new LinearLayout.LayoutParams(-2, dp(48)));
      rootSwitch.setOnCheckedChangeListener((b, enabled) -> changeRoot(enabled));
    }
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    listHost = new LinearLayout(this);
    listHost.setOrientation(1);
    listHost.setPadding(dp(8), dp(8), dp(8), dp(12));
    scroll.addView(listHost);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (!pickerMode) buildActionRibbon();
    bottomBar = new LinearLayout(this);
    bottomBar.setPadding(dp(14), dp(8), dp(14), dp(8));
    bottomBar.setGravity(Gravity.CENTER_VERTICAL);
    bottomBar.setBackgroundColor(surface());
    root.addView(bottomBar, new LinearLayout.LayoutParams(-1, -2));
  }

  /** Refresh the visible folder and keep selection, command availability and navigation in sync. */
  private void render() {
    if (root == null) return;
    visibleFiles = homeView ? new ArrayList<>() : files();
    selected.retainAll(visibleFiles);
    title.setText(
        pickerMode
            ? (pickerMultiple ? "Choose files" : "Choose a file")
            : (selected.isEmpty()
                ? (homeView ? "Home" : shortName(current))
                : selected.size() + " selected"));
    pathLabel.setText(homeView ? "Home" : current.getAbsolutePath());
    renderTabs();
    renderBreadcrumbs();
    renderFiles();
    renderBottom();
    updateActions();
    BrowseTab t = tabs.get(activeTab);
    backButton.setEnabled(t.position > 0);
    backButton.setAlpha(t.position > 0 ? 1f : .3f);
    forwardButton.setEnabled(t.position < t.history.size() - 1);
    forwardButton.setAlpha(forwardButton.isEnabled() ? 1f : .3f);
    upButton.setEnabled(!homeView && current.getParentFile() != null);
    upButton.setAlpha(upButton.isEnabled() ? 1f : .3f);
  }

  private void renderTabs() {
    tabsBar.removeAllViews();
    for (int i = 0; i < tabs.size(); i++) {
      final int at = i;
      LinearLayout tab = new LinearLayout(this);
      tab.setGravity(Gravity.CENTER_VERTICAL);
      GradientDrawable border = shape(i == activeTab ? bg() : surface(), dp(5));
      if (i == activeTab) border.setStroke(dp(1), accent);
      tab.setBackground(border);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
      lp.setMargins(dp(3), dp(4), dp(3), dp(4));
      tabsBar.addView(tab, lp);
      TextView name =
          text(
              tabs.get(i).path().isEmpty() ? "Home" : shortName(new File(tabs.get(i).path())),
              13,
              i == activeTab);
      name.setPadding(dp(12), 0, dp(12), 0);
      name.setGravity(Gravity.CENTER);
      tab.addView(name, new LinearLayout.LayoutParams(-2, -1));
      name.setOnClickListener(
          v -> {
            activeTab = at;
            selected.clear();
            restoreLocation();
            render();
          });
      if (tabs.size() > 1) {
        TextView close = text("×", 20, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close tab");
        tab.addView(close, new LinearLayout.LayoutParams(dp(40), -1));
        close.setOnClickListener(
            v -> {
              tabs.remove(at);
              if (at < activeTab) activeTab--;
              activeTab = Math.min(activeTab, tabs.size() - 1);
              selected.clear();
              restoreLocation();
              render();
            });
      }
    }
    button(tabsBar, "+", "New tab", () -> newTab());
  }

  private void renderBreadcrumbs() {
    breadcrumbs.removeAllViews();
    crumb("Home", "");
    if (homeView) return;
    ArrayList<File> chain = new ArrayList<>();
    File f = current;
    while (f != null) {
      chain.add(0, f);
      if (f.equals(base)) break;
      f = f.getParentFile();
    }
    for (File part : chain) crumb(shortName(part), part.getAbsolutePath());
  }

  private void crumb(String label, String path) {
    TextView c = text("  " + label + "  ›", 12, false);
    c.setTextColor(appearance.color("toolbarColor"));
    c.setGravity(Gravity.CENTER_VERTICAL);
    breadcrumbs.addView(c, new LinearLayout.LayoutParams(-2, dp(40)));
    c.setOnClickListener(v -> navigate(path));
  }

  private String shortName(File f) {
    if (f.equals(base)) return "Internal storage";
    return f.getName().isEmpty() ? "Root" : f.getName();
  }

  private String startupLocation() {
    String mode = prefs.getString("startup", "home");
    switch (mode) {
      case "internal":
        return base.getAbsolutePath();
      case "downloads":
        return new File(base, "Download").getAbsolutePath();
      case "custom":
        return prefs.getString("defaultPath", base.getAbsolutePath());
      case "last":
        return prefs.getString("lastPath", base.getAbsolutePath());
      default:
        return "";
    }
  }

  private void restoreLocation() {
    String location = tabs.get(activeTab).path();
    homeView = location.isEmpty();
    current = homeView ? base : new File(location);
  }

  private void navigate(String path) {
    tabs.get(activeTab).visit(path);
    selected.clear();
    restoreLocation();
    if (!homeView) prefs.edit().putString("lastPath", current.getAbsolutePath()).apply();
    render();
  }

  private void syncTab() {
    navigate(current.getAbsolutePath());
  }

  private void history(int step) {
    BrowseTab t = tabs.get(activeTab);
    int next = t.position + step;
    if (next < 0 || next >= t.history.size()) return;
    t.position = next;
    selected.clear();
    restoreLocation();
    if (!homeView) prefs.edit().putString("lastPath", current.getAbsolutePath()).apply();
    render();
  }

  private void newTab() {
    tabs.add(new BrowseTab(startupLocation()));
    activeTab = tabs.size() - 1;
    selected.clear();
    restoreLocation();
    render();
  }

  private void up() {
    if (!homeView && current.getParentFile() != null) navigate(current.getParent());
  }

  private void goHome() {
    navigate(
        prefs.getBoolean("homeToDefault", false)
            ? prefs.getString("defaultPath", base.getAbsolutePath())
            : "");
  }

  @Override
  public void onBackPressed() {
    if (!selected.isEmpty()) {
      selected.clear();
      render();
    } else if (tabs.get(activeTab).position > 0) history(-1);
    else if (!homeView) navigate("");
    else super.onBackPressed();
  }

  private void address() {
    EditText e = input("Absolute folder path");
    e.setText(current.getAbsolutePath());
    AlertDialog d =
        new DialogTheme.Builder(this)
            .setTitle("Go to folder")
            .setView(e)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      File f = new File(e.getText().toString().trim());
                      if (!f.isAbsolute() || (!rootMode && !f.isDirectory())) {
                        e.setError("Enter an existing absolute folder path");
                        return;
                      }
                      d.dismiss();
                      navigate(f.getAbsolutePath());
                    }));
    d.show();
  }

  /** Normal storage uses Java files; root listings additionally cache directory flags from su. */
  private ArrayList<File> files() {
    ArrayList<File> out = new ArrayList<>();
    rootError = null;
    if (rootMode) {
      try {
        rootDirectories.clear();
        String s = runRoot("ls -1Ap " + q(current.getAbsolutePath()));
        for (String line : s.split("\\n")) {
          if (line.trim().isEmpty() || line.startsWith("total ")) continue;
          boolean dir = line.endsWith("/");
          String name = dir ? line.substring(0, line.length() - 1) : line;
          if (name.startsWith(".") && !showHidden) continue;
          File item = new File(current, name);
          if (dir) rootDirectories.add(item.getAbsolutePath());
          out.add(item);
        }
      } catch (Exception error) {
        rootError = "Root access could not list this folder. Root Mode can be switched off above.";
      }
    } else {
      File[] fs = current.listFiles();
      if (fs != null) for (File f : fs) if (showHidden || !f.isHidden()) out.add(f);
    }
    if (pickerMode) out.removeIf(f -> !isDir(f) && (!f.isFile() || !pickerMimeMatches(f)));
    Collections.sort(
        out,
        (a, b) -> {
          if (isDir(a) != isDir(b)) return isDir(a) ? -1 : 1;
          int result;
          switch (sort) {
            case 1:
              result = Long.compare(a.lastModified(), b.lastModified());
              break;
            case 2:
              result = Long.compare(size(a), size(b));
              break;
            case 3:
              result = ext(a).compareToIgnoreCase(ext(b));
              break;
            default:
              result = a.getName().compareToIgnoreCase(b.getName());
          }
          if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
          return descending ? -result : result;
        });
    return out;
  }

  private void renderFiles() {
    listHost.removeAllViews();
    if (homeView) {
      renderHome();
      return;
    }
    if (!grid) {
      LinearLayout header = new LinearLayout(this);
      header.setPadding(dp(14), 0, dp(14), 0);
      listHost.addView(header);
      TextView name = text("Name" + (sort == 0 ? (descending ? " ↓" : " ↑") : ""), 12, true);
      name.setTextColor(muted());
      header.addView(name, new LinearLayout.LayoutParams(0, dp(36), 1));
      name.setGravity(Gravity.CENTER_VERTICAL);
      name.setOnClickListener(v -> setSort(0));
      TextView other = text(sortNames[sort] + (descending ? " ↓" : " ↑"), 12, false);
      other.setTextColor(muted());
      other.setGravity(Gravity.CENTER_VERTICAL);
      header.addView(other, new LinearLayout.LayoutParams(-2, dp(36)));
      other.setOnClickListener(v -> chooseSort());
    }
    if (visibleFiles.isEmpty()) {
      TextView empty =
          text(
              rootError != null
                  ? rootError
                  : current.canRead()
                      ? (pickerMode
                          ? "No files of the requested type in this folder"
                          : "This folder is empty")
                      : "This folder is unavailable. Check storage access or choose another"
                            + " location.",
              15,
              false);
      empty.setGravity(Gravity.CENTER);
      empty.setPadding(dp(20), dp(72), dp(20), dp(72));
      listHost.addView(empty);
      return;
    }
    if (grid) {
      int columns = Math.max(2, getResources().getConfiguration().screenWidthDp / 150);
      for (int i = 0; i < visibleFiles.size(); i += columns) {
        LinearLayout row = new LinearLayout(this);
        listHost.addView(row);
        for (int j = 0; j < columns; j++) {
          LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(156), 1);
          lp.setMargins(dp(3), dp(3), dp(3), dp(3));
          row.addView(
              i + j < visibleFiles.size() ? card(visibleFiles.get(i + j), true) : new View(this),
              lp);
        }
      }
    } else
      for (File f : visibleFiles) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(72));
        lp.bottomMargin = dp(1);
        listHost.addView(card(f, false), lp);
      }
  }

  private String displayName(File f) {
    String name = f.getName();
    if (!isDir(f) && !prefs.getBoolean("extensions", true)) {
      int dot = name.lastIndexOf('.');
      if (dot > 0) name = name.substring(0, dot);
    }
    return name;
  }

  /** A row or icon tile shares opening/selection behavior; only its presentation changes. */
  private View card(File f, boolean asGrid) {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(asGrid ? 1 : 0);
    box.setGravity(Gravity.CENTER_VERTICAL);
    box.setPadding(dp(8), dp(5), dp(5), dp(5));
    GradientDrawable background =
        shape(selected.contains(f) ? Appearance.blend(accent, surface(), .84f) : surface(), dp(4));
    if (selected.contains(f)) background.setStroke(dp(1), accent);
    box.setBackground(background);
    box.setAlpha(clipboardCut && clipboard.contains(f) ? .5f : 1f);
    ImageView icon = new ImageView(this);
    icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    icon.setImageDrawable(
        new FileIcon(isDir(f), ext(f), appearance.color(isDir(f) ? "folderColor" : "fileColor")));
    icon.setContentDescription(isDir(f) ? "Folder" : "File");
    if (prefs.getBoolean("thumbnails", true) && isImage(f) && f.isFile() && !rootMode) {
      try {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 192) sample *= 2;
        o.inSampleSize = sample;
        o.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (bitmap != null) icon.setImageBitmap(bitmap);
      } catch (Exception ignored) {
      }
    }
    if (asGrid) {
      LinearLayout tools = new LinearLayout(this);
      TextView tick =
          text(pickerMode && isDir(f) ? "›" : (selected.contains(f) ? "✓" : "□"), 17, true);
      tick.setTextColor(accent);
      tools.addView(tick, new LinearLayout.LayoutParams(0, dp(48), 1));
      tick.setGravity(Gravity.CENTER);
      tick.setContentDescription("Select " + f.getName());
      tick.setOnClickListener(v -> toggleSelection(f));
      box.addView(tools);
      box.addView(icon, new LinearLayout.LayoutParams(-1, dp(62)));
      TextView name = text(displayName(f), 13, false);
      name.setMaxLines(2);
      name.setGravity(Gravity.CENTER);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      box.addView(name, new LinearLayout.LayoutParams(-1, 0, 1));
    } else {
      TextView tick =
          text(pickerMode && isDir(f) ? "›" : (selected.contains(f) ? "✓" : "□"), 16, true);
      tick.setTextColor(accent);
      tick.setGravity(Gravity.CENTER);
      tick.setContentDescription("Select " + f.getName());
      box.addView(tick, new LinearLayout.LayoutParams(dp(48), -1));
      tick.setOnClickListener(v -> toggleSelection(f));
      box.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(44)));
      LinearLayout labels = new LinearLayout(this);
      labels.setOrientation(1);
      labels.setGravity(Gravity.CENTER_VERTICAL);
      labels.setPadding(dp(10), 0, 0, 0);
      box.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));
      TextView name = text(displayName(f), 15, false);
      name.setSingleLine(true);
      name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
      labels.addView(name);
      TextView meta =
          text(
              (isDir(f) ? "Folder" : pretty(size(f))) + "  ·  " + date(f.lastModified()),
              11,
              false);
      meta.setSingleLine(true);
      meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
      meta.setTextColor(muted());
      labels.addView(meta);
    }
    box.setOnClickListener(
        v -> {
          if (pickerMode) open(f);
          else if (!selected.isEmpty()) toggleSelection(f);
          else open(f);
        });
    box.setOnLongClickListener(
        v -> {
          toggleSelection(f);
          return true;
        });
    return box;
  }

  private void toggleSelection(File f) {
    if (pickerMode) {
      if (isDir(f)) {
        navigate(f.getAbsolutePath());
        return;
      }
      if (!pickerMimeMatches(f)) {
        toast("This file type wasn't requested");
        return;
      }
      if (!pickerMultiple) {
        finishPicking(Collections.singletonList(f));
        return;
      }
    }
    if (!selected.remove(f)) selected.add(f);
    render();
  }

  private void renderBottom() {
    bottomBar.removeAllViews();
    if (pickerMode) {
      TextView hint =
          text(
              pickerMultiple ? (selected.size() + " selected") : "Tap a file to choose", 13, false);
      hint.setTextColor(muted());
      hint.setGravity(Gravity.CENTER_VERTICAL);
      bottomBar.addView(hint, new LinearLayout.LayoutParams(0, dp(48), 1));
      if (pickerMultiple) {
        TextView add = text("Add (" + selected.size() + ")", 14, true);
        add.setTextColor(appearance.color("toolbarColor"));
        add.setGravity(Gravity.CENTER);
        add.setContentDescription("Add selected files to the requesting app");
        add.setEnabled(!selected.isEmpty());
        add.setAlpha(selected.isEmpty() ? .4f : 1f);
        bottomBar.addView(add, new LinearLayout.LayoutParams(dp(104), dp(48)));
        add.setOnClickListener(v -> finishPicking(new ArrayList<>(selected)));
      }
      TextView cancel = text("Cancel", 14, true);
      cancel.setTextColor(appearance.color("toolbarColor"));
      cancel.setGravity(Gravity.CENTER);
      bottomBar.addView(cancel, new LinearLayout.LayoutParams(dp(80), dp(48)));
      cancel.setOnClickListener(
          v -> {
            setResult(RESULT_CANCELED);
            finish();
          });
      return;
    }
    String status = homeView ? "Home" : visibleFiles.size() + " items";
    if (!selected.isEmpty()) status += " · " + selected.size() + " selected";
    if (!clipboard.isEmpty())
      status += " · " + clipboard.size() + (clipboardCut ? " to move" : " copied");
    if (rootMode) status += " · Root";
    TextView t = text(status, 12, false);
    t.setTextColor(muted());
    bottomBar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    if (!clipboard.isEmpty())
      button(
          bottomBar,
          "×",
          "Clear clipboard",
          () -> {
            clipboard.clear();
            render();
          });
  }

  private void homeHeading(String name) {
    TextView t = text(name, 15, true);
    t.setPadding(dp(10), dp(16), dp(10), dp(12));
    listHost.addView(t);
  }

  /** Quick access is a vertical alternative to Explorer's sidebar on a narrow phone screen. */
  private void renderHome() {
    homeHeading("Locations");
    homeShortcut("Internal storage", base);
    if (prefs.getBoolean("homeCommon", true)) {
      homeHeading("Quick access");
      for (String name :
          new String[] {"Download", "Documents", "DCIM", "Pictures", "Music", "Movies"})
        homeShortcut(
            name.equals("Download") ? "Downloads" : name.equals("DCIM") ? "Camera" : name,
            new File(base, name));
    }
    ArrayList<String> pinned =
        new ArrayList<>(prefs.getStringSet("pinned", Collections.emptySet()));
    Collections.sort(pinned);
    homeHeading("Pinned folders");
    if (pinned.isEmpty()) {
      TextView hint = text("Select a folder and tap Pin to keep it here.", 13, false);
      hint.setTextColor(muted());
      hint.setPadding(dp(10), 0, dp(10), dp(16));
      listHost.addView(hint);
    }
    for (String path : pinned) homeShortcut(shortName(new File(path)), new File(path));
  }

  private void homeShortcut(String name, File folder) {
    LinearLayout row = new LinearLayout(this);
    row.setPadding(dp(12), dp(8), dp(8), dp(8));
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setBackground(shape(surface(), dp(4)));
    ImageView icon = new ImageView(this);
    icon.setImageDrawable(new FileIcon(true, "", appearance.color("folderColor")));
    row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
    LinearLayout labels = new LinearLayout(this);
    labels.setOrientation(1);
    labels.setPadding(dp(12), 0, 0, 0);
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    labels.addView(text(name, 15, true));
    TextView path = text(folder.getAbsolutePath(), 11, false);
    path.setTextColor(muted());
    path.setSingleLine(true);
    path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
    labels.addView(path);
    ImageView pinIcon = new ImageView(this);
    pinIcon.setImageDrawable(new ActionIcon("pin", appearance.color("toolbarColor")));
    pinIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
    pinIcon.setContentDescription(isPinned(folder) ? "Unpin folder" : "Pin folder");
    row.addView(pinIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
    pinIcon.setOnClickListener(v -> pin(folder));
    row.setOnClickListener(v -> navigate(folder.getAbsolutePath()));
    row.setOnLongClickListener(
        v -> {
          openTab(folder);
          return true;
        });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(3);
    listHost.addView(row, lp);
  }

  private boolean isPinned(File f) {
    return prefs.getStringSet("pinned", Collections.emptySet()).contains(f.getAbsolutePath());
  }

  private void pin(File f) {
    Set<String> pins = new HashSet<>(prefs.getStringSet("pinned", Collections.emptySet()));
    if (!pins.remove(f.getAbsolutePath())) pins.add(f.getAbsolutePath());
    prefs.edit().putStringSet("pinned", pins).apply();
    render();
  }

  private void openTab(File f) {
    tabs.add(new BrowseTab(f.getAbsolutePath()));
    activeTab = tabs.size() - 1;
    selected.clear();
    restoreLocation();
    render();
  }

  /** Explorer's commands stay visible; enabled states follow selection and clipboard. */
  private void buildActionRibbon() {
    actionButtons.clear();
    actionRibbon = new LinearLayout(this);
    actionRibbon.setOrientation(1);
    actionRibbon.setBackgroundColor(surface());
    root.addView(actionRibbon);
    LinearLayout primary = new LinearLayout(this);
    actionRibbon.addView(primary);
    command(primary, "cut", "Cut", () -> putClipboard(new ArrayList<>(selected), true));
    command(primary, "copy", "Copy", () -> putClipboard(new ArrayList<>(selected), false));
    command(primary, "paste", "Paste", () -> paste());
    command(primary, "rename", "Rename", () -> rename(selected.iterator().next()));
    command(primary, "delete", "Delete", () -> confirmDelete(new ArrayList<>(selected)));
    LinearLayout secondary = new LinearLayout(this);
    actionRibbon.addView(secondary);
    command(secondary, "new", "New folder", () -> newFolder());
    command(
        secondary,
        "select",
        "Select all",
        () -> {
          if (selected.size() == visibleFiles.size()) selected.clear();
          else selected.addAll(visibleFiles);
          render();
        });
    command(secondary, "share", "Share", () -> shareFiles(new ArrayList<>(selected)));
    command(secondary, "info", "Properties", () -> details(selected.iterator().next()));
    command(
        secondary,
        "pin",
        "Pin",
        () -> pin(selected.isEmpty() ? current : selected.iterator().next()));
  }

  private void command(LinearLayout row, String icon, String label, Runnable action) {
    LinearLayout cell = new LinearLayout(this);
    cell.setOrientation(1);
    cell.setGravity(Gravity.CENTER);
    cell.setPadding(dp(2), dp(4), dp(2), dp(4));
    ImageView image = new ImageView(this);
    image.setImageDrawable(new ActionIcon(icon, appearance.color("toolbarColor")));
    cell.addView(image, new LinearLayout.LayoutParams(dp(23), dp(23)));
    TextView caption = text(label, 11, false);
    caption.setGravity(Gravity.CENTER);
    caption.setSingleLine(true);
    caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
    cell.addView(caption, new LinearLayout.LayoutParams(-1, dp(22)));
    cell.setContentDescription(label);
    cell.setFocusable(true);
    android.util.TypedValue value = new android.util.TypedValue();
    getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
    cell.setBackgroundResource(value.resourceId);
    row.addView(cell, new LinearLayout.LayoutParams(0, dp(56), 1));
    cell.setOnClickListener(v -> action.run());
    actionButtons.put(icon, cell);
  }

  private void actionEnabled(String key, boolean enabled) {
    View button = actionButtons.get(key);
    if (button != null) {
      button.setEnabled(enabled);
      button.setAlpha(enabled ? 1f : .3f);
    }
  }

  private void updateActions() {
    if (pickerMode) return;
    boolean any = !selected.isEmpty(), one = selected.size() == 1, share = any;
    for (File f : selected) if (isDir(f)) share = false;
    actionEnabled("cut", any);
    actionEnabled("copy", any);
    actionEnabled("paste", !homeView && !clipboard.isEmpty());
    actionEnabled("rename", one);
    actionEnabled("delete", any);
    actionEnabled("new", !homeView);
    actionEnabled("select", !visibleFiles.isEmpty());
    actionEnabled("share", share);
    actionEnabled("info", one);
    actionEnabled("pin", one ? isDir(selected.iterator().next()) : !any && !homeView);
    View select = actionButtons.get("select");
    if (select instanceof LinearLayout) {
      String label =
          any && selected.size() == visibleFiles.size() ? "Clear selection" : "Select all";
      ((TextView) ((LinearLayout) select).getChildAt(1)).setText(label);
      select.setContentDescription(label);
    }
    File target = one ? selected.iterator().next() : current;
    View pin = actionButtons.get("pin");
    if (pin instanceof LinearLayout) {
      String label = isPinned(target) ? "Unpin" : "Pin";
      ((TextView) ((LinearLayout) pin).getChildAt(1)).setText(label);
      pin.setContentDescription(label + " folder");
    }
  }

  private void openSettings() {
    Intent intent = new Intent(this, SettingsActivity.class);
    if (!homeView) intent.putExtra("currentFolder", current.getAbsolutePath());
    startActivity(intent);
  }

  /** Persist only a successful grant; su authorization never blocks the UI thread. */
  private void changeRoot(boolean enabled) {
    if (!enabled) {
      prefs.edit().putBoolean("rootMode", false).apply();
      rootMode = false;
      render();
      return;
    }
    rootSwitch.setEnabled(false);
    RootAccess.request(
        this,
        granted -> {
          rootMode = granted;
          prefs.edit().putBoolean("rootMode", granted).apply();
          buildUi();
          render();
          if (!granted) toast("Root access was not granted");
        });
  }

  /** Keep source paths until paste, with cut items dimmed rather than moved immediately. */
  private void putClipboard(List<File> targets, boolean cut) {
    clipboard = new ArrayList<>(targets);
    clipboardCut = cut;
    selected.clear();
    toast(clipboard.size() + (cut ? " item(s) ready to move" : " item(s) copied"));
    render();
  }

  /**
   * Picker requests return granted URIs; normal browsing chooses folder, preview or installed app.
   */
  private void open(File f) {
    if (pickerMode) {
      if (isDir(f)) navigate(f.getAbsolutePath());
      else if (pickerMultiple) toggleSelection(f);
      else finishPicking(Collections.singletonList(f));
      return;
    }
    if (isDir(f)) {
      navigate(f.getAbsolutePath());
      return;
    }
    if (ext(f).equals("zip")) {
      showZip(f);
      return;
    }
    if (isText(f)) {
      previewText(f);
      return;
    }
    try {
      Intent i = new Intent(Intent.ACTION_VIEW);
      i.setDataAndType(fileUri(f), mime(f));
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(i);
    } catch (Exception e) {
      toast("No app can open this file");
    }
  }

  /** Enforce the MIME type(s) requested by the receiving application. */
  private boolean pickerMimeMatches(File file) {
    String actual = mime(file);
    if ("*/*".equals(actual)) actual = "application/octet-stream";
    for (String requested : pickerMimeTypes)
      if (requested != null && ClipDescription.compareMimeTypes(actual, requested)) return true;
    return false;
  }

  /** Return a readable, permission-granted document URI, never a file:// path. */
  private void finishPicking(List<File> files) {
    if (files.isEmpty()) {
      toast("Choose at least one file");
      return;
    }
    try {
      ClipData clip = null;
      Uri first = null;
      for (File file : files) {
        if (!file.isFile() || !file.canRead() || !pickerMimeMatches(file)) {
          toast("Can't share " + file.getName());
          return;
        }
        Uri uri = SimpleDocumentsProvider.uriForFile(this, file);
        if (first == null) {
          first = uri;
          clip = ClipData.newUri(getContentResolver(), file.getName(), uri);
        } else clip.addItem(new ClipData.Item(uri));
      }
      Intent result = new Intent();
      result.setData(first);
      result.setClipData(clip);
      result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      if (pickerDocuments) result.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      setResult(RESULT_OK, result);
      finish();
    } catch (Exception e) {
      toast("Unable to provide the file: " + e.getMessage());
    }
  }

  private void setSort(int value) {
    if (sort == value) descending = !descending;
    else {
      sort = value;
      descending = false;
    }
    savePrefs();
    render();
  }

  private void chooseSort() {
    new DialogTheme.Builder(this)
        .setTitle("Sort by")
        .setSingleChoiceItems(
            sortNames,
            sort,
            (d, n) -> {
              setSort(n);
              d.dismiss();
            })
        .setPositiveButton(
            descending ? "Use ascending" : "Use descending",
            (d, w) -> {
              descending = !descending;
              savePrefs();
              render();
            })
        .show();
  }

  private void savePrefs() {
    prefs
        .edit()
        .putBoolean("grid", grid)
        .putBoolean("hidden", showHidden)
        .putInt("sort", sort)
        .putBoolean("descending", descending)
        .apply();
  }

  private void shareFiles(List<File> files) {
    if (files.size() == 1) {
      share(files.get(0));
      return;
    }
    try {
      ArrayList<Uri> uris = new ArrayList<>();
      for (File f : files) uris.add(fileUri(f));
      Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
      intent.setType("*/*");
      intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(intent, "Share files"));
    } catch (Exception e) {
      toast("Could not share selection");
    }
  }

  /**
   * Names are single path components, preventing accidental traversal and invalid file operations.
   */
  private boolean validName(EditText input) {
    String name = input.getText().toString().trim();
    if (name.isEmpty()
        || name.equals(".")
        || name.equals("..")
        || name.contains("/")
        || name.indexOf('\0') >= 0) {
      input.setError("Enter a name without path separators");
      return false;
    }
    return true;
  }

  private boolean operationExists(File f) {
    return rootMode
        ? root("test -e " + q(f.getAbsolutePath()) + " -o -L " + q(f.getAbsolutePath()))
        : f.exists();
  }

  private boolean operationDirectory(File f) {
    return rootMode ? root("test -d " + q(f.getAbsolutePath())) : f.isDirectory();
  }

  private void newFolder() {
    if (homeView) return;
    EditText input = input("Folder name");
    AlertDialog dialog =
        new DialogTheme.Builder(this)
            .setTitle("New folder")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      if (!validName(input)) return;
                      File folder = new File(current, input.getText().toString().trim());
                      if (operationExists(folder)) {
                        input.setError("That name already exists");
                        return;
                      }
                      boolean ok =
                          rootMode ? root("mkdir " + q(folder.getAbsolutePath())) : folder.mkdir();
                      if (ok) {
                        dialog.dismiss();
                        render();
                      } else input.setError("Could not create folder");
                    }));
    dialog.show();
  }

  private void rename(File file) {
    EditText input = input("New name");
    input.setText(file.getName());
    AlertDialog dialog =
        new DialogTheme.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      if (!validName(input)) return;
                      File dest = new File(file.getParentFile(), input.getText().toString().trim());
                      if (file.equals(dest)) {
                        dialog.dismiss();
                        return;
                      }
                      if (operationExists(dest)) {
                        input.setError("That name already exists");
                        return;
                      }
                      boolean ok =
                          rootMode
                              ? root(
                                  "mv "
                                      + q(file.getAbsolutePath())
                                      + " "
                                      + q(dest.getAbsolutePath()))
                              : file.renameTo(dest);
                      if (ok) {
                        dialog.dismiss();
                        selected.clear();
                        render();
                      } else input.setError("Rename failed");
                    }));
    dialog.show();
  }

  private EditText input(String hint) {
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setHint(hint);
    e.setTextColor(fg());
    e.setHintTextColor(muted());
    e.setPadding(dp(18), dp(12), dp(18), dp(12));
    return e;
  }

  private void confirmDelete(List<File> fs) {
    new DialogTheme.Builder(this)
        .setTitle("Delete " + fs.size() + " item(s)?")
        .setMessage("This cannot be undone.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Delete",
            (d, w) -> {
              int count = 0;
              for (File f : fs) if (delete(f)) count++;
              selected.clear();
              toast("Deleted " + count + " item(s)");
              render();
            })
        .show();
  }

  private boolean delete(File f) {
    if (rootMode) return root("rm -rf " + q(f.getAbsolutePath()));
    if (f.isDirectory()) {
      File[] cs = f.listFiles();
      if (cs != null) for (File c : cs) delete(c);
    }
    return f.delete();
  }

  /** Resolve name conflicts before doing any work; replacement remains an explicit confirmation. */
  private void paste() {
    if (homeView || clipboard.isEmpty()) return;
    if (!rootMode && (!current.isDirectory() || !current.canWrite())) {
      toast("This folder is not writable");
      return;
    }
    boolean conflict = false;
    for (File src : clipboard)
      if (operationExists(new File(current, src.getName()))) conflict = true;
    if (conflict)
      new DialogTheme.Builder(this)
          .setTitle("Items already exist")
          .setItems(
              new String[] {
                "Keep both (create unique names)", "Skip existing items", "Replace existing files"
              },
              (d, n) -> {
                if (n == 2)
                  new DialogTheme.Builder(this)
                      .setTitle("Replace matching files?")
                      .setMessage(
                          "Matching files will be overwritten. Existing folders will be skipped.")
                      .setNegativeButton("Cancel", null)
                      .setPositiveButton("Replace", (x, w) -> performPaste(2))
                      .show();
                else performPaste(n);
              })
          .setNegativeButton("Cancel", null)
          .show();
    else performPaste(0);
  }

  private File uniqueDestination(File dst) {
    String name = dst.getName(), suffix = "";
    int dot = name.lastIndexOf('.');
    if (dot > 0 && !operationDirectory(dst)) {
      suffix = name.substring(dot);
      name = name.substring(0, dot);
    }
    File candidate = dst;
    for (int n = 2; operationExists(candidate); n++)
      candidate = new File(dst.getParentFile(), name + " (" + n + ")" + suffix);
    return candidate;
  }

  /**
   * Refuse recursive self-copy, retain failed cut entries, and preserve existing folder conflicts.
   */
  private void performPaste(int policy) {
    int count = 0;
    ArrayList<File> remaining = new ArrayList<>();
    for (File src : new ArrayList<>(clipboard)) {
      File dst = new File(current, src.getName());
      boolean ok = false;
      try {
        String source = src.getCanonicalPath(), dest = dst.getCanonicalPath();
        if (dest.startsWith(source + File.separator)) {
          remaining.add(src);
          continue;
        }
        if (source.equals(dest) && clipboardCut) {
          count++;
          continue;
        }
        if (operationExists(dst)) {
          if (policy == 1
              || (policy == 2
                  && (operationDirectory(src) || operationDirectory(dst) || source.equals(dest)))) {
            remaining.add(src);
            continue;
          }
          if (policy == 0) dst = uniqueDestination(dst);
        }
        if (rootMode) {
          String cmd = clipboardCut ? "mv " : "cp -R ";
          ok = root(cmd + q(src.getAbsolutePath()) + " " + q(dst.getAbsolutePath()));
        } else if (clipboardCut) {
          ok = src.renameTo(dst);
          if (!ok) ok = copy(src, dst) && delete(src);
        } else ok = copy(src, dst);
      } catch (Exception ignored) {
      }
      if (ok) count++;
      else remaining.add(src);
    }
    if (clipboardCut) clipboard = remaining;
    toast(
        "Pasted "
            + count
            + " item(s)"
            + (remaining.isEmpty() ? "" : " · " + remaining.size() + " skipped or failed"));
    render();
  }

  /** Stream regular files and recurse into directories without loading entire files into memory. */
  private boolean copy(File source, File dest) {
    try {
      if (source.isDirectory()) {
        if (!dest.exists() && !dest.mkdirs()) return false;
        File[] children = source.listFiles();
        if (children == null) return false;
        for (File child : children) if (!copy(child, new File(dest, child.getName()))) return false;
        return true;
      }
      try (InputStream in = new FileInputStream(source);
          OutputStream out = new FileOutputStream(dest)) {
        byte[] bytes = new byte[32768];
        int n;
        while ((n = in.read(bytes)) > 0) out.write(bytes, 0, n);
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void details(File f) {
    String msg =
        "Path: "
            + f.getAbsolutePath()
            + "\nType: "
            + (isDir(f) ? "Folder" : mime(f))
            + "\nSize: "
            + (isDir(f) ? "—" : pretty(size(f)))
            + "\nModified: "
            + date(f.lastModified())
            + "\nReadable: "
            + f.canRead()
            + "  Writable: "
            + f.canWrite();
    new DialogTheme.Builder(this)
        .setTitle("Details")
        .setMessage(msg)
        .setPositiveButton("Close", null)
        .show();
  }

  private void showZip(File f) {
    try {
      java.util.zip.ZipFile z = new java.util.zip.ZipFile(f);
      ArrayList<String> names = new ArrayList<>();
      Enumeration<? extends java.util.zip.ZipEntry> en = z.entries();
      long total = 0;
      while (en.hasMoreElements()) {
        java.util.zip.ZipEntry e = en.nextElement();
        names.add(
            (e.isDirectory() ? "Folder: " : "File: ")
                + e.getName()
                + (e.isDirectory() ? "" : "  ·  " + pretty(e.getSize())));
        total += Math.max(0, e.getSize());
      }
      z.close();
      String[] a = names.toArray(new String[0]);
      new DialogTheme.Builder(this)
          .setTitle(f.getName() + "  ·  " + a.length + " entries")
          .setItems(a, (d, n) -> toast(a[n]))
          .setNeutralButton("Extract here", (d, w) -> extractZip(f))
          .setPositiveButton("Close", null)
          .show();
    } catch (Exception e) {
      toast("Could not read archive");
    }
  }

  /** Canonical containment rejects archive entries that would escape the destination folder. */
  private void extractZip(File zip) {
    new DialogTheme.Builder(this)
        .setTitle("Extract archive here?")
        .setMessage("Files with matching names may be replaced.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Extract",
            (d, w) -> {
              int count = 0;
              try {
                java.util.zip.ZipInputStream in =
                    new java.util.zip.ZipInputStream(new FileInputStream(zip));
                java.util.zip.ZipEntry en;
                byte[] b = new byte[32768];
                while ((en = in.getNextEntry()) != null) {
                  File out = new File(current, en.getName());
                  if (!out.getCanonicalPath()
                      .startsWith(current.getCanonicalPath() + File.separator)) continue;
                  if (en.isDirectory()) out.mkdirs();
                  else {
                    File p = out.getParentFile();
                    if (p != null) p.mkdirs();
                    FileOutputStream o = new FileOutputStream(out);
                    int n;
                    while ((n = in.read(b)) > 0) o.write(b, 0, n);
                    o.close();
                    count++;
                  }
                }
                in.close();
                toast("Extracted " + count + " file(s)");
              } catch (Exception e) {
                toast("Extraction failed: " + e.getMessage());
              }
              render();
            })
        .show();
  }

  private void previewText(File f) {
    try {
      BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
      StringBuilder b = new StringBuilder();
      String s;
      int lines = 0;
      while ((s = r.readLine()) != null && lines++ < 600) b.append(s).append('\n');
      r.close();
      ScrollView sv = new ScrollView(this);
      TextView t = text(b.toString(), 13, false);
      t.setTypeface(android.graphics.Typeface.MONOSPACE);
      t.setPadding(dp(16), dp(12), dp(16), dp(12));
      sv.addView(t);
      new DialogTheme.Builder(this)
          .setTitle(f.getName())
          .setView(sv)
          .setPositiveButton("Close", null)
          .show();
    } catch (Exception e) {
      toast("Could not preview file");
    }
  }

  private void search() {
    EditText e = input("Search this folder");
    new DialogTheme.Builder(this)
        .setTitle("Find files")
        .setView(e)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Search",
            (d, w) -> {
              String q = e.getText().toString().toLowerCase(Locale.ROOT);
              ArrayList<File> found = new ArrayList<>();
              searchRec(current, q, found, 0);
              String[] names = new String[found.size()];
              for (int i = 0; i < names.length; i++) names[i] = found.get(i).getAbsolutePath();
              new DialogTheme.Builder(this)
                  .setTitle(found.size() + " result(s)")
                  .setItems(
                      names,
                      (x, n) -> {
                        File f = found.get(n);
                        if (f.isDirectory()) {
                          current = f;
                          syncTab();
                          render();
                        } else open(f);
                      })
                  .setPositiveButton("Close", null)
                  .show();
            })
        .show();
  }

  private void searchRec(File dir, String q, List<File> out, int depth) {
    if (depth > 8 || out.size() > 250) return;
    File[] fs = dir.listFiles();
    if (fs == null) return;
    for (File f : fs) {
      if (f.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(f);
      if (f.isDirectory()) searchRec(f, q, out, depth + 1);
    }
  }

  private void share(File f) {
    try {
      Intent i = new Intent(Intent.ACTION_SEND);
      i.setType(mime(f));
      i.putExtra(Intent.EXTRA_STREAM, fileUri(f));
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(i, "Share file"));
    } catch (Exception e) {
      toast("Couldn't share file");
    }
  }

  /** Quote every user-controlled path before passing it to the superuser shell. */
  private String runRoot(String command) throws Exception {
    java.lang.Process p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    InputStream in = p.getInputStream();
    byte[] buf = new byte[4096];
    int n;
    while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
    if (p.waitFor() != 0) throw new IOException(b.toString("UTF-8").trim());
    return b.toString("UTF-8");
  }

  private boolean root(String command) {
    try {
      return runRoot(command + "; echo __SIMPLEFILES_OK__$?").contains("__SIMPLEFILES_OK__0");
    } catch (Exception e) {
      return false;
    }
  }

  private String q(String s) {
    return "'" + s.replace("'", "'\\''") + "'";
  }

  /**
   * Android 11+ uses all-files access; older supported versions use runtime storage permissions.
   */
  private void requestStorage() {
    if (Build.VERSION.SDK_INT >= 30) {
      if (!Environment.isExternalStorageManager()) {
        try {
          Intent i =
              new Intent(
                  Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                  Uri.parse("package:" + getPackageName()));
          startActivity(i);
        } catch (Exception e) {
          try {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
          } catch (Exception ignored) {
          }
        }
      }
    } else if (Build.VERSION.SDK_INT >= 23
        && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(
          new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
          },
          REQ_PERMISSION);
  }

  private void pickTree() {
    try {
      Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      i.addFlags(
          Intent.FLAG_GRANT_READ_URI_PERMISSION
              | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
              | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      startActivityForResult(i, REQ_TREE);
    } catch (Exception e) {
      toast("Folder picker unavailable");
    }
  }

  @Override
  protected void onActivityResult(int r, int c, Intent data) {
    super.onActivityResult(r, c, data);
    if (r == REQ_TREE && c == RESULT_OK && data != null) {
      safTree = data.getData();
      try {
        getContentResolver()
            .takePersistableUriPermission(
                safTree,
                data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
      } catch (Exception ignored) {
      }
      try {
        safStack.clear();
        safStack.add(DocumentsContract.getTreeDocumentId(safTree));
        showSafFolder();
      } catch (Exception e) {
        toast("Could not open selected folder");
      }
    }
  }

  /**
   * Provider folders use document IDs and grants rather than assuming they have filesystem paths.
   */
  private void showSafFolder() {
    if (safTree == null || safStack.isEmpty()) return;
    String docId = safStack.get(safStack.size() - 1);
    Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(safTree, docId);
    ArrayList<Uri> uris = new ArrayList<>();
    ArrayList<String> labels = new ArrayList<>();
    ArrayList<Boolean> dirs = new ArrayList<>();
    try (Cursor c =
        getContentResolver()
            .query(
                children,
                new String[] {
                  DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                  DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                  DocumentsContract.Document.COLUMN_MIME_TYPE,
                  DocumentsContract.Document.COLUMN_SIZE
                },
                null,
                null,
                null)) {
      if (c != null)
        while (c.moveToNext()) {
          String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
          boolean dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
          Uri u = DocumentsContract.buildDocumentUriUsingTree(safTree, id);
          uris.add(u);
          dirs.add(dir);
          labels.add(
              (dir ? "Folder: " : "File: ")
                  + name
                  + (dir ? "" : "  ·  " + pretty(c.isNull(3) ? 0 : c.getLong(3))));
        }
    } catch (Exception e) {
      toast("This folder provider could not be read");
      return;
    }
    String[] names = labels.toArray(new String[0]);
    new DialogTheme.Builder(this)
        .setTitle("Folder access · " + docId.substring(docId.lastIndexOf('/') + 1))
        .setItems(
            names,
            (d, n) -> {
              if (dirs.get(n)) {
                try {
                  safStack.add(DocumentsContract.getDocumentId(uris.get(n)));
                  showSafFolder();
                } catch (Exception e) {
                  toast("Could not open folder");
                }
              } else {
                try {
                  Intent i = new Intent(Intent.ACTION_VIEW);
                  i.setDataAndType(uris.get(n), getContentResolver().getType(uris.get(n)));
                  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  startActivity(i);
                } catch (Exception e) {
                  toast("No app can open this file");
                }
              }
            })
        .setNeutralButton(
            "Up",
            (d, w) -> {
              if (safStack.size() > 1) {
                safStack.remove(safStack.size() - 1);
                showSafFolder();
              }
            })
        .setNegativeButton("Close", null)
        .setPositiveButton("New folder", (d, w) -> safNewFolder())
        .show();
  }

  private void safNewFolder() {
    EditText e = input("Folder name");
    new DialogTheme.Builder(this)
        .setTitle("New folder")
        .setView(e)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Create",
            (d, w) -> {
              try {
                Uri parent =
                    DocumentsContract.buildDocumentUriUsingTree(
                        safTree, safStack.get(safStack.size() - 1));
                DocumentsContract.createDocument(
                    getContentResolver(),
                    parent,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    e.getText().toString().trim());
                showSafFolder();
              } catch (Exception x) {
                toast("Couldn't create folder");
              }
            })
        .show();
  }

  private boolean isDir(File f) {
    return rootMode ? rootDirectories.contains(f.getAbsolutePath()) : f.isDirectory();
  }

  private long size(File f) {
    return f.isFile() ? f.length() : 0;
  }

  private String pretty(long n) {
    if (n < 1024) return n + " B";
    String[] u = {"KB", "MB", "GB", "TB"};
    double d = n;
    int i = -1;
    do {
      d /= 1024;
      i++;
    } while (d >= 1024 && i < u.length - 1);
    return String.format(Locale.getDefault(), "%.1f %s", d, u[i]);
  }

  private String ext(File f) {
    String n = f.getName();
    int i = n.lastIndexOf('.');
    return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
  }

  private boolean isImage(File f) {
    return Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "bmp").contains(ext(f));
  }

  private boolean isText(File f) {
    return Arrays.asList(
            "txt",
            "md",
            "log",
            "json",
            "xml",
            "html",
            "htm",
            "java",
            "js",
            "css",
            "csv",
            "ini",
            "conf",
            "properties",
            "sh",
            "gradle")
        .contains(ext(f));
  }

  private String mime(File f) {
    String e = ext(f);
    if (e.equals("jpg")) e = "jpeg";
    String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
    return m == null ? "*/*" : m;
  }

  private String date(long t) {
    return t <= 0
        ? "—"
        : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(t));
  }

  private Uri fileUri(File f) {
    return new Uri.Builder()
        .scheme("content")
        .authority(getPackageName() + ".files")
        .appendPath(f.getAbsolutePath())
        .build();
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }
}
