package com.fantinel.logocopy;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.lang.reflect.Method;

// Vigia: a cada 15 s confere a bateria, trava o rádio em FM e mantém o
// z-link desligado (service.zlink.enable=0). Liga ao iniciar a central.
// --es mode zlink_on libera só o z-link; bateria e FM continuam vigiados.
public class Guard extends Service {
    static final String TAG = "LogoCopy";
    static final String KEY = "service.zlink.enable";
    static volatile boolean running;

    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            c.startService(new Intent(c, Guard.class).putExtra("som", true));
        }
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("guard", 0).getBoolean("on", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("guard", 0).edit().putBoolean("on", on).commit();
    }

    static String get(String k) throws Exception {
        Class<?> sp = Class.forName("android.os.SystemProperties");
        return (String) sp.getMethod("get", String.class).invoke(null, k);
    }

    static void set(String k, String v) throws Exception {
        Class<?> sp = Class.forName("android.os.SystemProperties");
        Method m = sp.getMethod("set", String.class, String.class);
        m.invoke(null, k, v);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        ModoNoite.iniciar(this);
        Fonte.liberarSempre(Fonte.RADIO); // o serviço do rádio de fábrica não pode ser encerrado por ócio
        Re.iniciar(this);                  // volume na ré com descida gradual
        // reaplica o som depois que o app original aplicar o dele
        if (intent == null || intent.getBooleanExtra("som", false)) {
            new Thread(() -> {
                try {
                    Thread.sleep(8000); Som.apply(this); telaSalva();
                    Thread.sleep(20000); Som.apply(this); telaSalva();
                } catch (InterruptedException e) {}
            }).start();
        }
        if (!running) {
            running = true;
            new Thread(() -> {
                Log.i(TAG, "vigia ligado");
                boolean vigiandoZlink = false;
                while (running) {
                    // z-link: só esta parte obedece ao zlink_on / zlink_off
                    try {
                        boolean vigiar = enabled(this);
                        if (vigiar != vigiandoZlink) {
                            Log.i(TAG, vigiar ? "vigia do z-link ligado" : "vigia do z-link parado");
                            vigiandoZlink = vigiar;
                        }
                        // confere de novo antes de escrever, caso o zlink_on chegue no meio
                        if (vigiar && !"0".equals(get(KEY)) && enabled(this)) {
                            set(KEY, "0");
                            Log.i(TAG, "z-link desligado pelo vigia");
                        }
                    } catch (Throwable t) { Log.e(TAG, "vigia z-link", t); }
                    // bateria e FM rodam sempre, mesmo com o z-link liberado;
                    // cada um no seu try para a falha de um não pular o outro
                    try { Bateria.verificar(this); } catch (Throwable t) { Log.e(TAG, "vigia bateria", t); }
                    try { Radio.travarFm(); } catch (Throwable t) { Log.e(TAG, "vigia fm", t); }
                    try { Thread.sleep(15000); } catch (InterruptedException e) { break; }
                }
                running = false;
                Log.i(TAG, "vigia parado");
                stopSelf();
            }).start();
        }
        return START_STICKY;
    }

    // cor da tela salva pelo modo "tela" (saturação/contraste)
    void telaSalva() {
        Cor.aplicarTela(this, true);
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
