package com.fantinel.logocopy;

import android.content.ComponentName;
import android.content.Context;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

// Acesso à biblioteca do VLC (MediaBrowserService do PlaybackService).
public class VlcBiblioteca {
    static final String TAG = "LogoCopy";

    public interface Itens { void prontos(String pai, List<MediaBrowser.MediaItem> itens); }
    public interface Pronto { void ok(boolean conectado); }

    final Context ctx;
    MediaBrowser browser;
    Pronto aoConectar;

    VlcBiblioteca(Context c) { ctx = c; }

    static class Conexao extends MediaBrowser.ConnectionCallback {
        final VlcBiblioteca b;
        Conexao(VlcBiblioteca b) { this.b = b; }
        @Override public void onConnected() { Log.i(TAG, "vlc conectado root=" + b.browser.getRoot()); if (b.aoConectar != null) b.aoConectar.ok(true); }
        @Override public void onConnectionFailed() { Log.e(TAG, "vlc conexao falhou"); if (b.aoConectar != null) b.aoConectar.ok(false); }
        @Override public void onConnectionSuspended() { Log.e(TAG, "vlc conexao suspensa"); }
    }

    static class Assinatura extends MediaBrowser.SubscriptionCallback {
        final VlcBiblioteca b; final Itens cb;
        Assinatura(VlcBiblioteca b, Itens cb) { this.b = b; this.cb = cb; }
        @Override public void onChildrenLoaded(String pai, List<MediaBrowser.MediaItem> itens) {
            cb.prontos(pai, itens == null ? new ArrayList<>() : itens);
            try { b.browser.unsubscribe(pai); } catch (Throwable t) {}
        }
        @Override public void onError(String pai) { Log.e(TAG, "vlc erro " + pai); cb.prontos(pai, new ArrayList<>()); }
    }

    void conectar(Pronto p) {
        aoConectar = p;
        if (browser != null && browser.isConnected()) { p.ok(true); return; }
        browser = new MediaBrowser(ctx, new ComponentName("org.videolan.vlc", "org.videolan.vlc.PlaybackService"),
                new Conexao(this), null);
        browser.connect();
    }

    String raiz() { return browser.getRoot(); }

    void listar(String id, Itens cb) {
        try { browser.subscribe(id, new Assinatura(this, cb)); } catch (Throwable t) { Log.e(TAG, "listar", t); }
    }

    MediaController controle() {
        return new MediaController(ctx, browser.getSessionToken());
    }

    void desconectar() { try { if (browser != null) browser.disconnect(); } catch (Throwable t) {} }
}
