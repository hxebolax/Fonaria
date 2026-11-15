package com.xebolax.fonaria

import com.xebolax.fonaria.actualizaciones.Actualizador
import com.xebolax.fonaria.texto.Diccionario
import com.xebolax.fonaria.texto.Entrada
import com.xebolax.fonaria.texto.Preparador
import com.xebolax.fonaria.texto.Simbolos
import com.xebolax.fonaria.texto.TipoEntrada
import com.xebolax.fonaria.texto.Trozo
import com.xebolax.fonaria.texto.Trozos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del procesado de texto, sin Android (gradle testDebugUnitTest). */
class TextoTest {

	@Test
	fun frasesSeparadasPorPuntos() {
		assertEquals(listOf("Hola.", "¿Qué tal?", "Bien!"), Trozos.frases("Hola. ¿Qué tal? Bien!"))
	}

	@Test
	fun abreviaturasEInicialesNoCortan() {
		assertEquals(1, Trozos.frases("El Sr. García vive en EE.UU. con J. R. R. Tolkien").size)
		assertEquals(1, Trozos.frases("Pesa 3.14 kilos y cuesta 1.500 euros").size)
	}

	@Test
	fun pausasEntreFrasesYParrafos() {
		val t = Trozos.dividir("Uno. Dos.\nTres.", pausaFrase = 100, pausaParrafo = 300)
		assertEquals(
			listOf(Trozo.Habla("Uno."), Trozo.Pausa(100), Trozo.Habla("Dos."), Trozo.Pausa(300), Trozo.Habla("Tres.")),
			t,
		)
	}

	@Test
	fun respuestaRapidaParteLaPrimeraFraseLarga() {
		val larga = "Esta es una frase bastante larga para la prueba, que sigue y sigue durante muchas palabras más."
		val t = Trozos.dividir(larga, 0, 0, rapida = true).filterIsInstance<Trozo.Habla>()
		assertEquals(2, t.size)
		assertTrue(t[0].texto.endsWith(","))
	}

	@Test
	fun palabraCompletaNoCambiaDentroDeOtras() {
		val e = Entrada(patron = "sol", reemplazo = "astro", tipo = TipoEntrada.PALABRA)
		assertEquals("el astro y el soldado", Diccionario.reemplazar(e.patronCompilado()!!, "el sol y el soldado", e))
	}

	@Test
	fun mayusculasYTildes() {
		val e = Entrada(patron = "ONCE", reemplazo = "la Once", mayusculas = true)
		assertEquals("la Once tiene once", Diccionario.reemplazar(e.patronCompilado()!!, "ONCE tiene once", e))
		val t = Entrada(patron = "camión", reemplazo = "camioneta")
		assertEquals("un camioneta", Diccionario.reemplazar(t.patronCompilado()!!, "un Camión", t))
	}

	@Test
	fun reemplazoLiteralConDolar() {
		val e = Entrada(patron = "precio", reemplazo = "$5", tipo = TipoEntrada.TEXTO)
		assertEquals("cuesta $5", Diccionario.reemplazar(e.patronCompilado()!!, "cuesta precio", e))
	}

	@Test
	fun expresionRegularConGrupos() {
		val e = Entrada(patron = "(\\d+)h", reemplazo = "$1 horas", tipo = TipoEntrada.REGEX)
		assertEquals("faltan 3 horas", Diccionario.reemplazar(e.patronCompilado()!!, "faltan 3h", e))
	}

	@Test
	fun regexNoValida() {
		assertNull(Entrada(patron = "(", reemplazo = "", tipo = TipoEntrada.REGEX).patronCompilado())
	}

	@Test
	fun importaYExportaFormatoNvda() {
		val dic = "#Mi comentario\r\nNVDA\tene uve de a\t1\t2\r\n(\\d+)km\t\\1 kilómetros\t0\t1\r\n"
		val e = Diccionario.leer(dic)
		assertEquals(2, e.size)
		assertEquals(TipoEntrada.PALABRA, e[0].tipo)
		assertTrue(e[0].mayusculas)
		assertEquals("Mi comentario", e[0].comentario)
		assertEquals("$1 kilómetros", e[1].reemplazo)
		val vuelta = Diccionario.exportarNvda(e)
		assertTrue(vuelta.contains("(\\d+)km\t\\1 kilómetros\t0\t1"))
	}

	@Test
	fun nombresDeLetras() {
		assertEquals("uve doble", Simbolos.nombre('w', "es"))
		assertEquals("eñe", Simbolos.nombre('Ñ', "es"))
		assertEquals("zee", Simbolos.nombre('z', "en", "US"))
		assertEquals("zed", Simbolos.nombre('z', "en", "GB"))
		assertEquals("almohadilla", Simbolos.nombre('#', "es"))
	}

	@Test
	fun deletreoDeMayusculas() {
		assertEquals(Trozo.Habla("be", 20), Preparador.deletrear('B', "es", "ES", 20))
		assertEquals(Trozo.Habla("mayúscula be"), Preparador.deletrear('B', "es", "ES", 0))
	}

	@Test
	fun puntuacionPorNiveles() {
		assertEquals("a@b", Preparador.puntuacion("a@b", Simbolos.NINGUNA, "es"))
		assertEquals("a arroba b", Preparador.puntuacion("a@b", Simbolos.ALGUNA, "es").trim().replace(Regex("\\s+"), " "))
		assertTrue(Preparador.puntuacion("hola, mundo", Simbolos.TODA, "es").contains("coma,"))
	}

	@Test
	fun versiones() {
		assertTrue(Actualizador.compararVersiones("1.0.10", "1.0.9") > 0)
		assertEquals(0, Actualizador.compararVersiones("1.0", "1.0.0"))
		assertEquals("hxebolax/Fonaria", Actualizador.repoDeGitHub("https://github.com/hxebolax/Fonaria.git"))
	}
}
