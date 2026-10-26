package com.fantinel.logocopy;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Player de vídeo próprio (MediaPlayer do Android, decodificação por hardware).
// Um só para o app todo: a imagem vai para o cartão da Início ou para a tela cheia (VideoTela).
// Arquivo que o Android não abre vai para o VLC, como antes.
public class Video {
    static final String TAG = "LogoCopy";
    static Context app;
    static MediaPlayer mp;
    static MediaSession sessao;
    static List<File> lista = new ArrayList<>();
    static int idx;
    static boolean pronto, tocou;
    static int largura, altura;
    static Surface tela, cartao;          // tela cheia tem preferência sobre o cartão
    static final Handler h = new Handler(Looper.getMainLooper());
    static final List<Runnable> ouvintes = new ArrayList<>();

    static boolean ativo() { return mp != null && pronto; }
    static boolean tocando() { try { return ativo() && mp.isPlaying(); } catch (Throwable t) { return false; } }
    static File atual() { return idx >= 0 && idx < lista.size() ? lista.get(idx) : null; }

    static String titulo() {
        File f = atual();
        if (f == null) return "";
        String n = f.getName();
        return n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
    }
    static String pasta() { File f = atual(); return f == null ? "" : f.getParentFile().getName(); }
    static int posicao() { try { return ativo() ? mp.getCurrentPosition() : 0; } catch (Throwable t) { return 0; } }
    static int duracao() { try { return ativo() ? mp.getDuration() : 0; } catch (Throwable t) { return 0; } }

    static void ouvir(Runnable r) { if (!ouvintes.contains(r)) ouvintes.add(r); }
    static void largar(Runnable r) { ouvintes.remove(r); }
    static void avisar() { for (Runnable r : new ArrayList<>(ouvintes)) r.run(); }

    // ---------- imagem ----------
    static void telaCheia(Surface s) { tela = s; aplicarSuperficie(); }
    static void noCartao(Surface s) { cartao = s; aplicarSuperficie(); }
    static void aplicarSuperficie() {
        Log.i(TAG, "video superficie tela=" + (tela != null) + " cartao=" + (cartao != null) + " mp=" + (mp != null));
        if (mp == null) return;
        try { mp.setSurface(tela != null ? tela : cartao); }   // null = só o som
        catch (Throwable t) { Log.e(TAG, "video superficie", t); }
    }

    // ---------- tocar ----------
    static void tocar(Context c, List<File> l, int i) {
        app = c.getApplicationContext();
        lista = new ArrayList<>(l);
        pausarOutros();
        if (!pegarFoco()) Log.w(TAG, "video: foco de áudio recusado");
        abrir(i);
    }

