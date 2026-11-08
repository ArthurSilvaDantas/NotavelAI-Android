package com.example.notavelai;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.accessibility.AccessibilityChecks;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notavelai.repository.UsuarioRepository;
import com.example.notavelai.util.Preferencias;
import com.example.notavelai.view.CadastroActivity;
import com.example.notavelai.view.LoginActivity;
import com.example.notavelai.view.MainActivity;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verificação automática de acessibilidade (Accessibility Test Framework do Google).
 *
 * A cada ação do Espresso, a tela inteira é conferida: elementos sem descrição para o
 * TalkBack, alvos de toque menores que 48dp, contraste baixo entre texto e fundo, rótulos
 * duplicados, entre outros. Qualquer problema faz o teste falhar.
 */
@RunWith(AndroidJUnit4.class)
public class AcessibilidadeTest {

    @BeforeClass
    public static void ativarVerificacoes() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true);
    }

    @Test
    public void telaDeLogin() {
        try (ActivityScenario<LoginActivity> tela = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.editEmail)).perform(click());
        }
    }

    @Test
    public void telaDeCadastro() {
        try (ActivityScenario<CadastroActivity> tela = ActivityScenario.launch(CadastroActivity.class)) {
            onView(withId(R.id.editNome)).perform(click());
        }
    }

    @Test
    public void telaPrincipal() {
        // A tela principal exige sessão ativa: cria um usuário e faz login direto no banco
        // (o teste não roda na thread principal, então o acesso ao Room é permitido).
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Preferencias.setNotificacaoPedida(contexto); // sem o diálogo de permissão por cima
        UsuarioRepository repositorio = new UsuarioRepository(contexto);
        String email = "acessibilidade" + System.currentTimeMillis() + "@teste.com";
        repositorio.cadastrar("Teste", email, "senha123", null);
        repositorio.login(email, "senha123");

        try (ActivityScenario<MainActivity> tela = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.textResultado)).perform(click());
        } finally {
            repositorio.logout();
        }
    }
}
