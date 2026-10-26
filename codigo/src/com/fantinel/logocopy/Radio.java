package com.fantinel.logocopy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Method;

// Rádio novo: tela própria que comanda o rádio do sistema (android.radio.RadioPlayer).
// O som continua saindo pelo app de fábrica (com.hcn.autoradio), que fica por trás.
public class Radio extends Activity {
    static final String TAG = "LogoCopy", FABRICA = "com.hcn.autoradio";
    static final int ACCENT = Color.rgb(64, 170, 255), MUTED = Color.rgb(170, 178, 190);

    final Handler h = new Handler();
    ImageView bg, capa;
    TextView freqTv, unidadeTv, nomeTv, textoTv, bandaFm, bandaAm, buscarTv;
    Regua regua;
    LinearLayout lista;
    int freq, ultimaFreqCapa = -1;
    String banda = "FM-1", nome = "";
    String listaKey = "";
    long buscandoAte;

    // ---------- acesso ao RadioPlayer (por reflexão) ----------
    static Object rp() throws Exception { return Class.forName("android.radio.RadioPlayer").getMethod("getRadioPlayer").invoke(null); }

    static Object call(String fn, Object... a) {
        try {
            Object r = rp();
            for (Method m : r.getClass().getMethods())
                if (m.getName().equals(fn) && m.getParameterTypes().length == a.length) return m.invoke(r, a);
        } catch (Throwable t) { Log.e(TAG, "radio " + fn, t); }
        return null;
    }

    static Object bandaEnum(String nome) {
        try {
            Class<?> c = Class.forName("android.radio.RadioPlayer$BAND");
            for (Object o : c.getEnumConstants()) if (o.toString().equals(nome) || ((Enum<?>) o).name().equals(nome.replace("-", ""))) return o;
        } catch (Throwable t) {}
        return null;
    }

    static boolean isFm(String banda) { return banda == null || banda.toUpperCase().startsWith("FM"); }

    static String fmt(int f, boolean fm) { return fm ? String.format("%.1f", f / 1000f).replace('.', ',') : String.valueOf(f); }

    // cor própria de cada estação (mesma frequência = mesma cor)
    static int corDa(int f) {
        float hue = (f / 100 * 47) % 360;
        return Color.HSVToColor(new float[]{hue, 0.8f, 1f});
    }

