package com.fantinel.logocopy;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

// Tela "Luzes": cor dinâmica (segue a capa) ou cor fixa para os LEDs dos botões e a Saveiro neon.
public class Luzes extends Activity {
    static final int BG = Color.rgb(16, 20, 28), CARD = Color.rgb(30, 36, 48),
            ACCENT = Color.rgb(64, 170, 255), TEXT = Color.rgb(235, 240, 245), MUTED = Color.rgb(150, 160, 175);
    static final int[] CORES = {
            Color.rgb(0, 200, 255), Color.rgb(40, 90, 255), Color.rgb(150, 60, 255), Color.rgb(255, 60, 200),
            Color.rgb(255, 30, 40), Color.rgb(255, 140, 0), Color.rgb(255, 220, 0), Color.rgb(40, 255, 90),
            Color.rgb(255, 255, 255)};
    static final String[] NOMES = {"Ciano", "Azul", "Roxo", "Rosa", "Vermelho", "Laranja", "Amarelo", "Verde", "Branco"};

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("luzes", 0); }
    static boolean dinamico(Context c) { return prefs(c).getBoolean("dinamico", true); }
    static int fixa(Context c) { return prefs(c).getInt("cor", Cor.PADRAO); }

    ImageView preview;
    LinearLayout fixaBox;
    View[] bolinhas = new View[CORES.length];
    SeekBar matiz;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(20));

        TextView title = text("Luzes", 26, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(14), 0, 0);

        // coluna da esquerda: opções
        LinearLayout opts = card();
        Switch sw = new Switch(this);
        sw.setText("Cor dinâmica (segue a capa da música)");
        sw.setTextSize(18); sw.setTextColor(TEXT);
        sw.setChecked(dinamico(this));
        sw.setOnCheckedChangeListener((v, on) -> {
            prefs(this).edit().putBoolean("dinamico", on).commit();
            atualizar();
            if (!on) Cor.leds(fixa(this));
        });
        opts.addView(sw);

        fixaBox = new LinearLayout(this);
        fixaBox.setOrientation(LinearLayout.VERTICAL);
        fixaBox.setPadding(0, dp(18), 0, 0);
        fixaBox.addView(text("Cor fixa", 16, MUTED));

        LinearLayout pal = new LinearLayout(this);
        pal.setPadding(0, dp(10), 0, dp(6));
        for (int i = 0; i < CORES.length; i++) {
            final int c = CORES[i];
            View bo = new View(this);
            bolinhas[i] = bo;
            bo.setContentDescription(NOMES[i]);
            bo.setOnClickListener(v -> escolher(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
            lp.setMargins(0, 0, dp(10), 0);
            pal.addView(bo, lp);
        }
        fixaBox.addView(pal);

        TextView pers = text("Personalizada (arraste)", 16, MUTED);
        pers.setPadding(0, dp(18), 0, dp(4));
        fixaBox.addView(pers);
        matiz = new SeekBar(this);
        matiz.setMax(359);
        float[] hsv = new float[3];
        Color.colorToHSV(fixa(this), hsv);
        matiz.setProgress((int) hsv[0]);
        GradientDrawable arco = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED});
        arco.setCornerRadius(dp(6));
        matiz.setProgressDrawable(arco);
        matiz.setOnSeekBarChangeListener(new Matiz(this));
        fixaBox.addView(matiz, new LinearLayout.LayoutParams(-1, dp(40)));
        opts.addView(fixaBox);

        // modo noturno por horário
        Switch mn = new Switch(this);
        mn.setText("Modo noturno automático");
        mn.setTextSize(18); mn.setTextColor(TEXT);
        mn.setPadding(0, dp(22), 0, 0);
        mn.setChecked(ModoNoite.ligado(this));
        mn.setOnCheckedChangeListener((v, on) -> {
            getSharedPreferences("tela", 0).edit().putBoolean("noturno", on).commit();
            ModoNoite.verificar(true);
        });
        opts.addView(mn);

        LinearLayout horas = new LinearLayout(this);
        horas.setPadding(0, dp(10), 0, 0);
        TextView ini = pill("Início  " + ModoNoite.hhmm(ModoNoite.inicio(this)));
        TextView fim = pill("Fim  " + ModoNoite.hhmm(ModoNoite.fim(this)));
        ini.setOnClickListener(v -> escolherHora("ini", ModoNoite.inicio(this), ini, "Início  "));
        fim.setOnClickListener(v -> escolherHora("fim", ModoNoite.fim(this), fim, "Fim  "));
        horas.addView(ini);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
        fl.setMargins(dp(12), 0, 0, 0);
        horas.addView(fim, fl);
        opts.addView(horas);

        TextView nt = text("", 16, MUTED);
        nt.setPadding(0, dp(14), 0, dp(4));
        opts.addView(nt);
        SeekBar noite = new SeekBar(this);
        noite.setMax(95); // 5%..100%
        float atual = ModoNoite.nivel(this);
        noite.setProgress(Math.round(atual * 100) - 5);
        nt.setText("Brilho no modo noturno: " + Math.round(atual * 100) + "%  (toque na tela: +20%)");
        noite.setOnSeekBarChangeListener(new Noite(this, nt));
        opts.addView(noite, new LinearLayout.LayoutParams(-1, dp(40)));

        // coluna da direita: prévia
        LinearLayout prev = card();
        prev.addView(text("Prévia", 16, MUTED));
        preview = new ImageView(this);
        preview.setImageResource(getResources().getIdentifier("saveiro_card", "drawable", getPackageName()));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        prev.addView(preview, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(560), 1.1f);
        l1.setMargins(0, 0, dp(12), 0);
        row.addView(opts, l1);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(560), 1);
        l2.setMargins(dp(12), 0, 0, 0);
        row.addView(prev, l2);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView dica = text("Com a cor dinâmica ligada, os LEDs dos botões e a Saveiro da tela inicial mudam para a cor da capa de cada música. "
                + "No rádio ou sem capa, fica ciano. Escolher uma cor desliga a cor dinâmica.", 15, MUTED);
        dica.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(dica);

        setContentView(root);
        ModoNoite.iniciar(this);
        atualizar();
    }

    // barra de matiz: mostra ao arrastar, aplica ao soltar
    static class Matiz implements SeekBar.OnSeekBarChangeListener {
        final Luzes a;
        Matiz(Luzes a) { this.a = a; }
        int cor(SeekBar s) { return Color.HSVToColor(new float[]{s.getProgress(), 0.85f, 1f}); }
        public void onProgressChanged(SeekBar s, int p, boolean user) { if (user) a.preview.setColorFilter(Cor.filtro(cor(s))); }
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) { a.escolher(cor(s)); }
    }

    // barra do brilho noturno: mostra na hora enquanto arrasta, salva ao soltar
    static class Noite implements SeekBar.OnSeekBarChangeListener {
        final Luzes a; final TextView t;
        Noite(Luzes a, TextView t) { this.a = a; this.t = t; }
        float k(SeekBar s) { return (s.getProgress() + 5) / 100f; }
        public void onProgressChanged(SeekBar s, int p, boolean user) {
            t.setText("Brilho no modo noturno: " + Math.round(k(s) * 100) + "%  (toque na tela: +20%)");
            if (user) {
                android.content.SharedPreferences pr = a.getSharedPreferences("tela", 0);
                Cor.tela(pr.getFloat("s", 1), pr.getFloat("c", 1), k(s));
            }
        }
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) {
            a.getSharedPreferences("tela", 0).edit().putFloat("noite", k(s)).commit();
            // volta ao brilho certo para o estado atual do farol (a prévia some se o farol estiver apagado)
            a.h.postDelayed(() -> ModoNoite.verificar(true), 2500);
        }
    }

    void escolherHora(String chave, int atual, TextView alvo, String rotulo) {
        new android.app.TimePickerDialog(this, (tp, hh, mm) -> {
            int min = hh * 60 + mm;
            getSharedPreferences("tela", 0).edit().putInt(chave, min).commit();
            alvo.setText(rotulo + ModoNoite.hhmm(min));
            ModoNoite.verificar(true);
        }, atual / 60, atual % 60, true).show();
    }

    TextView pill(String s) {
        TextView t = text(s, 18, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(Color.argb(60, 255, 255, 255));
        t.setBackground(g);
        return t;
    }

    final android.os.Handler h = new android.os.Handler();

    void escolher(int c) {
        prefs(this).edit().putInt("cor", c).putBoolean("dinamico", false).commit();
        Cor.leds(c);
        recreateSwitch();
        atualizar();
    }

    // se escolheu uma cor com a chave ligada, desliga a chave
    void recreateSwitch() {
        LinearLayout opts = (LinearLayout) fixaBox.getParent();
        ((Switch) opts.getChildAt(0)).setChecked(false);
    }

    void atualizar() {
        boolean din = dinamico(this);
        int c = fixa(this);
        fixaBox.setAlpha(din ? 0.4f : 1f);
        preview.setColorFilter(Cor.filtro(din ? Cor.PADRAO : c));
        for (int i = 0; i < CORES.length; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(CORES[i]);
            if (!din && CORES[i] == c) g.setStroke(dp(4), Color.WHITE);
            bolinhas[i].setBackground(g);
        }
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        return t;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(22), dp(18), dp(22), dp(18));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD); g.setCornerRadius(dp(14));
        c.setBackground(g);
        return c;
    }
}
