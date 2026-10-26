package com.fantinel.logocopy;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

// Cor dominante da capa -> Saveiro neon e LEDs dos botões da central.
public class Cor {
    static final int PADRAO = Color.rgb(0, 200, 255); // ciano original

    // matiz mais forte da capa (ignora cinza, preto e branco), devolvida em versão neon
    static int dominante(Bitmap src) {
        try {
            Bitmap s = Bitmap.createScaledBitmap(src, 32, 32, true);
            float[] peso = new float[36], soma = new float[36];
            float[] hsv = new float[3];
            float total = 0;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                Color.colorToHSV(s.getPixel(x, y), hsv);
                if (hsv[1] < 0.25f || hsv[2] < 0.2f) continue;
                float w = hsv[1] * hsv[2];
                int b = Math.min(35, (int) (hsv[0] / 10));
                peso[b] += w; soma[b] += w * hsv[0]; total += w;
            }
            if (total < 20) return PADRAO;          // capa quase sem cor
            int melhor = 0;
            for (int i = 1; i < 36; i++) {
                // junta com os vizinhos para não dividir uma cor entre duas faixas
                float a = peso[i] + peso[(i + 35) % 36] * 0.5f + peso[(i + 1) % 36] * 0.5f;
                float m = peso[melhor] + peso[(melhor + 35) % 36] * 0.5f + peso[(melhor + 1) % 36] * 0.5f;
                if (a > m) melhor = i;
            }
            float h = soma[melhor] / peso[melhor];
            return Color.HSVToColor(new float[]{h, 0.85f, 1f});
        } catch (Throwable t) { return PADRAO; }
    }

    // pinta a imagem neon (ciano) com a cor: usa o brilho de cada pixel e mantém a transparência
    static ColorMatrixColorFilter filtro(int c) {
        float k = 1.7f / 255f;
        float r = Color.red(c) * k, g = Color.green(c) * k, b = Color.blue(c) * k;
        return new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0.299f * r, 0.587f * r, 0.114f * r, 0, 0,
                0.299f * g, 0.587f * g, 0.114f * g, 0, 0,
                0.299f * b, 0.587f * b, 0.114f * b, 0, 0,
                0, 0, 0, 1, 0}));
    }

    // LEDs dos botões: serviço "carstatus", setLed(índice 0 = todos, r, g, b)
    // ---------- cor da tela (matriz de cor do SurfaceFlinger) ----------
    // s = saturação (1 = normal), c = contraste (1 = normal). Pretos ficam mais escuros com c > 1.
    static boolean tela(float s, float c) { return tela(s, c, 1); }

    // farol aceso (fio de iluminação no positivo)? serviço "carstatus", getHeadLightStatus = 7
    static boolean farol() {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "carstatus");
            d.writeInterfaceToken("android.carstatus.ICarStatus");
            b.transact(7, d, r, 0);
            r.readException();
            return r.readInt() != 0;
        } catch (Throwable t) { return false; }
        finally { d.recycle(); r.recycle(); }
    }

    // aplica saturação/contraste salvos + escurecimento extra quando o farol está aceso
    static String ultimoEstado = "";

    // aplica saturação/contraste salvos com o brilho do modo noturno (por horário)
    static void aplicarTela(android.content.Context ctx, boolean forcar) {
        ModoNoite.iniciar(ctx);
    }

    // k = brilho (1 = normal, 0.3 = 30%)
    static boolean tela(float s, float c, float k) {
        float rw = 0.2126f, gw = 0.7152f, bw = 0.0722f, a = 1 - s, o = -(c - 1) / 2 * k;
        c = c * k;
        // linhas: saída R,G,B; colunas: entrada R,G,B
        float[][] m = {
                {(rw * a + s) * c, gw * a * c, bw * a * c},
                {rw * a * c, (gw * a + s) * c, bw * a * c},
                {rw * a * c, gw * a * c, (bw * a + s) * c}};
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "SurfaceFlinger");
            d.writeInterfaceToken("android.ui.ISurfaceComposer");
            boolean normal = s == 1 && c == 1 && k == 1;
            d.writeInt(normal ? 0 : 1);
            if (!normal) {
                for (int col = 0; col < 3; col++) { for (int row = 0; row < 3; row++) d.writeFloat(m[row][col]); d.writeFloat(0); }
                d.writeFloat(o); d.writeFloat(o); d.writeFloat(o); d.writeFloat(1);
            }
            b.transact(1015, d, r, 0);
            Log.i("LogoCopy", "tela s=" + s + " c=" + c + " k=" + k);
            return true;
        } catch (Throwable t) { Log.e("LogoCopy", "tela", t); return false; }
        finally { d.recycle(); r.recycle(); }
    }

    // LEDs acendem demais com valores baixos: curva de gama deixa as cores puras (vermelho sem puxar pro rosa)
    static int gama(int v) { return (int) Math.round(Math.pow(v / 255.0, 2.2) * 255); }

    static int ultimaCorLed = PADRAO;
    static int ultimoEnviado = -1;

    // envia a cor aos LEDs já com o brilho do modo noturno (ModoNoite.kAtual)
    static void leds(int cor) {
        ultimaCorLed = cor;
        float k = Math.max(ModoNoite.kAtual, 0.45f);   // mínimo: os LEDs nunca apagam no modo noturno
        int c = Color.rgb(gama(Math.round(Color.red(cor) * k)), gama(Math.round(Color.green(cor) * k)), gama(Math.round(Color.blue(cor) * k)));
        if (c == ultimoEnviado) return;
        ultimoEnviado = c;
        new Thread(() -> {
            Parcel d = Parcel.obtain(), r = Parcel.obtain();
            try {
                IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "carstatus");
                d.writeInterfaceToken("android.carstatus.ICarStatus");
                d.writeInt(0); d.writeInt(Color.red(c)); d.writeInt(Color.green(c)); d.writeInt(Color.blue(c));
                b.transact(8, d, r, 0);
                r.readException();
            } catch (Throwable t) { Log.e("LogoCopy", "leds", t); }
            finally { d.recycle(); r.recycle(); }
        }).start();
    }
}
