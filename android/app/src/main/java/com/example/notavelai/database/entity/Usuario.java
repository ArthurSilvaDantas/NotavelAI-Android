package com.example.notavelai.database.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Tabela 1 - Usuários do aplicativo.
 *
 * - A senha NUNCA é gravada em texto puro: guardamos só o hash BCrypt. O BCrypt gera um salt
 *   aleatório para cada senha e o guarda dentro do próprio hash, por isso não há coluna de salt.
 * - A foto de perfil fica num arquivo da pasta privada do app; o banco guarda só o caminho.
 * - O e-mail é único (índice unique), pois é usado no login.
 */
@Entity(tableName = "usuarios",
        indices = @Index(value = "email", unique = true))
public class Usuario {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String nome = "";

    @NonNull
    public String email = "";

    @NonNull
    @ColumnInfo(name = "senha_hash")
    public String senhaHash = "";

    /** Caminho absoluto da foto de perfil (null = sem foto). */
    @ColumnInfo(name = "foto_caminho")
    public String fotoCaminho;
}
