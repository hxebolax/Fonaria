package com.xebolax.fonaria.motor

import sonic.Sonic
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lleva el audio del modelo (flotantes) a lo que se oye (PCM de 16 bits): recorta los silencios
 * del principio y del final, y cambia velocidad, tono y volumen con Sonic, que mantiene la voz
 * natural incluso a velocidades muy altas (las que usan los lectores de pantalla).
 *
 * Uno por trozo de texto: el recorte del final necesita saber dónde acaba.
 */
class Procesador(
	private val frecuencia: Int,
	velocidad: Float,
	tono: Float,
	volumen: Float,
	private val recortar: Boolean,
) {
	private val sonic: Sonic?
	private var empezado = !recortar
	/** Silencio pendiente: se suelta si después viene voz, y se descarta si es el final. */
	private val silencio = FloatBuffer()
	private val umbral = 0.008f
	/** Margen de silencio que se respeta antes y después de la voz (20 ms). */
	private val margen = frecuencia / 50

	init {
		val v = velocidad.coerceIn(0.2f, 8f)
		val t = tono.coerceIn(0.25f, 4f)
		val vol = volumen.coerceIn(0f, 4f)
		sonic = if (abs(v - 1f) < 0.01f && abs(t - 1f) < 0.01f && abs(vol - 1f) < 0.01f) null
		else Sonic(frecuencia, 1).apply {
			speed = v
			pitch = t
			this.volume = vol
			// Calidad alta hasta 2x (con la ayuda del modelo, lo normal); más allá no se nota y cuesta el doble
			quality = if (v <= 2.0f) 1 else 0
		}
	}

	/** Procesa un trozo del modelo y devuelve el PCM listo (puede estar vacío). */
	fun procesar(muestras: FloatArray, n: Int): ShortArray {
		var desde = 0
		if (!empezado) {
			while (desde < n && abs(muestras[desde]) < umbral) desde++
			if (desde == n) return VACIO
			empezado = true
			desde = (desde - margen).coerceAtLeast(0)
		}
		val util = if (recortar) recortarFinal(muestras, desde, n) else muestras.copyOfRange(desde, n)
		return convertir(util)
	}

	/** Lo que queda en Sonic y el margen de silencio final. */
	fun terminar(): ShortArray {
		val cola = silencio.tomar(minOf(silencio.tamano, margen))
		silencio.limpiar()
		val final = convertir(cola)
		val s = sonic ?: return final
		s.flushStream()
		return final + leerSonic(s)
	}

	/** Guarda aparte el silencio del final del trozo hasta saber si viene más voz. */
	private fun recortarFinal(m: FloatArray, desde: Int, n: Int): FloatArray {
		var ultimaVoz = n - 1
		while (ultimaVoz >= desde && abs(m[ultimaVoz]) < umbral) ultimaVoz--
		if (ultimaVoz < desde) {
			// Todo silencio: se acumula
			silencio.anadir(m, desde, n)
			return FloatArray(0)
		}
		val salida = FloatBuffer()
		salida.anadir(silencio.tomar(silencio.tamano))
		silencio.limpiar()
		salida.anadir(m, desde, ultimaVoz + 1)
		silencio.anadir(m, ultimaVoz + 1, n)
		return salida.tomar(salida.tamano)
	}

	private fun convertir(m: FloatArray): ShortArray {
		if (m.isEmpty()) return VACIO
		val s = sonic ?: return aPcm(m)
		s.writeFloatToStream(m, m.size)
		return leerSonic(s)
	}

	private fun leerSonic(s: Sonic): ShortArray {
		val disponibles = s.samplesAvailable()
		if (disponibles <= 0) return VACIO
		val salida = ShortArray(disponibles)
		val n = s.readShortFromStream(salida, disponibles)
		return if (n == disponibles) salida else salida.copyOf(n.coerceAtLeast(0))
	}

	private class FloatBuffer {
		private var datos = FloatArray(4096)
		var tamano = 0
			private set

		fun anadir(m: FloatArray, desde: Int = 0, hasta: Int = m.size) {
			val n = hasta - desde
			if (n <= 0) return
			if (tamano + n > datos.size) datos = datos.copyOf(maxOf(datos.size * 2, tamano + n))
			System.arraycopy(m, desde, datos, tamano, n)
			tamano += n
		}

		fun tomar(n: Int): FloatArray = datos.copyOf(n.coerceIn(0, tamano))

		fun limpiar() {
			tamano = 0
		}
	}

	companion object {
		val VACIO = ShortArray(0)

		fun aPcm(m: FloatArray): ShortArray = ShortArray(m.size) { i ->
			(m[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
		}
	}
}
