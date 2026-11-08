package com.example.notavelai.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

/**
 * Tabela 2 - Sessão, relacionamento 1:1 com Usuario.
 *
 * A chave primária É a própria chave estrangeira usuario_id, o que garante
 * que cada usuário tenha no máximo UMA linha de sessão.
 *
 * Login  -> ativa = true
 * Logout -> ativa = false
 */
@Entity(
        tableName = "sessoes",
        foreignKeys = @ForeignKey(
                entity = Usuario.class,
                parentColumns = "id",
                childColumns = "usuario_id",
                onDelete = ForeignKey.CASCADE))
public class Sessao {

    @PrimaryKey
    @ColumnInfo(name = "usuario_id")
    public long usuarioId;

    public boolean ativa;

    /** Momento do último login, em milissegundos. */
    @ColumnInfo(name = "data_login")
    public long dataLogin;
}
