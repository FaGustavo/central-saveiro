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
    static final int[] SUB_FREQS = {60, 80, 100, 120, 150, 200};
    static final int[] BASS_FREQS = {60, 80, 100, 200};

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
        call("setBassBoost", p.getInt("bass", 7), p.getInt("bassF", 100));
        call("setAlto", p.getInt("mid", 7), DONT_CARE);
        call("setTreble", p.getInt("treble", 7), DONT_CARE);
        call("setLoud", p.getBoolean("loud", false) ? 1 : 0);
        call("setSubWoofer", p.getBoolean("subOn", false) ? p.getInt("subLvl", 7) : 0, p.getInt("subF", 80));
        // balanço (x: -7 esquerda .. +7 direita) e fader (y: -7 trás .. +7 frente); cada caixa 0..15
        int x = p.getInt("bal", 7) - 7, y = p.getInt("fad", 7) - 7;
        float l = x > 0 ? (7 - x) / 7f : 1, r = x < 0 ? (7 + x) / 7f : 1;
        float f = y < 0 ? (7 + y) / 7f : 1, t = y > 0 ? (7 - y) / 7f : 1;
        call("setBalance", Math.round(15 * l * f), Math.round(15 * r * f), Math.round(15 * l * t), Math.round(15 * r * t));
        Log.i(TAG, "som aplicado " + p.getAll());
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
        eq.addView(slider("Grave", "bass", 0, 14, 7, true));
        eq.addView(chooser("Frequência do grave", "bassF", BASS_FREQS, 100));
        eq.addView(slider("Médio", "mid", 0, 14, 7, true));
        eq.addView(slider("Agudo", "treble", 0, 14, 7, true));
        eq.addView(toggle("Loudness (reforço em volume baixo)", "loud", false));

        // coluna 2: subwoofer
        LinearLayout sub = card("Subwoofer");
        sub.addView(toggle("Subwoofer ligado", "subOn", false));
        sub.addView(slider("Nível do sub", "subLvl", 1, 14, 7, false));
        sub.addView(chooser("Corte (passa-baixa)", "subF", SUB_FREQS, 80));
        TextView hint = text("Abaixo da frequência de corte vai para o sub.\nTeste no carro: se nada mudar, o chip desta central não controla o sub.", 13, MUTED);
        hint.setPadding(0, dp(12), 0, 0);
        sub.addView(hint);

        // balanço e fader ficam no mesmo cartão do sub (coluna da direita)
        TextView bt = text("Balanço e fader", 20, ACCENT);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        bt.setPadding(0, dp(24), 0, 0);
        sub.addView(bt);
        sub.addView(slider("Balanço (esquerda ↔ direita)", "bal", 0, 14, 7, false));
        sub.addView(slider("Fader (trás ↔ frente)", "fad", 0, 14, 7, false));

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
            else val = String.valueOf(v);
            t.setText(label + ":  " + val);
        };
        show.run();
        s.setOnSeekBarChangeListener(new Bar(this, key, min, show));
        box.addView(t);
        box.addView(s, new LinearLayout.LayoutParams(-1, dp(44)));
        return box;
    }

    // ouvinte da barra: mostra o valor ao arrastar e aplica ao soltar
    static class Bar implements SeekBar.OnSeekBarChangeListener {
        final Som a; final String key; final int min; final Runnable show;
        Bar(Som a, String key, int min, Runnable show) { this.a = a; this.key = key; this.min = min; this.show = show; }
        public void onProgressChanged(SeekBar sb, int p, boolean user) { show.run(); }
        public void onStartTrackingTouch(SeekBar sb) {}
        public void onStopTrackingTouch(SeekBar sb) {
            a.sp.edit().putInt(key, sb.getProgress() + min).commit();
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
