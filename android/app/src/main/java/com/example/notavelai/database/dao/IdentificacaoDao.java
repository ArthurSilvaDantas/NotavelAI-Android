package com.example.notavelai.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.notavelai.database.entity.Identificacao;

import java.util.List;

@Dao
public interface IdentificacaoDao {

    @Insert
    long inserir(Identificacao identificacao);

    /** Histórico do usuário, da identificação mais recente para a mais antiga. */
    @Query("SELECT * FROM identificacoes WHERE usuario_id = :usuarioId ORDER BY data_hora DESC")
    List<Identificacao> listarPorUsuario(long usuarioId);

    /** Soma dos valores identificados pelo usuário, em reais. */
    @Query("SELECT COALESCE(SUM(valor), 0) FROM identificacoes WHERE usuario_id = :usuarioId")
    int somarPorUsuario(long usuarioId);

    /** Exclui um item, conferindo o dono para um usuário não apagar o histórico de outro. */
    @Query("DELETE FROM identificacoes WHERE id = :id AND usuario_id = :usuarioId")
    int excluir(long id, long usuarioId);

    @Query("DELETE FROM identificacoes WHERE usuario_id = :usuarioId")
    int limparPorUsuario(long usuarioId);
}
