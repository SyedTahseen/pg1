package com.pg;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.animation.ObjectAnimator;
import android.provider.DocumentsContract;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int BG = 0xFF000000, CARD = 0xFF1C1C1E, CARD2 = 0xFF2C2C2E, TXT = 0xFFF2F2F2, SUB = 0xFF9A9AA0,
            GREEN = 0xFFD2F234 /* lime accent */, GREEN_BG = 0xFF1E3A2A, RED = 0xFFFF6B6B, RED_BG = 0xFF3B1F1F,
            AMBER = 0xFFFFB74D, AMBER_BG = 0xFF3A2E17, LINE = 0xFF262628, BTN_LIGHT = 0xFFEDEDED;
    static final int ON_GREEN = 0xFF1A2300, ON_LIGHT = 0xFF101010;     // text colour on a lime / light button
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    SharedPreferences sp;
    Typeface SEMI = Typeface.DEFAULT_BOLD, MED = Typeface.DEFAULT, REG = Typeface.DEFAULT;
    ScrollView sv;
    LinearLayout root;
    String pin, rootState = "checking", modState = "checking";
    boolean checked;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("pg", 0);
        initFonts();
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));
        getWindow().setDecorFitsSystemWindows(false);          // edge to edge: the glow runs under the status bar
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        FrameLayout frame = new FrameLayout(this);
        frame.addView(new Glow(this), new FrameLayout.LayoutParams(MATCH, MATCH));
        sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), 0, dp(20), dp(24));
        sv.addView(root);
        frame.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        frame.setOnApplyWindowInsetsListener((v, ins) -> {
            android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            sv.setPadding(0, i.top, 0, i.bottom);
            return ins;
        });
        setContentView(frame);
        gate();
    }

    /** Optional: drop a font file at app/src/main/assets/fonts/ui.ttf (e.g. Outfit or Sora) and the whole UI uses it. */
    void initFonts() {
        Typeface base = Typeface.DEFAULT;
        try { base = Typeface.createFromAsset(getAssets(), "fonts/ui.ttf"); } catch (Throwable ignored) {}
        REG = Typeface.create(base, 400, false);
        MED = Typeface.create(base, 500, false);
        SEMI = Typeface.create(base, 600, false);
    }

    /** Lime quarter-circle glow in the top-right corner (static, behind the scrolling content). */
    static class Glow extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glow(Context c) { super(c); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth();
            float cx = w * 1.04f, cy = -w * 0.07f, r = w * 0.34f / 0.865f;
            p.setShader(new RadialGradient(cx, cy, r,
                new int[]{0xFF000000, 0xFF080C00, 0xFF3B5004, 0xFFD2F234, 0x40D2F234, 0x00D2F234},
                new float[]{0f, 0.60f, 0.82f, 0.865f, 0.91f, 1f}, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, p);
        }
    }

    // ---------- small UI helpers ----------
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable rect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }
    TextView text(String s, int size, int color, boolean bold) { return text(s, size, color, bold ? SEMI : REG); }
    TextView text(String s, int size, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }
    /** Fill + press ripple, clipped to the same rounded shape. */
    Drawable ripple(int fill, int radiusDp, int fg) {
        return new RippleDrawable(android.content.res.ColorStateList.valueOf((fg & 0x00FFFFFF) | 0x30000000),
            rect(fill, radiusDp), rect(Color.WHITE, radiusDp));
    }
    GradientDrawable outline(int fill, int stroke, int radiusDp) {
        GradientDrawable g = rect(fill, radiusDp);
        g.setStroke(dp(1), stroke);
        return g;
    }
    GradientDrawable grad(int c1, int c2, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }
    Drawable plainRipple() {
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, new ColorDrawable(Color.WHITE));
    }
    ImageView ico(int res, int sizeDp, int color) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setColorFilter(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }
    Button pill(String s, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextColor(fg); b.setTextSize(15);
        b.setTypeface(MED);
        b.setBackground(ripple(bg, 28, fg));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }
    /** Layout params with an optional top margin in dp. */
    LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }
    LinearLayout hrow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }
    LinearLayout vcol() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        return r;
    }
    LinearLayout card(int bg) {
        LinearLayout c = vcol();
        c.setBackground(rect(bg, 24));
        c.setPadding(dp(20), dp(18), dp(20), dp(18));
        root.addView(c, lp(MATCH, WRAP, 12));
        return c;
    }
    // ---------- themed toast (replaces the stock Android Toast) ----------
    // For a dialog or the PIN screen, prefer an inline error message instead (see errView/setErr) -
    // a footer toast can end up behind that window's dim, or behind the keyboard.
    FrameLayout toastHost;
    final Handler toastHandler = new Handler(Looper.getMainLooper());
    Runnable toastHide;

    void toast(String s) { toast(s, false); }
    void errorToast(String s) { toast(s, true); }

    /** Banner near the bottom of the screen, about where the stock Android toast used to sit (not flush with the edge). */
    void toast(String s, boolean error) {
        if (toastHost == null) {
            toastHost = new FrameLayout(this);
            toastHost.setClickable(false);
            // MATCH_PARENT height: this is what lets the banner's own BOTTOM gravity below actually apply,
            // otherwise the host shrinks to the banner's height and sticks to the top of the screen.
            ((ViewGroup) getWindow().getDecorView()).addView(toastHost, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        toastHost.removeAllViews();
        if (toastHide != null) toastHandler.removeCallbacks(toastHide);
        // Solid, bright fills (not the app's own card greys) so the banner never blends into whatever card is behind it.
        TextView t = text(s, 14, error ? 0xFF3B0000 : ON_GREEN, true);
        t.setBackground(rect(error ? 0xFFFF6B6B : TXT, 16));
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, WRAP);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(72) + navBarHeight();
        lp.leftMargin = dp(20); lp.rightMargin = dp(20);
        toastHost.addView(t, lp);
        toastHide = () -> { if (toastHost != null) toastHost.removeAllViews(); };
        toastHandler.postDelayed(toastHide, error ? 2600 : 1800);
    }

    int navBarHeight() {
        try {
            WindowInsets wi = getWindow().getDecorView().getRootWindowInsets();
            if (wi != null) return wi.getInsets(WindowInsets.Type.navigationBars()).bottom;
        } catch (Throwable ignored) {}
        return 0;
    }

    /** Small red message, created hidden. Use this inside the PIN screen and dialogs instead of toast() -
     * a footer toast there can end up behind the keyboard or behind a dialog's own dim. */
    TextView errView() {
        TextView t = text("", 13, RED, true);
        t.setGravity(Gravity.CENTER);
        t.setVisibility(View.GONE);
        return t;
    }
    /** Shows msg (or hides for null/empty). Re-shakes every time, even if the text is the same as last time,
     * so retyping the same wrong PIN is still noticeably rejected. */
    void setErr(TextView err, String msg) {
        boolean has = msg != null && !msg.isEmpty();
        err.setText(has ? msg : "");
        err.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) shake(err);
    }

    void shake(View v) {
        v.clearAnimation();
        v.setTranslationX(0f);
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "translationX", 0f, -18f, 18f, -14f, 14f, -8f, 8f, 0f);
        a.setDuration(380);
        a.start();
    }

    /** Short buzz for a wrong PIN; does nothing on a device/profile without a vibrator. */
    void vibrateWrong() {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) vib.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable ignored) {}
    }
    Set<String> set(String k) { return new HashSet<>(sp.getStringSet(k, new HashSet<String>())); }

    static final int MATCH_HIDDEN = PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS
            | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;

    /** getApplicationIcon() throws for a package we've hidden (setApplicationHiddenSetting) unless asked to match it anyway. */
    Drawable icon(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationIcon(pkg); }
        catch (Exception e) {
            try { return pm.getApplicationInfo(pkg, MATCH_HIDDEN).loadIcon(pm); }
            catch (Exception e2) { return getDrawable(android.R.drawable.sym_def_app_icon); }
        }
    }

    // ---------- themed dialog (replaces the stock Android AlertDialog) ----------
    interface SheetAction { void run(Dialog d, Button ok); }

    /** Dark rounded dialog in the app's own style. pos == null -> a single "Close" button. The action decides when to dismiss. */
    Dialog sheet(String title, String msg, View body, String posLabel, final SheetAction pos) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout c = vcol();
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.addView(text(title, 20, TXT, true));
        if (msg != null) {
            TextView m = text(msg, 14, SUB, false);
            m.setPadding(0, dp(8), 0, 0);
            c.addView(m);
        }
        if (body != null) {
            c.addView(body, lp(MATCH, WRAP, 14));
        }
        LinearLayout row = hrow();
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        Button cancel = pill(pos == null ? "Close" : "Cancel", CARD2, TXT, v -> d.dismiss());
        row.addView(cancel, l1);
        if (pos != null) {
            l1.rightMargin = dp(6);
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
            l2.leftMargin = dp(6);
            final Button ok = pill(posLabel, GREEN, ON_GREEN, null);
            ok.setOnClickListener(v -> pos.run(d, ok));
            row.addView(ok, l2);
        }
        c.addView(row, lp(MATCH, WRAP, 18));
        d.setContentView(c);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new InsetDrawable(outline(CARD, 0xFF2C2C2E, 28), dp(16)));   // inset = side margin, rounded card = dialog
        w.setLayout(MATCH, WRAP);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
        return d;
    }

    /** Scrolling box that grows with its content but never taller than about half the screen. */
    ScrollView scroller(View content) {
        final int max = getResources().getDisplayMetrics().heightPixels * 55 / 100;
        ScrollView v = new ScrollView(this) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
            }
        };
        v.addView(content);
        return v;
    }

    // ---------- PIN screen ----------
    static final int NEW = 0, LOCAL = 1, RESTORE = 2;
    static final Object CALL_LOCK = new Object();

    void gate() {
        if (sp.getString("hash", null) != null) { pinScreen(LOCAL); return; }
        // No local data (fresh install, reinstall or update with a new key): ask the module whether it still remembers us.
        screenHeader("Looking for saved settings…");
        new Thread(() -> {
            boolean ok = pingModule();
            final int ver = ok ? sp.getInt("modver", 0) : -1;
            final boolean has = ok && sp.getBoolean("modhas", false);
            runOnUiThread(() -> {
                if (ver >= Common.MODULE_VER) pinScreen(has ? RESTORE : NEW);
                else moduleUnavailable(ver);
            });
        }).start();
    }

    /** Module silent or too old to hand back saved settings. */
    void moduleUnavailable(int ver) {
        screenHeader(ver < 0
            ? "The module is not responding.\nIf you used PrivacyGuard before, enable the module in LSPosed (scope: System Framework) and reboot. Your PIN and settings will then be restored here."
            : "The running module is an older version.\nReboot your phone so the updated module loads. Your PIN and settings will then be restored here.");
        root.addView(pill("Try again", GREEN, ON_GREEN, v -> gate()), lp(MATCH, dp(52), 32));
        root.addView(pill("Set up as new", CARD2, TXT, v -> pinScreen(NEW)), lp(MATCH, dp(52), 12));
    }

    /** Clears the screen and shows the app title with a centred subtitle (PIN screens). */
    void screenHeader(String subtitle) {
        root.removeAllViews();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = text("PrivacyGuard", 32, TXT, true);
        t.setGravity(Gravity.CENTER); t.setPadding(0, dp(60), 0, dp(8));
        root.addView(t);
        TextView s = text(subtitle, 15, SUB, false);
        s.setGravity(Gravity.CENTER);
        root.addView(s);
    }

    /** Pings the module and waits up to 3 s for the answer. Background thread only. */
    boolean pingModule() {
        long t0 = System.currentTimeMillis();
        send("ping");
        for (int i = 0; i < 30; i++) {
            try { Thread.sleep(100); } catch (Exception ignored) {}
            if (sp.getLong("pong", 0) >= t0) return true;
        }
        return false;
    }

    EditText pinField(String hint) { return pinField(hint, CARD); }

    EditText pinField(String hint, int bg) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setHintTextColor(SUB); e.setTextColor(TXT); e.setTextSize(22);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setTypeface(MED);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PIN_LEN)});
        e.setBackground(outline(bg == CARD ? 0xFF111113 : bg, 0xFF2E2E31, 20));
        e.setPadding(dp(16), dp(16), dp(16), dp(16));
        return e;
    }

    static final int PIN_LEN = 4;
    static final int KEY = 0xFFFFFFFF, KEY_TXT = 0xFF000000, DOT_OFF = 0xFF5C5C60;

    /** Redraws the PIN dots: always at least 4, one more per digit beyond that; the first `filled` are lime. */
    void drawDots(LinearLayout row, int filled) {
        row.removeAllViews();
        int n = PIN_LEN;
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (i < filled) { g.setColor(GREEN); g.setStroke(dp(2), GREEN); }
            else { g.setColor(Color.TRANSPARENT); g.setStroke(dp(2), DOT_OFF); }
            d.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(16), dp(16));
            p.leftMargin = p.rightMargin = dp(11);
            row.addView(d, p);
        }
    }

    Drawable circleRipple(int fill, int rippleColor) {
        GradientDrawable f = new GradientDrawable(); f.setShape(GradientDrawable.OVAL); f.setColor(fill);
        GradientDrawable m = new GradientDrawable(); m.setShape(GradientDrawable.OVAL); m.setColor(Color.WHITE);
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(rippleColor), f, m);
    }

    /** One round white number key, centred in its keypad cell. */
    FrameLayout keyCell(View key) {
        FrameLayout c = new FrameLayout(this);
        c.addView(key, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
        return c;
    }

    void pinScreen(final int mode) {
        root.removeAllViews();
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        final StringBuilder buf = new StringBuilder();
        final Runnable[] submit = new Runnable[1];
        final boolean[] busy = {false};
        final String[] first = {null};                       // NEW: the PIN typed on step 1, waiting to be confirmed

        // flexible space above the lock icon
        root.addView(new View(this), new LinearLayout.LayoutParams(MATCH, dp(24), 0.6f));

        // lock icon + title
        root.addView(ico(R.drawable.ic_lock_pin, 32, GREEN), lp(dp(32), dp(32), 0));
        final TextView title = text(mode == NEW ? "Create PIN" : "Enter PIN", 28, TXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(MATCH, WRAP, 18));
        String sub = mode == NEW ? "Use a different one from your phone lock."
            : mode == RESTORE ? "Saved settings found. Enter your PIN to restore them." : null;
        if (sub != null) {
            TextView s = text(sub, 14, SUB, false);
            s.setGravity(Gravity.CENTER);
            root.addView(s, lp(MATCH, WRAP, 8));
        }

        // dots
        final LinearLayout dots = hrow();
        dots.setGravity(Gravity.CENTER);
        drawDots(dots, 0);
        root.addView(dots, lp(MATCH, WRAP, 44));

        // inline error (fixed height so the keypad doesn't jump when it appears)
        final TextView err = errView();
        FrameLayout errBox = new FrameLayout(this);
        errBox.addView(err, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER));
        root.addView(errBox, lp(MATCH, dp(24), 14));

        final Runnable refresh = () -> drawDots(dots, buf.length());
        final Runnable wrong = () -> {
            buf.setLength(0);
            drawDots(dots, 0);
            shake(dots);
            vibrateWrong();
        };

        View.OnClickListener digit = v -> {
            if (busy[0] || buf.length() >= PIN_LEN) return;
            setErr(err, null);
            buf.append(((TextView) v).getText());
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            refresh.run();
            if (buf.length() == PIN_LEN) v.postDelayed(() -> submit[0].run(), 120);   // let the last dot fill first
        };

        // keypad: 1-9, then 0 centred with a backspace key to its right
        LinearLayout pad = vcol();
        pad.setPadding(dp(32), 0, dp(32), 0);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}};
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = hrow();
            for (String k : rows[r]) {
                TextView key = numKey(k, digit);
                row.addView(keyCell(key), new LinearLayout.LayoutParams(0, WRAP, 1f));
            }
            pad.addView(row, lp(MATCH, WRAP, r == 0 ? 0 : 14));
        }
        LinearLayout last = hrow();
        last.addView(new View(this), new LinearLayout.LayoutParams(0, dp(58), 1f));
        last.addView(keyCell(numKey("0", digit)), new LinearLayout.LayoutParams(0, WRAP, 1f));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_backspace);
        back.setColorFilter(SUB);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setBackground(circleRipple(Color.TRANSPARENT, 0x33FFFFFF));
        back.setClickable(true);
        back.setContentDescription("Delete");
        back.setOnClickListener(v -> {
            if (!busy[0] && buf.length() > 0) { buf.setLength(buf.length() - 1); setErr(err, null); refresh.run(); }
        });
        back.setOnLongClickListener(v -> { if (busy[0]) return true; buf.setLength(0); setErr(err, null); refresh.run(); return true; });
        last.addView(keyCell(back), new LinearLayout.LayoutParams(0, WRAP, 1f));
        pad.addView(last, lp(MATCH, WRAP, 14));
        root.addView(pad, lp(MATCH, WRAP, 6));

        // flexible space under the keypad
        root.addView(new View(this), new LinearLayout.LayoutParams(MATCH, dp(28), 1f));

        // runs by itself as soon as the 4th digit is in - there is no Unlock button
        submit[0] = () -> {
            final String p = buf.toString();
            if (mode == NEW) {
                if (first[0] == null) {                      // step 1 done: ask for it again
                    first[0] = p; buf.setLength(0); drawDots(dots, 0);
                    title.setText("Confirm PIN");
                    return;
                }
                if (!p.equals(first[0])) {                   // mismatch: back to step 1
                    first[0] = null; wrong.run();
                    title.setText("Create PIN");
                    setErr(err, "PINs don't match");
                    return;
                }
                sp.edit().putString("hash", Common.sha(p)).apply();
                pin = p; checked = false; main(); backgroundSync();
            } else if (mode == LOCAL) {
                if (!Common.sha(p).equals(sp.getString("hash", ""))) { wrong.run(); setErr(err, "Wrong PIN"); return; }
                pin = p; checked = false; main(); backgroundSync();
            } else {
                busy[0] = true; title.setText("Restoring\u2026");
                pull(p, r -> {
                    if (r == 1) { pin = p; checked = false; toast("Settings restored"); main(); }
                    else {
                        busy[0] = false; title.setText("Enter PIN");
                        wrong.run();
                        setErr(err, r == 0 ? "Wrong PIN" : "Module did not answer. Try again.");
                    }
                });
            }
        };
    }

    /** Round white key with a bold black digit. */
    TextView numKey(String d, View.OnClickListener l) {
        TextView t = text(d, 26, KEY_TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setBackground(circleRipple(KEY, 0x33000000));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    /** After every unlock: make sure the module holds our full config (PIN, apps, folders, limit). */
    void backgroundSync() {
        final String p = pin;
        new Thread(() -> {
            int r = call("config", p, sp.getString("hash", null));
            if (r == 0) runOnUiThread(this::mismatchDialog);   // module remembers a different PIN
            else if (r == 1) { Shell.ensureBundle(this, p); EncryptJob.schedule(this); }   // PIN accepted: make sure the file lock key exists
        }).start();
    }

    /** The module has a PIN that differs from this app's (e.g. set up again after a reinstall by an older version). */
    void mismatchDialog() {
        final EditText e = pinField("Original PIN", CARD2);
        final TextView err = errView();
        LinearLayout box = vcol();
        box.addView(e);
        box.addView(err, lp(MATCH, WRAP, 8));
        sheet("Different PIN found",
            "The module still remembers another PIN (from before a reinstall). Enter that PIN to restore your saved settings. Until then, changes made here are not applied.",
            box, "Restore", (d, ok) -> {
                setErr(err, null);
                final String p = e.getText().toString();
                if (p.length() != PIN_LEN) { setErr(err, "PIN is 4 digits"); return; }
                ok.setEnabled(false);
                pull(p, r -> {
                    if (r == 1) { pin = p; checked = false; d.dismiss(); toast("Settings restored"); main(); }
                    else { ok.setEnabled(true); setErr(err, r == 0 ? "Wrong PIN" : "Module did not answer. Try again."); if (r == 0) vibrateWrong(); }
                });
            });
    }

    // ---------- talking to the module ----------
    /** Asks the module for everything it remembers. The receiver stores it when the answer arrives. result: 1 ok, 0 refused, -1 no answer */
    void pull(final String p, final java.util.function.IntConsumer done) {
        new Thread(() -> { final int r = call("pull", p, null); runOnUiThread(() -> done.accept(r)); }).start();
    }

    /**
     * Sends a command with a one-time nonce and waits up to 3 s for the module's answer.
     * Returns 1 = accepted, 0 = refused (wrong PIN), -1 = no answer (module off, or still an older version). Background thread only.
     */
    int call(String cmd, String pinArg, String newhash) {
        synchronized (CALL_LOCK) {
            String nonce = Common.randHex(12);
            sp.edit().putString("pendnonce", nonce).remove("ack").commit();
            send(cmd, pinArg, nonce, newhash);
            for (int i = 0; i < 30; i++) {
                try { Thread.sleep(100); } catch (Exception ignored) {}
                String a = sp.getString("ack", null);
                if (a != null && a.startsWith(nonce + ":")) return a.endsWith(":1") ? 1 : 0;
            }
            sp.edit().remove("pendnonce").apply();
            return -1;
        }
    }

    // ---------- main screen ----------
    void main() {
        final int y = sv.getScrollY();
        root.removeAllViews();
        root.setGravity(Gravity.START);
        progShown = false; progBar = null; progTxt = null;
        header();
        statusCard();
        final boolean hid = sp.getBoolean("hidden", false);
        wasHidden = hid;
        if (hid || progActive()) hiddenCard(hid);
        heroButton(hid);
        settingsList();
        if (!checked) { checked = true; checkStatus(); }
        sv.post(() -> sv.scrollTo(0, y));
    }

    void header() {
        LinearLayout top = hrow();
        top.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ImageView more = ico(R.drawable.ic_more, 24, TXT);
        more.setPadding(dp(12), dp(12), dp(12), dp(12));
        more.setBackground(plainRipple());
        more.setOnClickListener(this::menu);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(48), dp(48));
        mp.rightMargin = -dp(14);
        top.addView(more, mp);
        root.addView(top, new LinearLayout.LayoutParams(MATCH, dp(48)));
        TextView t = text("PrivacyGuard", 32, TXT, true);
        t.setPadding(0, dp(12), 0, 0);
        root.addView(t);
    }

    void menu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Re-check status");
        m.getMenu().add(0, 2, 1, "Change PIN");
        m.setOnMenuItemClickListener(it -> {
            if (it.getItemId() == 1) { rootState = "checking"; modState = "checking"; checked = false; main(); }
            else changePin();
            return true;
        });
        m.show();
    }

    void statusCard() {
        boolean r = "ok".equals(rootState), m = "ok".equals(modState);
        boolean busy = "checking".equals(rootState) || "checking".equals(modState);
        boolean good = r && m;
        int ac = busy ? SUB : good ? GREEN : RED;
        LinearLayout c = vcol();
        c.setBackground(busy ? grad(0xFF232325, 0xFF161618, CARD2, 24)
            : good ? grad(0xFF4A5A0F, 0xFF1D2407, 0x55D2F234, 24)
            : grad(0xFF5C1D1D, 0xFF2A0E0E, 0x66FF6B6B, 24));
        c.setPadding(dp(24), dp(20), dp(20), dp(20));
        root.addView(c, lp(MATCH, WRAP, 28));
        LinearLayout top = hrow();
        c.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout left = vcol();
        top.addView(left, new LinearLayout.LayoutParams(0, WRAP, 1f));
        left.addView(text(busy ? "Checking…" : good ? "Working" : "Needs attention", 26, TXT, true));
        TextView sub = text(busy ? "Please wait" : good ? "Ready to protect your data" : "Fix the items below", 15,
            good ? 0xFFC9D0AE : SUB, false);
        sub.setPadding(0, dp(2), 0, 0);
        left.addView(sub);
        FrameLayout badge = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL); g.setColor(Color.TRANSPARENT); g.setStroke(dp(3), ac);
        badge.setBackground(g);
        if (good) {
            ImageView ck = ico(R.drawable.ic_check, 24, ac);
            badge.addView(ck, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        } else {
            TextView sym = text(busy ? "…" : "!", 24, ac, true);
            sym.setGravity(Gravity.CENTER);
            badge.addView(sym, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        top.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));
        statusLine(c, "LSPosed module", modState, "Active", "old".equals(modState)
            ? "Running an older version. Reboot so the updated module loads (needed for PIN change and restoring settings)."
            : "Not responding. Enable it in LSPosed (scope: System Framework), then reboot.");
        statusLine(c, "Storage access", rootState, "Granted",
            "Not granted. Allow \"All files access\" so folders can be hidden.");
        if ("bad".equals(rootState)) {
            TextView gr = text("Grant storage access", 14, GREEN, true);
            gr.setPadding(0, dp(12), 0, 0);
            gr.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
            c.addView(gr);
        }
        if (!busy && !good) {
            TextView rc = text("Tap to re-check", 13, ac, true);
            rc.setPadding(0, dp(14), 0, 0);
            rc.setOnClickListener(v -> { rootState = "checking"; modState = "checking"; checked = false; main(); });
            c.addView(rc);
        }
    }

    void statusLine(LinearLayout c, String name, String st, String okTxt, String badTxt) {
        boolean ok = "ok".equals(st), busy = "checking".equals(st);
        if (ok) return;   // only show items that need attention
        String label = busy ? "…  " + name : "✕  " + name;
        TextView t = text(label, 15, busy ? SUB : RED, true);
        t.setPadding(0, dp(14), 0, 0);
        c.addView(t);
        if (!busy) c.addView(text(badTxt, 13, SUB, false));
    }

    // ---- live progress bar for hiding / locking / restoring ----
    ProgressBar progBar; TextView progTxt;
    boolean progShown, wasHidden;
    final Handler ticker = new Handler(Looper.getMainLooper());
    final Runnable tick = new Runnable() { public void run() {
        boolean active = progActive();
        if (!active && sp.getBoolean("restore_fail", false)) {
            sp.edit().remove("restore_fail").apply();
            errorToast("Some items could not be restored. See the log.");
        }
        if (active != progShown || (!active && sp.getBoolean("hidden", false) != wasHidden)) { wasHidden = sp.getBoolean("hidden", false); refresh(); }             // bar appears / disappears: rebuild the screen once
        else if (active) progUpdate();
        ticker.postDelayed(this, 500);
    } };

    /** True while a run reported progress within the last 20 s (a killed process can't leave a stuck bar). */
    boolean progActive() {
        return sp.getString("pg_phase", null) != null && System.currentTimeMillis() - sp.getLong("pg_ts", 0) < 20000;
    }

    void progUpdate() {
        if (progBar == null || progTxt == null) return;
        String ph = sp.getString("pg_phase", ""), nm = sp.getString("pg_name", "");
        long d = sp.getLong("pg_done", 0), t = Math.max(1, sp.getLong("pg_total", 1));
        boolean items = "hide".equals(ph);
        int pct = (int) Math.min(100, d * 100 / t);
        progBar.setProgress((int) Math.min(1000, d * 1000 / t));
        String what = items ? "Hiding" : "lock".equals(ph) ? "Locking" : "Restoring";
        String amt = items ? d + " of " + t + " items" : (d >> 20) + " / " + (t >> 20) + " MB";
        progTxt.setText(what + (nm.isEmpty() ? "" : " " + nm) + " - " + pct + "%\n" + amt
            + ("lock".equals(ph) ? "\n(each file is written, then re-read to verify it)" : ""));
    }

    void progView(LinearLayout c) {
        progShown = progActive();
        progBar = null; progTxt = null;
        if (!progShown) return;
        progBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progBar.setMax(1000);
        progBar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        progBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD2));
        c.addView(progBar, lp(MATCH, dp(10), 14));
        progTxt = text("", 13, SUB, false);
        progTxt.setPadding(0, dp(8), 0, 0);
        c.addView(progTxt);
        progUpdate();
    }

    /** Amber banner shown while apps/files are hidden, with the live progress bar. */
    void hiddenCard(boolean hid) {
        LinearLayout c = vcol();
        c.setBackground(grad(0xFF3A2E17, 0xFF211A0B, 0x55FFB74D, 24));
        c.setPadding(dp(24), dp(18), dp(24), dp(18));
        root.addView(c, lp(MATCH, WRAP, 12));
        final boolean locking = hid && Shell.hasKey(this) && Shell.pending(this) > 0;
        c.addView(text(hid ? "Hidden" : "Working…", 20, AMBER, true));
        TextView sub = text(hid ? "Selected apps and files are hidden." + (locking && !progActive() ? " Locking files in the background…" : "")
            : "Please wait", 14, SUB, false);
        sub.setPadding(0, dp(2), 0, 0);
        c.addView(sub);
        progView(c);
    }

    /** The big pill: white "Hide now", or lime "Restore everything" while hidden. */
    void heroButton(final boolean hid) {
        int bg = hid ? GREEN : BTN_LIGHT, fg = hid ? ON_GREEN : ON_LIGHT;
        LinearLayout b = hrow();
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(bg, 28, fg));
        b.setClickable(true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(26), dp(26));
        ip.rightMargin = dp(14);
        b.addView(ico(hid ? R.drawable.ic_eye : R.drawable.ic_eye_off, 26, fg), ip);
        b.addView(text(hid ? "Restore everything" : "Hide now", 18, fg, SEMI));
        b.setOnClickListener(v -> {
            if (hid) {
                send("restore");
                // The service keeps going when the app is left; "hidden" is cleared by it when the restore is done.
                if (!LockService.restore(MainActivity.this, pin)) {
                    sp.edit().putBoolean("hidden", false).apply();
                    new Thread(() -> {
                        if (Shell.hasProblems(Shell.restoreSync(MainActivity.this, pin)))
                            runOnUiThread(() -> errorToast("Some items could not be restored. See the log."));
                    }).start();
                }
                toast("Restoring...");
            } else {
                send("hide"); sp.edit().putBoolean("hidden", true).apply();
                new Thread(() -> { Shell.hideSync(MainActivity.this); EncryptJob.schedule(MainActivity.this); }).start();
                toast("Hiding...");
            }
            main();
        });
        if (hid && LockService.RESTORING.get()) { b.setEnabled(false); b.setAlpha(0.5f); }   // restore already running
        root.addView(b, lp(MATCH, dp(56), 20));
    }

    // ---- flat settings list (icon, title, optional subtitle, trailing control, divider) ----
    void settingsList() {
        Set<String> apps = set("apps");
        root.addView(new View(this), new LinearLayout.LayoutParams(MATCH, dp(14)));
        row(R.drawable.ic_apps, "Apps to hide", apps.isEmpty() ? "None selected" : apps.size() + " selected", 18,
            true, v -> pickApps(), null);
        row(R.drawable.ic_folder, "Folders & files", filesSummary(), 18, true, v -> filesSheet(), null);
        row(R.drawable.ic_lock, "Wrong attempts before hiding", null, 15, false, null, stepper());
        row(R.drawable.ic_key, "PIN", null, 18, false, null, outlineBtn("Change PIN", v -> changePin()));
        row(R.drawable.ic_doc, "View last file-hiding log", null, 16, true, v -> showLog(), null);
    }

    void row(int iconRes, String title, String sub, int titleSp, boolean chevron, View.OnClickListener click, View trailing) {
        LinearLayout r = hrow();
        r.setPadding(dp(4), dp(14), dp(4), dp(14));
        r.setMinimumHeight(dp(68));
        r.addView(ico(iconRes, 24, TXT));
        LinearLayout col = vcol();
        col.addView(text(title, titleSp, TXT, MED));
        if (sub != null) {
            TextView st = text(sub, 15, SUB, false);
            st.setPadding(0, dp(2), 0, 0);
            col.addView(st);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        cp.leftMargin = dp(24);
        if (trailing != null) cp.rightMargin = dp(12);
        r.addView(col, cp);
        if (trailing != null) r.addView(trailing);
        else if (chevron) r.addView(ico(R.drawable.ic_chevron, 22, TXT));
        if (click != null) { r.setBackground(plainRipple()); r.setOnClickListener(click); }
        root.addView(r, new LinearLayout.LayoutParams(MATCH, WRAP));
        View d = new View(this);
        d.setBackgroundColor(LINE);
        root.addView(d, new LinearLayout.LayoutParams(MATCH, dp(1)));
    }

    String filesSummary() {
        int folders = 0, files = 0;
        for (String p : set("paths")) { if (p.substring(p.lastIndexOf('/') + 1).contains(".")) files++; else folders++; }
        if (folders + files == 0) return "Nothing selected";
        String f = folders == 0 ? "" : folders + (folders == 1 ? " folder" : " folders");
        String i = files == 0 ? "" : files + (files == 1 ? " file" : " files");
        return f.isEmpty() ? i : i.isEmpty() ? f : f + ", " + i;
    }

    Button outlineBtn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextColor(TXT); b.setTextSize(15); b.setTypeface(MED);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF),
            outline(0xFF111113, 0xFF3A3A3C, 22), rect(Color.WHITE, 22)));
        b.setStateListAnimator(null);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setLayoutParams(new LinearLayout.LayoutParams(WRAP, dp(42)));
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout stepper() {
        LinearLayout p = hrow();
        p.setBackground(outline(0xFF111113, 0xFF3A3A3C, 22));
        p.setPadding(dp(4), dp(4), dp(4), dp(4));
        final TextView val = text(String.valueOf(sp.getInt("th", 2)), 18, TXT, SEMI);
        val.setGravity(Gravity.CENTER);
        p.addView(step("−", -1, val));
        p.addView(val, new LinearLayout.LayoutParams(dp(34), WRAP));
        p.addView(step("+", 1, val));
        p.setLayoutParams(new LinearLayout.LayoutParams(WRAP, dp(42)));
        return p;
    }

    TextView step(String s, final int d, final TextView val) {
        TextView b = text(s, 22, TXT, MED);
        b.setGravity(Gravity.CENTER);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), rect(0xFF1E1E20, 17), rect(Color.WHITE, 17)));
        b.setOnClickListener(v -> {
            int n = Math.max(1, Math.min(10, sp.getInt("th", 2) + d));
            sp.edit().putInt("th", n).apply();
            val.setText(String.valueOf(n));
            send("config");
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
        return b;
    }

    void showLog() {
        TextView lt = text(sp.getString("log", "Nothing yet. Tap Hide now first."), 12, SUB, false);
        lt.setTextIsSelectable(true);
        sheet("Log", null, scroller(lt), null, null);
    }

    /** Folders & files list: remove entries, add a folder or a file. */
    void filesSheet() {
        final Dialog[] dref = new Dialog[1];
        LinearLayout list = vcol();
        final TextView err = errView();
        Set<String> paths = new TreeSet<>(set("paths"));
        if (paths.isEmpty()) list.addView(text("Nothing selected yet.", 14, SUB, false));
        for (final String p : paths) {
            LinearLayout r = hrow();
            r.setPadding(0, dp(10), 0, dp(10));
            String name = p.substring(p.lastIndexOf('/') + 1);
            r.addView(ico(name.contains(".") ? R.drawable.ic_doc : R.drawable.ic_folder, 22, SUB));
            LinearLayout col = vcol();
            col.addView(text(name, 15, TXT, MED));
            col.addView(text(p, 11, SUB, false));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            cp.leftMargin = dp(14);
            r.addView(col, cp);
            ImageView x = ico(R.drawable.ic_close, 20, SUB);
            x.setPadding(dp(8), dp(8), dp(8), dp(8));
            x.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
            x.setBackground(plainRipple());
            x.setOnClickListener(v -> {
                if (sp.getBoolean("hidden", false)) { setErr(err, "Restore first, then edit the list"); return; }
                Set<String> s = set("paths"); s.remove(p);
                sp.edit().putStringSet("paths", s).apply(); send("config");
                dref[0].dismiss(); main(); filesSheet();
            });
            r.addView(x);
            list.addView(r);
        }
        LinearLayout body = vcol();
        body.addView(scroller(list));
        body.addView(err, lp(MATCH, WRAP, 8));
        LinearLayout btns = hrow();
        Button f1 = pill("Add folder", CARD2, TXT, v -> { dref[0].dismiss(); startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1); });
        Button f2 = pill("Add file", CARD2, TXT, v -> { dref[0].dismiss(); startActivityForResult(
            new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 2); });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, MATCH, 1f);
        l1.rightMargin = dp(6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, MATCH, 1f);
        l2.leftMargin = dp(6);
        btns.addView(f1, l1);
        btns.addView(f2, l2);
        body.addView(btns, lp(MATCH, dp(48), 12));
        dref[0] = sheet("Folders & files", null, body, null, null);
    }

    void changePin() {
        final EditText cur = pinField("Current PIN", CARD2), nw = pinField("New PIN", CARD2), cf = pinField("Confirm new PIN", CARD2);
        cur.setTextSize(18); nw.setTextSize(18); cf.setTextSize(18);
        final TextView err = errView();
        LinearLayout box = vcol();
        box.addView(cur);
        box.addView(nw, lp(MATCH, WRAP, 10));
        box.addView(cf, lp(MATCH, WRAP, 10));
        box.addView(err, lp(MATCH, WRAP, 8));
        sheet("Change PIN", null, box, "Change", (d, ok) -> {
            setErr(err, null);
            final String o = cur.getText().toString(), n = nw.getText().toString();
            if (!Common.sha(o).equals(sp.getString("hash", ""))) { setErr(err, "Current PIN is wrong"); vibrateWrong(); return; }
            if (n.length() != PIN_LEN) { setErr(err, "New PIN must be 4 digits"); return; }
            if (!n.equals(cf.getText().toString())) { setErr(err, "New PINs don't match"); return; }
            if (n.equals(o)) { setErr(err, "New PIN is the same as the current one"); return; }
            ok.setEnabled(false);
            new Thread(() -> {
                // The module holds the PIN that really protects restore, so it must accept the change first.
                final int r = call("chpin", o, Common.sha(n));
                if (r == 1) Shell.rewrapPin(MainActivity.this, o, n);   // lock key follows the new PIN
                runOnUiThread(() -> {
                    if (r == 1) {
                        sp.edit().putString("hash", Common.sha(n)).apply();
                        pin = n; d.dismiss(); toast("PIN changed");
                    } else {
                        ok.setEnabled(true);
                        setErr(err, r == 0 ? "Module remembers a different PIN. Lock and reopen the app to resync."
                                     : "Module did not answer. Check it is active (reboot if you just updated) and try again.");
                    }
                });
            }).start();
        });
    }

    // ---------- status checks ----------
    void checkStatus() {
        modState = "checking";
        rootState = Environment.isExternalStorageManager() ? "ok" : "bad";   // storage access (no root needed)
        new Thread(() -> {
            boolean ok = pingModule();
            modState = ok ? (sp.getInt("modver", 0) >= Common.MODULE_VER ? "ok" : "old") : "bad";
            runOnUiThread(this::refresh);
        }).start();
    }

    void refresh() { if (pin != null) main(); }

    @Override protected void onPause() { ticker.removeCallbacks(tick); super.onPause(); }

    @Override protected void onResume() {
        super.onResume();
        ticker.removeCallbacks(tick); ticker.postDelayed(tick, 500);
        rootState = "checking"; modState = "checking"; checked = false;
        if (pin != null) main();
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77);   // shows locking progress
        new Thread(() -> EncryptJob.schedule(this)).start();   // finish locking anything still waiting
    }

    // ---------- pickers ----------
    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        String path = toPath(data.getData(), req == 1);
        if (path == null) { errorToast("Can't use that location (pick from internal storage / SD card)"); return; }
        if (Shell.resolve(path) == null) { errorToast("That folder is protected (Android, DCIM, Download...). Pick a subfolder."); return; }
        Set<String> s = set("paths"); s.add(path);
        sp.edit().putStringSet("paths", s).apply();
        send("config");
        if (pin != null) { main(); filesSheet(); }
    }

    /** Converts a picker URI to a real path like /storage/emulated/0/DCIM/Private. */
    String toPath(Uri u, boolean tree) {
        try {
            if (!"com.android.externalstorage.documents".equals(u.getAuthority())) return null;
            String id = tree ? DocumentsContract.getTreeDocumentId(u) : DocumentsContract.getDocumentId(u);
            int c = id.indexOf(':');
            if (c < 0 || c == id.length() - 1) return null; // whole-volume root not allowed
            String vol = id.substring(0, c), rel = id.substring(c + 1);
            if (rel.contains("'")) return null;
            return ("primary".equals(vol) ? "/storage/emulated/0/" : "/storage/" + vol + "/") + rel;
        } catch (Exception e) { return null; }
    }

    void pickApps() {
        PackageManager pm = getPackageManager();
        // MATCH_HIDDEN: without it, an app we've already hidden (setApplicationHiddenSetting) has no
        // resolvable launcher activity and silently drops out of this query.
        List<ResolveInfo> ri = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), MATCH_HIDDEN);
        final List<String[]> apps = new ArrayList<>(); // {label, pkg}
        Set<String> seen = new HashSet<>();
        Set<String> blocked = new HashSet<>();
        for (ResolveInfo h : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), MATCH_HIDDEN))
            blocked.add(h.activityInfo.packageName);
        String ime = android.provider.Settings.Secure.getString(getContentResolver(), "default_input_method");
        if (ime != null && ime.contains("/")) blocked.add(ime.substring(0, ime.indexOf('/')));
        for (ResolveInfo r : ri) {
            String pkg = r.activityInfo.packageName;
            if (Common.isCoreName(pkg) || blocked.contains(pkg) || !seen.add(pkg)) continue;
            apps.add(new String[]{String.valueOf(r.loadLabel(pm)), pkg});
        }
        // Belt and braces: an already-selected app might still be missing above on some OEM builds.
        // We remember it ourselves, so list it regardless of what PackageManager currently returns for it.
        for (String pkg : set("apps")) {
            if (Common.isCoreName(pkg) || !seen.add(pkg)) continue;
            String label = pkg;
            try { label = String.valueOf(pm.getApplicationInfo(pkg, MATCH_HIDDEN).loadLabel(pm)); } catch (Exception ignored) {}
            apps.add(new String[]{label, pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        Set<String> sel = set("apps");
        String[] names = new String[apps.size()];
        final boolean[] on = new boolean[apps.size()];
        for (int i = 0; i < names.length; i++) { names[i] = apps.get(i)[0]; on[i] = sel.contains(apps.get(i)[1]); }
        LinearLayout list = vcol();
        for (int i = 0; i < apps.size(); i++) {
            final int idx = i;
            LinearLayout r = hrow();
            r.setPadding(0, dp(8), 0, dp(8));
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon(apps.get(i)[1]));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(36), dp(36));
            ip.rightMargin = dp(14);
            r.addView(iv, ip);
            r.addView(text(apps.get(i)[0], 15, TXT, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
            final CheckBox cb = new CheckBox(this);
            cb.setChecked(on[i]);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
            cb.setOnCheckedChangeListener((x, c) -> on[idx] = c);
            r.addView(cb);
            r.setOnClickListener(v -> cb.toggle());
            list.addView(r);
        }
        sheet("Apps to hide", null, scroller(list), "OK", (d, ok) -> {
            Set<String> s2 = set("apps");            // keeps already-hidden apps that aren't listed right now
            for (String[] a2 : apps) s2.remove(a2[1]);
            for (int i = 0; i < on.length; i++) if (on[i]) s2.add(apps.get(i)[1]);
            sp.edit().putStringSet("apps", s2).apply();
            send("config");
            d.dismiss();
            main();
        });
    }

    /** Sends a command to the hook running in system_server (only receivers in package "android" get it). */
    void send(String cmd) {
        send(cmd, pin, null, "config".equals(cmd) ? sp.getString("hash", null) : null);
    }

    void send(String cmd, String pinArg, String nonce, String newhash) {
        Intent i = new Intent("com.pg.CMD").setPackage("android");
        i.putExtra("cmd", cmd);
        i.putExtra("pin", pinArg);
        i.putExtra("apps", android.text.TextUtils.join(",", set("apps")));
        i.putExtra("paths", android.text.TextUtils.join("\n", set("paths")));
        i.putExtra("threshold", sp.getInt("th", 2));
        if (nonce != null) i.putExtra("nonce", nonce);
        if (newhash != null) i.putExtra("newhash", newhash);
        sendBroadcast(i);
    }
}
