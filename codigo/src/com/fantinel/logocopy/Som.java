package com.fantinel.logocopy;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.lang.reflect.Method;

// App "Som": controla o chip de som (CD3313) direto, sem o EQ falso do app original.
public class Som extends Activity {
    static final String TAG = "LogoCopy";
    static final int DONT_CARE = 0x7fffffff;
    // grave/médio/agudo em dB, passos de 2: o chip vai de -14 a +14; o que passar disso
    // vai pelo equalizador do Android (som todo, sessão 0)
    static final int MIN_DB = -30, MAX_DB = 30;

    static final int BG = Color.rgb(16, 20, 28), CARD = Color.rgb(30, 36, 48),
            ACCENT = Color.rgb(64, 170, 255), TEXT = Color.rgb(235, 240, 245), MUTED = Color.rgb(150, 160, 175);

    SharedPreferences sp;

    // ---------- aplicar no chip ----------
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("som", 0); }

    static void call(String fn, Object... args) {
        try {
            Object fx = Class.forName("android.audio.AudioEffect").getMethod("getInstance").invoke(null);
            for (Method m : fx.getClass().getMethods())
                if (m.getName().equals(fn) && m.getParameterTypes().length == args.length) { m.invoke(fx, args); return; }
        } catch (Throwable t) { Log.e(TAG, "som " + fn, t); }
    }

    static void apply(Context c) {
        SharedPreferences p = prefs(c);
        int b = db(p, "bass"), m = db(p, "mid"), tr = db(p, "treble");
        call("setBassBoost", chip(b), 100);   // o chip ignora a frequência do grave
        call("setAlto", chip(m), DONT_CARE);
        call("setTreble", chip(tr), DONT_CARE);
        aplicarEq(c);
        call("setLoud", p.getBoolean("loud", false) ? 1 : 0);
        // o chip só liga/desliga o sub; nível e corte ficam no módulo do sub (GAIN e LPF)
        call("setSubWoofer", p.getBoolean("subOn", false) ? 7 : 0, 80);
        // balanço (x: -7 esquerda .. +7 direita) e fader (y: -7 trás .. +7 frente); cada caixa 0..15
        int x = p.getInt("bal", 7) - 7, y = p.getInt("fad", 7) - 7;
        float l = x > 0 ? (7 - x) / 7f : 1, r = x < 0 ? (7 + x) / 7f : 1;
        float f = y < 0 ? (7 + y) / 7f : 1, t = y > 0 ? (7 - y) / 7f : 1;
        call("setBalance", Math.round(15 * l * f), Math.round(15 * r * f), Math.round(15 * l * t), Math.round(15 * r * t));
        Log.i(TAG, "som aplicado " + p.getAll());
    }

    // valor em dB; aceita o formato antigo (0..14, centro 7, passos de 2 dB)
    static int db(SharedPreferences p, String key) {
        if (p.contains(key + "Db")) return p.getInt(key + "Db", 0);
        return (p.getInt(key, 7) - 7) * 2;
    }

    // dB -> índice do chip (0..14)
    static int chip(int db) { return Math.max(-14, Math.min(14, db)) / 2 + 7; }

    // o que passa de ±14 dB vai pelo equalizador do Android (precisa ficar vivo: guardado aqui)
    static android.media.audiofx.Equalizer eq;

    static int alem(int db) { return db < -14 ? db + 14 : db > 14 ? db - 14 : 0; }

    // Volume suave: a curva de volume da central (0..40, no chip) dá saltos enormes nos primeiros níveis.
    // Corte extra no som: nível 2 = "suave" dB; cresce mais SUAVE_EXTRA dB até o nível SUAVE_PICO
    // (achata os saltos 2..8, onde o chip sobe rápido) e depois diminui devagar até sumir no
    // nível SUAVE_ATE (passos de ~0,6 dB, imperceptíveis). O volume máximo não muda.
    static final int SUAVE_PICO = 8, SUAVE_EXTRA = 6, SUAVE_ATE = 30;

    static int corteVolume(Context c) {
        int s = prefs(c).getInt("suave", 8);
        int i = ((android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE))
                .getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        if (s == 0 || i < 2 || i >= SUAVE_ATE) return 0;
        float pico = s + SUAVE_EXTRA;
        float k = i <= SUAVE_PICO
                ? s + SUAVE_EXTRA * (i - 2) / (float) (SUAVE_PICO - 2)
                : pico * (SUAVE_ATE - i) / (float) (SUAVE_ATE - SUAVE_PICO);
        return -Math.round(k);
    }

    static boolean ouvindoVolume;

    // chamado pelo Guard: refaz o equalizador sempre que o volume da música muda
    static void ouvirVolume(Context c) {
        if (ouvindoVolume) return;
        ouvindoVolume = true;
        final Context app = c.getApplicationContext();
        app.registerReceiver(new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context x, android.content.Intent i) {
                if (i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == android.media.AudioManager.STREAM_MUSIC)
                    aplicarEq(app);
            }
        }, new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
    }

    static synchronized void aplicarEq(Context c) {
        SharedPreferences p = prefs(c);
        int v = corteVolume(c);
        int xb = alem(db(p, "bass")) + v, xm = alem(db(p, "mid")) + v, xt = alem(db(p, "treble")) + v;
        try {
            if (xb == 0 && xm == 0 && xt == 0) {
                if (eq != null) eq.setEnabled(false);
                return;
            }
            if (eq == null) eq = new android.media.audiofx.Equalizer(0, 0);
            short n = eq.getNumberOfBands();
            short[] r = eq.getBandLevelRange();
            StringBuilder s = new StringBuilder();
            for (short i = 0; i < n; i++) {
                int hz = eq.getCenterFreq(i) / 1000;
                int x = hz < 400 ? xb : hz < 2500 ? xm : xt;
                short lvl = (short) Math.max(r[0], Math.min(r[1], x * 100));
                eq.setBandLevel(i, lvl);
                s.append(' ').append(hz).append("Hz=").append(lvl / 100);
            }
            eq.setEnabled(true);
            Log.i(TAG, "eq extra (volume " + v + " dB)" + s);
        } catch (Throwable e) { Log.e(TAG, "eq extra", e); }
    }

    // ---------- tela ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = prefs(this);
        getWindow().getDecorView().setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Som", 26, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button reset = new Button(this);
        reset.setText("Zerar tudo");
        reset.setOnClickListener(v -> { sp.edit().clear().commit(); apply(this); recreate(); });
        top.addView(reset);
        root.addView(top);

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);

        // coluna 1: tonalidade
        LinearLayout eq = card("Tonalidade");
        eq.addView(tom("Grave", "bass"));
        eq.addView(tom("Médio", "mid"));
        eq.addView(tom("Agudo", "treble"));
        eq.addView(toggle("Loudness (reforço em volume baixo)", "loud", false));

        // coluna 2: subwoofer
        LinearLayout sub = card("Subwoofer");
        sub.addView(toggle("Subwoofer ligado", "subOn", false));

        // balanço e fader ficam no mesmo cartão do sub (coluna da direita)
        TextView bt = text("Balanço e fader", 20, ACCENT);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        bt.setPadding(0, dp(24), 0, 0);
        sub.addView(bt);
        sub.addView(slider("Balanço (esquerda ↔ direita)", "bal", 0, 14, 7, false));
        sub.addView(slider("Fader (trás ↔ frente)", "fad", 0, 14, 7, false));

        // volume na ré (Re.java): o som desce aos poucos até este nível ao engatar a ré
        TextView rt = text("Ré", 20, ACCENT);
        rt.setTypeface(Typeface.DEFAULT_BOLD);
        rt.setPadding(0, dp(24), 0, 0);
        sub.addView(rt);
        sub.addView(slider("Volume na ré", "reVol", 0, 40, 10, false));
        TextView rh = text("Ao engatar a ré o som desce aos poucos até este nível e volta ao sair. Se o volume já estiver mais baixo, não muda.", 13, MUTED);
        rh.setPadding(0, dp(6), 0, 0);
        sub.addView(rh);

        TextView vt = text("Volume", 20, ACCENT);
        vt.setTypeface(Typeface.DEFAULT_BOLD);
        vt.setPadding(0, dp(24), 0, 0);
        sub.addView(vt);
        sub.addView(slider("Suavizar volume baixo", "suave", 0, 15, 8, false));
        TextView vh = text("Os primeiros níveis de volume dão saltos grandes. Isto abaixa os níveis baixos (a partir do 2) para os passos ficarem menores; o corte some aos poucos até o nível " + SUAVE_ATE + " e o volume máximo não muda.", 13, MUTED);
        vh.setPadding(0, dp(6), 0, 0);
        sub.addView(vh);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(0, dp(12), dp(12), 0);
        cols.addView(eq, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, -2, 1);
        lp2.setMargins(dp(12), dp(12), 0, 0);
        cols.addView(sub, lp2);
        root.addView(cols);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        apply(this);
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        return t;
    }

    LinearLayout card(String name) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(16), dp(20), dp(20));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD); g.setCornerRadius(dp(14));
        c.setBackground(g);
        TextView t = text(name, 20, ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(t);
        return c;
    }

    // controle deslizante; "centered" mostra -7..+7 (passos de 2 dB)
    View slider(String label, String key, int min, int max, int def, boolean centered) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(12), 0, 0);
        TextView t = text("", 16, TEXT);
        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(sp.getInt(key, def) - min);
        Runnable show = () -> {
            int v = s.getProgress() + min;
            String val;
            if (centered) val = ((v - 7) * 2 > 0 ? "+" : "") + (v - 7) * 2 + " dB";
            else if (key.equals("bal")) val = v == 7 ? "centro" : v < 7 ? "esquerda " + (7 - v) : "direita " + (v - 7);
            else if (key.equals("fad")) val = v == 7 ? "centro" : v < 7 ? "trás " + (7 - v) : "frente " + (v - 7);
            else if (key.equals("reVol")) val = v == 0 ? "mudo" : v + " de 40";
            else if (key.equals("suave")) val = v == 0 ? "desligado" : "-" + v + " dB no nível 2";
            else val = String.valueOf(v);
            t.setText(label + ":  " + val);
        };
        show.run();
        s.setOnSeekBarChangeListener(new Bar(this, key, min, show));
        box.addView(t);
        box.addView(s, new LinearLayout.LayoutParams(-1, dp(44)));
        return box;
    }

    // grave/médio/agudo: -30..+14 dB em passos de 2 (salvo como "<key>Db")
    View tom(String label, String key) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(12), 0, 0);
        TextView t = text("", 16, TEXT);
        SeekBar s = new SeekBar(this);
        s.setMax((MAX_DB - MIN_DB) / 2);
        s.setProgress((db(sp, key) - MIN_DB) / 2);
        Runnable show = () -> {
            int v = s.getProgress() * 2 + MIN_DB;
            t.setText(label + ":  " + (v > 0 ? "+" : "") + v + " dB");
        };
        show.run();
        s.setOnSeekBarChangeListener(new Bar(this, key + "Db", MIN_DB, 2, show));
        box.addView(t);
        box.addView(s, new LinearLayout.LayoutParams(-1, dp(44)));
        return box;
    }

    // ouvinte da barra: mostra o valor ao arrastar e aplica ao soltar
    static class Bar implements SeekBar.OnSeekBarChangeListener {
        final Som a; final String key; final int min, step; final Runnable show;
        Bar(Som a, String key, int min, Runnable show) { this(a, key, min, 1, show); }
        Bar(Som a, String key, int min, int step, Runnable show) { this.a = a; this.key = key; this.min = min; this.step = step; this.show = show; }
        public void onProgressChanged(SeekBar sb, int p, boolean user) { show.run(); }
        public void onStartTrackingTouch(SeekBar sb) {}
        public void onStopTrackingTouch(SeekBar sb) {
            a.sp.edit().putInt(key, sb.getProgress() * step + min).commit();
            if (key.equals("reVol")) return;   // só o Re.java usa (lido na hora de engatar a ré)
            apply(a);
        }
    }

    View toggle(String label, String key, boolean def) {
        Switch w = new Switch(this);
        w.setText(label); w.setTextSize(16); w.setTextColor(TEXT);
        w.setPadding(0, dp(14), 0, dp(4));
        w.setChecked(sp.getBoolean(key, def));
        w.setOnCheckedChangeListener((v, on) -> { sp.edit().putBoolean(key, on).commit(); apply(this); });
        return w;
    }

    // linha de botões para escolher uma frequência
    View chooser(String label, String key, int[] opts, int def) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(12), 0, 0);
        box.addView(text(label, 16, TEXT));
        LinearLayout row = new LinearLayout(this);
        Button[] bs = new Button[opts.length];
        Runnable paint = () -> {
            int cur = sp.getInt(key, def);
            for (int i = 0; i < opts.length; i++) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(8));
                g.setColor(opts[i] == cur ? ACCENT : Color.rgb(50, 58, 74));
                bs[i].setBackground(g);
                bs[i].setTextColor(opts[i] == cur ? Color.BLACK : TEXT);
            }
        };
        for (int i = 0; i < opts.length; i++) {
            final int f = opts[i];
            Button btn = new Button(this);
            btn.setText(f + " Hz"); btn.setTextSize(14); btn.setAllCaps(false);
            btn.setOnClickListener(v -> { sp.edit().putInt(key, f).commit(); paint.run(); apply(this); });
            bs[i] = btn;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
            lp.setMargins(0, dp(6), dp(6), 0);
            row.addView(btn, lp);
        }
        paint.run();
        box.addView(row);
        return box;
    }
}
