package com.zorken;

import android.content.*;
import java.util.*;

public class FileReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context c, Intent i) {
        final SharedPreferences sp = c.getSharedPreferences("zk", 0);
        String a = i.getAction();
        if ("com.zorken.PONG".equals(a)) {
            sp.edit().putLong("pong", System.currentTimeMillis())
                .putInt("modver", i.getIntExtra("ver", 0))          // 0 = old module that doesn't report a version
                .putBoolean("modhas", i.getBooleanExtra("has", false)).apply();
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)) { EncryptJob.schedule(c); return; }   // finish locking after a reboot
        if ("com.zorken.ACK".equals(a)) { ack(sp, i); return; }
        if (!"com.zorken.HIDE_FILES".equals(a)) return;

        SharedPreferences.Editor ed = sp.edit().putBoolean("hidden", true);
        // Fresh install that was never set up: use the folder list the module remembers, so files still get hidden.
        if (sp.getString("hash", null) == null && sp.getStringSet("paths", new HashSet<String>()).isEmpty())
            ed.putStringSet("paths", paths(i.getStringExtra("paths")));
        ed.apply();
        final PendingResult pr = goAsync();
        new Thread(new Runnable() { public void run() { try { Shell.hideSync(c); EncryptJob.schedule(c); } finally { pr.finish(); } } }).start();
    }

    /**
     * Answer from the module to a command the app sent. It only counts if it carries the one-time nonce
     * the app generated for that command (the nonce travels only to the system hook, never to other apps).
     */
    static void ack(SharedPreferences sp, Intent i) {
        String nonce = i.getStringExtra("nonce"), pend = sp.getString("pendnonce", null);
        if (nonce == null || !nonce.equals(pend)) return;
        boolean ok = i.getBooleanExtra("ok", false);
        SharedPreferences.Editor ed = sp.edit().remove("pendnonce");
        String vk = i.getStringExtra("vkey");
        if (ok && vk != null && vk.length() >= 32) ed.putString("vkey", vk);      // device secret for the file vault
        if (ok && i.hasExtra("hash")) {          // answer to "pull": adopt everything the module remembered
            ed.putString("hash", i.getStringExtra("hash"));
            ed.putStringSet("apps", split(i.getStringExtra("apps"), ","));
            ed.putStringSet("paths", paths(i.getStringExtra("paths")));
            ed.putInt("th", Math.max(1, Math.min(10, i.getIntExtra("threshold", 2))));
            ed.putBoolean("hidden", i.getBooleanExtra("hidden", false));
        }
        ed.putString("ack", nonce + ":" + (ok ? "1" : "0")).commit();   // written last: the app polls for it
    }

    static Set<String> split(String s, String sep) {
        Set<String> out = new HashSet<>();
        if (s != null) for (String x : s.split(sep)) if (!x.isEmpty()) out.add(x);
        return out;
    }

    /** Folder list from the module, filtered through the same safety rules used when picking folders. */
    static Set<String> paths(String s) {
        Set<String> out = new HashSet<>();
        for (String p : split(s, "\n")) if (Shell.resolve(p) != null) out.add(p);
        return out;
    }
}
