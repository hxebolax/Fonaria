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
	fun velocidadDelSistemaSuavizada() {
		val f = { r: Int, e: String -> com.xebolax.fonaria.datos.Ajustes.factorSistema(r, e) }
		assertEquals(1f, f(100, "suave"), 0.001f)
		assertTrue(f(310, "suave") in 1.9f..2.05f)
		assertEquals(3.1f, f(310, "completa"), 0.001f)
		assertEquals(1f, f(310, "no"), 0.001f)
	}

	@Test
	fun aceleracionDelModelo() {
		val a = com.xebolax.fonaria.motor.Acelerar
		assertEquals(0f, a.ritmo(1f), 0.001f)
		assertEquals(a.MAXIMO, a.ritmo(2.34f), 0.001f)
		assertEquals(-0.3f, a.ritmo(1.67f), 0.001f)
		assertEquals(1.33f, a.factor(-0.6f), 0.01f)
		assertEquals(1f, a.factor(0.3f), 0.001f)
		// ritmoDe es lo contrario de factor, sin pasar del máximo
		assertEquals(-0.4f, a.ritmoDe(a.factor(-0.4f)), 0.001f)
		assertEquals(0f, a.ritmoDe(1f), 0.001f)
		assertEquals(a.MAXIMO, a.ritmoDe(3f), 0.001f)
	}

	@Test
	fun memoriaDeFrases() {
		val m = com.xebolax.fonaria.motor.Memoria
		// Un texto con «%» no rompe la clave (en la 1.0.5 cerraba el motor al leer «300 %»)
		val clave = m.clave("Al 300 % suena unas 2 veces más rápido, %s %d %", "alba|es|ES", 1.76f, -0.6f, 1f, 1f)
		assertTrue(clave.endsWith("%s %d %"))
		assertTrue(clave != m.clave("Al 300 % suena unas 2 veces más rápido, %s %d %", "alba|es|ES", 1.5f, -0.6f, 1f, 1f))
		m.vaciar()
		assertNull(m.buscar("hola"))
		m.guardar("hola", ShortArray(1000) { 7 })
		assertEquals(1000, m.buscar("hola")!!.size)
		// Las frases demasiado largas no se guardan
		m.guardar("larga", ShortArray(m.MAXIMO_MUESTRAS + 1))
		assertNull(m.buscar("larga"))
		// Al llenarse sale la que más tiempo lleva sin usarse, no la recién usada
		val grande = ShortArray(m.MAXIMO_MUESTRAS)
		for (i in 1..30) {
			m.buscar("hola")
			m.guardar("f$i", grande)
		}
		assertEquals(1000, m.buscar("hola")!!.size)
		assertNull(m.buscar("f1"))
		m.vaciar()
	}

	@Test
	fun versiones() {
		assertTrue(Actualizador.compararVersiones("1.0.10", "1.0.9") > 0)
		assertEquals(0, Actualizador.compararVersiones("1.0", "1.0.0"))
		assertEquals("hxebolax/Fonaria", Actualizador.repoDeGitHub("https://github.com/hxebolax/Fonaria.git"))
	}

	@Test
	fun numerosConLetras() {
		// Un lector de mentira: «<n>» o «<n>m» en masculino, para ver qué se convierte y qué no
		val lector = com.xebolax.fonaria.texto.Numeros.Lector { n, _, masculino -> "<$n${if (masculino) "m" else ""}>" }
		fun n(t: String) = com.xebolax.fonaria.texto.Numeros.aplicar(t, "es", lector)
		assertEquals("Velocidad, <105m> por ciento", n("Velocidad, 105 %"))
		assertEquals("<105m> por ciento", n("105%"))
		assertEquals("<3m> mensajes y <21>", n("3 mensajes y 21"))
		// Horas, fechas, decimales, versiones, teléfonos, códigos y ceros delante se dejan al motor
		for (t in listOf("a las 10:30", "el 12/05/2026", "1.234,5 euros", "versión 1.0.6", "612345678", "A320", "007", "3º", "2-1")) {
			assertEquals(t, n(t))
		}
		assertEquals("sin cifras", n("sin cifras"))
	}
}
