package com.fantinel.logocopy;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

// Vídeo em tela cheia. Tocar na tela mostra/esconde os controles; "voltar" devolve o vídeo ao cartão.
public class VideoTela extends Activity {
    final Handler h = new Handler();
    Video.Quadro quadro;
    LinearLayout controles;
    TextView titulo, tempo, total;
    SeekBar barra;
    Home.Icon play;
    boolean arrastando;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        quadro = new Video.Quadro(this, true);
        root.addView(quadro, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.setOnClickListener(v -> mostrar(controles.getVisibility() != View.VISIBLE));

        controles = new LinearLayout(this);
        controles.setOrientation(LinearLayout.VERTICAL);
        controles.setBackgroundColor(Color.argb(120, 0, 0, 0));
        controles.setPadding(dp(32), dp(24), dp(32), dp(24));
        controles.setOnClickListener(v -> mostrar(false));

        // topo: voltar + título
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        TextView voltar = texto(22, Color.WHITE, true);
        voltar.setText("‹  Voltar");
        voltar.setPadding(dp(20), dp(10), dp(24), dp(10));
        GradientDrawable vg = new GradientDrawable();
        vg.setColor(Color.argb(90, 255, 255, 255)); vg.setCornerRadius(dp(30));
        voltar.setBackground(vg);
        voltar.setOnClickListener(v -> finish());
        topo.addView(voltar);
        titulo = texto(26, Color.WHITE, true);
        titulo.setPadding(dp(24), 0, 0, 0);
        topo.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1));
        controles.addView(topo, new LinearLayout.LayoutParams(-1, -2));

        // meio: anterior / play / próximo
        LinearLayout meio = new LinearLayout(this);
        meio.setGravity(Gravity.CENTER);
        Home.Icon prev = new Home.Icon(this, Home.Icon.PREV, false);
        play = new Home.Icon(this, Home.Icon.PAUSE, true);
        Home.Icon next = new Home.Icon(this, Home.Icon.NEXT, false);
        prev.setOnClickListener(v -> { Video.pular(-1); adiar(); });
        play.setOnClickListener(v -> { Video.alternar(); adiar(); });
        next.setOnClickListener(v -> { Video.pular(1); adiar(); });
        meio.addView(prev, new LinearLayout.LayoutParams(dp(96), dp(96)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(120), dp(120));
        pl.setMargins(dp(56), 0, dp(56), 0);
        meio.addView(play, pl);
        meio.addView(next, new LinearLayout.LayoutParams(dp(96), dp(96)));
        controles.addView(meio, new LinearLayout.LayoutParams(-1, 0, 1));

        // baixo: tempo + barra
        LinearLayout baixo = new LinearLayout(this);
        baixo.setGravity(Gravity.CENTER_VERTICAL);
        tempo = texto(20, Color.WHITE, false);
        total = texto(20, Color.WHITE, false);
        barra = new SeekBar(this);
        barra.setMax(1000);
        barra.setOnSeekBarChangeListener(new Arraste(this));
        baixo.addView(tempo, new LinearLayout.LayoutParams(dp(80), -2));
        baixo.addView(barra, new LinearLayout.LayoutParams(0, dp(48), 1));
        total.setGravity(Gravity.END);
        baixo.addView(total, new LinearLayout.LayoutParams(dp(80), -2));
        controles.addView(baixo, new LinearLayout.LayoutParams(-1, -2));

        root.addView(controles, -1, -1);
        setContentView(root);
        mostrar(true);
    }

    static class Arraste implements SeekBar.OnSeekBarChangeListener {
        final VideoTela a;
        Arraste(VideoTela a) { this.a = a; }
        @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user) a.tempo.setText(BtMusica.mmss((long) Video.duracao() * p / 1000));
        }
        @Override public void onStartTrackingTouch(SeekBar s) { a.arrastando = true; a.h.removeCallbacks(a.esconder); }
        @Override public void onStopTrackingTouch(SeekBar s) {
            a.arrastando = false;
            Video.irPara((int) ((long) Video.duracao() * s.getProgress() / 1000));
            a.adiar();
        }
    }

    final Runnable esconder = () -> mostrar(false);
    final Runnable tick = () -> { atualizar(); h.postDelayed(this.tick, 500); };
    final Runnable mudou = () -> { quadro.requestLayout(); atualizar(); };

    void mostrar(boolean sim) {
        controles.setVisibility(sim ? View.VISIBLE : View.GONE);
        if (sim) adiar();
    }

    void adiar() { h.removeCallbacks(esconder); h.postDelayed(esconder, 4000); }

    void atualizar() {
        if (!Video.ativo() && Video.mp == null) { finish(); return; }   // parou (outra fonte assumiu)
        String t = Video.titulo();
        if (!t.equals(titulo.getText().toString())) titulo.setText(t);
        play.setKind(Video.tocando() ? Home.Icon.PAUSE : Home.Icon.PLAY);
        int d = Video.duracao(), p = Video.posicao();
        total.setText(d > 0 ? BtMusica.mmss(d) : "");
        if (!arrastando) {
            tempo.setText(BtMusica.mmss(p));
            barra.setProgress(d > 0 ? (int) ((long) p * 1000 / d) : 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Video.ouvir(mudou);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Video.largar(mudou);
        h.removeCallbacks(tick);
        h.removeCallbacks(esconder);
    }

    // saiu de vista (Início, outro app): devolve a imagem ao cartão e fecha
    @Override
    protected void onStop() {
        super.onStop();
        if (quadro.s != null && Video.tela == quadro.s) Video.telaCheia(null);
        finish();
    }

    TextView texto(int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(size); t.setTextColor(color); t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
