package com.example.notavelai.ml;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Executa o modelo de reconhecimento de cédulas (MobileNetV2 exportada para .tflite float32).
 *
 * Aceita mais de um modelo em ARQUIVOS_MODELO; nesse caso a resposta é a média das
 * probabilidades deles. Hoje o app usa um só.
 *
 * Contrato do modelo (README do projeto de ML):
 * - entrada [1, 224, 224, 3], RGB, float32, valores 0-255 sem normalizar, com o quadrado
 *   central da foto (ver PreProcessamento);
 * - saída [1, 8], probabilidades na ordem de labels.txt: 2, 5, 10, 20, 50, 100, 200, fundo.
 *
 * Carregar o modelo e classificar são operações demoradas: esta classe deve ser usada
 * apenas fora da thread principal.
 */
public class ClassificadorCedulas implements Closeable {

    /** Gerados por `python -m src.models.export_android`. */
    public static final String[] ARQUIVOS_MODELO = {"notavelai.tflite"};
    public static final String ARQUIVO_ROTULOS = "labels.txt";
    public static final String ROTULO_FUNDO = "fundo";
    public static final int TAMANHO_ENTRADA = 224;

    /**
     * Abaixo desta confiança o app diz que não conseguiu identificar, em vez de arriscar
     * um valor. Para quem não enxerga a nota, um valor errado é pior do que nenhum.
     */
    public static final float LIMIAR_CONFIANCA = 0.7f;

    private final List<Interpreter> modelos = new ArrayList<>();
    private final List<String> rotulos;
    /** Só na versão de desenvolvimento: registra no Logcat a resposta de cada modelo. */
    private final boolean depuracao;

    public ClassificadorCedulas(Context contexto) throws IOException {
        Interpreter.Options opcoes = new Interpreter.Options();
        opcoes.setNumThreads(2);
        for (String arquivo : ARQUIVOS_MODELO) {
            modelos.add(new Interpreter(carregarModelo(contexto, arquivo), opcoes));
        }
        rotulos = carregarRotulos(contexto);
        depuracao = (contexto.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /** Mapeia o arquivo do modelo direto do APK, sem copiá-lo para a memória. */
    private static MappedByteBuffer carregarModelo(Context contexto, String arquivo) throws IOException {
        try (AssetFileDescriptor descritor = contexto.getAssets().openFd(arquivo);
             FileInputStream entrada = new FileInputStream(descritor.getFileDescriptor())) {
            return entrada.getChannel().map(FileChannel.MapMode.READ_ONLY,
                    descritor.getStartOffset(), descritor.getDeclaredLength());
        }
    }

    private static List<String> carregarRotulos(Context contexto) throws IOException {
        List<String> lista = new ArrayList<>();
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(
                contexto.getAssets().open(ARQUIVO_ROTULOS), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    lista.add(linha.trim());
                }
            }
        }
        return lista;
    }

    /** Classifica a foto. Sincronizado porque um Interpreter não pode rodar duas vezes ao mesmo tempo. */
    public synchronized Resultado classificar(Bitmap foto) {
        int largura = foto.getWidth();
        int altura = foto.getHeight();
        int[] pixels = new int[largura * altura];
        foto.getPixels(pixels, 0, largura, 0, 0, largura, altura);

        float[] valores = PreProcessamento.recortarCentro(
                pixels, largura, altura, TAMANHO_ENTRADA);
        ByteBuffer entrada = ByteBuffer.allocateDirect(valores.length * 4).order(ByteOrder.nativeOrder());
        for (float valor : valores) {
            entrada.putFloat(valor);
        }
        entrada.rewind();

        float[] media = new float[rotulos.size()];
        StringBuilder registro = new StringBuilder();
        for (int m = 0; m < modelos.size(); m++) {
            float[][] saida = new float[1][rotulos.size()];
            entrada.rewind();
            modelos.get(m).run(entrada, saida);
            for (int i = 0; i < media.length; i++) {
                media[i] += saida[0][i] / modelos.size();
            }
            if (depuracao) {
                Resultado membro = new Resultado(rotulos, saida[0]);
                registro.append(ARQUIVOS_MODELO[m]).append('=').append(membro.rotulo)
                        .append(String.format(java.util.Locale.ROOT, " (%.2f)  ", membro.confianca));
            }
        }
        Resultado resultado = new Resultado(rotulos, media);
        if (depuracao) {
            Log.d("NotavelAI", registro + "media=" + resultado.rotulo
                    + String.format(java.util.Locale.ROOT, " (%.2f)", resultado.confianca));
        }
        return resultado;
    }

    @Override
    public void close() {
        for (Interpreter modelo : modelos) {
            modelo.close();
        }
    }

    /** Resposta do modelo para uma foto. */
    public static class Resultado {
        public final String rotulo;
        public final float confianca;
        public final float[] probabilidades;

        Resultado(List<String> rotulos, float[] probabilidades) {
            int melhor = 0;
            for (int i = 1; i < probabilidades.length; i++) {
                if (probabilidades[i] > probabilidades[melhor]) {
                    melhor = i;
                }
            }
            this.rotulo = rotulos.get(melhor);
            this.confianca = probabilidades[melhor];
            this.probabilidades = probabilidades;
        }

        /** O modelo achou que não há cédula na foto. */
        public boolean semCedula() {
            return ROTULO_FUNDO.equals(rotulo);
        }

        /** Há uma cédula e a confiança é suficiente para anunciar o valor. */
        public boolean confiavel() {
            return !semCedula() && confianca >= LIMIAR_CONFIANCA;
        }

        /** Valor da cédula em reais (só faz sentido quando não é "fundo"). */
        public int valor() {
            return Integer.parseInt(rotulo);
        }
    }
}
