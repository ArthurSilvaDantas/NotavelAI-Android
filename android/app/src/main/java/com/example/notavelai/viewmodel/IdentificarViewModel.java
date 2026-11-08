package com.example.notavelai.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.notavelai.R;
import com.example.notavelai.database.entity.Usuario;
import com.example.notavelai.ml.ClassificadorCedulas;
import com.example.notavelai.repository.IdentificacaoRepository;
import com.example.notavelai.repository.UsuarioRepository;
import com.example.notavelai.util.FotoUtil;
import com.example.notavelai.util.Formato;
import com.example.notavelai.util.Notificacoes;

import java.io.File;
import java.io.IOException;

/**
 * Estado da tela Identificar, que sobrevive quando a Activity é recriada.
 *
 * Ao voltar da câmera com o celular deitado, o Android gira e recria a tela. Se a análise e o
 * resultado ficassem no fragmento, eles se perderiam no meio do caminho: a notificação chegava,
 * mas a voz não falava e a tela voltava a "Nenhuma cédula analisada". Aqui ficam o modelo, a
 * análise (numa Thread) e o último resultado; o fragmento novo só observa e mostra.
 */
public class IdentificarViewModel extends AndroidViewModel {

    /** O que a tela deve mostrar. Imutável: a Thread cria um novo e publica pelo LiveData. */
    public static class Estado {
        public final boolean analisando;
        public final Bitmap foto;
        public final String frase;
        public final String valor;
        public final String confianca;
        /** Identifica o resultado, para a voz falar cada um uma vez só. */
        public final long id;

        Estado(boolean analisando, Bitmap foto, String frase, String valor, String confianca, long id) {
            this.analisando = analisando;
            this.foto = foto;
            this.frase = frase;
            this.valor = valor;
            this.confianca = confianca;
            this.id = id;
        }
    }

    private final MutableLiveData<Estado> estado = new MutableLiveData<>();
    private final UsuarioRepository usuarioRepository;
    private final IdentificacaoRepository identificacaoRepository;

    /** Carregado uma vez, numa Thread, e reaproveitado em todas as fotos. */
    private ClassificadorCedulas classificador;
    private final Object travaClassificador = new Object();
    /** true depois do onCleared: uma Thread atrasada não deve recriar o modelo. */
    private boolean encerrado = false;
    private long proximoId = 1;

    /** Último resultado já falado; sobrevive à recriação do fragmento. */
    private long ultimoFalado = 0;

    public IdentificarViewModel(@NonNull Application application) {
        super(application);
        usuarioRepository = new UsuarioRepository(application);
        identificacaoRepository = new IdentificacaoRepository(application);
        carregarModeloEmSegundoPlano();
    }

    public LiveData<Estado> getEstado() {
        return estado;
    }

    /** true na primeira vez que a tela pede para falar este resultado. */
    public boolean marcarComoFalado(Estado e) {
        if (e.id == 0 || e.id == ultimoFalado) {
            return false;
        }
        ultimoFalado = e.id;
        return true;
    }

    private void carregarModeloEmSegundoPlano() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                obterClassificador();
            }
        }).start();
    }

    /** Devolve o classificador, carregando-o na primeira vez. Chamado só fora da UI. */
    private ClassificadorCedulas obterClassificador() {
        synchronized (travaClassificador) {
            if (encerrado) {
                return null;
            }
            if (classificador == null) {
                try {
                    classificador = new ClassificadorCedulas(getApplication());
                } catch (IOException | RuntimeException e) {
                    return null;
                }
            }
            return classificador;
        }
    }

    /**
     * Lê a foto, roda o modelo, registra no histórico e notifica - tudo numa Thread. O resultado
     * volta para a tela pelo LiveData (postValue), mesmo que ela tenha sido recriada no caminho.
     */
    public void analisar(final Uri uri, final File arquivoTemporario, final String origem) {
        final Context contexto = getApplication();
        Estado anterior = estado.getValue();
        estado.setValue(new Estado(true, anterior != null ? anterior.foto : null,
                contexto.getString(R.string.resultado_analisando), "", "", 0));

        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap foto = null;
                ClassificadorCedulas.Resultado resultado = null;
                boolean modeloIndisponivel = false;
                String frase;
                String valor = "";
                String confianca = "";

                try {
                    foto = FotoUtil.carregarParaModelo(contexto, uri);
                    // Com a trava, o modelo não pode ser fechado (onCleared) no meio da análise.
                    synchronized (travaClassificador) {
                        ClassificadorCedulas modelo = obterClassificador();
                        if (modelo == null) {
                            modeloIndisponivel = true;
                        } else {
                            resultado = modelo.classificar(foto);
                        }
                    }
                } catch (IOException | SecurityException e) {
                    foto = null;
                } finally {
                    // A foto da cédula não é guardada: só o valor vai para o histórico. Na versão
                    // de desenvolvimento ela fica na pasta do app, para analisar erros do modelo.
                    boolean depuracao = (contexto.getApplicationInfo().flags
                            & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                    if (arquivoTemporario != null && !depuracao) {
                        arquivoTemporario.delete();
                    }
                }

                if (modeloIndisponivel) {
                    frase = contexto.getString(R.string.resultado_erro_modelo);
                } else if (resultado == null) {
                    frase = contexto.getString(R.string.msg_erro_imagem);
                } else if (resultado.confiavel()) {
                    valor = Formato.reais(resultado.valor());
                    frase = contexto.getString(R.string.resultado_fala, valor);
                    confianca = contexto.getString(R.string.resultado_confianca,
                            Math.round(resultado.confianca * 100));
                    Usuario usuario = usuarioRepository.buscarUsuarioLogado();
                    if (usuario != null) {
                        int total = identificacaoRepository.registrar(usuario.id,
                                resultado.valor(), resultado.confianca, origem);
                        Notificacoes.notificarIdentificacao(contexto, valor, Formato.reais(total));
                    }
                } else {
                    frase = contexto.getString(resultado.semCedula()
                            ? R.string.resultado_sem_cedula : R.string.resultado_incerto);
                }

                long id;
                synchronized (travaClassificador) {
                    id = proximoId++;
                }
                estado.postValue(new Estado(false, foto, frase, valor, confianca, id));
            }
        }).start();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        synchronized (travaClassificador) {
            encerrado = true;
            if (classificador != null) {
                classificador.close();
                classificador = null;
            }
        }
    }
}
