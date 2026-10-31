package com.fantinel.logocopy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

// Ao ligar a central: se o pendrive (Mídia/VLC) era a fonte, volta a tocar de onde parou.
// A central não reinicia ao desligar a ignição, ela "dorme" (quick boot): o sistema avisa
// com QB_POWEROFF ao desligar e QB_POWERON ao ligar.
public class Retomar {
    static final String TAG = "LogoCopy", VLC = "org.videolan.vlc";
    static final Handler h = new Handler(Looper.getMainLooper());

    public static class Aviso extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = String.valueOf(i.getAction());
            Log.i(TAG, "retomar: " + a);
            if (a.endsWith("QB_POWEROFF")) guardar(c);
            else ligou(c);
        }
    }

    static SharedPreferences p(Context c) { return c.getSharedPreferences("retomar", 0); }

    // ao desligar: anota a fonte que estava tocando (a tela inicial guarda a fonte atual em "home/fonte")
    static void guardar(Context c) {
        h.removeCallbacksAndMessages(null);
        String f = c.getSharedPreferences("home", 0).getString("fonte", "");
        p(c).edit().putString("fonte", f).commit();
        Log.i(TAG, "retomar: fonte ao desligar = " + f);
    }

    static int tentativas;

    static void ligou(Context c) {
        final Context app = c.getApplicationContext();
        String f = p(app).getString("fonte", app.getSharedPreferences("home", 0).getString("fonte", ""));
        Log.i(TAG, "retomar: ultima fonte = " + f);
        if (!VLC.equals(f)) return;
        tentativas = 0;
        h.removeCallbacksAndMessages(null);
        h.postDelayed(() -> esperarPendrive(app), 5000);
    }

    static boolean pendrive(Context c) {
        try {
            StorageManager sm = (StorageManager) c.getSystemService(Context.STORAGE_SERVICE);
            for (StorageVolume v : sm.getStorageVolumes())
                if (v.isRemovable() && Environment.MEDIA_MOUNTED.equals(v.getState())) return true;
        } catch (Throwable t) { Log.e(TAG, "retomar: pendrive", t); }
        return false;
    }

    // espera o pendrive montar (até ~2 min)
    static void esperarPendrive(Context c) {
        if (pendrive(c)) { h.postDelayed(() -> tocar(c), 3000); return; }
        if (++tentativas > 40) { Log.i(TAG, "retomar: pendrive não montou"); return; }
        h.postDelayed(() -> esperarPendrive(c), 3000);
    }

    static VlcBiblioteca vlc;

    static void tocar(Context c) {
        if (vlc != null) vlc.desconectar();
        vlc = new VlcBiblioteca(c);
        vlc.conectar(ok -> {
            if (!ok) { Log.e(TAG, "retomar: vlc não conectou"); return; }
            MediaController mc = vlc.controle();
            if (tocando(mc)) { Log.i(TAG, "retomar: vlc já está tocando"); return; }
            // sem nada carregado, o play do VLC carrega a última lista e volta à posição salva
            mc.getTransportControls().play();
            Log.i(TAG, "retomar: play no vlc");
            h.postDelayed(() -> {
                if (tocando(mc)) { Log.i(TAG, "retomar: tocando"); return; }
                // plano B: pedido de "última lista" do próprio VLC
                c.sendBroadcast(new Intent("org.videolan.vlc.remote.LastPlaylist").setPackage(VLC));
                Log.i(TAG, "retomar: pedido de última lista ao vlc");
            }, 4000);
        });
    }

    static boolean tocando(MediaController mc) {
        PlaybackState st = mc.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }
}
