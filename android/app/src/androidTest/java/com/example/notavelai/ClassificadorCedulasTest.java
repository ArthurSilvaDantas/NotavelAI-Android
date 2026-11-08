package com.example.notavelai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notavelai.ml.ClassificadorCedulas;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Roda o modelo de verdade no aparelho e compara com as respostas do Python.
 *
 * As fotos e o arquivo referencia.csv ficam em src/androidTest/assets e são gerados por
 * `python -m src.models.export_android`. O teste falha se o .tflite não rodar no LiteRT do
 * Android ou se o pré-processamento do app divergir do usado no treino.
 */
@RunWith(AndroidJUnit4.class)
public class ClassificadorCedulasTest {

    /** Diferença aceita na confiança: o decodificador de JPEG do Android não é o do Python. */
    private static final float TOLERANCIA_CONFIANCA = 0.1f;

    @Test
    public void respostasIguaisAsDoPython() throws IOException {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context testes = InstrumentationRegistry.getInstrumentation().getContext();

        int total = 0;
        int iguais = 0;
        StringBuilder diferencas = new StringBuilder();

        try (ClassificadorCedulas classificador = new ClassificadorCedulas(app);
             BufferedReader referencia = new BufferedReader(new InputStreamReader(
                     testes.getAssets().open("referencia.csv"), StandardCharsets.UTF_8))) {
            referencia.readLine(); // cabeçalho
            String linha;
            while ((linha = referencia.readLine()) != null) {
                String[] campos = linha.split(",");
                Bitmap foto;
                try (InputStream entrada = testes.getAssets().open("fotos_teste/" + campos[0])) {
                    foto = BitmapFactory.decodeStream(entrada);
                }
                ClassificadorCedulas.Resultado resultado = classificador.classificar(foto);
                float esperada = Float.parseFloat(campos[3]);

                total++;
                if (resultado.rotulo.equals(campos[2])
                        && Math.abs(resultado.confianca - esperada) <= TOLERANCIA_CONFIANCA) {
                    iguais++;
                } else {
                    diferencas.append(String.format("%n%s: Python %s (%.2f), Android %s (%.2f)",
                            campos[0], campos[2], esperada, resultado.rotulo, resultado.confianca));
                }
            }
        }

        assertTrue("nenhuma foto de referência", total > 0);
        assertEquals("respostas diferentes do Python:" + diferencas, total, iguais);
    }
}
