package com.sr3launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Общий экран настроек для игр, описанных в Games: разрешение, пресеты, параметры графики. */
public class GameActivity extends Activity {
    private static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    private static final int[] HEIGHTS = {540, 720, 900, 1080};
    private static final String[] PRESET_NAMES = {"Минимум", "Низкие", "Средние", "Высокие", "Ультра"};

    private Games.Spec S;
    private String prefPath, bakPath, gameActivity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int nativeW = 2400, nativeH = 1080;

    private Hw hw;
    private android.widget.RadioGroup hudGroup;
    private static final String[] D3D = {"HKEY_CURRENT_USER", "Software", "IndirectX", "Direct3D", "Config"};
    private Spinner resSpinner;
    private int resExtra = -1; // высота из файла, если нестандартная
    private final List<Spinner> spinners = new ArrayList<Spinner>();
    private final List<Games.Opt> optList = new ArrayList<Games.Opt>();
    private final List<String> loadedExtra = new ArrayList<String>(); // нестандартное значение из файла для параметра
    private TextView status;
    private LinearLayout setupCard, setupBody;
    private boolean noAccessShown;

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(S.card);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        c.setPadding(dp(16), dp(12), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        TextView h = text(title.toUpperCase(java.util.Locale.getDefault()), 13, S.accent, true);
        h.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = dp(8);
        c.addView(h, hp);
        return c;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 16 : 14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? S.accent : S.btn2);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        return b;
    }

    private Spinner row(LinearLayout parent, String title, String hint) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(title, 15, S.text, false));
        if (hint != null && hint.length() > 0) names.addView(text(hint, 11, S.muted, false));
        r.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        Spinner sp = new Spinner(this);
        r.addView(sp, new LinearLayout.LayoutParams(dp(150), -2));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(8);
        parent.addView(r, rp);
        return sp;
    }

    private void fill(Spinner sp, List<String> labels, int sel) {
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(Math.max(0, Math.min(sel, labels.size() - 1)));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        S = Games.byId(getIntent().getStringExtra("game"));
        if (S == null) { finish(); return; }
        gameActivity = S.pkg + ".MainActivity";
        String dir = "/sdcard/Android/data/" + S.pkg + "/files/feral_app_support";
        prefPath = dir + "/preferences";
        bakPath = dir + "/preferences.sr3launcher.bak";
        try {
            android.graphics.Rect r = getWindowManager().getMaximumWindowMetrics().getBounds();
            nativeW = Math.max(r.width(), r.height());
            nativeH = Math.min(r.width(), r.height());
        } catch (Throwable ignored) { }
        getWindow().setStatusBarColor(S.bg);
        getWindow().setNavigationBarColor(S.bg);
        buildUi();
        Fs.init(new Runnable() { @Override public void run() { handler.post(new Runnable() { @Override public void run() { load(); refreshSetup(); } }); } });
        load();
        refreshSetup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (setupBody != null) {
            if (!Fs.ready()) Fs.init(new Runnable() { @Override public void run() { handler.post(new Runnable() { @Override public void run() { load(); refreshSetup(); } }); } });
            refreshSetup();
        }
    }

    @Override
    protected void onDestroy() {
        Fs.release();
        super.onDestroy();
    }

    private int logoRes() {
        if ("gas".equals(S.id)) return R.drawable.logo_gas;
        if ("bm".equals(S.id)) return R.drawable.logo_bm;
        if ("xc".equals(S.id)) return R.drawable.logo_xc;
        return R.drawable.logo_tp;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(S.bg);

        // шапка (как на экранах остальных игр)
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(24), dp(10), dp(24), dp(10));
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {S.card, S.bg}));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(logoRes());
        logo.setAdjustViewBounds(true);
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(dp(80), dp(46));
        lgp.rightMargin = dp(14);
        head.addView(logo, lgp);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(S.title, 22, Color.WHITE, true));
        titles.addView(text("Лаунчер: разрешение, графика и параметры игры", 13, S.muted, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout credit = new LinearLayout(this);
        credit.setOrientation(LinearLayout.VERTICAL);
        credit.setGravity(Gravity.END);
        credit.addView(text("Автор: Tsifrokayf", 13, S.text, false));
        TextView gh = text("github.com/Tsifrokayf", 13, S.accent, true);
        gh.setPaintFlags(gh.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        gh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Tsifrokayf"))); }
                catch (Exception e) { say("Не удалось открыть ссылку"); }
            }
        });
        credit.addView(gh);
        head.addView(credit, new LinearLayout.LayoutParams(-2, -2));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));
        View accentLine = new View(this);
        accentLine.setBackgroundColor(S.accent);
        root.addView(accentLine, new LinearLayout.LayoutParams(-1, dp(2)));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setPadding(dp(20), dp(8), dp(20), dp(8));
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(8);
        cols.addView(left, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1.25f);
        rp.leftMargin = dp(8);
        cols.addView(right, rp);
        sv.addView(cols, new ViewGroup.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        setupCard = card(left, "Первичная настройка");
        setupBody = setupCard;
        setupCard.setVisibility(View.GONE);

        hw = Hw.detect(this);
        LinearLayout ca = card(left, "Подбор под ваше железо");
        ca.addView(text(hw.describe(), 12, S.muted, false));
        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        Button a60 = button("Авто · 60 FPS", false);
        Button a30 = button("Авто · 30 FPS", false);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1f);
        ap.topMargin = dp(8);
        ap.rightMargin = dp(6);
        autoRow.addView(a60, ap);
        LinearLayout.LayoutParams ap2 = new LinearLayout.LayoutParams(0, -2, 1f);
        ap2.topMargin = dp(8);
        autoRow.addView(a30, ap2);
        ca.addView(autoRow);
        ca.addView(text("Стартовые значения по классу GPU. Это не замер: проверьте в игре со счётчиком FPS и поправьте вручную.", 11, S.muted, false));
        a60.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { applyAuto(60); } });
        a30.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { applyAuto(30); } });

        LinearLayout pc = card(left, "Пресеты графики");
        pc.addView(text("Выставляют разрешение и параметры графики. Лимит FPS не меняют.", 11, S.muted, false));
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final int lvl = i;
            Button pb = button(PRESET_NAMES[i], false);
            pb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pb.setPadding(dp(2), dp(8), dp(2), dp(8));
            pb.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { applyPreset(lvl); } });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
            bp.rightMargin = dp(4);
            pr.addView(pb, bp);
        }
        LinearLayout.LayoutParams prp = new LinearLayout.LayoutParams(-1, -2);
        prp.topMargin = dp(8);
        pc.addView(pr, prp);

        LinearLayout c = card(left, "Разрешение рендера");
        resSpinner = row(c, "Высота рендера", "Меньше: больше FPS, но мягче картинка");
        resSpinner.getLayoutParams().width = dp(250);
        fill(resSpinner, resLabels(-1), 1);
        c.addView(text("Чем ниже разрешение, тем выше FPS и холоднее телефон.", 11, S.muted, false));

        LinearLayout ch = card(left, "Счётчик FPS в игре");
        hudGroup = new android.widget.RadioGroup(this);
        hudGroup.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        String[] hl = {"Выключен", "Компактный", "Полный"};
        for (int i = 0; i < hl.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setText(hl[i]);
            rb.setId(200 + i);
            rb.setTextColor(S.text);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            rb.setButtonTintList(new android.content.res.ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}}, new int[] {S.accent, 0xFF8A8A94}));
            hudGroup.addView(rb);
        }
        hudGroup.check(200);
        ch.addView(hudGroup);
        ch.addView(text("Встроенная статистика движка: FPS, время кадра, нагрузка. Отдельного лимита это не меняет.", 11, S.muted, false));

        for (Games.Group g : S.groups) {
            LinearLayout gc = card(right, g.title);
            for (Games.Opt o : g.opts) {
                Spinner sp = row(gc, o.title, o.hint);
                spinners.add(sp);
                optList.add(o);
                loadedExtra.add(null);
                List<String> labels = new ArrayList<String>();
                for (String x : o.labels) labels.add(x);
                fill(sp, labels, 0);
            }
        }
        if (S.note != null) {
            TextView n = text(S.note, 11, S.muted, false);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.bottomMargin = dp(10);
            right.addView(n, np);
        }

        // нижняя панель
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), dp(10), dp(24), dp(12));
        status = text("", 12, S.muted, false);
        bar.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        Button reset = button("Вернуть исходные", false);
        Button save = button("Сохранить", false);
        Button play = button("▶  ИГРАТЬ", true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.leftMargin = dp(10);
        bar.addView(reset, bp);
        bar.addView(save, bp);
        bar.addView(play, bp);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        reset.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { restore(); } });
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { save(); } });
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { play(); } });

        setContentView(root);
    }

    private void say(String s) {
        status.setText(s);
        status.setTextColor(s != null && s.startsWith("Нет доступа") ? 0xFFFF3B4E : S.muted);
    }

    // ---------------------------------------------------------------- доступ к файлам

    private boolean pkgInstalled(String p) {
        try { getPackageManager().getPackageInfo(p, 0); return true; } catch (Exception e) { return false; }
    }

    private void open(Intent i) {
        try { startActivity(i); } catch (Exception e) { say("Не удалось открыть: " + e.getMessage()); }
    }

    private interface Act { void go(); }

    private void step(String title, String hint, boolean ok, String btn, final Act act) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(text(ok ? "✓" : "✕", 18, ok ? 0xFF5BD68A : 0xFFFF6B7A, true), new LinearLayout.LayoutParams(dp(26), -2));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text(title, 14, S.text, false));
        if (!ok && hint != null) names.addView(text(hint, 11, S.muted, false));
        r.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!ok && btn != null && act != null) {
            Button b = button(btn, false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { act.go(); } });
            r.addView(b, new LinearLayout.LayoutParams(-2, -2));
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(8);
        setupBody.addView(r, rp);
    }

    private void refreshSetup() {
        if (setupBody == null) return;
        while (setupBody.getChildCount() > 1) setupBody.removeViewAt(1);
        boolean perm = Fs.ready();
        boolean inst = pkgInstalled(S.pkg);
        boolean prefs = perm && inst && Fs.exists(prefPath);
        if (perm && inst && prefs) { setupCard.setVisibility(View.GONE); return; }
        setupCard.setVisibility(View.VISIBLE);
        if (!perm && !Fs.rootChecking()) showAccessBanner();
        final boolean shInst = pkgInstalled(SHIZUKU_PKG);
        if (Fs.rootOk()) {
            step("Root-доступ получен", null, true, null, null);
        } else {
            step("Root", Fs.rootChecking() ? "Проверяю root…" : "Выдайте root лаунчеру в Magisk/KernelSU и нажмите «Проверить»",
                false, "Проверить", new Act() { public void go() { Fs.retryRoot(); refreshSetup(); } });
            step("Shizuku запущен", shInst ? "Запустите Shizuku и выдайте доступ лаунчеру" : "Установите Shizuku (Google Play или GitHub)",
                Fs.running(), shInst ? "Открыть" : "Скачать", new Act() { public void go() {
                    Intent i = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PKG);
                    if (i != null) open(i);
                    else open(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/RikkaApps/Shizuku/releases")));
                } });
            step("Доступ лаунчеру выдан", Fs.running() ? "Нажмите «Разрешить» в окне Shizuku" : "Сначала запустите Shizuku", perm,
                Fs.running() ? "Запросить" : null, new Act() { public void go() { Fs.requestAccess(); refreshSetup(); } });
        }
        step("Игра установлена", "Установите " + S.name, inst, null, null);
        step("Файл настроек игры создан", "Запустите игру один раз до главного меню и закройте её", prefs,
            inst && perm ? "Запустить игру" : null, new Act() { public void go() {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.setClassName(S.pkg, gameActivity);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                open(i);
            } });
    }

    private void showAccessBanner() {
        if (noAccessShown || isFinishing()) return;
        noAccessShown = true;
        new AlertDialog.Builder(this)
            .setTitle("Нет доступа к файлам игры")
            .setMessage("Лаунчер правит файл настроек игры, а Android не даёт обычным приложениям писать в папку другого приложения. Нужен root (Magisk/KernelSU/APatch) или Shizuku: запустите Shizuku через беспроводную отладку и нажмите «Разрешить» в его окне. Можно выдать доступ с компьютера: adb shell pm grant "
                + getPackageName() + " moe.shizuku.manager.permission.API_V23")
            .setPositiveButton("Понятно", null)
            .show();
    }

    // ---------------------------------------------------------------- чтение и запись

    private String readXml() {
        try {
            byte[] d = Fs.read(prefPath);
            return d == null ? null : new String(d, "UTF-8");
        } catch (Exception e) { return null; }
    }

    private String[] keyPath(Games.Opt o) {
        if (o.path == null) return S.setup;
        String[] r = new String[S.setup.length + o.path.length];
        System.arraycopy(S.setup, 0, r, 0, S.setup.length);
        System.arraycopy(o.path, 0, r, S.setup.length, o.path.length);
        return r;
    }

    private static String plain(String name) { return name.startsWith("~") ? name.substring(1) : name; }

    private static String firstToken(String v) {
        int c = v.indexOf(',');
        return (c < 0 ? v : v.substring(0, c)).trim();
    }

    private List<String> resLabels(int extra) {
        List<String> rl = new ArrayList<String>();
        for (int h : HEIGHTS) rl.add(h + "p   " + widthFor(h) + "×" + h);
        rl.add("Нативное   " + nativeW + "×" + nativeH);
        if (extra > 0) rl.add("Текущее   " + widthFor(extra) + "×" + extra);
        return rl;
    }

    private int widthFor(int h) {
        int w = Math.round(h * (nativeW / (float) nativeH));
        return w - (w % 2);
    }

    private void load() {
        if (spinners.isEmpty() || !Fs.ready()) return;
        String xml = readXml();
        if (xml == null) return;
        for (int i = 0; i < optList.size(); i++) {
            Games.Opt o = optList.get(i);
            String cur = null;
            for (String n : o.names) {
                String v = PrefsEditor.getInKey(xml, plain(n), keyPath(o));
                if (v != null) { cur = firstToken(v); break; }
            }
            List<String> labels = new ArrayList<String>();
            for (String s : o.labels) labels.add(s);
            int sel = 0;
            loadedExtra.set(i, null);
            if (cur != null) {
                int idx = -1;
                for (int k = 0; k < o.values.length; k++) if (o.values[k].equalsIgnoreCase(cur)) { idx = k; break; }
                if (idx < 0) { labels.add(cur + " (из файла)"); loadedExtra.set(i, cur); idx = labels.size() - 1; }
                sel = idx;
            }
            fill(spinners.get(i), labels, sel);
        }
        boolean showHud = "1".equals(PrefsEditor.getInKey(xml, "ShowFeralGUI", D3D));
        boolean compactHud = "1".equals(PrefsEditor.getInKey(xml, "CompactFeralGUI", D3D));
        hudGroup.check(!showHud ? 200 : (compactHud ? 201 : 202));
        String hs = PrefsEditor.getInKey(xml, "FixedScreenHeight", S.setup);
        List<String> rl = resLabels(-1);
        int sel = 1;
        resExtra = -1;
        if (hs != null) {
            try {
                int h = Integer.parseInt(hs.trim());
                int idx = -1;
                for (int k = 0; k < HEIGHTS.length; k++) if (HEIGHTS[k] == h) idx = k;
                if (h == nativeH) idx = HEIGHTS.length;
                if (idx < 0) { resExtra = h; rl = resLabels(h); idx = rl.size() - 1; }
                sel = idx;
            } catch (NumberFormatException ignored) { }
        }
        fill(resSpinner, rl, sel);
    }

    private void applyPreset(int lvl) {
        for (int i = 0; i < optList.size(); i++) {
            Games.Opt o = optList.get(i);
            if (!o.q) continue;
            int n = o.values.length;
            int idx;
            if (n == 2) idx = lvl >= o.thr ? 1 : 0;
            else idx = Math.round(lvl * (n - 1) / 4f);
            spinners.get(i).setSelection(Math.min(idx, spinners.get(i).getAdapter().getCount() - 1));
        }
        int h = S.presetRes[lvl];
        int ri = h == nativeH ? HEIGHTS.length : -1;
        for (int k = 0; k < HEIGHTS.length; k++) if (HEIGHTS[k] == h) ri = k;
        if (ri >= 0) resSpinner.setSelection(ri);
        say("Пресет «" + PRESET_NAMES[lvl] + "» выбран. Нажмите «Сохранить» или «Играть».");
    }

    // по классу GPU (0 флагман … 3 слабый): уровень пресета и высота рендера
    private static final int[] AUTO60_LVL = {2, 1, 1, 0};
    private static final int[] AUTO60_RES = {1080, 900, 720, 540};
    private static final int[] AUTO30_LVL = {3, 3, 2, 1};
    private static final int[] AUTO30_RES = {1080, 1080, 900, 720};

    private void applyAuto(int fps) {
        int t = Math.max(0, Math.min(3, hw.tier));
        int lvl = fps == 60 ? AUTO60_LVL[t] : AUTO30_LVL[t];
        int h = fps == 60 ? AUTO60_RES[t] : AUTO30_RES[t];
        applyPreset(lvl);
        for (int k = 0; k < HEIGHTS.length; k++) if (HEIGHTS[k] == h) resSpinner.setSelection(k);
        for (int i = 0; i < optList.size(); i++) {
            Games.Opt o = optList.get(i);
            if (!"Лимит FPS".equals(o.title)) continue;
            for (int k = 0; k < o.values.length; k++) if (o.values[k].equals(String.valueOf(fps))) spinners.get(i).setSelection(k);
        }
        say("Подобрано для " + (fps == 60 ? "60 FPS (упор на плавность)" : "30 FPS (упор на качество)") + " (класс: " + hw.tierName
            + "). Нажмите «Сохранить» или «Играть».");
    }

    private boolean write(boolean quiet) {
        if (!Fs.ready()) { say("Нет доступа к файлам игры: выдайте root или Shizuku."); return false; }
        byte[] orig = Fs.read(prefPath);
        if (orig == null) { say("Файл настроек не найден. Запустите игру один раз до главного меню."); return false; }
        try {
            if (!Fs.exists(bakPath)) Fs.write(bakPath, orig);
            String xml = new String(orig, "UTF-8");
            int res = resSpinner.getSelectedItemPosition();
            int h = res < HEIGHTS.length ? HEIGHTS[res] : res == HEIGHTS.length ? nativeH : resExtra;
            if (h > 0) {
                xml = PrefsEditor.setInKey(xml, "FixedScreenHeight", "integer", String.valueOf(h), S.setup);
                xml = PrefsEditor.setInKey(xml, "ScreenH", "integer", String.valueOf(h), S.setup);
                xml = PrefsEditor.setInKey(xml, "ScreenW", "integer", String.valueOf(widthFor(h)), S.setup);
            }
            for (int i = 0; i < optList.size(); i++) {
                Games.Opt o = optList.get(i);
                int sel = spinners.get(i).getSelectedItemPosition();
                String val = sel < o.values.length ? o.values[sel] : loadedExtra.get(i);
                if (val == null) continue;
                for (String n : o.names) {
                    boolean triple = n.startsWith("~");
                    String key = plain(n);
                    if (PrefsEditor.getInKey(xml, key, keyPath(o)) == null) continue;
                    xml = PrefsEditor.setInKey(xml, key, o.type, triple ? val + ", " + val + ", " + val : val, keyPath(o));
                }
            }
            int hud = hudGroup.getCheckedRadioButtonId() - 200;
            if (hud > 0 || PrefsEditor.getInKey(xml, "ShowFeralGUI", D3D) != null) {
                xml = PrefsEditor.setInKey(xml, "ShowFeralGUI", "integer", hud > 0 ? "1" : "0", D3D);
                xml = PrefsEditor.setInKey(xml, "CompactFeralGUI", "integer", hud == 1 ? "1" : "0", D3D);
            }
            for (String[] f : S.force) {
                if (PrefsEditor.getInKey(xml, f[0], S.setup) != null) xml = PrefsEditor.setInKey(xml, f[0], "string", f[1], S.setup);
            }
            if (!Fs.write(prefPath, xml.getBytes("UTF-8"))) { say("Не удалось записать файл настроек."); return false; }
            if (!quiet) say("Сохранено. Запустите игру: настройки применятся при старте.");
            return true;
        } catch (Exception e) {
            say("Ошибка: " + e.getMessage());
            return false;
        }
    }

    private void save() { write(false); }

    private void restore() {
        if (!Fs.exists(bakPath)) { say("Исходной копии пока нет: она создаётся при первом сохранении."); return; }
        byte[] d = Fs.read(bakPath);
        if (d != null && Fs.write(prefPath, d)) { load(); say("Исходные настройки возвращены."); }
        else say("Не удалось вернуть исходные настройки.");
    }

    private void play() {
        if (Fs.exists(prefPath) && !write(true)) return;
        Fs.sh("am force-stop " + S.pkg);
        say("Запуск игры…");
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (Fs.ready()) {
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final String out = Fs.rootOk() ? Fs.suOut("am start -n " + S.pkg + "/" + gameActivity) : null;
                            final int rc = out != null ? (out.contains("Error") || out.contains("Exception") ? 1 : 0)
                                : Fs.sh("am start -n " + S.pkg + "/" + gameActivity);
                            handler.post(new Runnable() { @Override public void run() {
                                if (rc == 0) finishAndRemoveTask();
                                else say("Не удалось запустить игру: " + (out == null ? "код " + rc : out.trim()));
                            } });
                        }
                    }, "play").start();
                    return;
                }
                try {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    i.setClassName(S.pkg, gameActivity);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finishAndRemoveTask();
                } catch (Exception e) { say("Не удалось запустить игру: " + e.getMessage()); }
            }
        }, 700);
    }
}
