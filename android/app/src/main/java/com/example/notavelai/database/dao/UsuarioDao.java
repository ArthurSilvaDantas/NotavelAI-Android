package com.example.notavelai.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.notavelai.database.entity.Usuario;

@Dao
public interface UsuarioDao {

    /** Retorna o id gerado para o novo usuário. */
    @Insert
    long inserir(Usuario usuario);

    @Update
    void atualizar(Usuario usuario);

    /**
     * Busca pelo e-mail para o login (a senha é conferida depois, com o BCrypt)
     * e para impedir dois cadastros com o mesmo e-mail.
     */
    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    Usuario buscarPorEmail(String email);

    @Query("SELECT * FROM usuarios WHERE id = :id")
    Usuario buscarPorId(long id);

    /** Usuário com sessão ativa, ou null se ninguém estiver logado. */
    @Query("SELECT * FROM usuarios WHERE id IN "
            + "(SELECT usuario_id FROM sessoes WHERE ativa = 1) LIMIT 1")
    Usuario buscarUsuarioLogado();
}
