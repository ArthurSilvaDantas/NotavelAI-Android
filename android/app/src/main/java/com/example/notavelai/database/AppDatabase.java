package com.example.notavelai.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.notavelai.database.dao.IdentificacaoDao;
import com.example.notavelai.database.dao.SessaoDao;
import com.example.notavelai.database.dao.UsuarioDao;
import com.example.notavelai.database.entity.Identificacao;
import com.example.notavelai.database.entity.Sessao;
import com.example.notavelai.database.entity.Usuario;

/**
 * Banco de dados Room do aplicativo (3 tabelas):
 * usuarios, sessoes (1:1 com usuarios) e identificacoes (1:N com usuarios).
 */
@Database(
        entities = {Usuario.class, Sessao.class, Identificacao.class},
        version = 1,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String NOME_BANCO = "notavelai.db";

    private static volatile AppDatabase instancia;

    public abstract UsuarioDao usuarioDao();
    public abstract SessaoDao sessaoDao();
    public abstract IdentificacaoDao identificacaoDao();

    /**
     * Instância única (singleton) do banco para todo o app.
     *
     * O banco é criado sem allowMainThreadQueries(): toda consulta roda numa Thread
     * separada e o resultado volta à tela com runOnUiThread ou Handler. Assim a interface
     * nunca trava esperando o banco e, se alguma consulta fosse feita por engano na thread
     * principal, o Room lançaria uma exceção.
     */
    public static AppDatabase getInstance(Context contexto) {
        if (instancia == null) {
            synchronized (AppDatabase.class) {
                if (instancia == null) {
                    instancia = Room.databaseBuilder(contexto.getApplicationContext(),
                                    AppDatabase.class, NOME_BANCO)
                            .build();
                }
            }
        }
        return instancia;
    }
}
