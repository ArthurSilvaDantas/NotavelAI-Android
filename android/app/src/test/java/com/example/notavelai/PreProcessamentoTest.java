package com.example.notavelai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.notavelai.ml.PreProcessamento;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Confere que o corte central em Java dá o mesmo resultado que o
 * keras.preprocessing.image.smart_resize usado no treino.
 *
 * Os arquivos de referência foram gerados em Python com o próprio TensorFlow:
 * - .rgb: largura e altura (int big-endian) seguidas dos pixels RGB;
 * - .esperado: 224 x 224 x 3 floats big-endian, a saída do smart_resize.
 */
public class PreProcessamentoTest {

    /** Diferença máxima aceita, numa escala de 0 a 255 (só arredondamento de float). */
    private static final float TOLERANCIA = 0.02f;

    @Test
    public void fotoDeCedulaEmPeIgualAoTensorFlow() throws IOException {
        conferir("cedula_257x413");
    }

    @Test
    public void imagemDeitadaComLadosImparesIgualAoTensorFlow() throws IOException {
        conferir("aleatoria_333x187");
    }

    @Test
    public void imagemQuadradaDoTamanhoCertoNaoMuda() {
        int[] pixels = new int[224 * 224];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF000000 | (i % 256) << 16 | 7 << 8 | 200;
        }
        float[] saida = PreProcessamento.recortarCentro(pixels, 224, 224, 224);
        for (int i = 0; i < pixels.length; i++) {
            assertEquals(i % 256, saida[i * 3], 0f);
            assertEquals(7, saida[i * 3 + 1], 0f);
            assertEquals(200, saida[i * 3 + 2], 0f);
        }
    }

    private void conferir(String nome) throws IOException {
        int largura;
        int altura;
        int[] pixels;
        try (DataInputStream entrada = abrir(nome + ".rgb")) {
            largura = entrada.readInt();
            altura = entrada.readInt();
            pixels = new int[largura * altura];
            for (int i = 0; i < pixels.length; i++) {
                int r = entrada.readUnsignedByte();
                int g = entrada.readUnsignedByte();
                int b = entrada.readUnsignedByte();
                pixels[i] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }

        float[] obtido = PreProcessamento.recortarCentro(pixels, largura, altura, 224);

        try (DataInputStream esperado = abrir(nome + ".esperado")) {
            assertEquals(224 * 224 * 3, obtido.length);
            for (int i = 0; i < obtido.length; i++) {
                float valor = esperado.readFloat();
                assertEquals("posição " + i, valor, obtido[i], TOLERANCIA);
            }
        }
    }

    private DataInputStream abrir(String arquivo) {
        InputStream entrada = getClass().getResourceAsStream("/preprocessamento/" + arquivo);
        assertNotNull("referência ausente: " + arquivo, entrada);
        return new DataInputStream(entrada);
    }
}
