package com.example.notavelai.view;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.notavelai.R;
import com.example.notavelai.database.entity.Usuario;
import com.example.notavelai.databinding.ActivityCadastroBinding;
import com.example.notavelai.repository.UsuarioRepository;
import com.example.notavelai.util.FotoUtil;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity de Cadastro e Edição de perfil (mesma tela reaproveitada), com foto de perfil
 * tirada pela câmera externa ou escolhida na galeria.
 *
 * Fluxo da foto:
 * Tirar foto -> Permissão -> Arquivo -> FileProvider -> Uri -> Câmera externa -> JPEG
 * Galeria -> Uri content://
 * Nos dois casos a foto é reduzida, copiada para a pasta privada do app e o CAMINHO do
 * arquivo é salvo no Room junto ao usuário.
 *
 * Banco, BCrypt e leitura da foto rodam numa Thread separada.
 */
public class CadastroActivity extends BaseActivity {

    /**
     * Id do usuário a editar. Sem este extra a tela abre em modo cadastro;
     * com ele, abre em modo edição de perfil.
     */
    public static final String EXTRA_ID_USUARIO = "id_usuario";

    private static final int TAMANHO_MINIMO_NOME = 2;
    private static final int TAMANHO_MINIMO_SENHA = 6;
    /** O BCrypt considera só os primeiros 72 bytes; limitamos para não haver surpresa. */
    private static final int TAMANHO_MAXIMO_SENHA = 64;

    private static final String ESTADO_ARQUIVO_CAMERA = "arquivo_camera";
    private static final String ESTADO_FOTO_NOVA = "foto_nova";

    private ActivityCadastroBinding binding;
    private UsuarioRepository repositorio;

    /** Usuário em edição (null = modo cadastro). */
    private Usuario usuarioEdicao;

    /** Arquivo temporário onde a câmera externa grava a foto (via Uri do FileProvider). */
    private File photoFile;
    /** Foto nova já copiada para a pasta do app, ainda não salva no banco. */
    private String fotoNovaCaminho;
    /** true depois de salvar: a foto nova passou a pertencer ao usuário e não deve ser apagada. */
    private boolean salvo = false;

