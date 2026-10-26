package com.fantinel.logocopy;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.lang.reflect.Method;

// Vigia: mantém o serviço z-link desligado (service.zlink.enable=0).
// Liga ao iniciar a central. Para desligar o vigia: --es mode zlink_on
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
        // reaplica o som depois que o app original aplicar o dele
        if (intent == null || intent.getBooleanExtra("som", false)) {
            new Thread(() -> {
                try {
                    Thread.sleep(8000); Som.apply(this); telaSalva();
                    Thread.sleep(20000); Som.apply(this); telaSalva();
                } catch (InterruptedException e) {}
            }).start();
        }
        if (!running && enabled(this)) {
            running = true;
            new Thread(() -> {
                Log.i(TAG, "vigia do z-link ligado");
                while (running && enabled(this)) {
                    try {
                        if (!"0".equals(get(KEY))) {
                            set(KEY, "0");
                            Log.i(TAG, "z-link desligado pelo vigia");
                        }
                        Bateria.verificar(this);
                        Thread.sleep(15000);
                    } catch (Throwable t) {
                        Log.e(TAG, "vigia", t);
                        try { Thread.sleep(15000); } catch (InterruptedException e) { break; }
                    }
                }
                running = false;
                Log.i(TAG, "vigia do z-link parado");
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
