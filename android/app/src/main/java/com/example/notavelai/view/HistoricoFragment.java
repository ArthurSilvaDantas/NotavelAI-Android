package com.example.notavelai.view;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.notavelai.R;
import com.example.notavelai.adapter.HistoricoAdapter;
import com.example.notavelai.database.entity.Identificacao;
import com.example.notavelai.database.entity.Usuario;
import com.example.notavelai.databinding.FragmentHistoricoBinding;
import com.example.notavelai.repository.IdentificacaoRepository;
import com.example.notavelai.repository.UsuarioRepository;
import com.example.notavelai.util.Formato;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragmento 2 - Histórico: ListView com BaseAdapter das cédulas identificadas
 * pelo usuário logado, com a quantidade (strings de plurais) e o total em reais.
 *
 * Um toque longo num item abre o menu de contexto para excluí-lo.
 * Toda leitura e escrita no banco roda numa Thread, com runOnUiThread para a tela.
 */
public class HistoricoFragment extends Fragment {

    private FragmentHistoricoBinding binding;
    private HistoricoAdapter adapter;
    private UsuarioRepository usuarioRepository;
    private IdentificacaoRepository identificacaoRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoricoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context contexto = requireContext().getApplicationContext();
        usuarioRepository = new UsuarioRepository(contexto);
        identificacaoRepository = new IdentificacaoRepository(contexto);

        //chamando o listView e inserindo o adapter personalizado
        adapter = new HistoricoAdapter(requireContext());
        binding.listHistorico.setAdapter(adapter);
        binding.listHistorico.setEmptyView(binding.textVazio);
        registerForContextMenu(binding.listHistorico);

        binding.buttonLimpar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmarLimpeza();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        carregar(); // volta da aba Identificar com cédulas novas
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Lê o histórico e o total do usuário logado numa Thread. */
    private void carregar() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Usuario usuario = usuarioRepository.buscarUsuarioLogado();
                final List<Identificacao> itens = usuario == null
                        ? new ArrayList<Identificacao>()
                        : identificacaoRepository.listar(usuario.id);
                final int total = usuario == null ? 0 : identificacaoRepository.total(usuario.id);

                if (getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (binding == null) {
                            return;
                        }
                        adapter.atualizar(itens);
                        binding.textQuantidade.setText(getResources().getQuantityString(
                                R.plurals.historico_quantidade, itens.size(), itens.size()));
                        binding.textTotal.setText(getString(R.string.historico_total,
                                Formato.reais(total)));
                        binding.buttonLimpar.setEnabled(!itens.isEmpty());
                    }
                });
            }
        }).start();
    }

    // Menu de contexto (toque longo num item)

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v,
                                    @Nullable ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        requireActivity().getMenuInflater().inflate(R.menu.menu_contexto_historico, menu);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_excluir_item
                && item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo info =
                    (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            excluir(info.id);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void excluir(final long idItem) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Usuario usuario = usuarioRepository.buscarUsuarioLogado();
                if (usuario != null) {
                    identificacaoRepository.excluir(idItem, usuario.id);
                }
                if (getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(), R.string.msg_item_excluido,
                                Toast.LENGTH_SHORT).show();
                        binding.listHistorico.announceForAccessibility(
                                getString(R.string.msg_item_excluido));
                        carregar();
                    }
                });
            }
        }).start();
    }

    // Limpar tudo

    private void confirmarLimpeza() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialogo_limpar_titulo)
                .setMessage(R.string.dialogo_limpar_mensagem)
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.btn_limpar, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int qual) {
                        limpar();
                    }
                })
                .show();
    }

    private void limpar() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Usuario usuario = usuarioRepository.buscarUsuarioLogado();
                if (usuario != null) {
                    identificacaoRepository.limpar(usuario.id);
                }
                if (getActivity() == null) {
                    return;
                }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(), R.string.msg_historico_limpo,
                                Toast.LENGTH_SHORT).show();
                        carregar();
                    }
                });
            }
        }).start();
    }
}
