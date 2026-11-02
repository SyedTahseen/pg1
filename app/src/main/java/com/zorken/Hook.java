package com.zorken;

import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.provider.Settings;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.*;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Runs inside system_server. RULES to avoid freezing the phone (Android's Watchdog restarts
 * system_server if a service thread is blocked ~60s):
 *  - hooks on system threads only read a result and queue work; they never wait on anything slow
 *  - all heavy work runs on ONE background thread (EXEC) with a bounded queue
 *  - no lock that a hooked system thread can need is ever held during slow operations
 */
public class Hook implements IXposedHookLoadPackage {
    static final String CFG = "/data/system/zk_config";
    static volatile Context ctx;
    static volatile boolean reg;
    static final AtomicBoolean regStarted = new AtomicBoolean();
    static final AtomicInteger fails = new AtomicInteger();
    static final Object LOCK = new Object();       // only for receiver registration
    static final Object HIDE_LOCK = new Object();  // only used by EXEC thread, never by system threads
    static long lastHide;
    static final ThreadPoolExecutor EXEC = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(8), new ThreadPoolExecutor.DiscardPolicy());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"android".equals(lp.packageName)) return;
        try {
            Class<?> c = XposedHelpers.findClass("com.android.server.locksettings.LockSettingsService", lp.classLoader);
            XC_MethodHook h = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        // never register receivers on this (system binder) thread
                        final Context c0 = (Context) XposedHelpers.getObjectField(p.thisObject, "mContext");
                        if (!reg && regStarted.compareAndSet(false, true))
                            new Thread(new Runnable() { public void run() { ensure(c0); } }).start();
                        Object r = p.getResult();
                        if (r == null) return;
                        int code = (Integer) XposedHelpers.callMethod(r, "getResponseCode");
                        if (code == 0) { fails.set(0); }       // correct unlock: never restores anything
                        else if (code == -1) {                 // wrong credential
                            int n = fails.incrementAndGet();
                            Properties cf = load();
                            int th = Integer.parseInt(cf.getProperty("threshold", "2"));
                            if (n >= th && !"1".equals(cf.getProperty("hidden")))
                                EXEC.execute(new Runnable() { public void run() { hide(false); } });
                        }
                    } catch (Throwable t) { XposedBridge.log("ZK: " + t); }
                }
            };
            XposedBridge.hookAllMethods(c, "verifyCredential", h);
            XposedBridge.hookAllMethods(c, "checkCredential", h);
            try {
                Class<?> am = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", lp.classLoader);
                XposedBridge.hookAllMethods(am, "systemReady", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try { ensure((Context) XposedHelpers.getObjectField(p.thisObject, "mContext")); }
                        catch (Throwable t) { XposedBridge.log("ZK ams: " + t); }
                    }
                });
            } catch (Throwable t) { XposedBridge.log("ZK ams hook: " + t); }
            XposedBridge.log("ZK: hooked");
        } catch (Throwable t) { XposedBridge.log("ZK hook failed: " + t); }
    }

    static void ensure(Context c) {
        if (reg || c == null) return;
        synchronized (LOCK) {
            if (reg) return;
            try {
                ctx = c;
                BroadcastReceiver rc = new BroadcastReceiver() {
                    @Override public void onReceive(Context x, final Intent i) {
                        // bounded single worker: a flood of broadcasts cannot create threads or pile up work
                        EXEC.execute(new Runnable() { public void run() { cmd(i); } });
                    }
                };
                IntentFilter f = new IntentFilter("com.zorken.CMD");
                if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(rc, f, 2 /* RECEIVER_EXPORTED */);
                else c.registerReceiver(rc, f);
                reg = true;
            } catch (Throwable t) { XposedBridge.log("ZK register: " + t); regStarted.set(false); }
        }
    }

    static void cmd(Intent i) {
        try {
            String cmd = i.getStringExtra("cmd"), pin = i.getStringExtra("pin"), nonce = i.getStringExtra("nonce");
            if ("ping".equals(cmd)) { pong(); return; }
            Properties c = load();
            String h = c.getProperty("hash");
            boolean pinOk = h != null && pin != null && Common.sha(pin).equals(h);
            boolean auth = h == null || pinOk;
            if ("hide".equals(cmd)) { hide(true); return; }       // hiding is the safe direction: no PIN needed
            if ("pull".equals(cmd)) {
                // The reinstalled app asks for everything we remembered. Needs the PIN; answer goes only to com.zorken.
                Intent r = reply("com.zorken.ACK", nonce, pinOk);
                if (pinOk) {
                    r.putExtra("hash", h);
                    r.putExtra("apps", nz(c.getProperty("apps")));
                    r.putExtra("paths", nz(c.getProperty("paths")));
                    r.putExtra("threshold", Integer.parseInt(c.getProperty("threshold", "2")));
                    r.putExtra("hidden", "1".equals(c.getProperty("hidden")));
                    r.putExtra("vkey", nz(c.getProperty("vkey")));
                }
                send(r);
                return;
            }
            if ("chpin".equals(cmd)) {
                String nh = i.getStringExtra("newhash");
                boolean ok = pinOk && nh != null && nh.matches("[0-9a-f]{64}");
                if (ok) { c.setProperty("hash", nh); save(c); }
                send(reply("com.zorken.ACK", nonce, ok));
                return;
            }
            if (!auth) { if (nonce != null) send(reply("com.zorken.ACK", nonce, false)); return; }
            if ("config".equals(cmd)) {
                c.setProperty("apps", nz(i.getStringExtra("apps")));
                if (i.hasExtra("paths")) c.setProperty("paths", nz(i.getStringExtra("paths")));
                c.setProperty("threshold", String.valueOf(Math.max(1, Math.min(10, i.getIntExtra("threshold", 2)))));
                String nh = i.getStringExtra("newhash");
                if (nh != null && nh.matches("[0-9a-f]{64}")) c.setProperty("hash", nh);
                if (c.getProperty("vkey") == null) c.setProperty("vkey", Common.randHex(32));   // device secret for the file vault
                save(c);
                if (nonce != null) {
                    Intent ack = reply("com.zorken.ACK", nonce, true);
                    ack.putExtra("vkey", c.getProperty("vkey"));
                    send(ack);
                }
            } else if ("restore".equals(cmd)) {
                synchronized (HIDE_LOCK) {
                    for (String p : nz(c.getProperty("apps")).split(",")) {
                        if (p.isEmpty()) continue;
                        setHidden(p, false);
                        try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                    }
                    c = load();
                    c.setProperty("hidden", "0"); fails.set(0); lastHide = 0; save(c);
                }
            }
        } catch (Throwable t) { XposedBridge.log("ZK cmd: " + t); }
    }

    /** An intent addressed only to the Zorken app's receiver (works even when the app is stopped). */
    static Intent toApp(String action) {
        return new Intent(action).setComponent(new ComponentName("com.zorken", "com.zorken.FileReceiver")).addFlags(0x20 /* FLAG_INCLUDE_STOPPED_PACKAGES */);
    }

    /** Builds an answer intent addressed only to the Zorken app. */
    static Intent reply(String action, String nonce, boolean ok) {
        Intent in = toApp(action);
        in.putExtra("nonce", nonce);
        in.putExtra("ok", ok);
        return in;
    }
    static void send(Intent in) {
        try { ctx.sendBroadcast(in); } catch (Throwable t) { XposedBridge.log("ZK send: " + t); }
    }

    /**
     * Sends the "hide files" broadcast the way only the system can, so the app's long locking job is not killed:
     *  1. puts com.zorken on the battery-optimisation allowlist (no throttling while it encrypts);
     *  2. delivers the broadcast with a temporary allowlist, which lets the app start its foreground service even
     *     though it is in the background (Android 12+ normally forbids that).
     * Everything is best effort: any failure falls back to the plain broadcast, so hiding itself is never affected.
     */
    static void sendAllowed(Intent in) {
        long id = Binder.clearCallingIdentity();
        try {
            try {      // battery-optimisation allowlist: PowerExemptionManager (Android 12+), old PowerWhitelistManager as fallback
                boolean done = false;
                try {
                    Object pe = ctx.getSystemService("power_exemption");
                    if (pe != null) { XposedHelpers.callMethod(pe, "addToPermanentAllowList", "com.zorken"); done = true; }
                } catch (Throwable t) { XposedBridge.log("ZK allowlist (exemption): " + t); }
                if (!done) {
                    Object pw = ctx.getSystemService("power_whitelist");
                    if (pw != null) XposedHelpers.callMethod(pw, "addToWhitelist", "com.zorken");
                }
            } catch (Throwable t) { XposedBridge.log("ZK allowlist: " + t); }
            try {
                Object bo = XposedHelpers.callStaticMethod(Class.forName("android.app.BroadcastOptions"), "makeBasic");
                boolean set = false;
                // (duration, type FOREGROUND_SERVICE_ALLOWED = 0, reason code, reason text) - Android 12L/13/14+
                for (int reason : new int[]{0, 1}) {      // 0 = REASON_UNKNOWN (what the platform itself uses for the legacy call)
                    if (set) break;
                    try { XposedHelpers.callMethod(bo, "setTemporaryAppAllowlist", 600000L, 0, reason, "Zorken locking"); set = true; }
                    catch (Throwable ignored) {}
                }
                if (!set) { try { XposedHelpers.callMethod(bo, "setTemporaryAppAllowlist", 600000L); set = true; } catch (Throwable ignored) {} }
                if (!set) throw new IllegalStateException("no setTemporaryAppAllowlist");
                Bundle opts = (Bundle) XposedHelpers.callMethod(bo, "toBundle");
                java.lang.reflect.Method m = ctx.getClass().getMethod("sendBroadcastAsUser", Intent.class, UserHandle.class, String.class, Bundle.class);
                m.invoke(ctx, in, XposedHelpers.callStaticMethod(UserHandle.class, "of", 0), null, opts);
                return;
            } catch (Throwable t) { XposedBridge.log("ZK allowlisted send: " + t); }
            send(in);       // fallback
        } finally { Binder.restoreCallingIdentity(id); }
    }

    static void pong() {
        Intent in = toApp("com.zorken.PONG");
        in.putExtra("ver", Common.MODULE_VER);
        in.putExtra("has", load().getProperty("hash") != null);   // lets a freshly reinstalled app know a PIN already exists
        send(in);
    }

    static void hide(boolean force) {
        synchronized (HIDE_LOCK) {
            long now = System.currentTimeMillis();
            Properties c = load();
            if (!force && "1".equals(c.getProperty("hidden"))) return;   // already hidden
            if (now - lastHide < 10000) return;                          // rate limit: ignore hide spam
            lastHide = now;
            for (String p : nz(c.getProperty("apps")).split(",")) {
                if (p.isEmpty()) continue;
                setHidden(p, true);
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}  // go easy on PackageManager
            }
            c = load();
            c.setProperty("hidden", "1"); save(c);
        }
        // ask the UI app (root) to hide files
        // send the folder list along: a freshly reinstalled app has none of its own yet
        Intent in = toApp("com.zorken.HIDE_FILES");
        in.putExtra("paths", nz(load().getProperty("paths")));
        sendAllowed(in);
    }

    static void setHidden(String pkg, boolean hide) {
        long id = Binder.clearCallingIdentity();
        try {
            if (hide && isProtected(pkg)) { XposedBridge.log("ZK: skipped protected " + pkg); return; }
            if (hide) {
                try {
                    ActivityManager am = (ActivityManager) ctx.getSystemService("activity");
                    XposedHelpers.callMethod(am, "forceStopPackage", pkg);
                } catch (Throwable ignored) {}
            }
            PackageManager pm = ctx.getPackageManager();
            try {
                XposedHelpers.callMethod(pm, "setApplicationHiddenSettingAsUser", pkg, hide, XposedHelpers.callStaticMethod(UserHandle.class, "of", 0));
            } catch (Throwable t) {
                pm.setApplicationEnabledSetting(pkg, hide ? 3 : 0, 0);
            }
        } catch (Throwable t) { XposedBridge.log("ZK hide " + pkg + ": " + t); }
        finally { Binder.restoreCallingIdentity(id); }
    }

    /** Never hide launcher, keyboard, accessibility/notification services, SystemUI, Settings, Phone, Play services, etc. */
    static boolean isProtected(String pkg) {
        if (Common.isCoreName(pkg)) return true;
        try {
            Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo r : ctx.getPackageManager().queryIntentActivities(h, 0))
                if (pkg.equals(r.activityInfo.packageName)) return true;
            ContentResolver cr = ctx.getContentResolver();
            String ime = Settings.Secure.getString(cr, "default_input_method");
            if (ime != null && ime.startsWith(pkg + "/")) return true;
            String acc = Settings.Secure.getString(cr, "enabled_accessibility_services");
            if (acc != null && acc.contains(pkg + "/")) return true;
            String nl = Settings.Secure.getString(cr, "enabled_notification_listeners");
            if (nl != null && nl.contains(pkg + "/")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    static String nz(String s) { return s == null ? "" : s; }
    static Properties load() {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(CFG)) { p.load(in); } catch (Exception ignored) {}
        return p;
    }
    /** Atomic write (temp file + rename) so a crash can never leave a half-written config. */
    static void save(Properties p) {
        try {
            File t = new File(CFG + ".tmp");
            try (FileOutputStream o = new FileOutputStream(t)) { p.store(o, null); }
            if (!t.renameTo(new File(CFG))) XposedBridge.log("ZK save: rename failed");
        } catch (Exception e) { XposedBridge.log("ZK save: " + e); }
    }
}
