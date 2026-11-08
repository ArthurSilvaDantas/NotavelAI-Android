package com.example.notavelai.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;

import com.example.notavelai.R;
import com.example.notavelai.database.entity.Usuario;
import com.example.notavelai.databinding.ActivityLoginBinding;
import com.example.notavelai.repository.UsuarioRepository;

/**
 * Tela de Login, com a consulta ao banco numa Thread separada.
 */
public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;
    private UsuarioRepository repositorio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        MargensSistema.aplicar(binding.getRoot());

        repositorio = new UsuarioRepository(getApplicationContext());

        binding.buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                realizarLogin();
            }
        });

        // Abre a CadastroActivity em modo cadastro
        binding.buttonCadastro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, CadastroActivity.class);
                startActivity(intent);
            }
        });
    }

    private void realizarLogin() {
        final String email = binding.editEmail.getText().toString().trim();
        final String senha = binding.editSenha.getText().toString();

        if (email.isEmpty()) {
            binding.editEmail.setError(getString(R.string.msg_preencha_login));
            binding.editEmail.requestFocus();
            return;
        }
        if (senha.isEmpty()) {
            binding.editSenha.setError(getString(R.string.msg_preencha_login));
            binding.editSenha.requestFocus();
            return;
        }

        mostrarCarregando(true);

        // O BCrypt e o banco são lentos: rodam fora da thread principal
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Usuario usuario = repositorio.login(email, senha);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mostrarCarregando(false);
                        if (usuario != null) {
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            // Limpa a pilha: o "voltar" da tela principal não retorna ao login
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            binding.editSenha.setText("");
                            Toast.makeText(LoginActivity.this, R.string.msg_login_invalido,
                                    Toast.LENGTH_LONG).show();
                            binding.textStatus.setText(R.string.msg_login_invalido);
                        }
                    }
                });
            }
        }).start();
    }

    /** Desabilita os botões durante a consulta, evitando dois logins ao mesmo tempo. */
    private void mostrarCarregando(boolean carregando) {
        binding.buttonLogin.setEnabled(!carregando);
        binding.buttonCadastro.setEnabled(!carregando);
        binding.progressLogin.setVisibility(carregando ? View.VISIBLE : View.GONE);
        binding.textStatus.setText(carregando ? getString(R.string.msg_entrando) : "");
    }
}
