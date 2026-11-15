package com.xebolax.fonaria

import com.xebolax.fonaria.motor.Procesador
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El recorte de silencios y el paso por Sonic (gradle testDebugUnitTest). */
class ProcesadorTest {
	private val f = 24000

	private fun voz(ms: Int) = FloatArray(f * ms / 1000) { i -> if (i % 2 == 0) 0.3f else -0.3f }
	private fun silencio(ms: Int) = FloatArray(f * ms / 1000)

	/** Pasa [trozos] en piezas de 80 ms, como el motor, y devuelve todo lo que sale. */
	private fun procesar(p: Procesador, vararg trozos: FloatArray): ShortArray {
		val todo = trozos.reduce { a, b -> a + b }
		var salida = ShortArray(0)
		var i = 0
		while (i < todo.size) {
			val n = minOf(1920, todo.size - i)
			salida += p.procesar(todo.copyOfRange(i, i + n), n)
			i += n
		}
		return salida + p.terminar()
	}

	@Test
	fun pausaLargaSeAcortaYNoSePierdeVoz() {
		val p = Procesador(f, 1f, 1f, 1f, recortar = true)
		val salida = procesar(p, silencio(300), voz(400), silencio(1000), voz(400), silencio(800))
		val ms = salida.size * 1000 / f
		// 400 de voz + 500 de pausa (como mucho) + 400 de voz + 60 de final + margen inicial (con los bordes de las piezas de 80 ms)
		assertTrue("Duró $ms ms", ms in 1370..1410)
		assertEquals(800, salida.count { it.toInt() != 0 } * 1000 / f)
	}

	@Test
	fun pausaCortaQuedaEntera() {
		val p = Procesador(f, 1f, 1f, 1f, recortar = true)
		val salida = procesar(p, voz(400), silencio(200), voz(400))
		assertEquals(1000, salida.size * 1000 / f)
	}

	@Test
	fun conSonicLaVozLlegaAntesDeAcabar() {
		// A 1,76x, el final de la primera palabra debe salir antes de que acabe la pausa, no al final
		val p = Procesador(f, 1.76f, 1f, 1f, recortar = true)
		val antes = procesar(p, voz(400)).size
		val p2 = Procesador(f, 1.76f, 1f, 1f, recortar = true)
		var salida = p2.procesar(voz(400), f * 400 / 1000)
		// Llega la pausa: con el empujón, Sonic suelta la cola de la palabra
		salida += p2.procesar(silencio(80), f * 80 / 1000)
		assertTrue("Solo salió ${salida.size} de ${antes}", salida.count { it.toInt() != 0 } >= antes * 9 / 10)
	}

	@Test
	fun picosPorEncimaDelMaximoNoDanLaVuelta() {
		// El modelo a veces pasa de 1,0: con Sonic de por medio no debe salir con el signo cambiado
		val fuerte = FloatArray(f * 400 / 1000) { 1.4f }
		val p = Procesador(f, 1.5f, 1f, 1f, recortar = false)
		val salida = procesar(p, fuerte)
		assertTrue("Salieron muestras negativas", salida.drop(f / 20).dropLast(f / 20).all { it >= 0 })
	}

	@Test
	fun elArranqueDelDecodificadorNoSeOye() {
		// Un golpe justo al empezar la frase (lo primero que suelta el decodificador) se silencia
		val golpe = FloatArray(f * 10 / 1000) { 0.9f }
		val p = Procesador(f, 1f, 1f, 1f, recortar = false)
		val salida = procesar(p, golpe, silencio(10), voz(200))
		assertTrue(salida.take(f / 50).all { it.toInt() == 0 })
		// Y la voz empieza con un fundido, no de golpe
		assertTrue(kotlin.math.abs(salida[f / 50].toInt()) < 2000)
	}
}