    // Launcher para solicitação de permissão da câmera
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    new ActivityResultCallback<Boolean>() {
                        @Override
                        public void onActivityResult(Boolean concedida) {
                            if (concedida) {
                                abrirCamera();
                            } else {
                                Toast.makeText(CadastroActivity.this,
                                        R.string.msg_permissao_camera, Toast.LENGTH_LONG).show();
                            }
                        }
                    });

    // Launcher que dispara a câmera externa e recebe o resultado
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(),
                    new ActivityResultCallback<Boolean>() {
                        @Override
                        public void onActivityResult(Boolean sucesso) {
                            if (sucesso && photoFile != null) {
                                processarFoto(Uri.fromFile(photoFile), photoFile);
                            } else if (photoFile != null) {
                                photoFile.delete(); // câmera cancelada: descarta o arquivo vazio
                                photoFile = null;
                            }
                        }
                    });

    // Launcher da galeria: não precisa de permissão, o sistema entrega só a imagem escolhida
    private final ActivityResultLauncher<String> galeriaLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    new ActivityResultCallback<Uri>() {
                        @Override
                        public void onActivityResult(Uri uri) {
                            if (uri != null) {
                                processarFoto(uri, null);
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityCadastroBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        MargensSistema.aplicar(binding.getRoot());

        repositorio = new UsuarioRepository(getApplicationContext());
        binding.imageView.setClipToOutline(true); // recorta a foto no círculo do fundo

        // Abrir a câmera pode fazer o Android recriar esta tela: restaura o estado da foto
        if (savedInstanceState != null) {
            String caminhoCamera = savedInstanceState.getString(ESTADO_ARQUIVO_CAMERA);
            if (caminhoCamera != null) {
                photoFile = new File(caminhoCamera);
            }
            fotoNovaCaminho = savedInstanceState.getString(ESTADO_FOTO_NOVA);
            exibirFoto(fotoNovaCaminho);
        }

        // Modo edição: carrega do Room o usuário logado
        long idUsuario = getIntent().getLongExtra(EXTRA_ID_USUARIO, 0);
        if (idUsuario > 0) {
            prepararModoEdicao(idUsuario, savedInstanceState == null);
        }

        binding.buttonCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checarPermissao();
            }
        });
        binding.buttonGaleria.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                galeriaLauncher.launch("image/*");
            }
        });
        binding.buttonSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salvarCadastro();
            }
        });
    }

    /**
     * Ajusta os textos para edição, mostra o campo de senha atual e, na primeira abertura,
     * preenche os campos com os dados do banco (lidos numa Thread).
     */
    private void prepararModoEdicao(final long idUsuario, final boolean preencherCampos) {
        binding.txtTituloCadastro.setText(R.string.editar_titulo);
        binding.txtSubtituloCadastro.setText(R.string.editar_subtitulo);
        binding.buttonSalvar.setText(R.string.btn_salvar_alteracoes);
        binding.rotuloSenha.setText(R.string.rotulo_nova_senha);
        binding.rotuloConfirmarSenha.setText(R.string.rotulo_confirmar_nova_senha);
        binding.rotuloSenhaAtual.setVisibility(View.VISIBLE);
        binding.editSenhaAtual.setVisibility(View.VISIBLE);
        binding.buttonSalvar.setEnabled(false); // até o usuário ser carregado

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Usuario usuario = repositorio.buscarPorId(idUsuario);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (usuario == null) {
                            finish();
                            return;
                        }
                        usuarioEdicao = usuario;
                        binding.buttonSalvar.setEnabled(true);
                        if (preencherCampos) {
                            binding.editNome.setText(usuario.nome);
                            binding.editEmail.setText(usuario.email);
                        }
                        if (fotoNovaCaminho == null) {
                            exibirFoto(usuario.fotoCaminho);
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (photoFile != null) {
            outState.putString(ESTADO_ARQUIVO_CAMERA, photoFile.getAbsolutePath());
        }
        if (fotoNovaCaminho != null) {
            outState.putString(ESTADO_FOTO_NOVA, fotoNovaCaminho);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Saiu sem salvar: a foto nova não pertence a ninguém e é apagada.
        if (isFinishing() && !salvo) {
            FotoUtil.apagar(fotoNovaCaminho);
        }
    }

    // Câmera

    /** Verifica a permissão: se já concedida abre a câmera, senão pede ao usuário. */
    private void checarPermissao() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            abrirCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /** Cria um arquivo vazio com timestamp na pasta privada de imagens do app. */
    private File criarArquivoImagem() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String nome = "JPEG_" + timeStamp + "_";
        File pasta = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(nome, ".jpg", pasta);
    }

    /** Gera o Uri do arquivo pelo FileProvider e abre a câmera externa. */
    private void abrirCamera() {
        try {
            photoFile = criarArquivoImagem();
            Uri photoUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", photoFile);
            takePictureLauncher.launch(photoUri);
        } catch (IOException e) {
            Toast.makeText(this, R.string.msg_erro_imagem, Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.msg_sem_camera, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Reduz a foto e a copia para a pasta privada do app, numa Thread. O arquivo temporário
     * da câmera é apagado em seguida.
     */
    private void processarFoto(final Uri origem, final File arquivoTemporario) {
        binding.buttonCamera.setEnabled(false);
        binding.buttonGaleria.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                String caminho;
                try {
                    caminho = FotoUtil.salvarFotoPerfil(getApplicationContext(), origem);
                } catch (IOException | SecurityException e) {
                    caminho = null;
                }
                if (arquivoTemporario != null) {
                    arquivoTemporario.delete();
                }
                final String caminhoFinal = caminho;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        photoFile = null;
                        binding.buttonCamera.setEnabled(true);
                        binding.buttonGaleria.setEnabled(true);
                        if (caminhoFinal == null) {
                            Toast.makeText(CadastroActivity.this, R.string.msg_erro_imagem,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        FotoUtil.apagar(fotoNovaCaminho); // troca de foto antes de salvar
                        fotoNovaCaminho = caminhoFinal;
                        exibirFoto(caminhoFinal);
                        binding.imageView.announceForAccessibility(
                                getString(R.string.msg_foto_atualizada));
                    }
                });
            }
        }).start();
    }

    private void exibirFoto(String caminho) {
        Bitmap bitmap = FotoUtil.carregarFotoPerfil(caminho);
        if (bitmap != null) {
            binding.imageView.setImageBitmap(bitmap);
            binding.imageView.setContentDescription(getString(R.string.desc_foto_perfil));
        } else {
            binding.imageView.setImageResource(R.drawable.ic_avatar_padrao);
            binding.imageView.setContentDescription(getString(R.string.desc_foto_perfil_padrao));
        }
    }

    // Salvar

    private void salvarCadastro() {
        final boolean modoEdicao = usuarioEdicao != null;
        final String nome = binding.editNome.getText().toString().trim();
        final String email = binding.editEmail.getText().toString().trim();
        final String senha = binding.editSenha.getText().toString();
        final String confirmacao = binding.editConfirmarSenha.getText().toString();
        final String senhaAtual = binding.editSenhaAtual.getText().toString();

        // Validação dos campos, do primeiro ao último, levando o foco ao campo com erro
        // (o TalkBack lê a mensagem de erro do campo focado).
        if (nome.length() < TAMANHO_MINIMO_NOME) {
            mostrarErro(binding.editNome, nome.isEmpty()
                    ? R.string.msg_preencha_campos : R.string.msg_nome_curto);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mostrarErro(binding.editEmail, email.isEmpty()
                    ? R.string.msg_preencha_campos : R.string.msg_email_invalido);
            return;
        }
        // Na edição, senha em branco = manter a atual
        boolean trocarSenha = !modoEdicao || !senha.isEmpty() || !confirmacao.isEmpty();
        if (trocarSenha) {
            if (senha.length() < TAMANHO_MINIMO_SENHA || senha.length() > TAMANHO_MAXIMO_SENHA) {
                mostrarErro(binding.editSenha, R.string.msg_senha_curta);
                return;
            }
            if (!senha.equals(confirmacao)) {
                mostrarErro(binding.editConfirmarSenha, R.string.msg_senhas_diferentes);
                return;
            }
        }
        if (modoEdicao && senhaAtual.isEmpty()) {
            mostrarErro(binding.editSenhaAtual, R.string.msg_preencha_campos);
            return;
        }

        binding.buttonSalvar.setEnabled(false);
        binding.textStatus.setText(R.string.msg_salvando);
        final String novaSenha = trocarSenha ? senha : "";

        new Thread(new Runnable() {
            @Override
            public void run() {
                long idProprio = modoEdicao ? usuarioEdicao.id : 0;
                int erro = 0;
                EditText campoErro = null;
                String fotoAntiga = null;

                try {
                    if (modoEdicao && !repositorio.conferirSenha(usuarioEdicao, senhaAtual)) {
                        erro = R.string.msg_senha_atual_incorreta;
                        campoErro = binding.editSenhaAtual;
                    } else if (repositorio.emailEmUso(email, idProprio)) {
                        erro = R.string.msg_email_em_uso;
                        campoErro = binding.editEmail;
                    } else if (modoEdicao) {
                        fotoAntiga = usuarioEdicao.fotoCaminho;
                        usuarioEdicao.nome = nome;
                        usuarioEdicao.email = email;
                        if (fotoNovaCaminho != null) {
                            usuarioEdicao.fotoCaminho = fotoNovaCaminho;
                        }
                        repositorio.atualizarPerfil(usuarioEdicao, novaSenha);
                    } else {
                        repositorio.cadastrar(nome, email, novaSenha, fotoNovaCaminho);
                    }
                } catch (RuntimeException e) {
                    // Ex.: e-mail cadastrado por outra tela entre a verificação e o insert
                    erro = R.string.msg_erro_banco;
                }

                final int erroFinal = erro;
                final EditText campoFinal = campoErro;
                final String fotoParaApagar = fotoNovaCaminho != null ? fotoAntiga : null;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        binding.textStatus.setText("");
                        if (erroFinal != 0) {
                            binding.buttonSalvar.setEnabled(true);
                            if (campoFinal != null) {
                                mostrarErro(campoFinal, erroFinal);
                            } else {
                                Toast.makeText(CadastroActivity.this, erroFinal,
                                        Toast.LENGTH_LONG).show();
                            }
                            return;
                        }
                        salvo = true;
                        FotoUtil.apagar(fotoParaApagar);
                        Toast.makeText(CadastroActivity.this, modoEdicao
                                        ? R.string.msg_perfil_atualizado
                                        : R.string.msg_cadastro_ok,
                                Toast.LENGTH_LONG).show();
                        finish(); // volta para a tela anterior (Login ou principal)
                    }
                });
            }
        }).start();
    }

    private void mostrarErro(EditText campo, int mensagem) {
        campo.setError(getString(mensagem));
        campo.requestFocus();
    }
}
