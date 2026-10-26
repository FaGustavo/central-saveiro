package com.fantinel.logocopy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

// Volume na ré: a central de fábrica corta o som de uma vez ao engatar a ré
// (manda o volume "audio_rev_volume" = 0 direto ao chip). Aqui o corte de fábrica
// fica desligado e o volume desce aos poucos até o nível escolhido na tela Som
// ("Volume na ré"); ao sair da ré, sobe aos poucos até onde estava.
public class Re {
    static final String TAG = "LogoCopy";
    static final String ACAO = "android.mcu.device.action.REVSTATUS", EXTRA = "REVSTATUS";
    static final int DESCER_MS = 1500, SUBIR_MS = 2500;
    static final Handler h = new Handler(Looper.getMainLooper());
    static Context app;
    static boolean iniciado, naRe;
    static int alvo, passoMs;

    static int volumeNaRe(Context c) { return Som.prefs(c).getInt("reVol", 10); }

    // chamado pelo Guard (processo que fica sempre vivo)
    static void iniciar(Context c) {
        if (iniciado) return;
        iniciado = true;
        app = c.getApplicationContext();
        desligarMudoDeFabrica();
        // a regra de fábrica continua ativa mesmo assim: na ré ela põe o chip em
        // audio_rev_volume % do volume, de uma vez. Com 100% ela não muda nada e a descida fica com a gente.
        try { android.provider.Settings.System.putInt(app.getContentResolver(), "audio_rev_volume", 100); } catch (Throwable t) { Log.e(TAG, "re: audio_rev_volume", t); }
        app.registerReceiver(new Aviso(), new IntentFilter(ACAO));
        Log.i(TAG, "re: vigia ligado");
    }

    // a regra de fábrica fica presa a este processo; se ele morrer, o Android a religa sozinho
    static void desligarMudoDeFabrica() {
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            Object ok = am.getClass().getMethod("enableBackcarVolumePolicy", boolean.class).invoke(am, false);
            Log.i(TAG, "re: mudo de fabrica desligado = " + ok);
        } catch (Throwable t) { Log.e(TAG, "re: desligar mudo de fabrica", t); }
    }

    static class Aviso extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            // o sistema manda REVSTATUS=true/false (boolean); aceita número também, por garantia
            Object v = i.getExtras() == null ? null : i.getExtras().get(EXTRA);
            boolean re = v instanceof Boolean ? (Boolean) v : v instanceof Integer && (Integer) v != 0;
            Log.i(TAG, "re: estado " + v);
            if (re) entrar(); else sair();
        }
    }

    static AudioManager am() { return (AudioManager) app.getSystemService(Context.AUDIO_SERVICE); }

    static void entrar() {
        if (naRe) return;
        naRe = true;
        int atual = am().getStreamVolume(AudioManager.STREAM_MUSIC);
        Som.prefs(app).edit().putInt("reSalvo", atual).apply();   // sobrevive se o processo reiniciar
        int destino = Math.min(atual, volumeNaRe(app));
        Log.i(TAG, "re: descendo " + atual + " -> " + destino);
        ir(destino, DESCER_MS);
    }

    static void sair() {
        if (!naRe) return;
        naRe = false;
        int salvo = Som.prefs(app).getInt("reSalvo", -1);
        if (salvo < 0) return;
        Log.i(TAG, "re: subindo ate " + salvo);
        ir(salvo, SUBIR_MS);
    }

    // anda 1 ponto por vez até o destino, dividindo o tempo total entre os passos
    static void ir(int destino, int totalMs) {
        h.removeCallbacks(passo);
        int dif = Math.abs(destino - am().getStreamVolume(AudioManager.STREAM_MUSIC));
        if (dif == 0) return;
        alvo = destino;
        passoMs = Math.max(30, totalMs / dif);
        h.post(passo);
    }

    static final Runnable passo = new Passo();
    static class Passo implements Runnable {
        @Override public void run() {
            int v = am().getStreamVolume(AudioManager.STREAM_MUSIC);
            if (v == alvo) return;
            am().setStreamVolume(AudioManager.STREAM_MUSIC, v + (alvo > v ? 1 : -1), 0);
            h.postDelayed(this, passoMs);
        }
    }
}
