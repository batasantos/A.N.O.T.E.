package br.unesp.anote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TriggerNormalizeTest {

    @Test
    fun ignoraMaiusculasEEspacosNasPontas() {
        assertEquals(SecurePrefs.normalize("receita de bolo"), SecurePrefs.normalize("  Receita De Bolo  "))
    }

    @Test
    fun reticenciasDoTecladoValemTresPontos() {
        assertEquals("...", SecurePrefs.normalize("…"))
        assertEquals(SecurePrefs.normalize("..."), SecurePrefs.normalize(" … "))
    }

    @Test
    fun titulosDiferentesNaoSaoConfundidos() {
        assertNotEquals(SecurePrefs.normalize("..."), SecurePrefs.normalize("...."))
        assertNotEquals(SecurePrefs.normalize("lista"), SecurePrefs.normalize("lista 2"))
    }

    @Test
    fun tituloPadraoENormalizadoParaTresPontos() {
        assertEquals(SecurePrefs.DEFAULT_TRIGGER, SecurePrefs.normalize(SecurePrefs.DEFAULT_TRIGGER))
    }
}
