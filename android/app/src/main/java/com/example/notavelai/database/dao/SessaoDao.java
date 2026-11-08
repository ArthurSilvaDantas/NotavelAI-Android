package com.example.notavelai.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.notavelai.database.entity.Sessao;

@Dao
public interface SessaoDao {

    /**
     * Abre (ou reabre) a sessão do usuário. REPLACE sobrescreve a linha
     * existente, mantendo uma única sessão por usuário (1:1).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void salvar(Sessao sessao);

    /** Logout: encerra qualquer sessão ativa. */
    @Query("UPDATE sessoes SET ativa = 0 WHERE ativa = 1")
    void encerrarSessoesAtivas();
}
