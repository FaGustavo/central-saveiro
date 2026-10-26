package com.fantinel.logocopy;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

// Troca a fonte de som da central (rádio, Bluetooth) sem abrir a tela do app de fábrica.
public class Fonte {
    static final String RADIO = "com.hcn.autoradio", BT = "com.autochips.bluetooth";
    static final android.media.AudioManager.OnAudioFocusChangeListener FOCO = f -> {};

    // pode ser chamado de qualquer thread; o pedido em si roda na thread principal
    static boolean pedir(Context c, String pkg) {
        // 0) pausa os outros players (senão eles pegam o som de volta)
        try {
            android.media.session.MediaSessionManager m = (android.media.session.MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (android.media.session.MediaController mc : m.getActiveSessions(null)) {
                if (mc.getPackageName().equals("com.android.bluetooth") && BT.equals(pkg)) continue;
                android.media.session.PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == android.media.session.PlaybackState.STATE_PLAYING) mc.getTransportControls().pause();
            }
        } catch (Throwable t) {}
        new Handler(Looper.getMainLooper()).postDelayed(() -> pedirAgora(c, pkg), 250);
        return true;
    }

    static void pedirAgora(Context c, String pkg) {
        // 1) pede ao serviço de fontes, "em nome" do app de fábrica
        try {
            Context outro = c.createPackageContext(pkg, 0);
            Class<?> si = Class.forName("android.sourceservice.SourceInfo");
            Object inst = si.getMethod("getInstance").invoke(null);
            si.getMethod("onRequestPlayAudio", Context.class).invoke(inst, outro);
            Log.i("LogoCopy", "fonte pedida: " + pkg);
        } catch (Throwable t) { Log.e("LogoCopy", "fonte " + pkg, t); }
        // 2) acorda o serviço do app de fábrica (ele pega o foco de áudio)
        try {
            if (RADIO.equals(pkg)) c.startService(new Intent().setClassName(RADIO, RADIO + ".service.FMPlugService"));
        } catch (Throwable t) { Log.e("LogoCopy", "servico " + pkg, t); }
        // 3) Bluetooth: pega o foco de áudio (o rádio/outros se calam) e dá play no celular
        if (BT.equals(pkg)) {
            try {
                android.media.AudioManager am = (android.media.AudioManager) c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                am.requestAudioFocus(FOCO, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN);
                // o play em si é enviado pela tela BT Música (conexão A2DP)
            } catch (Throwable t) { Log.e("LogoCopy", "foco bt", t); }
        }
    }
}
