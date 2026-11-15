package br.unesp.anote

import java.security.SecureRandom
import java.util.Random

/**
 * Codigo de recuperacao da conta: 16 caracteres (alfabeto sem I, L, O, 0 e 1, que se confundem),
 * mostrado uma unica vez e guardado so como hash. Quem esquece a senha usa o codigo para criar uma
 * nova sem perder as notas. Logica pura (sem Android) para poder ser testada na JVM.
 */
object RecoveryCode {

    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 16

    fun generate(random: Random = SecureRandom()): String = buildString {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** XXXX-XXXX-XXXX-XXXX, para ler e anotar. */
    fun format(raw: String): String = raw.chunked(4).joinToString("-")

    /** Aceita minusculas, espacos e hifens: so os caracteres do alfabeto contam. */
    fun normalize(input: String): String = input.uppercase().filter { it in ALPHABET }
}
