package com.sr3launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Главный экран: выбор игры. Настройки каждой игры открываются в LauncherActivity. */
public class HomeActivity extends Activity {
    private static final class G {
        final String id, title, pkg; final int logo, accent, card;
        G(String id, String title, String pkg, int logo, int accent, int card) {
            this.id = id; this.title = title; this.pkg = pkg; this.logo = logo; this.accent = accent; this.card = card;
        }
    }

    private G[] games;
    private LinearLayout row;
    private LinearLayout updBar;
    private TextView updText;
    private Button updBtn;
    private Updater.Release updRel;
    private boolean updBusy;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        games = new G[] {
            new G("sr3", "Saints Row:\nThe Third", "com.feralinteractive.saintsrow3_android", R.drawable.logo_sr3, 0xFFB52FE0, 0xFF1D1730),
            new G("tr", "Tomb Raider", "com.feralinteractive.tombraider_android", R.drawable.logo_tr, 0xFFE8A33D, 0xFF132529),
            new G("hm", "Hitman:\nAbsolution", "com.feralinteractive.hitmanabsolution_android", R.drawable.logo_hm, 0xFFDB2D2D, 0xFF19191D),
            new G("gl", "GRID Legends", "com.feralinteractive.gridlegends_android", R.drawable.logo_gl, 0xFFFF6A2B, 0xFF121D29),
        };
        getWindow().setStatusBarColor(0xFF0B0D12);
        getWindow().setNavigationBarColor(0xFF0B0D12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0D12);
        root.setPadding(dp(24), dp(14), dp(24), dp(14));

        TextView title = new TextView(this);
        title.setText("FERAL ЛАУНЧЕР");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.08f);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Выберите игру: откроются её настройки графики, FPS и запуск. Версия " + BuildConfig.VERSION_NAME);
        sub.setTextColor(0xFF8A93A3);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.bottomMargin = dp(12);
        root.addView(sub, sp);

        updBar = new LinearLayout(this);
        updBar.setOrientation(LinearLayout.HORIZONTAL);
        updBar.setGravity(Gravity.CENTER_VERTICAL);
        updBar.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable ub = new GradientDrawable();
        ub.setColor(0xFF1E2A44);
        ub.setCornerRadius(dp(12));
        updBar.setBackground(ub);
        updText = new TextView(this);
        updText.setTextColor(Color.WHITE);
        updText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        updBar.addView(updText, new LinearLayout.LayoutParams(0, -2, 1f));
        updBtn = new Button(this);
        updBtn.setText("Обновить");
        updBtn.setAllCaps(false);
        updBtn.setTextColor(Color.WHITE);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(0xFF3D6BFF);
        bb.setCornerRadius(dp(10));
        updBtn.setBackground(bb);
        updBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { downloadUpdate(); } });
        LinearLayout.LayoutParams ubp = new LinearLayout.LayoutParams(-2, -2);
        ubp.leftMargin = dp(12);
        updBar.addView(updBtn, ubp);
        updBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams ubl = new LinearLayout.LayoutParams(-1, -2);
        ubl.bottomMargin = dp(8);
        root.addView(updBar, ubl);

        ScrollView sv = new ScrollView(this);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        sv.setFillViewport(true);
        sv.addView(row, new ViewGroupLP().fill());
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        buildCards();
        checkUpdate();
    }

    private void checkUpdate() {
        new Thread(new Runnable() {
            @Override public void run() {
                Updater.Release r = null;
                try { r = Updater.latest(BuildConfig.REPO); } catch (Exception ignored) { }
                final Updater.Release rel = r;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (rel != null && rel.apkUrl.length() > 0 && Updater.newer(rel.tag, BuildConfig.VERSION_NAME)) {
                            updRel = rel;
                            updText.setText("Доступна новая версия " + rel.tag + " (у вас " + BuildConfig.VERSION_NAME + ")");
                            updBar.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }, "update-check").start();
    }

    private void downloadUpdate() {
        if (updBusy || updRel == null) return;
        updBusy = true;
        updBtn.setEnabled(false);
        updText.setText("Скачиваю " + updRel.tag + "…");
        final String url = updRel.apkUrl;
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                try {
                    Updater.download(url, ApkProvider.apk(HomeActivity.this).getParentFile(), new Updater.Progress() {
                        @Override public void onProgress(final int pct) {
                            handler.post(new Runnable() { @Override public void run() { updText.setText("Скачиваю… " + pct + "%"); } });
                        }
                    });
                } catch (Exception e) { err = e.getMessage() == null ? e.toString() : e.getMessage(); }
                final String error = err;
                handler.post(new Runnable() {
                    @Override public void run() {
                        updBusy = false;
                        updBtn.setEnabled(true);
                        if (error != null) { updText.setText("Не удалось скачать: " + error); return; }
                        installUpdate();
                    }
                });
            }
        }, "update-download").start();
    }

    private void installUpdate() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            updText.setText("Разрешите установку обновлений в открывшемся окне и снова нажмите «Обновить».");
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(ApkProvider.uri(this), "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            updText.setText("Файл скачан, подтвердите установку в окне системы.");
        } catch (Exception e) {
            updText.setText("Не удалось открыть установщик: " + e.getMessage());
        }
    }

    /** Мелочь, чтобы не тянуть лишние импорты. */
    private static final class ViewGroupLP {
        android.widget.FrameLayout.LayoutParams fill() { return new android.widget.FrameLayout.LayoutParams(-1, -1); }
    }

    private boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void buildCards() {
        row.removeAllViews();
        for (final G g : games) {
            final boolean ok = installed(g.pkg);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(10), dp(14), dp(10), dp(14));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(g.card);
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(2), ok ? g.accent : 0xFF2A2F3A);
            c.setBackground(bg);
            c.setAlpha(ok ? 1f : 0.55f);

            ImageView logo = new ImageView(this);
            logo.setImageResource(g.logo);
            logo.setAdjustViewBounds(true);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            c.addView(logo, new LinearLayout.LayoutParams(dp(110), dp(70)));

            TextView t = new TextView(this);
            t.setText(g.title);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.topMargin = dp(8);
            c.addView(t, tp);

            TextView st = new TextView(this);
            st.setText(ok ? "Открыть настройки" : "Игра не установлена");
            st.setTextColor(ok ? g.accent : 0xFF8A93A3);
            st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-2, -2);
            stp.topMargin = dp(4);
            c.addView(st, stp);

            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!ok) { Toast.makeText(HomeActivity.this, "Установите игру, потом откроются настройки", Toast.LENGTH_SHORT).show(); return; }
                    startActivity(new Intent(HomeActivity.this, LauncherActivity.class).putExtra("game", g.id));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = dp(6); lp.rightMargin = dp(6);
            row.addView(c, lp);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (row != null) buildCards();
    }
}
