package com.xebolax.fonaria.motor

/**
 * Recuerda el audio de las frases cortas ya dichas, para repetirlas al instante.
 *
 * Un lector de pantalla repite muchísimo: «botón», «atrás», las letras al escribir, los nombres
 * de las apps... Generar cada vez cuesta entre 100 y 250 ms antes del primer sonido; de memoria
 * suena al momento. Solo vale con «Misma lectura cada vez» (si no, cada toma debe ser distinta) y
 * la clave incluye todo lo que cambia el sonido, así que al tocar un ajuste no se reutiliza nada.
 */
object Memoria {
	/** Unos tres minutos de voz a 24 kHz. */
	private const val MAXIMO_BYTES = 8L * 1024 * 1024
	/** Frases de hasta 8 segundos: las largas rara vez se repiten. */
	const val MAXIMO_MUESTRAS = 8 * 24000
	/** Textos de hasta 160 caracteres. */
	const val MAXIMO_TEXTO = 160

	/** Ordenadas por uso: la que más tiempo lleva sin usarse es la primera en salir. */
	private val frases = LinkedHashMap<String, ShortArray>(64, 0.75f, true)
	private var bytes = 0L

	@Synchronized
	fun buscar(clave: String): ShortArray? = frases[clave]

	@Synchronized
	fun guardar(clave: String, pcm: ShortArray) {
		if (pcm.isEmpty() || pcm.size > MAXIMO_MUESTRAS) return
		frases.put(clave, pcm)?.let { bytes -= it.size * 2L }
		bytes += pcm.size * 2L
		val it = frases.entries.iterator()
		while (bytes > MAXIMO_BYTES && it.hasNext()) {
			bytes -= it.next().value.size * 2L
			it.remove()
		}
	}

	@Synchronized
	fun vaciar() {
		frases.clear()
		bytes = 0
	}
}
