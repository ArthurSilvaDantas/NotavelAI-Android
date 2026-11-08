package com.example.notavelai.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.notavelai.R;
import com.example.notavelai.database.entity.Identificacao;
import com.example.notavelai.util.Formato;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter personalizado da ListView do histórico (BaseAdapter).
 * Cada linha mostra o valor da cédula, a data e a confiança do reconhecimento.
 */
public class HistoricoAdapter extends BaseAdapter {

    private final Context contexto;
    private final List<Identificacao> lista = new ArrayList<>();

    public HistoricoAdapter(Context contexto) {
        this.contexto = contexto;
    }

    /** Troca os itens exibidos (chamado na thread principal, depois da consulta ao banco). */
    public void atualizar(List<Identificacao> novos) {
        lista.clear();
        lista.addAll(novos);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return lista.size();
    }

    @Override
    public Identificacao getItem(int position) {
        return lista.get(position);
    }

    @Override
    public long getItemId(int position) {
        return lista.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(contexto).inflate(R.layout.item_historico, parent, false);
        }
        Identificacao item = getItem(position);
        String valor = Formato.reais(item.valor);
        String data = Formato.dataHora(item.dataHora);
        int confianca = Math.round(item.confianca * 100);
        String origem = contexto.getString("galeria".equals(item.origem)
                ? R.string.origem_galeria : R.string.origem_camera);

        TextView textValor = view.findViewById(R.id.textValorItem);
        TextView textDetalhes = view.findViewById(R.id.textDetalhesItem);
        textValor.setText(valor);
        textDetalhes.setText(data + " · " + origem + " · "
                + contexto.getString(R.string.resultado_confianca, confianca));

        // O TalkBack lê a linha inteira numa frase só, em vez de três pedaços soltos.
        view.setContentDescription(contexto.getString(
                R.string.historico_item_descricao, valor, data, confianca));
        return view;
    }
}
