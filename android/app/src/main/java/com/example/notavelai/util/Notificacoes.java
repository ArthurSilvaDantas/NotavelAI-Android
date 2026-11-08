package com.example.notavelai.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.notavelai.R;
import com.example.notavelai.view.MainActivity;

/**
 * Notificações do aplicativo.
 *
 * Dois canais, para o usuário poder desligar um tipo de aviso sem perder o outro:
 * - identificações: cédula registrada no histórico, com o total atualizado;
 * - cotação: cotação consultada pronta.
 *
 * Sem permissão (Android 13+) ou com as notificações desligadas nas configurações do
 * aparelho, nada é emitido e o app continua funcionando normalmente.
 */
public final class Notificacoes {

    public static final String CANAL_IDENTIFICACOES = "identificacoes";
    public static final String CANAL_COTACAO = "cotacao";

    private static final int ID_IDENTIFICACAO = 1;
    private static final int ID_COTACAO = 2;

    private Notificacoes() {
        // classe utilitária
    }

    /** Cria os canais (Android 8+). Pode ser chamado várias vezes: o sistema ignora repetições. */
    public static void criarCanais(Context contexto) {
        NotificationChannel identificacoes = new NotificationChannel(CANAL_IDENTIFICACOES,
                contexto.getString(R.string.canal_identificacoes_nome),
                NotificationManager.IMPORTANCE_DEFAULT);
        identificacoes.setDescription(contexto.getString(R.string.canal_identificacoes_descricao));

        NotificationChannel cotacao = new NotificationChannel(CANAL_COTACAO,
                contexto.getString(R.string.canal_cotacao_nome),
                NotificationManager.IMPORTANCE_LOW);
        cotacao.setDescription(contexto.getString(R.string.canal_cotacao_descricao));

        NotificationManager manager = contexto.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(identificacoes);
            manager.createNotificationChannel(cotacao);
        }
    }

    /** O Android 13 exige permissão em tempo de execução para notificar. */
    public static boolean precisaPedirPermissao(Context contexto) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(contexto,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    /** Permissão concedida e notificações do app ligadas nas configurações do aparelho. */
    public static boolean podeNotificar(Context contexto) {
        return !precisaPedirPermissao(contexto)
                && NotificationManagerCompat.from(contexto).areNotificationsEnabled();
    }

    public static void notificarIdentificacao(Context contexto, String valor, String total) {
        notificar(contexto, CANAL_IDENTIFICACOES, ID_IDENTIFICACAO,
                contexto.getString(R.string.notif_identificacao_titulo, valor),
                contexto.getString(R.string.notif_identificacao_texto, total),
                MainActivity.DESTINO_HISTORICO);
    }

    public static void notificarCotacao(Context contexto, String codigo, String valor) {
        notificar(contexto, CANAL_COTACAO, ID_COTACAO,
                contexto.getString(R.string.notif_cotacao_titulo, codigo),
                contexto.getString(R.string.notif_cotacao_texto, codigo, valor),
                MainActivity.DESTINO_COTACAO);
    }

    private static void notificar(Context contexto, String canal, int id, String titulo,
                                  String texto, int destino) {
        if (!podeNotificar(contexto)) {
            return;
        }
        // Ação da notificação: abre a tela principal direto na aba relacionada ao aviso.
        Intent intent = new Intent(contexto, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_DESTINO, destino);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendente = PendingIntent.getActivity(contexto, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(contexto, canal)
                .setSmallIcon(R.drawable.ic_notificacao)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setContentIntent(pendente)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(contexto).notify(id, builder.build());
        } catch (SecurityException e) {
            // A permissão pode ser revogada entre a verificação e o envio; nesse caso só não avisa.
        }
    }
}
