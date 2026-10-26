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
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.List;

// BT Música no estilo da tela inicial: capa (ou capa gerada), fundo desfocado, progresso e controles.
public class BtMusica extends Activity {
    static final String BT_APP = "com.autochips.bluetooth";
    static final int ACCENT = Color.rgb(64, 170, 255), MUTED = Color.rgb(190, 198, 210);

    final Handler h = new Handler();
    ImageView bg, capa;
    TextView titulo, artista, album, tempo, total, aviso;
    ProgressBar barra;
    Home.Icon prev, play, next;
    MediaController ctl;
    Object capaKey;

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
        shade.setBackgroundColor(Color.argb(150, 0, 0, 0));
        root.addView(shade, -1, -1);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(40), dp(24), dp(40), dp(24));
        root.addView(col, -1, -1);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView tt = text(30, Color.WHITE, true);
        tt.setText("BT Música");
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        TextView cfg = text(18, Color.WHITE, true);
        cfg.setText("Conexões Bluetooth");
        cfg.setPadding(dp(22), dp(10), dp(22), dp(10));
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(22)); pg.setColor(Color.argb(70, 255, 255, 255));
        cfg.setBackground(pg);
        cfg.setOnClickListener(v -> abrir(new Intent().setClassName(BT_APP, BT_APP + ".MainBluetoothActivity")));
        top.addView(cfg);
        col.addView(top);

        LinearLayout mid = new LinearLayout(this);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        mid.setPadding(0, 0, 0, dp(20));
        capa = new ImageView(this);
        capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ag = new GradientDrawable();
        ag.setColor(Color.argb(60, 255, 255, 255)); ag.setCornerRadius(dp(18));
        capa.setBackground(ag);
        capa.setClipToOutline(true);
        mid.addView(capa, new LinearLayout.LayoutParams(dp(470), dp(470)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(40), 0, 0, 0);
        aviso = text(18, ACCENT, true);
        titulo = text(44, Color.WHITE, true);
        titulo.setEllipsize(TextUtils.TruncateAt.MARQUEE); titulo.setMarqueeRepeatLimit(-1); titulo.setSelected(true);
        artista = text(26, MUTED, false);
        album = text(20, MUTED, false);
        info.addView(aviso); info.addView(titulo); info.addView(artista); info.addView(album);

        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(1000);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(8));
        bl.setMargins(0, dp(28), 0, dp(6));
        info.addView(barra, bl);
        LinearLayout tempos = new LinearLayout(this);
        tempo = text(16, MUTED, false);
        total = text(16, MUTED, false);
        total.setGravity(Gravity.END);
        tempos.addView(tempo, new LinearLayout.LayoutParams(0, -2, 1));
        tempos.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        info.addView(tempos);

        LinearLayout ctrls = new LinearLayout(this);
        ctrls.setGravity(Gravity.CENTER_VERTICAL);
        ctrls.setPadding(0, dp(22), 0, 0);
        prev = new Home.Icon(this, Home.Icon.PREV, false);
        play = new Home.Icon(this, Home.Icon.PLAY, true);
        next = new Home.Icon(this, Home.Icon.NEXT, false);
        prev.setOnClickListener(v -> controle(-1));
        play.setOnClickListener(v -> controle(0));
        next.setOnClickListener(v -> controle(1));
        ctrls.addView(prev, new LinearLayout.LayoutParams(dp(96), dp(96)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(120), dp(120));
        pl.setMargins(dp(34), 0, dp(34), 0);
        ctrls.addView(play, pl);
        ctrls.addView(next, new LinearLayout.LayoutParams(dp(96), dp(96)));
        info.addView(ctrls);

        mid.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));
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

    // o som do Bluetooth sai pelo app de fábrica: se ele não for a fonte atual, abre por um instante e volta
    static long abrindo;

    void garantirSom() {
        String fonte = getSharedPreferences("home", 0).getString("fonte", "");
        if (fonte.contains(BT_APP) || System.currentTimeMillis() - abrindo < 5000) return;
        abrindo = System.currentTimeMillis();
        // pede a fonte Bluetooth ao sistema (sem abrir a tela de fábrica) e dá play pelo serviço A2DP
        new Thread(() -> Fonte.pedir(getApplicationContext(), BT_APP)).start();
        h.postDelayed(() -> { if (a2dp != null) a2dp.getTransportControls().play(); }, 900);
    }

    Runnable tick = () -> { atualizar(); h.postDelayed(this.tick, 500); };

    // conexão com o serviço A2DP do sistema (é por ela que play/pause chegam ao celular)
    android.media.browse.MediaBrowser browser;
    MediaController a2dp;

    static class Conexao extends android.media.browse.MediaBrowser.ConnectionCallback {
        final BtMusica a;
        Conexao(BtMusica a) { this.a = a; }
        @Override public void onConnected() {
            a.a2dp = new MediaController(a, a.browser.getSessionToken());
            android.util.Log.i("LogoCopy", "a2dp conectado");
        }
    }

    void conectarA2dp() {
        if (browser != null) return;
        browser = new android.media.browse.MediaBrowser(this,
                new android.content.ComponentName("com.android.bluetooth", "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService"),
                new Conexao(this), null);
        browser.connect();
    }

    @Override
    protected void onDestroy() { super.onDestroy(); try { browser.disconnect(); } catch (Throwable t) {} }

    MediaController achar() {
        conectarA2dp();
        if (a2dp != null) return a2dp;
        try {
            MediaSessionManager m = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = m.getActiveSessions(null);
            for (MediaController c : l) if (c.getPackageName().equals("com.android.bluetooth")) return c;
        } catch (Throwable t) {}
        return null;
    }

    void atualizar() {
        ctl = achar();
        MediaMetadata md = ctl == null ? null : ctl.getMetadata();
        PlaybackState st = ctl == null ? null : ctl.getPlaybackState();
        boolean conectado = st != null && st.getState() != PlaybackState.STATE_ERROR && st.getState() != PlaybackState.STATE_NONE;
        String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (!conectado || TextUtils.isEmpty(t)) {
            aviso.setText("Bluetooth");
            titulo.setText(conectado ? "Nada tocando" : "Celular não conectado");
            artista.setText(conectado ? "Dê play no celular ou aqui" : "Toque em \"Conexões Bluetooth\" para parear");
            album.setText("");
            barra.setProgress(0); tempo.setText(""); total.setText("");
            mostrarCapa(null, "", "", conectado);
            play.setKind(Home.Icon.PLAY);
            return;
        }
        String ar = md.getString(MediaMetadata.METADATA_KEY_ARTIST), al = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
        aviso.setText("Bluetooth  •  " + nomeCelular());
        if (!t.equals(titulo.getText().toString())) titulo.setText(t);
        artista.setText(ar == null ? "" : ar);
        album.setText(al == null ? "" : al);

        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = st.getPosition();
        boolean tocando = st.getState() == PlaybackState.STATE_PLAYING;
        if (tocando && st.getLastPositionUpdateTime() > 0)
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        if (dur > 0) {
            pos = Math.max(0, Math.min(dur, pos));
            barra.setProgress((int) (pos * 1000 / dur));
            tempo.setText(mmss(pos)); total.setText(mmss(dur));
        } else { barra.setProgress(0); tempo.setText(""); total.setText(""); }
        play.setKind(tocando ? Home.Icon.PAUSE : Home.Icon.PLAY);

        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = Home.capaDeOutro(this, t);   // capa real vinda do Spotify espelhado, se houver
        mostrarCapa(art, al == null || al.isEmpty() ? t : al, ar == null ? "" : ar, true);
    }

    void mostrarCapa(Bitmap art, String nome, String sub, boolean ok) {
        Object key = art != null ? art : nome + "|" + ok;
        if (key.equals(capaKey)) return;
        capaKey = key;
        Bitmap c = art != null ? art : gerada(nome, sub, 500);
        capa.setImageBitmap(c);
        bg.setImageBitmap(Home.blur(this, c));
    }

    // capa gerada para quando o celular não manda a imagem: cor própria por álbum + nome
    static Bitmap gerada(String nome, String sub, int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float hue = nome.isEmpty() ? 205 : (Math.abs(nome.hashCode()) % 360);
        int cor = Color.HSVToColor(new float[]{hue, 0.75f, 0.95f});
        int esc = Color.HSVToColor(new float[]{(hue + 40) % 360, 0.85f, 0.25f});
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, s, s, cor, esc, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, s, s), s * 0.05f, s * 0.05f, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        // nota musical
        p.setAlpha(230);
        float cx = s * 0.5f, cy = s * 0.4f, r = s * 0.07f;
        c.drawCircle(cx - s * 0.09f, cy + s * 0.12f, r, p);
        c.drawCircle(cx + s * 0.11f, cy + s * 0.08f, r, p);
        p.setStrokeWidth(s * 0.025f);
        c.drawLine(cx - s * 0.09f + r * 0.9f, cy + s * 0.12f, cx - s * 0.09f + r * 0.9f, cy - s * 0.14f, p);
        c.drawLine(cx + s * 0.11f + r * 0.9f, cy + s * 0.08f, cx + s * 0.11f + r * 0.9f, cy - s * 0.18f, p);
        c.drawLine(cx - s * 0.09f + r * 0.9f, cy - s * 0.14f, cx + s * 0.11f + r * 0.9f, cy - s * 0.18f, p);
        if (!nome.isEmpty()) {
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(s * 0.075f);
            p.setAlpha(255);
            c.drawText(nome.length() > 22 ? nome.substring(0, 21) + "…" : nome, cx, s * 0.78f, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(s * 0.055f);
            p.setAlpha(200);
            c.drawText(sub.length() > 28 ? sub.substring(0, 27) + "…" : sub, cx, s * 0.87f, p);
        }
        return b;
    }

    String nomeCelular() {
        try {
            android.bluetooth.BluetoothAdapter a = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            for (android.bluetooth.BluetoothDevice d : a.getBondedDevices())
                if (d.getBluetoothClass() != null && d.getBluetoothClass().getMajorDeviceClass() == android.bluetooth.BluetoothClass.Device.Major.PHONE)
                    return d.getName();
        } catch (Throwable t) {}
        return "celular";
    }

    void controle(int o) {
        if (ctl == null) return;
        MediaController.TransportControls tc = ctl.getTransportControls();
        if (o == -1) tc.skipToPrevious();
        else if (o == 1) tc.skipToNext();
        else {
            PlaybackState st = ctl.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) tc.pause(); else tc.play();
        }
        h.postDelayed(this::atualizar, 300);
    }

    void abrir(Intent i) { try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable t) {} }

    static String mmss(long ms) { long s = ms / 1000; return String.format("%d:%02d", s / 60, s % 60); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView text(int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(size); t.setTextColor(color); t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
