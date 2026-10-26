package com.fantinel.logocopy;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

// Tela "Mídia": biblioteca do VLC no estilo da central (o VLC toca por trás).
public class Midia extends Activity {
    static final String VLC = "org.videolan.vlc";
    static final String R_BIB = "//org.videolan.vlc/r/l", R_INICIO = "//org.videolan.vlc/r/home",
            R_LISTAS = "//org.videolan.vlc/r/playlist", VIDEOS = "videos";
    static final int ACCENT = Color.rgb(64, 170, 255), MUTED = Color.rgb(190, 198, 210);

    final Handler h = new Handler();
    VlcBiblioteca vb;
    MediaController ctl;
    ImageView bg, capa;
    TextView titulo, artista, tempo, total, cabecalho, voltar, carregando;
    ProgressBar barra;
    Home.Icon prev, play, next;
    ListView lista;
    Lista adapter;
    TextView[] abas = new TextView[4];
    final List<String> pilha = new ArrayList<>();   // caminho de navegação (ids)
    final List<String> nomes = new ArrayList<>();
    Object capaKey;
    static final LruCache<String, Bitmap> cache = new LruCache<>(80);

    // item da lista: vem do VLC (MediaItem) ou é um vídeo local
    static class Item {
        String id, titulo, sub; boolean pasta; Uri icone; Bitmap bmp; File arquivo;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build()); // permite abrir vídeo por file://

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 15, 22));
        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, -1, -1);
        View shade = new View(this);
        shade.setBackgroundColor(Color.argb(165, 0, 0, 0));
        root.addView(shade, -1, -1);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(24), dp(18), dp(24), dp(18));
        root.addView(row, -1, -1);

        // ---- esquerda: navegação ----
        LinearLayout esq = new LinearLayout(this);
        esq.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tabs = new LinearLayout(this);
        String[] nomesAbas = {"Biblioteca", "Início", "Listas", "Vídeos"};
        String[] ids = {R_BIB, R_INICIO, R_LISTAS, VIDEOS};
        for (int i = 0; i < 4; i++) {
            final String id = ids[i], nome = nomesAbas[i];
            TextView t = pill(nome);
            t.setOnClickListener(v -> abrirRaiz(id, nome));
            abas[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(10), 0);
            tabs.addView(t, lp);
        }
        esq.addView(tabs);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0, dp(14), 0, dp(8));
        voltar = text(20, ACCENT, true);
        voltar.setText("‹ Voltar");
        voltar.setPadding(0, 0, dp(18), 0);
        voltar.setOnClickListener(v -> voltar());
        cabecalho = text(22, Color.WHITE, true);
        nav.addView(voltar);
        nav.addView(cabecalho, new LinearLayout.LayoutParams(0, -2, 1));
        esq.addView(nav);

        FrameLayout listBox = new FrameLayout(this);
        lista = new ListView(this);
        lista.setDivider(null);
        lista.setSelector(new GradientDrawable());
        adapter = new Lista(this);
        lista.setAdapter(adapter);
        lista.setOnItemClickListener((p, v, pos, id) -> tocar((Item) adapter.getItem(pos)));
        listBox.addView(lista, -1, -1);
        carregando = text(20, MUTED, false);
        carregando.setGravity(Gravity.CENTER);
        listBox.addView(carregando, -1, -1);
        esq.addView(listBox, new LinearLayout.LayoutParams(-1, 0, 1));
        row.addView(esq, new LinearLayout.LayoutParams(0, -1, 1.25f));

        // ---- direita: tocando agora ----
        LinearLayout dir = new LinearLayout(this);
        dir.setOrientation(LinearLayout.VERTICAL);
        dir.setGravity(Gravity.CENTER_HORIZONTAL);
        dir.setPadding(dp(28), dp(6), 0, 0);
        capa = new ImageView(this);
        capa.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ag = new GradientDrawable();
        ag.setColor(Color.argb(60, 255, 255, 255)); ag.setCornerRadius(dp(18));
        capa.setBackground(ag);
        capa.setClipToOutline(true);
        dir.addView(capa, new LinearLayout.LayoutParams(dp(330), dp(330)));
        titulo = text(28, Color.WHITE, true);
        titulo.setGravity(Gravity.CENTER);
        titulo.setEllipsize(TextUtils.TruncateAt.MARQUEE); titulo.setMarqueeRepeatLimit(-1); titulo.setSelected(true);
        titulo.setPadding(0, dp(16), 0, 0);
        artista = text(19, MUTED, false);
        artista.setGravity(Gravity.CENTER);
        dir.addView(titulo, new LinearLayout.LayoutParams(-1, -2));
        dir.addView(artista, new LinearLayout.LayoutParams(-1, -2));
        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(1000);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(8));
        bl.setMargins(0, dp(16), 0, dp(4));
        dir.addView(barra, bl);
        LinearLayout tempos = new LinearLayout(this);
        tempo = text(15, MUTED, false); total = text(15, MUTED, false);
        total.setGravity(Gravity.END);
        tempos.addView(tempo, new LinearLayout.LayoutParams(0, -2, 1));
        tempos.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        dir.addView(tempos, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout ctrls = new LinearLayout(this);
        ctrls.setGravity(Gravity.CENTER);
        ctrls.setPadding(0, dp(10), 0, 0);
        prev = new Home.Icon(this, Home.Icon.PREV, false);
        play = new Home.Icon(this, Home.Icon.PLAY, true);
        next = new Home.Icon(this, Home.Icon.NEXT, false);
        prev.setOnClickListener(v -> controle(-1));
        play.setOnClickListener(v -> controle(0));
        next.setOnClickListener(v -> controle(1));
        ctrls.addView(prev, new LinearLayout.LayoutParams(dp(80), dp(80)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(100), dp(100));
        pl.setMargins(dp(28), 0, dp(28), 0);
        ctrls.addView(play, pl);
        ctrls.addView(next, new LinearLayout.LayoutParams(dp(80), dp(80)));
        dir.addView(ctrls, new LinearLayout.LayoutParams(-1, -2));
        row.addView(dir, new LinearLayout.LayoutParams(0, -1, 1));

        setContentView(root);

        carregando.setText("Abrindo a biblioteca…");
        vb = new VlcBiblioteca(getApplicationContext());
        vb.conectar(ok -> {
            if (!ok) { carregando.setText("Não foi possível abrir a biblioteca do VLC"); return; }
            ctl = vb.controle();
            abrirRaiz(R_BIB, "Biblioteca");
        });
    }

    @Override
    protected void onResume() { super.onResume(); h.removeCallbacks(tick); h.post(tick); }
    @Override
    protected void onPause() { super.onPause(); h.removeCallbacks(tick); }
    @Override
    protected void onDestroy() { super.onDestroy(); vb.desconectar(); }
    @Override
    public void onBackPressed() { if (pilha.size() > 1) voltar(); else Home.irParaInicio(this); }

    // ---------- navegação ----------
    void abrirRaiz(String id, String nome) {
        pilha.clear(); nomes.clear();
        for (int i = 0; i < 4; i++) marcar(abas[i], abas[i].getText().toString().equals(nome));
        entrar(id, nome);
    }

    void entrar(String id, String nome) {
        pilha.add(id); nomes.add(nome);
        carregar();
    }

    void voltar() {
        if (pilha.size() <= 1) return;
        pilha.remove(pilha.size() - 1); nomes.remove(nomes.size() - 1);
        carregar();
    }

    void carregar() {
        String id = pilha.get(pilha.size() - 1);
        cabecalho.setText(nomes.get(nomes.size() - 1));
        voltar.setVisibility(pilha.size() > 1 ? View.VISIBLE : View.GONE);
        adapter.itens = new ArrayList<>();
        adapter.notifyDataSetChanged();
        carregando.setText("Carregando…");
        carregando.setVisibility(View.VISIBLE);
        if (VIDEOS.equals(id)) { carregarVideos(); return; }
        if (ctl == null) return;
        vb.listar(id, (pai, itens) -> {
            if (!pai.equals(pilha.get(pilha.size() - 1))) return;
            List<Item> l = new ArrayList<>();
            for (MediaBrowser.MediaItem m : itens) {
                if (m.getMediaId() != null && m.getMediaId().contains("/r/error/")) continue;
                MediaDescription d = m.getDescription();
                Item it = new Item();
                it.id = m.getMediaId();
                it.titulo = String.valueOf(d.getTitle());
                it.sub = d.getSubtitle() == null ? "" : String.valueOf(d.getSubtitle());
                it.pasta = m.isBrowsable();
                it.bmp = d.getIconBitmap();
                it.icone = d.getIconUri();
                l.add(it);
            }
            mostrar(l, "Nada aqui ainda");
        });
    }

    void mostrar(List<Item> l, String vazio) {
        adapter.itens = l;
        adapter.notifyDataSetChanged();
        lista.setSelection(0);
        carregando.setText(vazio);
        carregando.setVisibility(l.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // vídeos do pendrive (busca em segundo plano)
    void carregarVideos() {
        new Thread(() -> {
            List<Item> l = new ArrayList<>();
            File[] vols = new File("/storage").listFiles();
            if (vols != null) for (File v : vols) {
                if (v.getName().equals("emulated") || v.getName().equals("self")) continue;
                buscar(v, 0, l);
            }
            File interno = new File("/storage/emulated/0/Movies");
            buscar(interno, 0, l);
            java.util.Collections.sort(l, (a, b) -> a.titulo.compareToIgnoreCase(b.titulo));
            h.post(() -> { if (VIDEOS.equals(pilha.get(pilha.size() - 1))) mostrar(l, "Nenhum vídeo no pendrive"); });
        }).start();
    }

    static final String[] EXT = {".mp4", ".mkv", ".avi", ".mov", ".3gp", ".ts", ".wmv", ".flv", ".m4v", ".webm", ".mpg", ".mpeg"};

    void buscar(File d, int nivel, List<Item> l) {
        if (nivel > 7 || l.size() > 400) return;
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.getName().startsWith(".") || f.getName().startsWith("$")) continue;
            if (f.isDirectory()) { buscar(f, nivel + 1, l); continue; }
            String n = f.getName().toLowerCase();
            for (String e : EXT) if (n.endsWith(e)) {
                Item it = new Item();
                it.arquivo = f;
                it.titulo = f.getName().substring(0, f.getName().lastIndexOf('.'));
                it.sub = f.getParentFile().getName() + "  •  " + (f.length() / (1024 * 1024)) + " MB";
                l.add(it);
                break;
            }
        }
    }

    void tocar(Item it) {
        if (it == null) return;
        if (it.arquivo != null) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW).setPackage(VLC)
                        .setDataAndType(Uri.fromFile(it.arquivo), "video/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t) {}
            return;
        }
        if (it.pasta) { entrar(it.id, it.titulo); return; }
        if (ctl != null) ctl.getTransportControls().playFromMediaId(it.id, null);
    }

    // ---------- tocando agora ----------
    Runnable tick = () -> { atualizar(); h.postDelayed(this.tick, 500); };

    void atualizar() {
        if (ctl == null) return;
        MediaMetadata md = ctl.getMetadata();
        PlaybackState st = ctl.getPlaybackState();
        String t = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(t)) {
            titulo.setText("Escolha uma música");
            artista.setText("na lista ao lado");
            mostrarCapa(null);
            play.setKind(Home.Icon.PLAY);
            barra.setProgress(0); tempo.setText(""); total.setText("");
            return;
        }
        if (!t.equals(titulo.getText().toString())) titulo.setText(t);
        String ar = md.getString(MediaMetadata.METADATA_KEY_ARTIST), al = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
        artista.setText(TextUtils.isEmpty(ar) ? (al == null ? "" : al) : (TextUtils.isEmpty(al) ? ar : ar + "  •  " + al));
        boolean tocando = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        play.setKind(tocando ? Home.Icon.PAUSE : Home.Icon.PLAY);
        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION), pos = st == null ? 0 : st.getPosition();
        if (tocando && st.getLastPositionUpdateTime() > 0)
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        if (dur > 0) {
            pos = Math.max(0, Math.min(dur, pos));
            barra.setProgress((int) (pos * 1000 / dur));
            tempo.setText(BtMusica.mmss(pos)); total.setText(BtMusica.mmss(dur));
        }
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (Home.semCapaReal(art)) art = null;
        if (art == null) art = BtMusica.gerada(TextUtils.isEmpty(al) ? t : al, ar == null ? "" : ar, 460);
        mostrarCapa(art);
    }

    void mostrarCapa(Bitmap art) {
        Object key = art == null ? "vazio" : (Object) art;
        if (art != null && capaKey instanceof Bitmap && ((Bitmap) capaKey).sameAs(art)) return;
        if (key.equals(capaKey)) return;
        capaKey = key;
        Bitmap c = art != null ? art : BtMusica.gerada("", "", 400);
        capa.setImageBitmap(c);
        bg.setImageBitmap(Home.blur(this, c));
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
    }

    // ---------- lista ----------
    static class Lista extends BaseAdapter {
        final Midia a; List<Item> itens = new ArrayList<>();
        Lista(Midia a) { this.a = a; }
        public int getCount() { return itens.size(); }
        public Object getItem(int i) { return itens.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup parent) {
            Item it = itens.get(i);
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(a.dp(12)); g.setColor(Color.argb(45, 255, 255, 255));
            row.setBackground(g);
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable ig = new GradientDrawable();
            ig.setCornerRadius(a.dp(8)); ig.setColor(Color.argb(70, 255, 255, 255));
            iv.setBackground(ig);
            iv.setClipToOutline(true);
            a.icone(it, iv);
            row.addView(iv, new LinearLayout.LayoutParams(a.dp(64), a.dp(64)));
            LinearLayout tx = new LinearLayout(a);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.setPadding(a.dp(16), 0, a.dp(8), 0);
            TextView t1 = a.text(20, Color.WHITE, true); t1.setText(it.titulo);
            TextView t2 = a.text(15, MUTED, false); t2.setText(it.sub);
            tx.addView(t1); if (!it.sub.isEmpty()) tx.addView(t2);
            row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
            if (it.pasta) { TextView seta = a.text(28, MUTED, false); seta.setText("›"); row.addView(seta); }
            FrameLayout wrap = new FrameLayout(a);
            wrap.setPadding(0, a.dp(4), 0, a.dp(4));
            wrap.addView(row);
            return wrap;
        }
    }

    // capa/ícone do item: bitmap do VLC, imagem pelo endereço do VLC (em segundo plano) ou capa gerada
    void icone(Item it, ImageView iv) {
        if (it.bmp != null) { iv.setImageBitmap(it.bmp); return; }
        if (it.arquivo != null) { iv.setImageResource(getResources().getIdentifier("g_play", "drawable", getPackageName())); iv.setPadding(dp(10), dp(10), dp(10), dp(10)); iv.setBackground(Home.fundoIcone(Color.rgb(64, 120, 255), dp(8))); return; }
        String k = it.icone == null ? null : it.icone.toString();
        Bitmap c = k == null ? null : cache.get(k);
        if (c != null) { iv.setImageBitmap(c); return; }
        iv.setImageBitmap(BtMusica.gerada(it.pasta ? "" : it.titulo, "", 120));
        if (k == null) return;
        iv.setTag(k);
        new Thread(() -> {
            Bitmap bm = null;
            try (InputStream in = getContentResolver().openInputStream(it.icone)) {
                BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 2;
                bm = BitmapFactory.decodeStream(in, null, o);
            } catch (Throwable t) {}
            if (bm == null) return;
            cache.put(k, bm);
            final Bitmap f = bm;
            h.post(() -> { if (k.equals(iv.getTag())) iv.setImageBitmap(f); });
        }).start();
    }

    // ---------- utilidades ----------
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
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        marcar(t, false);
        return t;
    }

    void marcar(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(on ? ACCENT : Color.argb(70, 255, 255, 255));
        t.setBackground(g);
        t.setTextColor(on ? Color.BLACK : Color.WHITE);
    }
}
