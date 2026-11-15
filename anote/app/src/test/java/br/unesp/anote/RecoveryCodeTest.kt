package br.unesp.anote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class RecoveryCodeTest {

    @Test
    fun codigoTemTamanhoEAlfabetoCorretos() {
        val code = RecoveryCode.generate()
        assertEquals(RecoveryCode.LENGTH, code.length)
        assertTrue(code.all { it in RecoveryCode.ALPHABET })
    }

    @Test
    fun alfabetoNaoTemCaracteresQueSeConfundem() {
        assertEquals(31, RecoveryCode.ALPHABET.length)
        assertTrue("IL O01".none { it in RecoveryCode.ALPHABET && it != ' ' })
    }

    @Test
    fun formatoAgrupaDeQuatroEmQuatro() {
        assertEquals("ABCD-EFGH-JKMN-PQRS", RecoveryCode.format("ABCDEFGHJKMNPQRS"))
    }

    @Test
    fun normalizaMinusculasEspacosEHifens() {
        assertEquals("ABCDEFGHJKMNPQRS", RecoveryCode.normalize("abcd-efgh jkmn-pqrs"))
    }

    @Test
    fun formatarENormalizarVoltaAoOriginal() {
        val raw = RecoveryCode.generate()
        assertEquals(raw, RecoveryCode.normalize(RecoveryCode.format(raw)))
    }

    @Test
    fun codigosGeradosSaoDiferentes() {
        assertNotEquals(RecoveryCode.generate(), RecoveryCode.generate())
    }

    @Test
    fun mesmaSementeDaMesmoCodigo() {
        assertEquals(RecoveryCode.generate(Random(42)), RecoveryCode.generate(Random(42)))
    }
}
