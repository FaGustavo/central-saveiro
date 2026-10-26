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

    // põe o app na lista temporária de exceções de energia (o mesmo que "cmd deviceidle tempwhitelist")
    static void liberarSegundoPlano(String pkg) {
        try {
            android.os.IBinder b = (android.os.IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "deviceidle");
            Object di = Class.forName("android.os.IDeviceIdleController$Stub")
                    .getMethod("asInterface", android.os.IBinder.class).invoke(null, b);
            di.getClass().getMethod("addPowerSaveTempWhitelistApp", String.class, long.class, int.class, String.class)
                    .invoke(di, pkg, 10000L, 0, "fonte");
        } catch (Throwable t) { Log.e("LogoCopy", "liberar " + pkg, t); }
    }

    static void pedirAgora(Context c, String pkg) {
        // 1) pede ao serviço de fontes, "em nome" do app de fábrica
        try {
            Class<?> si = Class.forName("android.sourceservice.SourceInfo");
            Object inst = si.getMethod("getInstance").invoke(null);
            if (BT.equals(pkg)) {
                // Bluetooth: tem que ser em nome da tela de música BT. Só assim o caminho vira A2DP;
                // sem ele a ROM recusa o foco de áudio ao Bluetooth, que então pausa o celular.
                si.getMethod("onRequestPlayAudio", android.content.ComponentName.class)
                        .invoke(inst, new android.content.ComponentName(BT, BT + ".BtMusicActivity"));
            } else {
                si.getMethod("onRequestPlayAudio", Context.class).invoke(inst, c.createPackageContext(pkg, 0));
            }
            Log.i("LogoCopy", "fonte pedida: " + pkg);
        } catch (Throwable t) { Log.e("LogoCopy", "fonte " + pkg, t); }
        // 2) acorda o serviço do app de fábrica (ele pega o foco de áudio e toca o som do rádio).
        //    A ROM fecha o app do rádio quando outra fonte assume, e o Android 8 não deixa iniciar
        //    serviço de app parado ("app is in background"). Por isso libera o app por 10 s antes.
        try {
            if (RADIO.equals(pkg)) {
                liberarSegundoPlano(RADIO);
                c.startService(new Intent().setClassName(RADIO, RADIO + ".service.FMPlugService"));
            }
        } catch (Throwable t) { Log.e("LogoCopy", "servico " + pkg, t); }
        // 3) Bluetooth: NÃO pegar o foco de áudio aqui. O próprio Bluetooth pede o foco quando o
        //    som começa; se a gente tomar o foco dele, ele manda pause ao celular.
        //    Só devolve um foco antigo nosso, se houver. O play é enviado pela tela BT Música.
        if (BT.equals(pkg)) {
            try {
                android.media.AudioManager am = (android.media.AudioManager) c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
                am.abandonAudioFocus(FOCO);
            } catch (Throwable t) { Log.e("LogoCopy", "foco bt", t); }
        }
    }
}
