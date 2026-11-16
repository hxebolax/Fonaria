package com.xebolax.fonaria.motor

import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.SynthesisCallback
import android.speech.tts.TextToSpeech

/**
 * Entrega el audio al sistema y decide cuándo empieza a sonar.
 *
 * La pista de audio de Android no suena hasta tener su búfer lleno ([pistaMs], unos 170 ms). Una
 * voz que llega poco a poco tardaría ese tiempo de más en oírse y, sobre todo, no se podría elegir
 * con cuánto margen arranca. Por eso, al empezar se **ceba** la pista con silencio (se pone en
 * marcha ya, mientras el motor prepara la frase) y la voz se retiene hasta tener un **colchón**
 * calculado para este móvil y esta velocidad, contando el silencio que aún queda por sonar. A
 * partir de ahí, cada trozo suena según llega.
 *
 * Tampoco se adelanta más de [adelantoMs] a lo que suena, para que al mandar callar no se pierdan
 * frases ya generadas que nadie ha oído.
 */
class Salida(
	private val callback: SynthesisCallback,
	private val adelantoMs: Int,
	private val parado: () -> Boolean,
) {
	var frecuencia = 24000
	private var empezado = false
	/** Guardar en un archivo (synthesizeToFile) no se oye: ni se ceba ni se va al ritmo de la voz. */
	val aArchivo = callback.javaClass.name.contains("File")
	private var bytes = 0L
	private var desde = 0L
	private var primerSonido = 0L

	/** Desde la llegada de la petición hasta que se suelta la primera voz (o -1). */
	fun primerSonidoMs(llegada: Long) = if (primerSonido != 0L) (primerSonido - llegada) / 1_000_000L else -1L

	/** Voz con la que arrancó (la retenida hasta reunir el colchón), en milisegundos. */
	var retenidoMs = 0L
		private set

	/** Búfer de la pista de audio del sistema para la voz, en milisegundos. */
	private val pistaMs: Int by lazy {
		val minimo = try {
			AudioTrack.getMinBufferSize(frecuencia, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
		} catch (e: Exception) {
			0
		}
		// Lo mismo que hace Android al crear la pista (BlockingAudioTrack): 8192 bytes como poco
		(maxOf(8192, minimo) * 1000L / (frecuencia * 2L)).toInt()
	}

	// ---- Simulación de la pista (para el registro): los huecos que se habrían oído ----------

	var huecos = 0
		private set
	var huecosMs = 0L
		private set
	private var simInicio = 0L
	private var simEntregadoMs = 0.0
	private var simPerdidoMs = 0.0
	private var simVoz = false

	private fun simular(n: Int, voz: Boolean) {
		val ahora = System.nanoTime()
		val antes = simEntregadoMs
		simEntregadoMs += n * 1000.0 / (frecuencia * 2)
		if (simInicio == 0L) {
			if (simEntregadoMs >= pistaMs) simInicio = ahora
		} else {
			val falta = (ahora - simInicio) / 1e6 - simPerdidoMs - antes
			if (falta > 2.0) {
				// Quedarse sin audio antes de la primera voz no se oye
				if (simVoz) {
					huecos++
					huecosMs += falta.toLong()
				}
				simPerdidoMs += falta
			}
		}
		simVoz = simVoz || voz
	}

	private fun abrir(): Boolean {
		if (empezado) return true
		empezado = callback.start(frecuencia, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS
		desde = System.nanoTime()
		return empezado
	}

	// ---- Arranque: cebado y colchón ---------------------------------------------------------

	private val retenido = ArrayList<ShortArray>()
	private var retenidas = 0
	private var colchon = 0
	private var reteniendo = false
	/** Cuándo se cebó la pista y con cuánto silencio (muestras). */
	private var cebadoEn = 0L
	private var cebadas = 0

	/**
	 * Antes de generar: con [cebar], pone en marcha la pista con silencio; la voz arrancará al
	 * tener [colchonMs] por delante de lo que suena. Al guardar en archivo no hace nada.
	 */
	fun preparar(cebar: Boolean, colchonMs: Int) {
		if (aArchivo) return
		colchon = frecuencia * colchonMs / 1000
		reteniendo = colchon > 0
		if (cebar && abrir()) {
			cebadas = frecuencia * (pistaMs + 10) / 1000
			cebadoEn = System.nanoTime()
			entregar(ShortArray(cebadas), voz = false)
		}
	}

	/** Silencio del cebado que aún queda por sonar, en muestras. */
	private fun silencioPendiente(): Int {
		if (cebadas == 0) return 0
		val sonadas = (System.nanoTime() - cebadoEn) / 1_000_000L * frecuencia / 1000L
		return (cebadas - sonadas).coerceAtLeast(0L).toInt()
	}

	/**
	 * Cuánto audio entregado queda por sonar, en milisegundos (negativo: la pista ya se ha quedado
	 * sin nada). Cuenta también lo retenido.
	 */
	fun ventajaMs(): Long {
		val retenidoAhora = retenidas * 1000L / frecuencia
		// Con el reloj de la simulación de la pista, que descuenta el tiempo que estuvo sin audio
		if (simInicio == 0L) return simEntregadoMs.toLong() + retenidoAhora
		val sonado = (System.nanoTime() - simInicio) / 1e6 - simPerdidoMs
		return (simEntregadoMs - sonado).toLong() + retenidoAhora
	}

	/** Suelta lo retenido (al reunir el colchón o al acabar la frase). */
	fun soltar(): Boolean {
		reteniendo = false
		if (retenido.isEmpty()) return !parado()
		retenidoMs = retenidas * 1000L / frecuencia
		val todo = retenido.toList()
		retenido.clear()
		retenidas = 0
		for (pcm in todo) if (!entregar(pcm, voz = true)) return false
		return !parado()
	}

	/** Entrega PCM (o lo guarda mientras se reúne el colchón). Devuelve false si hay que dejar de generar. */
	fun escribir(pcm: ShortArray): Boolean {
		if (pcm.isEmpty()) return !parado()
		if (parado()) return false
		if (reteniendo) {
			retenido += pcm
			retenidas += pcm.size
			return if (retenidas + silencioPendiente() >= colchon) soltar() else true
		}
		return entregar(pcm, voz = true)
	}

	// ---- Grabación para la memoria de frases -------------------------------------------------

	private var grabacion: ShortArray? = null
	private var grabadas = 0

	/** Guarda aparte todo lo que se entregue (sin el cebado), hasta [Memoria.MAXIMO_MUESTRAS]. */
	fun grabar() {
		grabacion = ShortArray(frecuencia * 2)
	}

	/** Lo grabado, o null si no se pidió o se pasó del máximo. */
	fun grabado(): ShortArray? = grabacion?.copyOf(grabadas)

	private fun anotar(pcm: ShortArray) {
		val g = grabacion ?: return
		if (grabadas + pcm.size > Memoria.MAXIMO_MUESTRAS) {
			grabacion = null
			return
		}
		val destino = if (grabadas + pcm.size > g.size) g.copyOf(maxOf(g.size * 2, grabadas + pcm.size)).also { grabacion = it } else g
		System.arraycopy(pcm, 0, destino, grabadas, pcm.size)
		grabadas += pcm.size
	}

	// ---- Entrega ------------------------------------------------------------------------------

	private fun entregar(pcm: ShortArray, voz: Boolean): Boolean {
		if (pcm.isEmpty()) return !parado()
		if (parado() || !abrir()) return false
		if (voz) {
			if (primerSonido == 0L) primerSonido = System.nanoTime()
			anotar(pcm)
		}
		val maximo = callback.maxBufferSize.coerceIn(1024, 16384) and 1.inv()
		val datos = ByteArray(pcm.size * 2)
		for (i in pcm.indices) {
			val s = pcm[i].toInt()
			datos[2 * i] = (s and 0xff).toByte()
			datos[2 * i + 1] = (s shr 8 and 0xff).toByte()
		}
		var i = 0
		while (i < datos.size) {
			if (parado()) return false
			val n = minOf(maximo, datos.size - i)
			simular(n, voz)
			if (callback.audioAvailable(datos, i, n) != TextToSpeech.SUCCESS) return false
			i += n
			bytes += n
			esperar()
		}
		return true
	}

	/** Una frase entera ya hecha (de la memoria): sale de una vez. */
	fun entera(pcm: ShortArray): Boolean = entregar(pcm, voz = true)

	/** Silencio de [ms] milisegundos (solo entre cosas dichas, nunca al principio). */
	fun silencio(ms: Int) {
		if (primerSonido == 0L || ms <= 0) return
		entregar(ShortArray(frecuencia * ms / 1000), voz = true)
	}

	/** No se adelanta más de [adelantoMs] a lo que suena. */
	private fun esperar() {
		if (aArchivo) return
		while (!parado()) {
			val sonando = (System.nanoTime() - desde) / 1_000_000L
			val generado = bytes * 1000L / (frecuencia * 2L)
			val sobra = generado - sonando - adelantoMs
			if (sobra <= 0) return
			try {
				Thread.sleep(minOf(sobra, 40L))
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
				return
			}
		}
	}

	/** Nada que decir: se abre y se cierra para que quien espera sepa que ha terminado. */
	fun vacio() {
		if (empezado || callback.start(frecuencia, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS) callback.done()
	}

	fun fallo() {
		callback.error(TextToSpeech.ERROR_SYNTHESIS)
	}

	fun terminar() {
		if (retenido.isNotEmpty() && !parado()) soltar()
		when {
			empezado -> callback.done()
			// Mandaron callar antes de que sonara nada: no se debe nada (como RHVoice y evvdroid)
			parado() -> Unit
			else -> vacio()
		}
	}
}
