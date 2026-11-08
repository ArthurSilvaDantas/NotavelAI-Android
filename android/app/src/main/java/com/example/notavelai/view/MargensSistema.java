package com.example.notavelai.view;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Margens das barras do sistema e do teclado (EdgeToEdge): o conteúdo não fica escondido
 * atrás da barra de status, da barra de navegação nem do teclado.
 */
final class MargensSistema {

    private MargensSistema() {
        // classe utilitária
    }

    static void aplicar(View raiz) {
        ViewCompat.setOnApplyWindowInsetsListener(raiz, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets teclado = insets.getInsets(WindowInsetsCompat.Type.ime());
                v.setPadding(barras.left, barras.top, barras.right,
                        Math.max(barras.bottom, teclado.bottom));
                return insets;
            }
        });
    }
}
