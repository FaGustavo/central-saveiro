package com.fantinel.logocopy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.io.*;

// Roda como sistema. Modos:
//   list -> so mostra no log o que existe em /apd, /apd/logo e /apd/media
//   copy -> copia logo.png do pendrive para /apd/logo/saveiro.png
public class Main extends Activity {
    static final String TAG = "LogoCopy";
    static VlcBiblioteca teste;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String mode = getIntent().getStringExtra("mode");
        try {
            if ("prop".equals(mode)) { prop(); finish(); return; }
            if ("testbat".equals(mode)) { Bateria.avisar(getApplicationContext(), 11.3f); moveTaskToBack(true); finish(); return; }
            if ("vlcteste".equals(mode)) {
                // lista a biblioteca do VLC no log (2 níveis)
                VlcBiblioteca vb = new VlcBiblioteca(getApplicationContext());
                teste = vb;
                vb.conectar(ok -> {
                    if (!ok) return;
                    vb.listar(vb.raiz(), (pai, itens) -> {
                        for (android.media.browse.MediaBrowser.MediaItem it : itens) {
                            Log.i(TAG, "VLC " + it.getMediaId() + " | " + it.getDescription().getTitle() + " | browsable=" + it.isBrowsable());
                            if (it.isBrowsable()) vb.listar(it.getMediaId(), (p2, f) -> {
                                Log.i(TAG, "VLC   " + p2 + " -> " + f.size() + " itens; ex: " + (f.isEmpty() ? "-" : f.get(0).getMediaId() + " | " + f.get(0).getDescription().getTitle()));
                            });
                        }
                    });
                });
                finish();
                return;
            }
            if ("fonte".equals(mode)) {
                Log.i(TAG, "fonte -> " + Fonte.pedir(this, getIntent().getStringExtra("pkg")));
                finish(); return;
            }
            if ("banda".equals(mode)) {
                // força uma banda do rádio (teste da trava em FM): --es b AM-1
                Object be = Radio.bandaEnum(getIntent().getStringExtra("b"));
                Log.i(TAG, "banda " + be + " -> " + Radio.call("setUibandIndexFreq", be, 0, Radio.isFm(String.valueOf(be)) ? 87500 : 531));
                finish(); return;
            }
            if ("srcteste".equals(mode)) {
                // testa as variantes do pedido de fonte: --es tipo cn|str --es arg pacote/classe
                String tipo = getIntent().getStringExtra("tipo"), arg = getIntent().getStringExtra("arg");
                try {
                    Class<?> si = Class.forName("android.sourceservice.SourceInfo");
                    Object inst = si.getMethod("getInstance").invoke(null);
                    if ("cn".equals(tipo)) si.getMethod("onRequestPlayAudio", android.content.ComponentName.class).invoke(inst, android.content.ComponentName.unflattenFromString(arg));
                    else si.getMethod("onRequestPlayAudio", String.class).invoke(inst, arg);
                    Log.i(TAG, "srcteste ok " + tipo + " " + arg);
                } catch (Throwable t) { Log.e(TAG, "srcteste", t); }
                finish(); return;
            }
            if ("abrir".equals(mode)) {
                // abre qualquer activity, mesmo não exportada: --es cmp pacote/classe [--es acao X]
                try {
                    Intent i = new Intent().setComponent(android.content.ComponentName.unflattenFromString(getIntent().getStringExtra("cmp")))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (getIntent().hasExtra("acao")) i.setAction(getIntent().getStringExtra("acao"));
                    startActivity(i);
                } catch (Throwable t) { Log.e(TAG, "abrir", t); }
                finish(); return;
            }
            if ("tela".equals(mode)) {
                // cor da tela: --ef s 1.35 --ef c 1.08  (1 e 1 = normal)
                float s = getIntent().getFloatExtra("s", 1), c = getIntent().getFloatExtra("c", 1);
                getSharedPreferences("tela", 0).edit().putFloat("s", s).putFloat("c", c).commit();
                Cor.aplicarTela(this, true);
                finish(); return;
            }
            if ("tz".equals(mode)) {
                // fuso horário: --es tz America/Sao_Paulo
                ((android.app.AlarmManager) getSystemService(ALARM_SERVICE)).setTimeZone(getIntent().getStringExtra("tz"));
                Log.i(TAG, "fuso " + java.util.TimeZone.getDefault().getID());
                finish(); return;
            }
            if ("cp".equals(mode)) {
                // copia um arquivo como sistema: --es src /caminho --es dst /sdcard/x
                File s = new File(getIntent().getStringExtra("src")), d = new File(getIntent().getStringExtra("dst"));
                try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                d.setReadable(true, false);
                Log.i(TAG, "cp " + s + " -> " + d + " " + d.length());
                finish(); return;
            }
            if ("fx".equals(mode)) {
                // chama android.audio.AudioEffect.<fn>(ints...): --es fn setSubWoofer --es args 1,20
                Object fx = Class.forName("android.audio.AudioEffect").getMethod("getInstance").invoke(null);
                String fn = getIntent().getStringExtra("fn");
                String[] p = getIntent().getStringExtra("args").split(",");
                for (java.lang.reflect.Method m : fx.getClass().getMethods()) {
                    if (!m.getName().equals(fn) || m.getParameterTypes().length != p.length) continue;
                    Object[] v = new Object[p.length];
                    for (int i = 0; i < p.length; i++)
                        v[i] = m.getParameterTypes()[i] == float.class ? (Object) Float.parseFloat(p[i]) : (Object) Integer.parseInt(p[i].trim());
                    Log.i(TAG, "fx " + fn + "(" + getIntent().getStringExtra("args") + ") -> " + m.invoke(fx, v));
                }
                finish(); return;
            }
            if ("reflect".equals(mode)) {
                // lista métodos e constantes de uma classe: --es cls nome.da.Classe
                Class<?> c = Class.forName(getIntent().getStringExtra("cls"));
                Log.i(TAG, "R class " + c + " super=" + c.getSuperclass());
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) Log.i(TAG, "R m " + m);
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    String v = "";
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        try { f.setAccessible(true); v = " = " + f.get(null); } catch (Throwable t) {}
                    }
                    Log.i(TAG, "R f " + f + v);
                }
                finish(); return;
            }
            if ("comp".equals(mode)) {
                // esconde/mostra um componente: --es comp pacote/classe --ez on false
                android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(getIntent().getStringExtra("comp"));
                boolean on = getIntent().getBooleanExtra("on", false);
                getPackageManager().setComponentEnabledSetting(cn,
                    on ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                       : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
                Log.i(TAG, "comp " + cn + " on=" + on);
                finish(); return;
            }
            if ("zlink_off".equals(mode)) {
                Guard.setEnabled(this, true);
                startService(new Intent(this, Guard.class));
                finish(); return;
            }
            if ("zlink_on".equals(mode)) {
                Guard.setEnabled(this, false); // o laço do Guard continua; só para de mexer no z-link
                Guard.set(Guard.KEY, "1");
                Log.i(TAG, "z-link religado, vigia do z-link desligado");
                finish(); return;
            }
            if ("copy".equals(mode)) copy();
            for (String d : new String[]{"/apd", "/apd/logo", "/apd/media"}) list(new File(d));
        } catch (Throwable t) {
            Log.e(TAG, "erro", t);
        }
        Log.i(TAG, "fim");
        finish();
    }

    // prop -> muda uma propriedade do sistema (--es key ... --es value ...)
    void prop() throws Exception {
        String k = getIntent().getStringExtra("key"), v = getIntent().getStringExtra("value");
        Class<?> sp = Class.forName("android.os.SystemProperties");
        sp.getMethod("set", String.class, String.class).invoke(null, k, v);
        Log.i(TAG, "prop " + k + " = " + sp.getMethod("get", String.class).invoke(null, k));
    }

    void list(File dir) {
        File[] fs = dir.listFiles();
        Log.i(TAG, "== " + dir + (fs == null ? " (sem acesso ou nao existe)" : ""));
        if (fs != null) for (File f : fs)
            Log.i(TAG, (f.isDirectory() ? "d " : "- ") + f.getName() + " " + f.length());
    }

    void copy() throws IOException {
        File src = null;
        File[] vols = new File("/storage").listFiles();
        if (vols != null) for (File v : vols) {
            File c = new File(v, "logo.png");
            if (c.isFile()) { src = c; break; }
        }
        if (src == null) { Log.e(TAG, "logo.png nao encontrado no pendrive"); return; }

        File dir = new File("/apd/logo");
        if (!dir.isDirectory()) Log.i(TAG, "criando /apd/logo: " + dir.mkdirs());
        File dst = new File(dir, "saveiro.png");
        if (dst.exists()) { Log.e(TAG, "saveiro.png ja existe, nao sobrescrevo"); return; }

        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        dst.setReadable(true, false);
        Log.i(TAG, "copiado " + src + " -> " + dst + " (" + dst.length() + " bytes)");
    }
}
