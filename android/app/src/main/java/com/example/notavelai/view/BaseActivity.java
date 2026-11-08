package com.example.notavelai.view;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.notavelai.util.Preferencias;

/**
 * Base de todas as Activities do aplicativo.
 *
 * Existe para que as opções de acessibilidade (tema e tamanho do texto) valham em TODAS as
 * telas, inclusive no login e no cadastro.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context contextoBase) {
        // 1. TEMA (claro / escuro)
        // Definido aqui, antes de o AppCompat criar o delegate da tela, para a
        // Activity já nascer no tema salvo, sem piscar no tema errado.
        AppCompatDelegate.setDefaultNightMode(Preferencias.getModoNoturno(contextoBase));

        // 2. TAMANHO DO TEXTO
        // Multiplicamos o fontScale da configuração. Como todos os tamanhos dos
        // layouts estão em "sp", um único ajuste aqui reescala o app inteiro.
        //
        // É multiplicação, e não substituição, de propósito: assim respeitamos
        // quem já aumentou a fonte nas configurações do próprio Android.
        Configuration config = new Configuration(contextoBase.getResources().getConfiguration());
        config.fontScale = config.fontScale * Preferencias.getEscalaTexto(contextoBase);

        super.attachBaseContext(contextoBase.createConfigurationContext(config));
    }
}
