package com.example.notavelai.repository;

import android.content.Context;

import com.example.notavelai.database.AppDatabase;
import com.example.notavelai.database.dao.IdentificacaoDao;
import com.example.notavelai.database.entity.Identificacao;

import java.util.List;

/**
 * Acesso ao histórico de cédulas identificadas (tabela identificacoes).
 *
 * Como no UsuarioRepository, todos os métodos devem rodar fora da thread principal.
 */
public class IdentificacaoRepository {

    private final IdentificacaoDao dao;

    public IdentificacaoRepository(Context contexto) {
        dao = AppDatabase.getInstance(contexto).identificacaoDao();
    }

    /** Registra uma cédula identificada e devolve o novo total do usuário, em reais. */
    public int registrar(long usuarioId, int valor, float confianca, String origem) {
        Identificacao identificacao = new Identificacao();
        identificacao.usuarioId = usuarioId;
        identificacao.valor = valor;
        identificacao.confianca = confianca;
        identificacao.origem = origem;
        identificacao.dataHora = System.currentTimeMillis();
        dao.inserir(identificacao);
        return dao.somarPorUsuario(usuarioId);
    }

    public List<Identificacao> listar(long usuarioId) {
        return dao.listarPorUsuario(usuarioId);
    }

    public int total(long usuarioId) {
        return dao.somarPorUsuario(usuarioId);
    }

    public boolean excluir(long id, long usuarioId) {
        return dao.excluir(id, usuarioId) > 0;
    }

    public void limpar(long usuarioId) {
        dao.limparPorUsuario(usuarioId);
    }
}
