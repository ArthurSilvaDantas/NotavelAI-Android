package com.example.notavelai.ml;

/**
 * Prepara a foto para o modelo exatamente como no treino em Python.
 *
 * O treino lia as imagens com keras.preprocessing.image.smart_resize: corta o maior quadrado
 * central da foto e o reduz a 224x224 (interpolação bilinear, sem suavização). Assim a cédula,
 * que costuma estar no meio da foto, ocupa a maior parte da imagem que o modelo vê. O modelo é
 * sensível a esse passo, por isso esta classe reproduz as mesmas contas, em float.
 *
 * Usa só Java puro (sem Bitmap) para poder ser testada no computador, comparando com a
 * saída do Keras/TensorFlow (ver PreProcessamentoTest).
 */
public final class PreProcessamento {

    private PreProcessamento() {
        // classe utilitária
    }

    /**
     * @param argb    pixels no formato de Bitmap.getPixels() (0xAARRGGBB), linha a linha
     * @param largura largura da imagem original
     * @param altura  altura da imagem original
     * @param tamanho lado do quadrado de saída (224)
     * @return tamanho x tamanho x 3 valores RGB entre 0 e 255, sem normalizar
     */
    public static float[] recortarCentro(int[] argb, int largura, int altura, int tamanho) {
        // Mesmas contas do smart_resize: o lado do recorte é a menor dimensão e o início é
        // metade da sobra, arredondada para baixo.
        int lado = Math.min(largura, altura);
        int inicioY = (altura - lado) / 2;
        int inicioX = (largura - lado) / 2;

        float[] saida = new float[tamanho * tamanho * 3];
        float escala = (float) lado / tamanho;

        for (int y = 0; y < tamanho; y++) {
            // half_pixel_centers: o centro do pixel de saída é mapeado no centro de entrada.
            float origemY = (y + 0.5f) * escala - 0.5f;
            int y0 = inicioY + Math.max((int) Math.floor(origemY), 0);
            int y1 = inicioY + Math.min((int) Math.ceil(origemY), lado - 1);
            float pesoY = origemY - (float) Math.floor(origemY);

            for (int x = 0; x < tamanho; x++) {
                float origemX = (x + 0.5f) * escala - 0.5f;
                int x0 = inicioX + Math.max((int) Math.floor(origemX), 0);
                int x1 = inicioX + Math.min((int) Math.ceil(origemX), lado - 1);
                float pesoX = origemX - (float) Math.floor(origemX);

                int destino = (y * tamanho + x) * 3;
                for (int canal = 0; canal < 3; canal++) {
                    int deslocamento = 16 - 8 * canal; // R, G e B dentro do int ARGB
                    float superiorEsquerdo = componente(argb[y0 * largura + x0], deslocamento);
                    float superiorDireito = componente(argb[y0 * largura + x1], deslocamento);
                    float inferiorEsquerdo = componente(argb[y1 * largura + x0], deslocamento);
                    float inferiorDireito = componente(argb[y1 * largura + x1], deslocamento);
                    float superior = superiorEsquerdo + (superiorDireito - superiorEsquerdo) * pesoX;
                    float inferior = inferiorEsquerdo + (inferiorDireito - inferiorEsquerdo) * pesoX;
                    saida[destino + canal] = superior + (inferior - superior) * pesoY;
                }
            }
        }
        return saida;
    }

    private static float componente(int pixel, int deslocamento) {
        return (pixel >> deslocamento) & 0xFF;
    }
}
