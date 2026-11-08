package com.example.notavelai.view;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.notavelai.R;
import com.example.notavelai.database.entity.Usuario;
import com.example.notavelai.databinding.ActivityMainBinding;
import com.example.notavelai.repository.UsuarioRepository;
import com.example.notavelai.util.FotoUtil;
import com.example.notavelai.util.Notificacoes;
import com.example.notavelai.util.Preferencias;

/**
 * Tela principal (template Basic Views Activity + BottomNavigationView):
 * Toolbar com o menu de opções e três fragmentos - Identificar, Histórico e Cotação.
 *
 * Controle de sessão: a cada vez que a tela volta ao primeiro plano, a sessão é consultada
 * no banco (numa Thread). Sem sessão ativa, o usuário é levado ao Login e esta tela é
 * fechada, impedindo o acesso às funcionalidades restritas.
 */
public class MainActivity extends BaseActivity {

    /** Aba a abrir quando a tela é aberta por uma notificação. */
    public static final String EXTRA_DESTINO = "destino";
    public static final int DESTINO_HISTORICO = 1;
    public static final int DESTINO_COTACAO = 2;

    private ActivityMainBinding binding;
    private AppBarConfiguration appBarConfiguration;
    private NavController navController;
    private UsuarioRepository repositorio;

    /** Usuário com sessão ativa (null até a consulta terminar). */
    private Usuario usuarioLogado;
    /** Foto do usuário logado, lida do arquivo cujo caminho está no banco. */
    private Bitmap avatar;

    // Permissão de notificações (Android 13+): negar não impede o uso do app
    private final ActivityResultLauncher<String> notificacaoLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    new ActivityResultCallback<Boolean>() {
                        @Override
                        public void onActivityResult(Boolean concedida) {
                            if (!concedida) {
                                Toast.makeText(MainActivity.this,
                                        R.string.msg_notificacoes_negadas,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Ajuste de margens do sistema (EdgeToEdge): a barra inferior já reserva o
        // espaço da navegação do sistema, então a raiz só recua no topo e nas laterais.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(barras.left, barras.top, barras.right, 0);
                return insets;
            }
        });

        // Vincula a Toolbar
        setSupportActionBar(binding.toolbar);
        repositorio = new UsuarioRepository(getApplicationContext());

        // Configura o componente de navegação dos Fragmentos
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.IdentificarFragment, R.id.HistoricoFragment, R.id.CotacaoFragment
            ).build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

