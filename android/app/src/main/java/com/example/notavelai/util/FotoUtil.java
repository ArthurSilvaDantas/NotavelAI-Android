package com.example.notavelai.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Leitura de fotos da câmera ou da galeria, já reduzidas e na orientação correta.
 *
 * As duas origens chegam como Uri: a câmera grava num arquivo (Uri.fromFile) e a galeria
 * devolve um content://. Todas as leituras são demoradas e devem rodar fora da thread
 * principal.
 */
public final class FotoUtil {

    /** Lado maior da foto de perfil salva no app. */
    private static final int LADO_PERFIL = 480;
    /** Lado maior da foto entregue ao modelo, como as fotos de celular usadas no teste do modelo. */
    public static final int LADO_MODELO = 1024;
    private static final int QUALIDADE_JPEG = 85;
    private static final String PASTA_PERFIL = "perfil";

    private FotoUtil() {
        // classe utilitária
    }

    /**
     * Decodifica a imagem reduzindo-a para que o lado maior fique próximo de ladoAlvo (sem
     * passar dele) e corrige a rotação indicada no EXIF.
     */
    public static Bitmap carregar(Context contexto, Uri uri, int ladoAlvo) throws IOException {
        // 1. Lê só as dimensões para calcular quanto reduzir na decodificação
        BitmapFactory.Options opcoes = new BitmapFactory.Options();
        opcoes.inJustDecodeBounds = true;
        try (InputStream entrada = contexto.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(entrada, null, opcoes);
        }
        if (opcoes.outWidth <= 0 || opcoes.outHeight <= 0) {
            throw new IOException("Imagem inválida: " + uri);
        }
        int amostra = 1;
        while (Math.max(opcoes.outWidth, opcoes.outHeight) / (amostra * 2) >= ladoAlvo) {
            amostra *= 2;
        }

        // 2. Decodifica já reduzida (economiza memória)
        BitmapFactory.Options leitura = new BitmapFactory.Options();
        leitura.inSampleSize = amostra;
        Bitmap bitmap;
        try (InputStream entrada = contexto.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(entrada, null, leitura);
        }
        if (bitmap == null) {
            throw new IOException("Não foi possível decodificar: " + uri);
        }

        // 3. Ajusta ao tamanho final e à orientação em que a foto foi tirada
        bitmap = reduzir(bitmap, ladoAlvo);
        return corrigirRotacao(bitmap, lerRotacao(contexto, uri));
    }

    /** Foto pronta para o modelo: lado maior com no máximo 1024 px. */
    public static Bitmap carregarParaModelo(Context contexto, Uri uri) throws IOException {
        return carregar(contexto, uri, LADO_MODELO);
    }

    /**
     * Salva a foto de perfil reduzida na pasta interna do app (privada: outros apps não têm
     * acesso) e devolve o caminho, que é o que vai para o banco.
     */
    public static String salvarFotoPerfil(Context contexto, Uri origem) throws IOException {
        Bitmap bitmap = carregar(contexto, origem, LADO_PERFIL);
        File pasta = new File(contexto.getFilesDir(), PASTA_PERFIL);
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Não foi possível criar " + pasta);
        }
        File destino = new File(pasta, "perfil_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream saida = new FileOutputStream(destino)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, saida);
        }
        return destino.getAbsolutePath();
    }

    /** Lê a foto de perfil salva; devolve null se o arquivo não existir mais. */
    public static Bitmap carregarFotoPerfil(String caminho) {
        if (caminho == null || !new File(caminho).exists()) {
            return null;
        }
        return BitmapFactory.decodeFile(caminho);
    }

    /** Apaga uma foto de perfil antiga, depois que a nova já foi salva no banco. */
    public static void apagar(String caminho) {
        if (caminho != null) {
            File arquivo = new File(caminho);
            if (arquivo.exists()) {
                arquivo.delete();
            }
        }
    }

    private static Bitmap reduzir(Bitmap original, int ladoAlvo) {
        int maior = Math.max(original.getWidth(), original.getHeight());
        if (maior <= ladoAlvo) {
            return original;
        }
        float escala = (float) ladoAlvo / maior;
        return Bitmap.createScaledBitmap(original,
                Math.round(original.getWidth() * escala),
                Math.round(original.getHeight() * escala), true);
    }

    /** Muitas câmeras salvam a foto "deitada" e indicam a rotação no EXIF. */
    private static int lerRotacao(Context contexto, Uri uri) {
        try (InputStream entrada = contexto.getContentResolver().openInputStream(uri)) {
            if (entrada == null) {
                return 0;
            }
            int orientacao = new ExifInterface(entrada).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientacao) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }

    private static Bitmap corrigirRotacao(Bitmap bitmap, int graus) {
        if (graus == 0) {
            return bitmap;
        }
        Matrix matriz = new Matrix();
        matriz.postRotate(graus);
        return Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matriz, true);
    }
}
