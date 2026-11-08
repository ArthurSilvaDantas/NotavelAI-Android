package com.example.notavelai.util;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;

/** Formatação de valores e datas no padrão brasileiro. */
public final class Formato {

    public static final Locale BRASIL = new Locale("pt", "BR");

    private Formato() {
        // classe utilitária
    }

    /** 35 -> "R$ 35,00" */
    public static String reais(double valor) {
        return NumberFormat.getCurrencyInstance(BRASIL).format(valor);
    }

    /** Valor em outra moeda, com o símbolo dela: 6.5, "USD" -> "US$ 6,50". */
    public static String moeda(double valor, String codigo) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(BRASIL);
        formato.setCurrency(Currency.getInstance(codigo));
        return formato.format(valor);
    }

    /** "2026-11-04 11:49:16" (formato da AwesomeAPI) -> "04/11/2026 11:49". */
    public static String dataDaApi(String texto) {
        try {
            Date data = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", BRASIL).parse(texto);
            return data == null ? texto : dataHora(data.getTime());
        } catch (ParseException e) {
            return texto; // formato inesperado: mostra como veio
        }
    }

    /** "04/11/2026 14:35" */
    public static String dataHora(long milissegundos) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, BRASIL)
                .format(new Date(milissegundos));
    }
}
