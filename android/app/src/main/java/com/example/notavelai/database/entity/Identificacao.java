package com.example.notavelai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Tabela 3 - Cédulas identificadas, relacionamento 1:N com Usuario.
 *
 * Cada usuário vê apenas o próprio histórico. Ao excluir o usuário, o histórico dele
 * também é apagado (CASCADE).
 */
@Entity(
        tableName = "identificacoes",
        foreignKeys = @ForeignKey(
                entity = Usuario.class,
                parentColumns = "id",
                childColumns = "usuario_id",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("usuario_id"))
public class Identificacao {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "usuario_id")
    public long usuarioId;

    /** Valor da cédula em reais: 2, 5, 10, 20, 50, 100 ou 200. */
    public int valor;

    /** Probabilidade da classe escolhida pelo modelo, de 0 a 1. */
    public float confianca;

    /** Momento da identificação, em milissegundos. */
    @ColumnInfo(name = "data_hora")
    public long dataHora;

    /** "camera" ou "galeria". */
    @NonNull
    public String origem = "";
}
