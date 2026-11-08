package com.example.notavelai.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Hash de senhas com BCrypt.
 *
 * - No cadastro e na troca de senha: BCrypt.gensalt() sorteia um salt novo e o hashpw()
 *   devolve um texto que já contém o salt e o custo. Só esse texto vai para o banco.
 * - No login: checkpw() recalcula o hash da senha digitada com o salt guardado e compara.
 *
 * O BCrypt é lento de propósito (custo ajustável), o que dificulta tentativas de adivinhar
 * a senha. Por isso estas chamadas rodam sempre numa Thread separada, nunca na principal.
 */
public final class SenhaUtil {

    /** 2^10 rodadas: o padrão do BCrypt, cerca de 0,1 s num celular atual. */
    private static final int CUSTO = 10;

    private SenhaUtil() {
        // classe utilitária
    }

    public static String gerarHash(String senha) {
        return BCrypt.hashpw(senha, BCrypt.gensalt(CUSTO));
    }

    /** Confere a senha digitada contra o hash salvo no banco. */
    public static boolean conferir(String senhaDigitada, String hashSalvo) {
        try {
            return BCrypt.checkpw(senhaDigitada, hashSalvo);
        } catch (IllegalArgumentException e) {
            // Hash corrompido ou em outro formato: trata como senha incorreta, sem travar o app.
            return false;
        }
    }
}
