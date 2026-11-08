package com.example.notavelai.rede;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Consulta a cotação de uma moeda em reais na AwesomeAPI, com HttpURLConnection.
 *
 * Exemplo de resposta de https://economia.awesomeapi.com.br/json/last/USD-BRL:
 * {"USDBRL":{"code":"USD","bid":"5.4806","create_date":"2026-11-04 11:29:08", ...}}
 *
 * A requisição é bloqueante: consultar() deve ser chamado dentro de uma Thread separada.
 *
 * Economia de energia: só há requisição quando o usuário pede, e uma cotação obtida há
 * menos de 5 minutos é reaproveitada sem usar a rede de novo.
 */
public final class CotacaoCliente {

    private static final String URL_BASE = "https://economia.awesomeapi.com.br/json/last/";
    private static final int TEMPO_LIMITE_MS = 10000;
    private static final long VALIDADE_CACHE_MS = 5 * 60 * 1000;

    /** Última cotação de cada moeda, guardada só enquanto o app está aberto. */
    private static final Map<String, Cotacao> CACHE = new HashMap<>();

    private CotacaoCliente() {
        // classe utilitária
    }

    /** Valor de 1 unidade da moeda estrangeira, em reais. */
    public static class Cotacao {
        public final String codigo;
        public final double valorEmReais;
        public final String atualizadaEm;
        final long obtidaEm;
        public boolean doCache;

        Cotacao(String codigo, double valorEmReais, String atualizadaEm) {
            this.codigo = codigo;
            this.valorEmReais = valorEmReais;
            this.atualizadaEm = atualizadaEm;
            this.obtidaEm = System.currentTimeMillis();
        }
    }

    /** Falha na consulta, com o motivo para a tela mostrar a mensagem certa. */
    public static class ErroCotacao extends Exception {
        public static final int SEM_INTERNET = 1;
        public static final int SERVIDOR = 2;
        public static final int REDE = 3;

        public final int motivo;

        ErroCotacao(int motivo, String mensagem, Throwable causa) {
            super(mensagem, causa);
            this.motivo = motivo;
        }
    }

    public static Cotacao consultar(Context contexto, String codigo) throws ErroCotacao {
        synchronized (CACHE) {
            Cotacao guardada = CACHE.get(codigo);
            if (guardada != null
                    && System.currentTimeMillis() - guardada.obtidaEm < VALIDADE_CACHE_MS) {
                guardada.doCache = true;
                return guardada;
            }
        }

        if (!temInternet(contexto)) {
            throw new ErroCotacao(ErroCotacao.SEM_INTERNET, "Sem conexão", null);
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(URL_BASE + codigo + "-BRL");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TEMPO_LIMITE_MS);
            connection.setReadTimeout(TEMPO_LIMITE_MS);
            connection.setDoInput(true);
            connection.connect();

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new ErroCotacao(ErroCotacao.SERVIDOR, "HTTP " + status, null);
            }
            Cotacao cotacao = interpretar(codigo, lerTexto(connection.getInputStream()));
            synchronized (CACHE) {
                CACHE.put(codigo, cotacao);
            }
            return cotacao;
        } catch (JSONException | NumberFormatException e) {
            throw new ErroCotacao(ErroCotacao.SERVIDOR, "Resposta inesperada", e);
        } catch (IOException e) {
            throw new ErroCotacao(ErroCotacao.REDE, "Falha de rede", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Lê o JSON da resposta e extrai o valor de compra ("bid") e a data. */
    static Cotacao interpretar(String codigo, String json) throws JSONException {
        JSONObject dados = new JSONObject(json).getJSONObject(codigo + "BRL");
        double valor = Double.parseDouble(dados.getString("bid"));
        if (valor <= 0) {
            throw new NumberFormatException("Cotação inválida: " + valor);
        }
        return new Cotacao(codigo, valor, dados.optString("create_date", ""));
    }

    private static String lerTexto(InputStream entrada) throws IOException {
        StringBuilder texto = new StringBuilder();
        try (BufferedReader leitor = new BufferedReader(
                new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                texto.append(linha);
            }
        }
        return texto.toString();
    }

    /** Verifica se há rede com acesso à internet antes de tentar a requisição. */
    public static boolean temInternet(Context contexto) {
        ConnectivityManager conexoes = contexto.getSystemService(ConnectivityManager.class);
        if (conexoes == null) {
            return false;
        }
        Network rede = conexoes.getActiveNetwork();
        NetworkCapabilities capacidades = rede == null ? null : conexoes.getNetworkCapabilities(rede);
        return capacidades != null
                && capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
