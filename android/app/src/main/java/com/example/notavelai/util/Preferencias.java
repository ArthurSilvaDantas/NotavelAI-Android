package com.example.notavelai.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Guarda as opções de acessibilidade escolhidas em Configurações.
 *
 * Ficam em SharedPreferences, então continuam valendo depois de fechar o app -
 * um ajuste de acessibilidade que o usuário precisa refazer toda vez que abre
 * o aplicativo não serve para nada.
 */
public final class Preferencias {

    private static final String ARQUIVO = "config_notavelai";
    private static final String CHAVE_TEMA = "tema";
    private static final String CHAVE_TEXTO = "escala_texto";
    private static final String CHAVE_FALA = "falar_resultado";
    private static final String CHAVE_NOTIFICACAO_PEDIDA = "notificacao_pedida";

    // Tema (contraste / luminosidade da tela)
    public static final int TEMA_CLARO = 0;
    public static final int TEMA_ESCURO = 1;

    // Tamanho do texto
    public static final int TEXTO_PADRAO = 0;
    public static final int TEXTO_GRANDE = 1;
    public static final int TEXTO_MUITO_GRANDE = 2;

    /** Multiplicadores aplicados sobre o tamanho de fonte do sistema. */
    private static final float[] ESCALAS = {1.00f, 1.15f, 1.30f};

    private Preferencias() {
        // classe utilitária
    }

    private static SharedPreferences prefs(Context contexto) {
        return contexto.getApplicationContext()
                .getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ tema
    public static int getTema(Context contexto) {
        return prefs(contexto).getInt(CHAVE_TEMA, TEMA_CLARO);
    }

    public static void setTema(Context contexto, int tema) {
        prefs(contexto).edit().putInt(CHAVE_TEMA, tema).apply();
    }

    /** Traduz a escolha para a constante que o AppCompat entende. */
    public static int getModoNoturno(Context contexto) {
        return getTema(contexto) == TEMA_ESCURO
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO;
    }

    // --------------------------------------------------------- tamanho texto
    public static int getOpcaoTexto(Context contexto) {
        return prefs(contexto).getInt(CHAVE_TEXTO, TEXTO_PADRAO);
    }

    public static void setOpcaoTexto(Context contexto, int opcao) {
        prefs(contexto).edit().putInt(CHAVE_TEXTO, opcao).apply();
    }

    /** Fator que a BaseActivity aplica sobre o fontScale da configuração. */
    public static float getEscalaTexto(Context contexto) {
        int opcao = getOpcaoTexto(contexto);
        if (opcao < 0 || opcao >= ESCALAS.length) {
            return ESCALAS[TEXTO_PADRAO];
        }
        return ESCALAS[opcao];
    }

    // ------------------------------------------------------------------ fala
    /** Falar o valor identificado com a voz do aparelho (ligado por padrão). */
    public static boolean getFalarResultado(Context contexto) {
        return prefs(contexto).getBoolean(CHAVE_FALA, true);
    }

    public static void setFalarResultado(Context contexto, boolean falar) {
        prefs(contexto).edit().putBoolean(CHAVE_FALA, falar).apply();
    }

    // ---------------------------------------------------------- notificações
    /** Evita pedir a permissão de notificações toda vez que o app abre. */
    public static boolean getNotificacaoPedida(Context contexto) {
        return prefs(contexto).getBoolean(CHAVE_NOTIFICACAO_PEDIDA, false);
    }

    public static void setNotificacaoPedida(Context contexto) {
        prefs(contexto).edit().putBoolean(CHAVE_NOTIFICACAO_PEDIDA, true).apply();
    }
}