            // Conecta o BottomNavigationView ao NavController
            NavigationUI.setupWithNavController(binding.bottomNav, navController);
        }

        abrirDestino(getIntent());
        pedirPermissaoNotificacoes();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        abrirDestino(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificarSessao();
    }

    /** Notificação tocada: abre a aba relacionada ao aviso. */
    private void abrirDestino(Intent intent) {
        if (intent == null || navController == null) {
            return;
        }
        int destino = intent.getIntExtra(EXTRA_DESTINO, 0);
        if (destino == DESTINO_HISTORICO) {
            binding.bottomNav.setSelectedItemId(R.id.HistoricoFragment);
        } else if (destino == DESTINO_COTACAO) {
            binding.bottomNav.setSelectedItemId(R.id.CotacaoFragment);
        }
        intent.removeExtra(EXTRA_DESTINO);
    }

    /**
     * Consulta a sessão numa Thread. Sem sessão ativa (logout, ou aberta por uma notificação
     * depois de sair), volta ao Login.
     */
    private void verificarSessao() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Usuario usuario = repositorio.buscarUsuarioLogado();
                final Bitmap foto = usuario == null ? null
                        : FotoUtil.carregarFotoPerfil(usuario.fotoCaminho);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (usuario == null) {
                            irParaLogin();
                            return;
                        }
                        usuarioLogado = usuario;
                        avatar = foto;
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setSubtitle(
                                    getString(R.string.formato_saudacao, usuario.nome));
                        }
                        // Recalcula os itens do menu da Toolbar (avatar atualizado)
                        invalidateOptionsMenu();
                    }
                });
            }
        }).start();
    }

    private void irParaLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Android 13+: explica para que servem as notificações e pede a permissão uma única vez.
     * Se o usuário negar, o app segue funcionando, só sem os avisos.
     */
    private void pedirPermissaoNotificacoes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || !Notificacoes.precisaPedirPermissao(this)
                || Preferencias.getNotificacaoPedida(this)) {
            return;
        }
        Preferencias.setNotificacaoPedida(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialogo_notificacao_titulo)
                .setMessage(R.string.dialogo_notificacao_mensagem)
                .setPositiveButton(R.string.btn_permitir, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int qual) {
                        notificacaoLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    }
                })
                .setNegativeButton(R.string.btn_agora_nao, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int qual) {
                        Toast.makeText(MainActivity.this, R.string.msg_notificacoes_negadas,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Infla o menu da Toolbar
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem itemAvatar = menu.findItem(R.id.action_avatar);
        itemAvatar.setVisible(usuarioLogado != null);

        // Miniatura do usuário na Toolbar (actionLayout do item avatar)
        View avatarView = itemAvatar.getActionView();
        if (avatarView != null) {
            ImageView imgAvatar = avatarView.findViewById(R.id.imgAvatarToolbar);
            imgAvatar.setClipToOutline(true); // recorta no círculo da moldura
            if (avatar != null) {
                imgAvatar.setImageBitmap(avatar);
            } else {
                imgAvatar.setImageResource(R.drawable.ic_avatar_padrao);
            }
            // Itens com actionLayout não chamam onOptionsItemSelected sozinhos
            avatarView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    abrirEdicaoPerfil();
                }
            });
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            abrirConfiguracoes();
            return true;
        } else if (id == R.id.action_avatar || id == R.id.action_editar_perfil) {
            abrirEdicaoPerfil();
            return true;
        } else if (id == R.id.action_logout) {
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Abre a CadastroActivity em modo edição com o id do usuário logado. */
    private void abrirEdicaoPerfil() {
        if (usuarioLogado == null) {
            return;
        }
        Intent intent = new Intent(this, CadastroActivity.class);
        intent.putExtra(CadastroActivity.EXTRA_ID_USUARIO, usuarioLogado.id);
        startActivity(intent);
    }

    /** Encerra a sessão no banco (numa Thread) e volta ao Login. */
    private void logout() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                repositorio.logout();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        irParaLogin();
                    }
                });
            }
        }).start();
    }

    /** Configurações > Acessibilidade: tema, tamanho do texto e fala do resultado. */
    private void abrirConfiguracoes() {
        View corpo = LayoutInflater.from(this)
                .inflate(R.layout.dialog_configuracoes, null, false);

        final RadioGroup grupoTema = corpo.findViewById(R.id.grupoTema);
        final RadioGroup grupoTexto = corpo.findViewById(R.id.grupoTexto);
        final CheckBox checkFala = corpo.findViewById(R.id.checkFala);

        // Marca no diálogo o que já está valendo
        grupoTema.check(Preferencias.getTema(this) == Preferencias.TEMA_ESCURO
                ? R.id.temaEscuro
                : R.id.temaClaro);

        switch (Preferencias.getOpcaoTexto(this)) {
            case Preferencias.TEXTO_GRANDE:
                grupoTexto.check(R.id.textoGrande);
                break;
            case Preferencias.TEXTO_MUITO_GRANDE:
                grupoTexto.check(R.id.textoMuitoGrande);
                break;
            default:
                grupoTexto.check(R.id.textoPadrao);
                break;
        }
        checkFala.setChecked(Preferencias.getFalarResultado(this));

        new AlertDialog.Builder(this)
                .setTitle(R.string.action_settings)
                .setView(corpo)
                .setNegativeButton(R.string.config_cancelar, null)
                .setPositiveButton(R.string.config_aplicar, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int qual) {
                        int tema = grupoTema.getCheckedRadioButtonId() == R.id.temaEscuro
                                ? Preferencias.TEMA_ESCURO
                                : Preferencias.TEMA_CLARO;

                        int texto;
                        int marcado = grupoTexto.getCheckedRadioButtonId();
                        if (marcado == R.id.textoGrande) {
                            texto = Preferencias.TEXTO_GRANDE;
                        } else if (marcado == R.id.textoMuitoGrande) {
                            texto = Preferencias.TEXTO_MUITO_GRANDE;
                        } else {
                            texto = Preferencias.TEXTO_PADRAO;
                        }

                        Preferencias.setTema(MainActivity.this, tema);
                        Preferencias.setOpcaoTexto(MainActivity.this, texto);
                        Preferencias.setFalarResultado(MainActivity.this, checkFala.isChecked());

                        // Recria a tela para o tema e o texto entrarem em vigor.
                        AppCompatDelegate.setDefaultNightMode(
                                Preferencias.getModoNoturno(MainActivity.this));
                        recreate();
                    }
                })
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            return NavigationUI.navigateUp(navController, appBarConfiguration)
                    || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}
