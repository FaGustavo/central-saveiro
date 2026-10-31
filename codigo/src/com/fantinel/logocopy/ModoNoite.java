package com.fantinel.logocopy;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.Calendar;

// Modo noturno por horário: tela bem escura; ao tocar, +20% por alguns segundos. Tudo com transição suave.
public class ModoNoite {
    static final long REALCE_MS = 8000;      // quanto tempo o brilho fica mais alto depois de um toque
    static final float REALCE = 0.20f;       // +20%
    static final int PASSOS = 15, PASSO_MS = 40;   // transição de ~0,6 s

    static Context ctx;
    static final Handler h = new Handler(Looper.getMainLooper());
    static float kAtual = 1f, kAlvo = 1f;
    static long realceAte;
    static boolean iniciado;

    static SharedPreferences p(Context c) { return c.getSharedPreferences("tela", 0); }
    static boolean ligado(Context c) { return p(c).getBoolean("noturno", true); }
    static int inicio(Context c) { return p(c).getInt("ini", 19 * 60); }   // minutos do dia
    static int fim(Context c) { return p(c).getInt("fim", 6 * 60); }
    static float nivel(Context c) { return p(c).getFloat("noite", 0.25f); }

    static String hhmm(int min) { return String.format("%02d:%02d", min / 60, min % 60); }

    static boolean noHorario(Context c) {
        if (!ligado(c)) return false;
        Calendar k = Calendar.getInstance();
        int agora = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE), a = inicio(c), b = fim(c);
        return a <= b ? agora >= a && agora < b : agora >= a || agora < b;   // atravessa a meia-noite
    }

    // chamado pelo vigia ao ligar a central
    static void iniciar(Context c) {
        ctx = c.getApplicationContext();
        if (iniciado) { verificar(true); return; }
        iniciado = true;
        h.post(() -> { ouvirToques(); verificar(true); });
        h.postDelayed(ciclo, 20000);
    }

    static Runnable ciclo = () -> { verificar(false); h.postDelayed(ModoNoite.ciclo, 20000); };

    // decide o brilho certo agora e faz a transição até ele
    static void verificar(boolean forcar) {
        if (ctx == null) return;
        float alvo = 1f;
        if (noHorario(ctx)) {
            alvo = nivel(ctx);
            if (System.currentTimeMillis() < realceAte) alvo = Math.min(1f, alvo + REALCE);
        }
        if (!forcar && Math.abs(alvo - kAlvo) < 0.001f) return;
        transicao(alvo);
    }

    static int geracao;

    static void transicao(float alvo) {
        final float de = kAtual;
        kAlvo = alvo;
        final int g = ++geracao;
        for (int i = 1; i <= PASSOS; i++) {
            final float t = i / (float) PASSOS;
            h.postDelayed(() -> {
                if (g != geracao) return;          // outra transição começou
                kAtual = de + (alvo - de) * t;
                aplicar();
                if (t >= 1f || (Math.round(t * PASSOS) % 3 == 0)) Cor.leds(Cor.ultimaCorLed); // LEDs acompanham (a cada 3 passos)
            }, (long) i * PASSO_MS);
        }
    }

    // Na ré a matriz de cor tem que sair. Com ela o SurfaceFlinger monta a tela toda pela GPU (Mali),
    // que não desenha o formato de vídeo da câmera (YUV 0x10c): a imagem da ré fica preta.
    static boolean naRe;

    static void re(boolean on) {
        if (naRe == on) return;
        naRe = on;
        geracao++;                        // cancela transição em andamento
        if (on) Cor.tela(1, 1, 1);        // sem matriz
        else { kAtual = 1f; verificar(true); }   // volta suave do normal até o brilho certo
    }

    static void aplicar() {
        if (naRe) return;
        SharedPreferences pr = p(ctx);
        Cor.tela(pr.getFloat("s", 1), pr.getFloat("c", 1), kAtual);
    }

    static void toque() {
        if (ctx == null || !noHorario(ctx)) return;
        boolean jaRealcado = System.currentTimeMillis() < realceAte;
        realceAte = System.currentTimeMillis() + REALCE_MS;
        if (!jaRealcado) verificar(false);
        h.removeCallbacks(fimRealce);
        h.postDelayed(fimRealce, REALCE_MS + 50);
    }

    static final Runnable fimRealce = () -> verificar(false);

    // janelinha invisível de 1 pixel que é avisada de qualquer toque na tela (ACTION_OUTSIDE)
    static void ouvirToques() {
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            View v = new View(ctx);
            v.setOnTouchListener((vv, e) -> {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE || e.getAction() == MotionEvent.ACTION_DOWN) toque();
                return false;
            });
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(v, lp);
        } catch (Throwable t) { Log.e("LogoCopy", "toques", t); }
    }
}
