package com.example.notavelai.repository;

import android.content.Context;

import com.example.notavelai.database.AppDatabase;
import com.example.notavelai.database.dao.SessaoDao;
import com.example.notavelai.database.dao.UsuarioDao;
import com.example.notavelai.database.entity.Sessao;
import com.example.notavelai.database.entity.Usuario;
import com.example.notavelai.util.SenhaUtil;

/**
 * Acesso aos dados de usuários e sessão (tabelas usuarios e sessoes).
 *
 * Todos os métodos acessam o banco (e alguns calculam o hash BCrypt), então devem ser
 * chamados dentro de uma Thread separada - nunca na thread principal.
 */
public class UsuarioRepository {

    private final AppDatabase db;
    private final UsuarioDao usuarioDao;
    private final SessaoDao sessaoDao;

    public UsuarioRepository(Context contexto) {
        db = AppDatabase.getInstance(contexto);
        usuarioDao = db.usuarioDao();
        sessaoDao = db.sessaoDao();
    }

    // Sessão

    /** Usuário com sessão ativa, ou null se ninguém estiver logado. */
    public Usuario buscarUsuarioLogado() {
        return usuarioDao.buscarUsuarioLogado();
    }

    /**
     * Valida e-mail e senha. A senha digitada é conferida com o BCrypt contra o hash
     * salvo. Se estiver correta, abre a sessão.
     *
     * @return o usuário autenticado, ou null se os dados forem inválidos.
     */
    public Usuario login(String email, String senha) {
        Usuario usuario = usuarioDao.buscarPorEmail(email);
        if (usuario == null || !SenhaUtil.conferir(senha, usuario.senhaHash)) {
            return null;
        }
        iniciarSessao(usuario.id);
        return usuario;
    }

    /** Logout: encerra a sessão ativa no banco. */
    public void logout() {
        sessaoDao.encerrarSessoesAtivas();
    }

    private void iniciarSessao(final long usuarioId) {
        // Numa transação: nunca fica mais de uma sessão ativa ao mesmo tempo.
        db.runInTransaction(new Runnable() {
            @Override
            public void run() {
                sessaoDao.encerrarSessoesAtivas();
                Sessao sessao = new Sessao();
                sessao.usuarioId = usuarioId;
                sessao.ativa = true;
                sessao.dataLogin = System.currentTimeMillis();
                sessaoDao.salvar(sessao);
            }
        });
    }

    // Cadastro/perfil

    public Usuario buscarPorId(long id) {
        return usuarioDao.buscarPorId(id);
    }

    /**
     * Verifica se o e-mail já pertence a OUTRO usuário.
     * @param idIgnorado id do próprio usuário na edição (0 no cadastro).
     */
    public boolean emailEmUso(String email, long idIgnorado) {
        Usuario existente = usuarioDao.buscarPorEmail(email);
        return existente != null && existente.id != idIgnorado;
    }

    /** Confere a senha atual antes de permitir alterações no perfil. */
    public boolean conferirSenha(Usuario usuario, String senha) {
        return SenhaUtil.conferir(senha, usuario.senhaHash);
    }

    /** Cadastra um novo usuário, gravando só o hash BCrypt da senha. */
    public long cadastrar(String nome, String email, String senha, String fotoCaminho) {
        Usuario usuario = new Usuario();
        usuario.nome = nome;
        usuario.email = email;
        usuario.fotoCaminho = fotoCaminho;
        usuario.senhaHash = SenhaUtil.gerarHash(senha);
        return usuarioDao.inserir(usuario);
    }

    /**
     * Atualiza o perfil. A senha só é trocada se uma nova for informada (novaSenha vazia =
     * mantém a atual); nesse caso é gerado um hash novo, com salt novo.
     */
    public void atualizarPerfil(Usuario usuario, String novaSenha) {
        if (novaSenha != null && !novaSenha.isEmpty()) {
            usuario.senhaHash = SenhaUtil.gerarHash(novaSenha);
        }
        usuarioDao.atualizar(usuario);
    }
}