    static void abrir(int i) {
        if (lista.isEmpty()) return;
        idx = (i % lista.size() + lista.size()) % lista.size();
        File f = lista.get(idx);
        pronto = false; tocou = false; largura = 0; altura = 0;
        try {
            if (mp == null) mp = new MediaPlayer(); else mp.reset();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(f.getAbsolutePath());
            mp.setOnPreparedListener(p -> {
                pronto = true;
                aplicarSuperficie();
                p.start(); tocou = true;
                atualizarSessao();
                avisar();
            });
            mp.setOnVideoSizeChangedListener((p, w, hh) -> { largura = w; altura = hh; avisar(); });
            mp.setOnCompletionListener(p -> { if (tocou) pular(1); });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.e(TAG, "video erro " + what + "/" + extra + " em " + f);
                if (!tocou) noVlc(f);   // formato que o Android não abre
                else pular(1);
                return true;
            });
            aplicarSuperficie();
            mp.prepareAsync();
        } catch (Throwable t) {
            Log.e(TAG, "video abrir " + f, t);
            noVlc(f);
        }
        avisar();
    }

    static void pular(int dir) {
        if (lista.isEmpty()) return;
        for (int n = 1; n <= lista.size(); n++) {   // pula arquivos que sumiram (pendrive trocado)
            int j = ((idx + dir * n) % lista.size() + lista.size()) % lista.size();
            if (lista.get(j).exists()) { abrir(j); return; }
        }
        parar();
    }

    static void alternar() {
        if (!ativo()) return;
        if (mp.isPlaying()) mp.pause();
        else { pegarFoco(); mp.start(); }
        atualizarSessao();
        avisar();
    }

    static void pausar() {
        if (tocando()) { mp.pause(); atualizarSessao(); avisar(); }
    }

    static void irPara(int ms) {
        if (ativo()) { mp.seekTo(ms); atualizarSessao(); }
    }

    static void parar() {
        try { if (mp != null) mp.release(); } catch (Throwable t) {}
        mp = null; pronto = false; tocou = false;
        if (sessao != null) { sessao.setActive(false); sessao.release(); sessao = null; }
        if (app != null) ((AudioManager) app.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(FOCO);
        avisar();
    }

    // o Android não abriu o arquivo: manda para o VLC (que toca quase tudo)
    static void noVlc(File f) {
        parar();
        if (app == null) return;
        Midia.guardarVideo(app, f);
        try {
            app.startActivity(new Intent(Intent.ACTION_VIEW).setPackage(Midia.VLC)
                    .setDataAndType(Uri.fromFile(f), "video/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } catch (Throwable t) { Log.e(TAG, "video vlc", t); }
    }

    // ---------- som ----------
    static boolean voltarAoGanhar;
    static final AudioManager.OnAudioFocusChangeListener FOCO = f -> {
        if (f == AudioManager.AUDIOFOCUS_LOSS) parar();          // outra fonte assumiu (rádio, Spotify...)
        else if (f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { voltarAoGanhar = tocando(); pausar(); }
        else if (f == AudioManager.AUDIOFOCUS_GAIN && voltarAoGanhar) { voltarAoGanhar = false; if (ativo() && !mp.isPlaying()) alternar(); }
    };

    static boolean pegarFoco() {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        return am.requestAudioFocus(FOCO, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    // pausa VLC, Spotify etc. antes de começar
    static void pausarOutros() {
        try {
            MediaSessionManager m = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController mc : m.getActiveSessions(null)) {
                if (mc.getPackageName().equals(app.getPackageName())) continue;
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) mc.getTransportControls().pause();
            }
        } catch (Throwable t) {}
    }

    // ---------- sessão de mídia (botões do volante) ----------
    static class Botoes extends MediaSession.Callback {
        @Override public void onPlay() { if (!tocando()) alternar(); }
        @Override public void onPause() { pausar(); }
        @Override public void onSkipToNext() { pular(1); }
        @Override public void onSkipToPrevious() { pular(-1); }
        @Override public void onStop() { parar(); }
        @Override public void onSeekTo(long p) { irPara((int) p); }
    }

    // Área de vídeo que mantém a proporção (centralizada no espaço que recebe).
    // SurfaceView e não TextureView: o decodificador de hardware da MediaTek entrega os quadros num
    // formato próprio que a TextureView não lê (a imagem fica verde).
    static class Quadro extends android.view.SurfaceView implements android.view.SurfaceHolder.Callback {
        final boolean cheia;
        Surface s;
        Quadro(Context c, boolean cheia) { super(c); this.cheia = cheia; getHolder().addCallback(this); }

        @Override
        protected void onMeasure(int wm, int hm) {
            int w = MeasureSpec.getSize(wm), hh = MeasureSpec.getSize(hm);
            if (largura > 0 && altura > 0 && w > 0 && hh > 0) {
                float k = Math.min(w / (float) largura, hh / (float) altura);
                setMeasuredDimension(Math.round(largura * k), Math.round(altura * k));
            } else setMeasuredDimension(w, hh);
        }

        @Override public void surfaceCreated(android.view.SurfaceHolder sh) {
            s = sh.getSurface();
            if (cheia) telaCheia(s); else noCartao(s);
        }
        @Override public void surfaceDestroyed(android.view.SurfaceHolder sh) {
            if (cheia) { if (tela == s) telaCheia(null); } else if (cartao == s) noCartao(null);
            s = null;
        }
        @Override public void surfaceChanged(android.view.SurfaceHolder sh, int f, int w, int hh) {}
    }
    static void atualizarSessao() {
        if (app == null) return;
        if (sessao == null) {
            sessao = new MediaSession(app, "Video");
            sessao.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            sessao.setCallback(new Botoes(), h);
        }
        sessao.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, titulo())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Vídeo")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, pasta())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duracao()).build());
        sessao.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO)
                .setState(tocando() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, posicao(), tocando() ? 1f : 0f)
                .build());
        sessao.setActive(true);
    }
}
