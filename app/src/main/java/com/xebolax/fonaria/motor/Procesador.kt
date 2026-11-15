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
	/**
	 * Silencio más largo que se deja dentro de una lectura (500 ms, antes de acelerar; más corto
	 * gastaría el margen de voz por delante, porque ese silencio también cuesta generarlo). El motor
	 * parte los textos largos en trozos y entre uno y otro deja pausas del doble que entre frases
	 * (660-730 ms frente a unos 330): con la voz rápida suenan a tropezón.
	 */
	private val maxSilencio = frecuencia / 2

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

	/** Lo que queda en Sonic (el silencio pendiente del final se descarta: ya salió su margen). */
	fun terminar(): ShortArray {
		silencio.limpiar()
		val s = sonic ?: return VACIO
		s.flushStream()
		return leerSonic(s)
	}

	/**
	 * Silencio que se pasa enseguida al empezar cada pausa (60 ms). Sonic se guarda siempre la cola
	 * de lo último que recibe hasta tener más audio: si el silencio se apartase entero, el final de
	 * la palabra anterior se quedaría atascado dentro de Sonic y saldría después de la pausa (o al
	 * final de la frase), y se oiría como un microcorte. Con este empujón sale a su tiempo.
	 */
	private val empuje = frecuencia * 3 / 50
	/** Silencio ya pasado desde la última voz. */
	private var pasado = 0

	/**
	 * Separa la voz del silencio: la voz sale ya; del silencio sale enseguida el empujón y el resto se
	 * aparta hasta saber si después viene más voz (entonces se suelta, hasta [maxSilencio] en total)
	 * o es el final (entonces se descarta).
	 */
	private fun recortarFinal(m: FloatArray, desde: Int, n: Int): FloatArray {
		var ultimaVoz = n - 1
		while (ultimaVoz >= desde && abs(m[ultimaVoz]) < umbral) ultimaVoz--
		val salida = FloatBuffer()
		if (ultimaVoz >= desde) {
			// Vuelve la voz: el silencio apartado, sin pasar del máximo
			salida.anadir(silencio.tomar(minOf(silencio.tamano, (maxSilencio - pasado).coerceAtLeast(0))))
			silencio.limpiar()
			pasado = 0
			salida.anadir(m, desde, ultimaVoz + 1)
		}
		// El silencio de este trozo: primero el empujón, luego se aparta
		var i = if (ultimaVoz >= desde) ultimaVoz + 1 else desde
		if (pasado < empuje && i < n) {
			val cuanto = minOf(empuje - pasado, n - i)
			salida.anadir(m, i, i + cuanto)
			pasado += cuanto
			i += cuanto
		}
		silencio.anadir(m, i, n)
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
