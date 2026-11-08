package com.example.notavelai.view;

import android.content.Intent;
import android.os.Bundle;

import androidx.core.splashscreen.SplashScreen;

import com.example.notavelai.database.entity.Usuario;
import com.example.notavelai.repository.UsuarioRepository;
import com.example.notavelai.util.Notificacoes;

/**
 * Tela de abertura (Splash Screen API do androidx.core).
 *
 * O próprio sistema desenha a splash (tema Theme.NotavelAI.Splash) enquanto o app abre.
 * Ela fica na tela só enquanto a sessão é consultada no banco - não há atraso artificial.
 * Com sessão ativa, segue para a MainActivity; sem sessão, para o Login.
 */
public class SplashActivity extends BaseActivity {

    /** Lido pela splash a cada quadro; volatile porque é alterado por outra thread. */
    private volatile boolean sessaoVerificada = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Precisa vir antes do super.onCreate()
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        splashScreen.setKeepOnScreenCondition(new SplashScreen.KeepOnScreenCondition() {
            @Override
            public boolean shouldKeepOnScreen() {
                return !sessaoVerificada;
            }
        });

        Notificacoes.criarCanais(this);

        // A consulta ao banco roda numa Thread separada
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Usuario usuario = new UsuarioRepository(getApplicationContext())
                        .buscarUsuarioLogado();

                // A troca de tela precisa acontecer na thread principal
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sessaoVerificada = true;
                        Class<?> destino = usuario != null ? MainActivity.class : LoginActivity.class;
                        startActivity(new Intent(SplashActivity.this, destino));
                        finish();
                    }
                });
            }
        }).start();
    }
}
