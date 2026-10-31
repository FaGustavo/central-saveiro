package com.fantinel.logocopy;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Tela inicial simples: um cartão com a fonte atual + todos os apps numa grade.
public class Home extends Activity {
    static final String TAG = "LogoCopy";
    static final String RADIO = "com.hcn.autoradio";
    static final int ACCENT = Color.rgb(64, 170, 255), MUTED = Color.rgb(190, 198, 210);

    ImageView bg, art;
    TextView src, title, sub;
    Icon prev, play, next;
    GridView grid;
    final Handler h = new Handler();
    MediaSessionManager msm;
    MediaController ctl;
    String focusPkg = "";
    boolean radioMuted;
    Object lastArtKey;
    Binder srcCallback;

    // ---------- tela ----------
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN); // sem barra de status na tela inicial
        msm =(MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 15, 22));
        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, -1, -1);
        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(150, 0, 0, 0));
        root.addView(shade, -1, -1);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(28), dp(24), dp(28), dp(8));
        root.addView(col, -1, -1);

        // relógio
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.BOTTOM);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm");
        clock.setTextSize(44); clock.setTextColor(Color.WHITE); clock.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM"); date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(18); date.setTextColor(MUTED);
        date.setPadding(dp(18), 0, 0, dp(10));
        top.addView(date);
        volt = text(18, MUTED, false);
        volt.setPadding(dp(12), dp(4), dp(12), dp(4));
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.setMargins(dp(14), 0, 0, dp(6));
        top.addView(volt, vl);
        col.addView(top);

        // cartão "tocando agora"
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(20), dp(28), dp(20));
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(Color.argb(110, 20, 26, 38)); cg.setCornerRadius(dp(20));
        cg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        // moldura do cartão: fundo arredondado + Saveiro neon à direita, atrás do conteúdo
        FrameLayout cardBox = new FrameLayout(this);
        cardBox.setBackground(cg);
        cardBox.setClipToOutline(true);
        cardBox.setOnClickListener(v -> openSource());
        car = new ImageView(this);
        car.setImageResource(getResources().getIdentifier("saveiro_card", "drawable", getPackageName()));
        car.setScaleType(ImageView.ScaleType.FIT_END);
        car.setColorFilter(Cor.filtro(Cor.PADRAO));
        FrameLayout.LayoutParams carLp = new FrameLayout.LayoutParams(dp(560), -1, Gravity.END | Gravity.CENTER_VERTICAL);
        carLp.setMargins(0, dp(10), dp(6), dp(10));
        cardBox.addView(car, carLp);
        cardBox.addView(card, -1, -1);
        card.setOnClickListener(v -> openSource());

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ag = new GradientDrawable();
        ag.setColor(Color.argb(80, 255, 255, 255)); ag.setCornerRadius(dp(14));
        art.setBackground(ag);
        art.setClipToOutline(true);
        // moldura da capa; com vídeo tocando vira 16:9 e mostra o vídeo no lugar da capa
        artBox = new FrameLayout(this);
        artBox.addView(art, -1, -1);
        videoBox = new FrameLayout(this);
        GradientDrawable vg = new GradientDrawable();
        vg.setColor(Color.BLACK); vg.setCornerRadius(dp(14));
        videoBox.setBackground(vg);
        videoBox.setClipToOutline(true);
        videoBox.setVisibility(View.GONE);
        quadro = new Video.Quadro(this, false);
        videoBox.addView(quadro, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        artBox.addView(videoBox, -1, -1);
        card.addView(artBox, new LinearLayout.LayoutParams(dp(325), dp(325)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(28), 0, 0, 0);
        src = text(20, ACCENT, true);
        title = text(40, Color.WHITE, true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE); title.setMarqueeRepeatLimit(-1); title.setSelected(true);
        sub = text(22, MUTED, false);
        info.addView(src); info.addView(title); info.addView(sub);

        LinearLayout ctrls = new LinearLayout(this);
        ctrls.setGravity(Gravity.CENTER_VERTICAL);
        ctrls.setPadding(0, dp(16), 0, 0);
        prev = new Icon(this, Icon.PREV, false);
        play = new Icon(this, Icon.PLAY, true);
        next = new Icon(this, Icon.NEXT, false);
        prev.setOnClickListener(v -> control(-1));
        play.setOnClickListener(v -> control(0));
        next.setOnClickListener(v -> control(1));
        ctrls.addView(prev, new LinearLayout.LayoutParams(dp(88), dp(88)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(108), dp(108));
        pl.setMargins(dp(30), 0, dp(30), 0);
        ctrls.addView(play, pl);
        ctrls.addView(next, new LinearLayout.LayoutParams(dp(88), dp(88)));
        info.addView(ctrls);
        card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, dp(375));
        cl.setMargins(0, dp(14), 0, dp(12));
        col.addView(cardBox, cl);

        // apps
        grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setVerticalSpacing(dp(10));
        grid.setPadding(0, dp(28), 0, 0);
        grid.setSelector(new GradientDrawable());
        grid.setOnItemClickListener((p, v, pos, id) -> {
            ResolveInfo r = (ResolveInfo) p.getItemAtPosition(pos);
            if (r == null) { panel.setVisibility(View.VISIBLE); return; }
            if (r.activityInfo.name.endsWith(".Midia") && Midia.voltarAoVideo(this)) return;
            if (r.activityInfo.packageName.equals(DIPLAY)) { menuEspelhar(r); return; }
            start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(r.activityInfo.packageName, r.activityInfo.name));
        });
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(buildPanel(), -1, -1);
        setContentView(root);
        // garante o vigia (ré, bateria, modo noturno, z-link) mesmo depois de reinstalar o app
        if (Guard.enabled(this)) startService(new android.content.Intent(this, Guard.class));
        ModoNoite.iniciar(this);
        focusPkg = getSharedPreferences("home", 0).getString("fonte", "");
        cPkg = getSharedPreferences("home", 0).getString("cPkg", null);
        if (cPkg != null) {
            cTitle = getSharedPreferences("home", 0).getString("cTitle", "");
            cLine = getSharedPreferences("home", 0).getString("cLine", "");
            cSrc = label(cPkg);
        }
        registerSource();
    }

    @Override
    protected void onResume() {
        super.onResume();
        icones.clear();
        grid.setAdapter(new Apps(this));
        h.removeCallbacks(tick);
        h.post(tick);
        Video.ouvir(videoMudou);
    }

    @Override
    protected void onPause() {
        super.onPause(); // o tick continua rodando: a cor dos LEDs acompanha a música em qualquer app
        Video.largar(videoMudou);
    }

    @Override
    public void onBackPressed() { panel.setVisibility(View.GONE); }

    // ---------- painel "Ajustes" ----------
    static final String[][] AJUSTES = {
            {"Definições", "com.android.settings/com.android.settings.Settings"},
            {"Bluetooth", "com.autochips.bluetooth/com.autochips.bluetooth.MainBluetoothActivity"},
            {"Volante", "com.hcn.swc/com.hcn.swc.MainActivity"},
            {"Veículo", "com.hcn.autosetting/com.hcn.autosetting.ui.SettingActivity"},
            {"Som", "com.fantinel.logocopy/com.fantinel.logocopy.Som"},
            {"Arquivos", "com.mediatek.filemanager/com.mediatek.filemanager.FileManagerOperationActivity"},
            {"Luzes", "com.fantinel.logocopy/com.fantinel.logocopy.Luzes"},
    };
    FrameLayout panel;

    static boolean isAjuste(ResolveInfo r) {
        String c = r.activityInfo.packageName + "/" + r.activityInfo.name;
        for (String[] a : AJUSTES) if (a[1].equals(c)) return true;
        return false;
    }

    View buildPanel() {
        panel = new FrameLayout(this);
        panel.setBackgroundColor(Color.argb(170, 0, 0, 0));
        panel.setVisibility(View.GONE);
        panel.setOnClickListener(v -> panel.setVisibility(View.GONE));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), dp(20), dp(28), dp(24));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(28, 34, 46)); g.setCornerRadius(dp(20));
        box.setBackground(g);
        box.setClickable(true);
        TextView t = text(24, Color.WHITE, true);
        t.setText("Ajustes");
        box.addView(t);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(16), 0, 0);
        PackageManager pm = getPackageManager();
        for (String[] a : AJUSTES) {
            ComponentName cn = ComponentName.unflattenFromString(a[1]);
            LinearLayout it = new LinearLayout(this);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setGravity(Gravity.CENTER_HORIZONTAL);
            it.setPadding(dp(10), dp(8), dp(10), dp(8));
            ImageView iv = new ImageView(this);
            if (cn.getPackageName().equals("com.mediatek.filemanager"))
                iv.setImageResource(getResources().getIdentifier("ic_pasta", "drawable", getPackageName()));
            else try { iv.setImageDrawable(pm.getActivityIcon(cn)); } catch (Throwable e) {}
            it.addView(iv, new LinearLayout.LayoutParams(dp(80), dp(80)));
            TextView l = text(16, Color.WHITE, false);
            l.setText(a[0]); l.setGravity(Gravity.CENTER); l.setPadding(0, dp(8), 0, 0);
            it.addView(l, new LinearLayout.LayoutParams(dp(130), -2));
            it.setOnClickListener(v -> {
                panel.setVisibility(View.GONE);
                start(new Intent(Intent.ACTION_MAIN).setComponent(cn));
            });
            row.addView(it);
        }
        box.addView(row);
        panel.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return panel;
    }

    TextView volt;
    int ticks;

    // ---------- cor da Saveiro neon + LEDs dos botões ----------
    ImageView car;
    int corAtual = Cor.PADRAO, corCapa = Cor.PADRAO;
    android.animation.ValueAnimator anim;

    void mudarCor(int nova) {
        if (nova == corAtual) return;
        if (anim != null) anim.cancel();
        anim = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), corAtual, nova);
        anim.setDuration(800);
        anim.addUpdateListener(a -> {
            int c = (Integer) a.getAnimatedValue();
            car.setColorFilter(Cor.filtro(c));
            pintarIcones(c);
        });
        corAtual = nova;
        anim.start();
        Cor.leds(nova);
    }
    Runnable tick = () -> {
        update();
        mudarCor(Luzes.dinamico(this) ? corCapa : Luzes.fixa(this));        if (ticks++ % 5 == 0) {
            float v = Bateria.ler();
            volt.setText(v <= 0 ? "" : Bateria.texto(v));
            boolean baixa = v > 0 && v < Bateria.LIMITE || Bateria.alertaAtivo(this);
            if (baixa) {
                GradientDrawable g = new GradientDrawable();
                g.setStroke(dp(2), Color.rgb(230, 40, 40));
                g.setCornerRadius(dp(8));
                volt.setBackground(g);
            } else volt.setBackground(null);
        }
        h.postDelayed(this.tick, 1000);
    };

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView text(int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(size); t.setTextColor(color); t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    void start(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable t) { Log.e(TAG, "abrir", t); }
    }

    // ---------- botão DiPlay: escolher DiPlay ou ZLink (CarPlay / Android Auto) ----------
    static final String DIPLAY = "com.shihab.diplay", ZLINK = "com.zjinnova.zlink";

    void menuEspelhar(ResolveInfo r) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(20), dp(24), dp(24));
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("CarPlay / Android Auto").setView(box).create();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(130));
        lp.setMargins(dp(10), 0, dp(10), 0);
        box.addView(opcao("DiPlay", Color.rgb(64, 170, 255), v -> {
            d.dismiss();
            Guard.usarZlink(this, false);
            start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(r.activityInfo.packageName, r.activityInfo.name));
        }), lp);
        box.addView(opcao("ZLink", Color.rgb(255, 140, 40), v -> {
            d.dismiss();
            Guard.usarZlink(this, true);
            start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(ZLINK, "com.zjinnova.android.zlink.features.main.MainActivity"));
        }), lp);
        d.show();
    }

    TextView opcao(String s, int cor, View.OnClickListener l) {
        TextView t = text(30, Color.WHITE, true);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor); g.setCornerRadius(dp(20));
        t.setBackground(g);
        t.setOnClickListener(l);
        return t;
    }

    // ---------- fonte atual (aviso do serviço "sourceinfo") ----------
    void registerSource() {
        try {
            Object svc = sourceService();
            Class<?> cb = Class.forName("android.sourceservice.ISourceCallback");
            srcCallback = new SrcBinder(this);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(cb.getClassLoader(), new Class<?>[]{cb},
                    (o, m, a) -> m.getName().equals("asBinder") ? srcCallback : null);
            svc.getClass().getMethod("setcallback", cb).invoke(svc, proxy);
        } catch (Throwable t) { Log.e(TAG, "sourceinfo", t); }
    }

    static Object sourceService() throws Exception {
        Object b = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "sourceinfo");
        return Class.forName("android.sourceservice.ISourceService$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
    }

    static class SrcBinder extends Binder {
        final Home a;
        SrcBinder(Home a) { this.a = a; }
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == 2) {
                data.enforceInterface("android.sourceservice.ISourceCallback");
                String s = data.readString();
                Log.i(TAG, "fonte: " + s);
                a.h.post(() -> {
                    a.focusPkg = s == null ? "" : s;
                    a.getSharedPreferences("home", 0).edit().putString("fonte", a.focusPkg).apply();
                    a.update();
                });
                if (reply != null) reply.writeNoException();
                return true;
            }
            if (code == 1) { if (reply != null) reply.writeNoException(); return true; }
            try { return super.onTransact(code, data, reply, flags); } catch (Exception e) { return false; }
        }
    }

    // ---------- rádio ----------
    static Object radio() throws Exception {
        return Class.forName("android.radio.RadioPlayer").getMethod("getRadioPlayer").invoke(null);
    }

    static Object rcall(String fn) {
        try { Object r = radio(); return r.getClass().getMethod(fn).invoke(r); } catch (Throwable t) { return null; }
    }

    boolean radioActive() { return focusPkg.contains(RADIO); }
    boolean showingRadio() { return ctl == null && radioActive(); }
    String lastPlaying = "";

    // ---------- atualizar o cartão ----------
    MediaController pickController() {
        List<MediaController> list;
        try { list = msm.getActiveSessions(null); } catch (Throwable t) { return null; }
        // 0º quem está com o som da central (foco de áudio informado pelo sistema)
        // meu app só pega o foco de áudio para o Bluetooth (BtMusica), então ele conta como Bluetooth
        String dono = focusPkg.contains("com.autochips.bluetooth") || focusPkg.contains(getPackageName()) ? "com.android.bluetooth" : focusPkg;
        if (!dono.isEmpty())
            for (MediaController c : list)
                if (useful(c) && dono.contains(c.getPackageName())) { if (isPlaying(c)) lastPlaying = c.getPackageName(); return c; }
        // 1º o que está tocando; 2º o último que tocou (ex.: pausado); 3º o último app que pegou o áudio; 4º qualquer um
        for (MediaController c : list) if (useful(c) && isPlaying(c)) { lastPlaying = c.getPackageName(); return c; }
        for (MediaController c : list) if (useful(c) && c.getPackageName().equals(lastPlaying)) return c;
        for (MediaController c : list)
            if (useful(c) && !focusPkg.isEmpty() && focusPkg.contains(c.getPackageName())) return c;
        for (MediaController c : list) if (useful(c)) return c;
        return null;
    }

    static boolean isPlaying(MediaController c) {
        PlaybackState st = c.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    // só conta fonte com música de verdade (ignora Bluetooth desconectado etc.)
    static boolean useful(MediaController c) {
        PlaybackState st = c.getPlaybackState();
        if (st == null || st.getState() == PlaybackState.STATE_ERROR || st.getState() == PlaybackState.STATE_NONE) return false;
        MediaMetadata md = c.getMetadata();
        return md != null && !TextUtils.isEmpty(md.getString(MediaMetadata.METADATA_KEY_TITLE));
    }

    // ---------- vídeo no cartão ----------
    FrameLayout artBox, videoBox;
    Video.Quadro quadro;
    boolean modoVideo;
    final Runnable videoMudou = () -> { quadro.requestLayout(); update(); };

    void modoVideo(boolean sim) {
        if (sim == modoVideo) return;
        modoVideo = sim;
        artBox.setLayoutParams(new LinearLayout.LayoutParams(sim ? dp(512) : dp(325), sim ? dp(288) : dp(325)));
        videoBox.setVisibility(sim ? View.VISIBLE : View.GONE);
        art.setVisibility(sim ? View.GONE : View.VISIBLE);
        lastArtKey = null;
    }

    void mostrarVideo() {
        modoVideo(true);
        ctl = null;
        src.setText("Vídeo  •  " + Video.pasta());
        String t = Video.titulo();
        if (!t.equals(title.getText().toString())) title.setText(t);
        int d = Video.duracao();
        sub.setText(d > 0 ? BtMusica.mmss(Video.posicao()) + " / " + BtMusica.mmss(d) : "Abrindo…");
        play.setKind(Video.tocando() ? Icon.PAUSE : Icon.PLAY);
        bg.setImageDrawable(null);
        corCapa = Cor.PADRAO;
    }

    void update() {
        if (Video.ativo() || Video.mp != null) { mostrarVideo(); return; }
        modoVideo(false);
        ctl = pickController();
        // o rádio está com o som. Exceção: outro app tocando (ex.: vídeo do VLC), porque o
        // serviço de fontes não avisa quando o VLC pega o som. O Bluetooth não entra na exceção.
        boolean outroTocando = ctl != null && isPlaying(ctl) && !"com.android.bluetooth".equals(ctl.getPackageName());
        if (radioActive() && !outroTocando) { ctl = null; showRadio(); return; }
        if (ZLINK.equals(focusPkg) && !outroTocando) {
            // ZLink (CarPlay) não informa a música: o cartão só diz "CarPlay"
            ctl = null;
            show("ZLink", "CarPlay", "", null, appIcon(ZLINK), true);
            return;
        }
        if (ctl == null) {
            // player parado sai da lista do Android: mostra a última música conhecida
            if (cPkg != null) show(cSrc, cTitle, cLine, cArt, appIcon(cPkg), false);
            else show("Tela inicial", "Nada tocando", "Toque num app para começar", null, appIcon(null), false);
            return;
        }
        MediaMetadata md = ctl.getMetadata();
        String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String ar = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String al = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ALBUM);
        Bitmap bm = md == null ? null : md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bm == null && md != null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (semCapaReal(bm)) bm = null;
        if (bm == null && "com.android.bluetooth".equals(ctl.getPackageName())) bm = capaDeOutro(this, t);
        if (bm == null && !TextUtils.isEmpty(t)) {
            // Bluetooth não manda a capa: usa a mesma capa gerada da tela BT Música (mesma cor por álbum)
            String nome = TextUtils.isEmpty(al) ? t : al, chave = nome + "|" + ar;
            if (!chave.equals(btChave)) { btChave = chave; btCapa = BtMusica.gerada(nome, ar == null ? "" : ar, 460); }
            bm = btCapa;
        }
        PlaybackState st = ctl.getPlaybackState();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        String line2 = TextUtils.isEmpty(ar) ? (al == null ? "" : al) : (TextUtils.isEmpty(al) ? ar : ar + "  •  " + al);
        String novo = TextUtils.isEmpty(t) ? "Sem título" : t;
        if (Midia.VLC.equals(ctl.getPackageName())) { novo = Midia.nomeFaixa(novo); line2 = ""; }   // pendrive: só o nome da faixa
        if (bm != null || !novo.equals(cTitle)) cArt = bm;   // mesma música sem capa (ainda carregando) mantém a capa
        if (!novo.equals(cTitle) || !ctl.getPackageName().equals(cPkg))
            getSharedPreferences("home", 0).edit().putString("cPkg", ctl.getPackageName())
                    .putString("cTitle", novo).putString("cLine", line2).apply();
        cPkg = ctl.getPackageName(); cSrc = label(cPkg); cTitle = novo; cLine = line2;
        show(cSrc, cTitle, line2, bm, appIcon(cPkg), playing);
    }

    String btChave;
    Bitmap btCapa;

    // "voltar" das telas próprias: sempre para a tela inicial (nunca para telas antigas de fábrica)
    static void irParaInicio(Activity a) {
        a.startActivity(new Intent(a, Home.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    // ícone padrão do app no lugar da capa (ex.: cone do VLC): cantos transparentes = não é capa de verdade
    static boolean semCapaReal(Bitmap b) {
        if (b == null) return true;
        try {
            int w = b.getWidth() - 1, h = b.getHeight() - 1;
            return Color.alpha(b.getPixel(0, 0)) < 20 && Color.alpha(b.getPixel(w, 0)) < 20
                    && Color.alpha(b.getPixel(0, h)) < 20 && Color.alpha(b.getPixel(w, h)) < 20;
        } catch (Throwable t) { return false; }
    }

    // capa real da mesma música em outra sessão (ex.: Spotify da central espelhando o iPhone)
    static Bitmap capaDeOutro(Context ctx, String titulo) {
        if (TextUtils.isEmpty(titulo)) return null;
        try {
            MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController c : m.getActiveSessions(null)) {
                if (c.getPackageName().equals("com.android.bluetooth")) continue;
                MediaMetadata md = c.getMetadata();
                if (md == null) continue;
                String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (t == null || !t.trim().equalsIgnoreCase(titulo.trim())) continue;
                Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (b != null) return b;
            }
        } catch (Throwable e) {}
        return null;
    }

    // última música mostrada (para quando o player para e some da lista)
    String cPkg, cSrc, cTitle, cLine;
    Bitmap cArt;

    // manda play/anterior/próxima para o último player usado (igual aos botões do volante)
    void tecla(int code) {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code));
    }

    void showRadio() {
        Object info = rcall("getRadioInfo");
        int f = 0; String band = "", ps = "";
        try {
            f = info.getClass().getField("mFreq").getInt(info);
            band = String.valueOf(info.getClass().getField("mUiBand").get(info));
            Object p = info.getClass().getField("mPSname").get(info);
            ps = p == null ? "" : String.valueOf(p).trim();
        } catch (Throwable t) {}
        boolean fm = band.toUpperCase().startsWith("FM");
        String freq = f == 0 ? "—" : fm ? String.format("%.1f MHz", f / 1000f) : f + " kHz";
        if (f != capaRadioFreq || !ps.equals(capaRadioNome)) {
            capaRadioFreq = f; capaRadioNome = ps;
            capaRadio = f > 0 ? Radio.capa(f, ps, fm, 460) : null;
        }
        corCapa = f > 0 ? Radio.corDa(f) : Cor.PADRAO;
        show("Rádio  •  " + band, freq, ps.isEmpty() ? (fm ? "FM" : "AM") : ps, capaRadio, appIcon(RADIO), !radioMuted);
    }

    int capaRadioFreq = -1;
    String capaRadioNome = "";
    Bitmap capaRadio;

    void abrirRadio() {
        start(new Intent(this, Radio.class));
    }

    void show(String s, String t, String line2, Bitmap bm, Drawable icon, boolean playing) {
        src.setText(s);
        if (!t.equals(title.getText().toString())) title.setText(t);
        sub.setText(line2);
        play.setKind(playing ? Icon.PAUSE : Icon.PLAY);
        Object key = bm != null ? bm : icon;
        if (key == lastArtKey) return;
        lastArtKey = key;
        if (bm != null) {
            art.setImageBitmap(bm);
            art.setPadding(0, 0, 0, 0);
            bg.setImageBitmap(blur(this, bm));
            corCapa = Cor.dominante(bm);
        } else {
            if (ctl == null) corCapa = Cor.PADRAO; // rádio/nada tocando; música sem capa ainda mantém a cor
            art.setImageDrawable(icon);
            art.setPadding(dp(36), dp(36), dp(36), dp(36));
            bg.setImageDrawable(null);
        }
    }

    // ---------- botões ----------
    // próxima/anterior entre as 6 memórias, pulando frequências repetidas
    void pularMemoria(int dir) {
        Object band = Radio.call("getUiband");
        Object p = Radio.call("getPreset", band);
        Object info = Radio.call("getRadioInfo");
        if (!(p instanceof int[]) || info == null) return;
        int atual = 0;
        try { atual = info.getClass().getField("mFreq").getInt(info); } catch (Throwable t) {}
        int[] m = java.util.Arrays.copyOf((int[]) p, Math.min(Radio.MEMORIAS, ((int[]) p).length));
        // lista de estações distintas, na ordem das memórias (guarda a posição de cada uma)
        List<int[]> est = new ArrayList<>();
        for (int i = 0; i < m.length; i++) {
            boolean rep = false;
            for (int[] e : est) if (e[1] == m[i]) rep = true;
            if (!rep && m[i] > 0) est.add(new int[]{i, m[i]});
        }
        if (est.size() < 2) { Radio.call(dir > 0 ? "seekDown" : "seekUp", band); return; } // invertidos nesta central
        int idx = -1;
        for (int i = 0; i < est.size(); i++) if (est.get(i)[1] == atual) idx = i;
        int alvo;
        if (idx >= 0) alvo = (idx + dir + est.size()) % est.size();
        else {
            // fora das memórias: vai para a mais próxima na direção pedida (por frequência)
            alvo = 0;
            int melhor = Integer.MAX_VALUE;
            for (int i = 0; i < est.size(); i++) {
                int d = (est.get(i)[1] - atual) * dir;
                if (d > 0 && d < melhor) { melhor = d; alvo = i; }
            }
        }
        Radio.call("setUibandIndexFreq", band, est.get(alvo)[0], est.get(alvo)[1]);
    }

    void control(int what) {
        if (Video.mp != null) {
            if (what == 0) Video.alternar(); else Video.pular(what);
            h.postDelayed(this::update, 300);
            return;
        }
        if (showingRadio()) {
            try {
                Object r = radio();
                if (what != 0) pularMemoria(what);
                else { radioMuted = !radioMuted; r.getClass().getMethod("setMute", boolean.class).invoke(r, radioMuted); }
            } catch (Throwable t) { Log.e(TAG, "radio", t); }
        } else if (ctl != null) {
            MediaController.TransportControls tc = ctl.getTransportControls();
            if (what != 0 && "org.videolan.vlc".equals(ctl.getPackageName()) && Midia.pularVideo(this, ctl, what)) { }
            else if (what == -1) tc.skipToPrevious();
            else if (what == 1) tc.skipToNext();
            else {
                PlaybackState st = ctl.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) tc.pause(); else tc.play();
            }
        } else {
            tecla(what == -1 ? android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    : what == 1 ? android.view.KeyEvent.KEYCODE_MEDIA_NEXT : android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
        }
        h.postDelayed(this::update, 300);
    }

    void openSource() {
        if (Video.mp != null) { start(new Intent(this, VideoTela.class)); return; }
        if (showingRadio()) { abrirRadio(); return; }
        if (ZLINK.equals(focusPkg)) {
            start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(ZLINK, "com.zjinnova.android.zlink.features.main.MainActivity"));
            return;
        }
        String pkg = ctl != null ? ctl.getPackageName() : cPkg;
        if ("com.android.bluetooth".equals(pkg)) { start(new Intent(this, BtMusica.class)); return; }
        if ("org.videolan.vlc".equals(pkg)) { if (!Midia.voltarAoVideo(this)) start(new Intent(this, Midia.class)); return; }
        if (pkg == null) return;
        if (ctl != null && ctl.getSessionActivity() != null) {
            try { ctl.getSessionActivity().send(); return; } catch (PendingIntent.CanceledException e) {}
        }
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) start(i);
    }

    // ---------- utilidades ----------
    String label(String pkg) {
        if ("com.android.bluetooth".equals(pkg)) return "Bluetooth";
        if ("org.videolan.vlc".equals(pkg)) return "Mídia";
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Throwable t) { return pkg; }
    }

    Drawable appIcon(String pkg) {
        try { if (pkg != null) return getPackageManager().getApplicationIcon(pkg); } catch (Throwable t) {}
        return getResources().getDrawable(R_ic_som(), null);
    }

    int R_ic_som() { return getResources().getIdentifier("ic_som", "drawable", getPackageName()); }

    static Bitmap blur(Context c, Bitmap src) {
        try {
            int w = 120, hh = Math.max(1, w * src.getHeight() / Math.max(1, src.getWidth()));
            Bitmap s = Bitmap.createScaledBitmap(src, w, hh, true).copy(Bitmap.Config.ARGB_8888, true);
            RenderScript rs = RenderScript.create(c);
            Allocation in = Allocation.createFromBitmap(rs, s);
            Allocation out = Allocation.createTyped(rs, in.getType());
            ScriptIntrinsicBlur bl = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            bl.setRadius(18f); bl.setInput(in); bl.forEach(out); out.copyTo(s);
            bl.setInput(out); bl.forEach(in); in.copyTo(s);
            rs.destroy();
            return s;
        } catch (Throwable t) { Log.e(TAG, "blur", t); return src; }
    }

    // grade de apps
    static class Apps extends BaseAdapter {
        final Home a; final List<ResolveInfo> list; final PackageManager pm;
        Apps(Home a) {
            this.a = a; pm = a.getPackageManager();
            list = new ArrayList<>(pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
            // fora da grade: atualizações do sistema, temas do launcher antigo e itens sem nome
            java.util.Iterator<ResolveInfo> it = list.iterator();
            while (it.hasNext()) {
                ResolveInfo r = it.next();
                String p = r.activityInfo.packageName, l = String.valueOf(r.loadLabel(pm)).trim();
                boolean meuRadio = p.equals(a.getPackageName())
                        && (r.activityInfo.name.endsWith(".Radio") || r.activityInfo.name.endsWith(".BtMusica")
                            || r.activityInfo.name.endsWith(".Midia"));
                if (p.equals("org.videolan.vlc")) { it.remove(); continue; }   // VLC: substituído pela tela Mídia
                if (r.activityInfo.name.endsWith(".BtMusicActivity")) { it.remove(); continue; } // BT Música de fábrica: substituído
                if (l.isEmpty() || p.equals(a.getPackageName()) && !meuRadio || isAjuste(r)
                        || p.equals(RADIO)   // rádio de fábrica: substituído pelo rádio novo
                        || p.equals("com.hcn.autoupdate") || p.equals("com.hcn.mcuupgrade") || p.equals("com.hcn.canupgrade")
                        || p.equals("com.hcn.themepicker") || p.contains("fota") || p.contains("Fota")
                        || p.equals("com.auto.hcamera")    // Av In (o app da câmera reativa o ícone a cada boot)
                        || p.equals(ZLINK))                // ZLink: abre pelo menu do botão DiPlay
                    it.remove();
            }
            Collections.sort(list, (x, y) -> String.valueOf(x.loadLabel(pm)).compareToIgnoreCase(String.valueOf(y.loadLabel(pm))));
            list.add(null); // último item: "Ajustes"
        }
        public int getCount() { return list.size(); }
        public Object getItem(int i) { return list.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup parent) {
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(0, a.dp(6), 0, a.dp(6));
            ResolveInfo r = list.get(i);
            ImageView iv = new ImageView(a);
            String glifo = glifo(r);
            if (glifo != null) {
                // ícone no estilo próprio: símbolo branco sobre degradê na cor das luzes
                iv.setImageResource(a.getResources().getIdentifier(glifo, "drawable", a.getPackageName()));
                iv.setBackground(fundoIcone(a.corAtual, a.dp(28)));
                a.icones.add(iv);
            }
            else if (r.activityInfo.packageName.equals("com.mediatek.filemanager"))
                iv.setImageResource(a.getResources().getIdentifier("ic_pasta", "drawable", a.getPackageName()));
            else if (r.activityInfo.name.endsWith(".Radio"))
                iv.setImageResource(a.getResources().getIdentifier("ic_radio", "drawable", a.getPackageName()));
            else iv.setImageDrawable(r.loadIcon(pm));
            box.addView(iv, new LinearLayout.LayoutParams(a.dp(120), a.dp(120)));
            TextView t = new TextView(a);
            t.setText(r == null ? "Ajustes"
                    : r.activityInfo.packageName.equals("com.mediatek.filemanager") ? "Arquivos"
                    : r.activityInfo.packageName.equals("org.videolan.vlc") ? "Mídia" : r.loadLabel(pm)); t.setTextColor(Color.WHITE); t.setTextSize(18);
            t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); t.setGravity(Gravity.CENTER);
            t.setPadding(a.dp(4), a.dp(8), a.dp(4), 0);
            box.addView(t, new LinearLayout.LayoutParams(-1, -2));
            return box;
        }

        // qual símbolo próprio usar para cada app (null = ícone original do app)
        static String glifo(ResolveInfo r) {
            if (r == null) return "g_gear";
            String p = r.activityInfo.packageName, n = r.activityInfo.name;
            if (n.endsWith(".Radio")) return "g_radio";
            if (n.endsWith(".BtMusica")) return "g_bt";
            if (n.endsWith(".Midia")) return "g_midia";
            if (p.equals("com.autochips.bluetooth")) return "g_bt";
            if (p.equals("com.shihab.diplay")) return "g_play";
            if (p.equals("com.spotify.music")) return "g_spotify";
            if (p.equals("org.videolan.vlc")) return "g_midia";
            return null;
        }
    }

    // degradê do ícone a partir da cor das luzes (cor quase branca/cinza vira grafite, para o símbolo branco aparecer)
    static GradientDrawable fundoIcone(int cor, int raio) {
        float[] hsv = new float[3];
        Color.colorToHSV(cor, hsv);
        int c1, c2;
        if (hsv[1] < 0.2f) { c1 = Color.rgb(70, 74, 86); c2 = Color.rgb(28, 30, 38); }
        else {
            c1 = Color.HSVToColor(new float[]{hsv[0], Math.min(1, hsv[1] * 0.95f), 0.95f});
            c2 = Color.HSVToColor(new float[]{(hsv[0] + 35) % 360, Math.min(1, hsv[1] + 0.1f), 0.62f});
        }
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(raio);
        return g;
    }

    final List<ImageView> icones = new ArrayList<>();

    void pintarIcones(int cor) {
        for (ImageView iv : icones) iv.setBackground(fundoIcone(cor, dp(28)));
    }

    // botão redondo desenhado à mão (anterior / tocar / pausar / próxima)
    static class Icon extends View {
        static final int PREV = 0, PLAY = 1, PAUSE = 2, NEXT = 3;
        int kind; final boolean main;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c, int kind, boolean main) { super(c); this.kind = kind; this.main = main; setClickable(true); }
        void setKind(int k) { if (k != kind) { kind = k; invalidate(); } }
        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(main ? Color.WHITE : Color.argb(isPressed() ? 90 : 50, 255, 255, 255));
            c.drawCircle(cx, cy, r, p);
            p.setColor(main ? Color.rgb(15, 20, 30) : Color.WHITE);
            float s = r * 0.42f;
            Path path = new Path();
            if (kind == PLAY) {
                path.moveTo(cx - s * 0.6f, cy - s); path.lineTo(cx + s, cy); path.lineTo(cx - s * 0.6f, cy + s); path.close();
                c.drawPath(path, p);
            } else if (kind == PAUSE) {
                c.drawRect(cx - s * 0.75f, cy - s, cx - s * 0.2f, cy + s, p);
                c.drawRect(cx + s * 0.2f, cy - s, cx + s * 0.75f, cy + s, p);
            } else {
                int d = kind == NEXT ? 1 : -1;
                for (int k = 0; k < 2; k++) {
                    float x0 = cx + d * (-s * 0.9f + k * s * 0.9f);
                    path.reset();
                    path.moveTo(x0, cy - s * 0.8f); path.lineTo(x0 + d * s * 0.9f, cy); path.lineTo(x0, cy + s * 0.8f); path.close();
                    c.drawPath(path, p);
                }
            }
        }
        @Override
        protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }
    }
}
