package com.example.notavelai.view;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.notavelai.R;
import com.example.notavelai.database.entity.Usuario;
import com.example.notavelai.databinding.FragmentCotacaoBinding;
import com.example.notavelai.rede.CotacaoCliente;
import com.example.notavelai.repository.IdentificacaoRepository;
import com.example.notavelai.repository.UsuarioRepository;
import com.example.notavelai.util.Formato;
import com.example.notavelai.util.Notificacoes;

/**
 * Fragmento 3 - Cotação: recurso obrigatório "Requisições HTTP (Consumo de API)".
 *
 * O usuário escolhe a moeda num Spinner e o app consulta a AwesomeAPI com
 * HttpURLConnection numa Thread separada; o resultado volta com runOnUiThread.
 * Mostra a cotação e quanto vale o total identificado no histórico nessa moeda.
 */
public class CotacaoFragment extends Fragment {

    private FragmentCotacaoBinding binding;
    private String[] codigos;
    private UsuarioRepository usuarioRepository;
    private IdentificacaoRepository identificacaoRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCotacaoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context contexto = requireContext().getApplicationContext();
        usuarioRepository = new UsuarioRepository(contexto);
        identificacaoRepository = new IdentificacaoRepository(contexto);

        // Para popular o Spinner, utilizamos um ArrayAdapter com a matriz de strings
        codigos = getResources().getStringArray(R.array.moedas_codigos);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.moedas_nomes, R.layout.item_spinner);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        binding.spinnerMoeda.setAdapter(adapter);

        binding.buttonConsultar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                consultar();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void consultar() {
        final String codigo = codigos[binding.spinnerMoeda.getSelectedItemPosition()];
        final Context contexto = requireContext().getApplicationContext();

        binding.buttonConsultar.setEnabled(false);
        binding.progressCotacao.setVisibility(View.VISIBLE);
        binding.textCotacao.setText(R.string.cotacao_consultando);
        binding.textConversao.setText("");
        binding.textAtualizacao.setText("");

        // A requisição pode demorar. Por isso, ela será executada fora da thread principal.
        new Thread(new Runnable() {
            @Override
            public void run() {
                CotacaoCliente.Cotacao cotacao = null;
                int erro = 0;
                try {
                    cotacao = CotacaoCliente.consultar(contexto, codigo);
                } catch (CotacaoCliente.ErroCotacao e) {
                    erro = e.motivo;
                }
                Usuario usuario = usuarioRepository.buscarUsuarioLogado();
                final int total = usuario == null ? 0 : identificacaoRepository.total(usuario.id);

                final CotacaoCliente.Cotacao cotacaoFinal = cotacao;
                final int erroFinal = erro;
                if (getActivity() == null) {
                    return;
                }
                // Atualizando a interface na thread principal
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (binding == null) {
                            return;
                        }
                        binding.buttonConsultar.setEnabled(true);
                        binding.progressCotacao.setVisibility(View.GONE);
                        if (cotacaoFinal != null) {
                            mostrarCotacao(contexto, cotacaoFinal, total);
                        } else {
                            mostrarErro(erroFinal);
                        }
                    }
                });
            }
        }).start();
    }

    private void mostrarCotacao(Context contexto, CotacaoCliente.Cotacao cotacao, int total) {
        String valorUnidade = Formato.reais(cotacao.valorEmReais);
        binding.textCotacao.setText(getString(R.string.cotacao_resultado,
                cotacao.codigo, valorUnidade));
        binding.textConversao.setText(getString(R.string.cotacao_conversao,
                Formato.reais(total),
                Formato.moeda(total / cotacao.valorEmReais, cotacao.codigo)));
        binding.textAtualizacao.setText(cotacao.doCache
                ? getString(R.string.cotacao_em_cache)
                : getString(R.string.cotacao_atualizada_em, Formato.dataDaApi(cotacao.atualizadaEm)));

        // Só notifica quando houve consulta de verdade, não quando veio do cache.
        if (!cotacao.doCache) {
            Notificacoes.notificarCotacao(contexto, cotacao.codigo, valorUnidade);
        }
    }

    private void mostrarErro(int motivo) {
        int mensagem;
        if (motivo == CotacaoCliente.ErroCotacao.SEM_INTERNET) {
            mensagem = R.string.cotacao_sem_internet;
        } else if (motivo == CotacaoCliente.ErroCotacao.SERVIDOR) {
            mensagem = R.string.cotacao_erro_servidor;
        } else {
            mensagem = R.string.cotacao_erro_rede;
        }
        binding.textCotacao.setText(mensagem);
    }
}
