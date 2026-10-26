package com.fantinel.logocopy;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

// Tensão da bateria do carro, lida pela placa MCU (serviço "mcu_service").
public class Bateria {
    static final float LIMITE = 11.50f;

    static float ler() {
        try {
            Object b = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "mcu_service");
            Object m = Class.forName("android.carsource.IMcuManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
            return (Float) m.getClass().getMethod("getBattery").invoke(m);
        } catch (Throwable t) { return 0; }
    }

    static String texto(float v) { return v <= 0 ? "" : String.format("Bateria %.1f V", v).replace('.', ','); }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("bateria", 0); }

    // o contorno vermelho na tela inicial fica enquanto este alerta estiver ativo
    static boolean alertaAtivo(Context c) { return prefs(c).getBoolean("alerta", false); }

    // chamado pelo vigia a cada 15 s: avisa se ficar abaixo do limite por 3 leituras seguidas (~30 s)
    static int baixas;

    static void verificar(Context c) {
        float v = ler();
        if (v <= 0) return;
        long ultimo = prefs(c).getLong("ultimo", 0), agora = System.currentTimeMillis();
        if (v >= LIMITE) {
            baixas = 0;
            // só limpa o alerta 1 min depois do aviso (para o teste também mostrar o contorno)
            if (alertaAtivo(c) && agora - ultimo > 60 * 1000) prefs(c).edit().putBoolean("alerta", false).apply();
            return;
        }
        baixas++;
        if (baixas >= 3 && agora - ultimo > 10 * 60 * 1000) avisar(c, v);
    }

    static void avisar(Context c, float v) {
        prefs(c).edit().putBoolean("alerta", true).putLong("ultimo", System.currentTimeMillis()).apply();
        Log.i("LogoCopy", "bateria baixa " + v);
        new Handler(c.getMainLooper()).post(() -> mostrarFaixa(c, v));
    }

    // retângulo vermelho (50% opaco) cobrindo 80% da tela por 5 s, por cima de qualquer app
    static void mostrarFaixa(Context c, float v) {
        try {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            FrameLayout root = new FrameLayout(c);

            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.argb(128, 220, 20, 30));
            g.setCornerRadius(40);
            box.setBackground(g);

            TextView t1 = new TextView(c);
            t1.setText("BATERIA BAIXA");
            t1.setTextColor(Color.WHITE); t1.setTextSize(64); t1.setTypeface(Typeface.DEFAULT_BOLD);
            t1.setGravity(Gravity.CENTER);
            TextView t2 = new TextView(c);
            t2.setText(String.format("%.1f V", v).replace('.', ','));
            t2.setTextColor(Color.WHITE); t2.setTextSize(96); t2.setTypeface(Typeface.DEFAULT_BOLD);
            t2.setGravity(Gravity.CENTER);
            TextView t3 = new TextView(c);
            t3.setText("Abaixo de 11,5 V");
            t3.setTextColor(Color.WHITE); t3.setTextSize(24);
            t3.setGravity(Gravity.CENTER);
            box.addView(t1); box.addView(t2); box.addView(t3);

            int w = c.getResources().getDisplayMetrics().widthPixels, h = c.getResources().getDisplayMetrics().heightPixels;
            root.addView(box, new FrameLayout.LayoutParams((int) (w * 0.8f), (int) (h * 0.8f), Gravity.CENTER));

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            wm.addView(root, lp);

            try {
                ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 600);
                new Handler(c.getMainLooper()).postDelayed(tg::release, 1500);
            } catch (Throwable t) {}

            new Handler(c.getMainLooper()).postDelayed(() -> {
                try { wm.removeView(root); } catch (Throwable t) {}
            }, 5000);
        } catch (Throwable t) { Log.e("LogoCopy", "faixa", t); }
    }
}