    // capa gerada: degradê na cor da estação + frequência + nome
    static Bitmap capa(int f, String nome, boolean fm, int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int cor = corDa(f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, s, s, cor, Color.rgb(Color.red(cor) / 5, Color.green(cor) / 5, Color.blue(cor) / 5), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, s, s), s * 0.06f, s * 0.06f, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(s * 0.26f);
        c.drawText(f <= 0 ? "—" : fmt(f, fm), s / 2f, s * 0.52f, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(s * 0.09f);
        p.setAlpha(210);
        c.drawText(fm ? "FM  •  MHz" : "AM  •  kHz", s / 2f, s * 0.66f, p);
        if (!TextUtils.isEmpty(nome)) {
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(s * 0.1f);
            p.setAlpha(255);
            c.drawText(nome.length() > 14 ? nome.substring(0, 14) : nome, s / 2f, s * 0.84f, p);
        }
        return b;
    }

    // ---------- tela ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 15, 22));
        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, -1, -1);
        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(160, 0, 0, 0));
        root.addView(shade, -1, -1);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(28), dp(18), dp(28), dp(14));
        root.addView(col, -1, -1);

        // topo: título, FM/AM, buscar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView tt = text(30, Color.WHITE, true);
        tt.setText("Rádio");
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        // só FM (sem AM)
        bandaFm = pill("FM"); bandaAm = pill("AM");
        buscarTv = pill("Buscar estações");
        buscarTv.setOnClickListener(v -> buscar());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.setMargins(dp(24), 0, 0, 0);
        top.addView(buscarTv, bl);
        col.addView(top);

        // meio: capa + frequência + botões
        LinearLayout mid = new LinearLayout(this);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        mid.setPadding(0, dp(40), 0, dp(6));
        capa = new ImageView(this);
        mid.addView(capa, new LinearLayout.LayoutParams(dp(250), dp(250)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(32), 0, 0, 0);
        LinearLayout fr = new LinearLayout(this);
        fr.setGravity(Gravity.BOTTOM);
        freqTv = text(96, Color.WHITE, true);
        unidadeTv = text(28, MUTED, false);
        unidadeTv.setPadding(dp(12), 0, 0, dp(22));
        fr.addView(freqTv); fr.addView(unidadeTv);
        info.addView(fr);
        nomeTv = text(26, ACCENT, true);
        textoTv = text(18, MUTED, false);
        info.addView(nomeTv); info.addView(textoTv);

        LinearLayout bts = new LinearLayout(this);
        bts.setGravity(Gravity.CENTER_VERTICAL);
        bts.setPadding(0, dp(14), 0, 0);
        // obs.: no rádio desta central seekUp/seekDown são invertidos (seekUp desce)
        bts.addView(botao("◀◀", "Buscar para trás", v -> call("seekUp", call("getUiband"))));
        bts.addView(botao("−", "Ajuste fino para baixo", v -> call("stepDown")));
        bts.addView(botao("+", "Ajuste fino para cima", v -> call("stepUp")));
        bts.addView(botao("▶▶", "Buscar para frente", v -> call("seekDown", call("getUiband"))));
        info.addView(bts);
        mid.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(mid);

        // régua de sintonia
        regua = new Regua(this);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(96));
        rl.setMargins(0, dp(22), 0, dp(4));
        col.addView(regua, rl);

        // lista de estações (memórias)
        TextView lt = text(15, MUTED, false);
        lt.setText("Estações  (toque para ouvir • segure para salvar a frequência atual aqui)");
        lt.setPadding(0, dp(6), 0, dp(6));
        col.addView(lt);
        lista = new LinearLayout(this);
        col.addView(lista, new LinearLayout.LayoutParams(-1, dp(124)));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        garantirSom();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    public void onBackPressed() { Home.irParaInicio(this); }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    // se o rádio não é a fonte atual, abre o app de fábrica por um instante (ele liga o som) e volta para cá
    static long abrindo;

    void garantirSom() {
        String fonte = getSharedPreferences("home", 0).getString("fonte", "");
        if (fonte.contains(FABRICA) || System.currentTimeMillis() - abrindo < 5000) return;
        abrindo = System.currentTimeMillis();
        // liga o som do rádio sem abrir a tela do app de fábrica
        new Thread(() -> Fonte.pedir(getApplicationContext(), FABRICA)).start();
    }

    Runnable tick = () -> { atualizar(); h.postDelayed(this.tick, 400); };

    void atualizar() {
        Object info = call("getRadioInfo");
        String ps = "", rt = "";
        try {
            freq = info.getClass().getField("mFreq").getInt(info);
            banda = String.valueOf(info.getClass().getField("mUiBand").get(info));
            Object p = info.getClass().getField("mPSname").get(info);
            Object r = info.getClass().getField("mRTtype").get(info);
            ps = p == null ? "" : String.valueOf(p).trim();
            rt = r == null ? "" : String.valueOf(r).trim();
        } catch (Throwable t) {}
        boolean fm = isFm(banda);
        if (!regua.arrastando) freqTv.setText(freq <= 0 ? "—" : fmt(freq, fm));
        unidadeTv.setText(fm ? "MHz" : "kHz");
        nome = ps;
        nomeTv.setText(ps.isEmpty() ? banda.replace("-1", "").replace("-", " ") : ps);
        textoTv.setText(rt.equals(ps) ? "" : rt);
        marcar(bandaFm, fm); marcar(bandaAm, !fm);
        regua.definir(fm, freq);

        boolean buscando = System.currentTimeMillis() < buscandoAte;
        buscarTv.setText(buscando ? "Buscando…" : "Buscar estações");

        if (freq != ultimaFreqCapa || !ps.equals(capaNome)) {
            ultimaFreqCapa = freq; capaNome = ps;
            Bitmap c = capa(freq, ps, fm, 460);
            capa.setImageBitmap(c);
            bg.setImageBitmap(Home.blur(this, c));
        }
        montarLista(fm);
    }

    String capaNome = "";

    // ---------- lista de estações ----------
    static final int MEMORIAS = 6;

    // lista completa do sistema (o rádio guarda mais posições; usamos só as 6 primeiras)
    int[] todas() {
        Object r = call("getPreset", call("getUiband"));
        return r instanceof int[] ? (int[]) r : new int[0];
    }

    int[] memorias() { return java.util.Arrays.copyOf(todas(), Math.min(MEMORIAS, todas().length)); }

    void montarLista(boolean fm) {
        int[] m = memorias();
        StringBuilder k = new StringBuilder(banda).append(freq);
        for (int f : m) k.append(',').append(f);
        if (k.toString().equals(listaKey)) return;
        listaKey = k.toString();
        lista.removeAllViews();
        for (int i = 0; i < m.length; i++) {
            final int idx = i, f = m[i];
            TextView t = text(30, Color.WHITE, true);
            t.setText(fmt(f, fm));
            t.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            int cor = corDa(f);
            g.setColor(f == freq ? Color.argb(200, Color.red(cor), Color.green(cor), Color.blue(cor)) : Color.argb(90, 255, 255, 255));
            t.setBackground(g);
            t.setOnClickListener(v -> call("setUibandIndexFreq", call("getUiband"), idx, f));
            t.setOnLongClickListener(v -> { salvar(idx); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(118), 1);
            lp.setMargins(i == 0 ? 0 : dp(12), 0, 0, 0);
            lista.addView(t, lp);
        }
    }

    void salvar(int idx) {
        int[] m = todas();
        if (idx >= m.length || freq <= 0) return;
        m[idx] = freq;
        call("setPreset", call("getUiband"), m);
        listaKey = "";
        android.widget.Toast.makeText(this, "Salvo: " + fmt(freq, isFm(banda)), android.widget.Toast.LENGTH_SHORT).show();
    }

    void buscar() {
        call("autoScan", call("getUiband"), MEMORIAS);
        buscandoAte = System.currentTimeMillis() + 45000;
        listaKey = "";
    }

    void trocarBanda(String nova) {
        if (isFm(nova) == isFm(banda)) return;
        Object be = bandaEnum(nova);
        if (be == null) return;
        Object r = call("getPreset", be);
        int f = r instanceof int[] && ((int[]) r).length > 0 ? ((int[]) r)[0] : (isFm(nova) ? 87500 : 531);
        call("setUibandIndexFreq", be, 0, f);
        listaKey = "";
    }

    void sintonizar(int f) { call("setUibandIndexFreq", call("getUiband"), -1, f); }

    // ---------- utilidades de tela ----------
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView text(int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(size); t.setTextColor(color); t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    TextView pill(String s) {
        TextView t = text(18, Color.WHITE, true);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(22), dp(10), dp(22), dp(10));
        marcar(t, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(8), 0, 0, 0);
        t.setLayoutParams(lp);
        return t;
    }

    void marcar(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(on ? ACCENT : Color.argb(70, 255, 255, 255));
        t.setBackground(g);
        t.setTextColor(on ? Color.BLACK : Color.WHITE);
    }

    View botao(String s, String desc, View.OnClickListener l) {
        TextView t = text(30, Color.WHITE, true);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(desc);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.argb(60, 255, 255, 255));
        t.setBackground(g);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(84), dp(84));
        lp.setMargins(0, 0, dp(18), 0);
        t.setLayoutParams(lp);
        return t;
    }

    // régua de sintonia: arraste para escolher a frequência, solte para sintonizar
    static class Regua extends View {
        final Radio a;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean fm = true, arrastando;
        int freq, previa;

        Regua(Radio a) { super(a); this.a = a; }

        int min() { return fm ? 87500 : 531; }
        int max() { return fm ? 108000 : 1602; }
        int passo() { return fm ? 100 : 9; }

        void definir(boolean fm, int f) {
            if (this.fm != fm || this.freq != f) { this.fm = fm; this.freq = f; invalidate(); }
        }

        float x(int f) {
            float pad = getWidth() * 0.03f;
            return pad + (f - min()) / (float) (max() - min()) * (getWidth() - 2 * pad);
        }

        int fDe(float x) {
            float pad = getWidth() * 0.03f;
            float t = Math.max(0, Math.min(1, (x - pad) / (getWidth() - 2 * pad)));
            int f = min() + Math.round(t * (max() - min()) / passo()) * passo();
            return Math.max(min(), Math.min(max(), f));
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), mid = h * 0.55f;
            int step = fm ? 500 : 45;
            for (int f = min(); f <= max(); f += fm ? 100 : 9) {
                boolean big = fm ? f % 1000 == 0 : (f - min()) % 90 == 0;
                if (!big && f % step != 0 && fm) continue;
                p.setColor(Color.argb(big ? 200 : 110, 255, 255, 255));
                p.setStrokeWidth(big ? 3 : 2);
                float len = big ? h * 0.32f : h * 0.18f;
                c.drawLine(x(f), mid - len / 2, x(f), mid + len / 2, p);
                if (big && fm && ((f / 1000) % 2 == 0)) {
                    p.setTextSize(h * 0.2f); p.setTextAlign(Paint.Align.CENTER);
                    c.drawText(String.valueOf(f / 1000), x(f), h * 0.98f, p);
                }
            }
            int at = arrastando ? previa : freq;
            if (at > 0) {
                p.setColor(corDa(at));
                p.setStrokeWidth(6);
                c.drawLine(x(at), h * 0.08f, x(at), h * 0.78f, p);
                c.drawCircle(x(at), h * 0.08f, 8, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    arrastando = true;
                    previa = fDe(e.getX());
                    a.freqTv.setText(fmt(previa, fm));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    arrastando = false;
                    a.sintonizar(fDe(e.getX()));
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
