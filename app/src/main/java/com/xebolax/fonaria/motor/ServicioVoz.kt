package com.xebolax.fonaria.motor

import android.media.AudioFormat
import android.os.PowerManager
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.texto.Preparador
import com.xebolax.fonaria.texto.Trozo
import com.xebolax.fonaria.voces.Almacen
import kotlin.random.Random

/**
 * El motor de voz que ve Android: lo usan TalkBack y cualquier app que hable con TextToSpeech.
 *
 * [onSynthesizeText] llega en el hilo de síntesis del sistema y es el único sitio donde se
 * genera audio. [onStop] llega desde otro hilo y solo levanta [parar]: el receptor del audio lo
 * ve en el siguiente trozo y el motor deja de generar.
 */
class ServicioVoz : TextToSpeechService() {

	@Volatile
	private var parar = false

	/**
	 * Mantiene despierto el procesador solo mientras se genera voz: con la pantalla apagada el
	 * móvil podría dormirse entre un trozo de audio y el siguiente, y la voz se entrecortaría.
	 */
	private val despierto by lazy {
		(getSystemService(POWER_SERVICE) as PowerManager)
			.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fonaria:voz")
			.apply { setReferenceCounted(false) }
	}

	override fun onCreate() {
		super.onCreate()
		PrimerPlano.mantener(this)
		// El modelo se carga ya, en segundo plano: la primera frase no tiene que esperarlo entero
		Thread({
			try {
				Motor.asegurar(this)
			} catch (e: Throwable) {
				Log.e(ETIQUETA, "No se pudo precargar el motor", e)
			}
		}, "fonaria-precarga").start()
	}

	// ---- Idiomas -------------------------------------------------------------------------

	override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
		Idiomas.disponibilidad(this, lang, country)

	override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
		Idiomas.disponibilidad(this, lang, country)

	override fun onGetLanguage(): Array<String> {
		val voz = Idiomas.elegirVoz(this, "es", "ES") ?: return arrayOf("spa", "ESP", "")
		val (i, r) = Idiomas.aIso3(voz.idioma, voz.region)
		return arrayOf(i, r, "")
	}

	// ---- Voces ---------------------------------------------------------------------------

	override fun onGetVoices(): MutableList<Voice> = Almacen.instaladas(this).map { v ->
		Voice(v.nombreSistema, v.locale, Voice.QUALITY_VERY_HIGH, Voice.LATENCY_NORMAL, false, emptySet())
	}.toMutableList()

	override fun onIsValidVoiceName(voiceName: String?): Int =
		if (Almacen.instaladas(this).any { it.nombreSistema == voiceName }) TextToSpeech.SUCCESS else TextToSpeech.ERROR

	override fun onLoadVoice(voiceName: String?): Int = onIsValidVoiceName(voiceName)

	override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
		val idioma = Idiomas.codigo(lang) ?: return null
		return Idiomas.elegirVoz(this, idioma, Idiomas.region(country))?.nombreSistema
	}

	// ---- Hablar --------------------------------------------------------------------------

	override fun onStop() {
		parar = true
	}

	override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
		if (request == null || callback == null) return
		parar = false
		despierto.acquire(DESPIERTO_MAXIMO_MS)
		try {
			sintetizar(request, callback)
		} finally {
			if (despierto.isHeld) despierto.release()
		}
	}

	private fun sintetizar(request: SynthesisRequest, callback: SynthesisCallback) {
		val idioma = Idiomas.codigo(request.language)
		if (idioma == null) {
			callback.error(TextToSpeech.ERROR_INVALID_REQUEST)
			return
		}
		val region = Idiomas.region(request.country)
		// Las pantallas de Fonaria piden una voz concreta para probarla
		val forzada = request.params?.getString(PARAM_VOZ)?.let { id -> Almacen.instaladas(this).firstOrNull { it.id == id } }
		val voz = forzada ?: Idiomas.elegirVoz(this, idioma, region, request.voiceName)
		if (voz == null) {
			callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
			return
		}
		val llegada = System.nanoTime()
		val ajustes = Ajustes(this)
		val texto: CharSequence = request.charSequenceText ?: ""
		val trozos = try {
			Preparador.preparar(this, texto, idioma, region, voz.id, ajustes)
		} catch (e: Exception) {
			Log.e(ETIQUETA, "No se pudo preparar el texto; se lee tal cual", e)
			listOf(Trozo.Habla(texto.toString()))
		}
		val salida = Salida(callback, ajustes.adelantoMs) { parar }
		if (trozos.none { it is Trozo.Habla }) {
			salida.vacio()
			return
		}
		if (!Motor.asegurar(this)) {
			Log.e(ETIQUETA, "Motor no disponible: ${Motor.error}")
			callback.error(TextToSpeech.ERROR_SERVICE)
			return
		}
		salida.frecuencia = Motor.frecuencia
		PrimerPlano.mantener(this)
		Hilos.subir()
		val p = ajustes.parametros(voz.id)
		// Velocidad final pedida (la de Fonaria por la del sistema). Parte la hace el propio modelo
		// hablando más ligero (menos audio que generar: responde antes y no se entrecorta) y el
		// resto Sonic; el modelo suena más natural que estirar el audio.
		val pedida = p.velocidad / 100f * ajustes.factorSistema(request.speechRate)
		val ritmoAuto = if (ajustes.acelerarModelo) Acelerar.ritmo(pedida) else 0f
		val porModelo = Acelerar.factor(ritmoAuto)
		val velocidad = pedida / porModelo
		val ritmo = (p.ritmo / 100f + ritmoAuto).coerceIn(-0.8f, 1f)
		val tono = p.tono / 100f * (if (ajustes.tonoDelSistema) request.pitch / 100f else 1f)
		val volumen = p.volumen / 100f
		val semilla = if (ajustes.semillaFija) SEMILLA else Random.nextLong()
		val normalizar = if (ajustes.normalizar) idioma else "none"

		// Todas las frases se piden ya: el motor las genera seguidas, por delante de lo que suena
		val peticiones = trozos.filterIsInstance<Trozo.Habla>().map { t ->
			Motor.Peticion(
				texto = t.texto,
				voz = voz.id,
				idioma = normalizar,
				temperatura = p.variacion / 100f,
				semilla = semilla,
				ritmo = ritmo,
				guia = ajustes.guia / 10f,
				tokensTrozo = ajustes.tokensTrozo,
				igualarVolumen = ajustes.igualarVolumen,
			)
		}
		// El motor genera en su hilo y este hilo procesa y entrega (Android lo bloquea cuando lleva
		// medio segundo por delante; así el motor no se para por ello)
		val generador = Generador(this, peticiones) { parar }
		generador.empezar()
		try {
			for (t in trozos) {
				if (parar) break
				when (t) {
					// El silencio también se acelera con la velocidad
					is Trozo.Pausa -> salida.silencio((t.ms / pedida.coerceAtLeast(0.25f)).toInt())
					is Trozo.Habla -> {
						// Si el móvil genera más despacio de lo que se oye a esta velocidad, se guarda
						// audio antes de soltarlo, para que la frase suene seguida y no a trompicones.
						salida.retenerPara(t.texto.length / porModelo, velocidad, Motor.velocidadGeneracion)
						val proc = Procesador(Motor.frecuencia, velocidad, tono * (1f + t.subirTono / 100f), volumen, ajustes.recortarSilencios)
						val resultado = generador.recibir { muestras ->
							!parar && salida.escribir(proc.procesar(muestras, muestras.size))
						}
						if (parar || resultado == Generador.Resultado.CORTADA) break
						if (resultado == Generador.Resultado.FALLO) {
							salida.fallo()
							return
						}
						if (!salida.escribir(proc.terminar())) break
						if (!salida.soltar()) break
					}
				}
			}
		} finally {
			generador.cerrar()
		}
		salida.terminar()
		Log.i(
			ETIQUETA,
			"Frase de ${texto.length} car.: velocidad %.2f (sistema ${request.speechRate}, modelo %.2f, Sonic %.2f), motor %.2fx, primer sonido %d ms, retenido %d ms, total %d ms%s".format(
				pedida, ritmo, velocidad, Motor.velocidadGeneracion, salida.primerSonidoMs(llegada), salida.retenidoMs,
				(System.nanoTime() - llegada) / 1_000_000L, if (parar) ", cortada" else "",
			),
		)
	}

	companion object {
		private const val ETIQUETA = "Fonaria.Servicio"
		/** Semilla fija: la misma frase suena igual cada vez (como un lector de pantalla clásico). */
		const val SEMILLA = 4242424242424242L
		/** Parámetro de speak() con el id de la voz que debe sonar (lo usan las pantallas de la app). */
		const val PARAM_VOZ = "com.xebolax.fonaria.VOZ"
		/** Tope de seguridad del bloqueo de suspensión (se suelta al acabar cada lectura). */
		private const val DESPIERTO_MAXIMO_MS = 10 * 60 * 1000L
	}
}

/**
 * Entrega el audio al sistema: dice que empieza solo cuando hay algo que oír, lo trocea al
 * tamaño que admite el sistema y no se adelanta más de [adelantoMs] a lo que suena, para que al
 * mandar callar no se pierdan frases ya generadas que nadie ha oído.
 */
class Salida(
	private val callback: SynthesisCallback,
	private val adelantoMs: Int,
	private val parado: () -> Boolean,
) {
	var frecuencia = 24000
	private var empezado = false
	/** Guardar en un archivo (synthesizeToFile) no se oye: no hace falta ir al ritmo de la voz. */
	private val aArchivo = callback.javaClass.name.contains("File")
	private var bytes = 0L
	private var desde = 0L

	/** Desde la llegada de la petición hasta el primer audio entregado (o -1). */
	fun primerSonidoMs(llegada: Long) = if (empezado) (desde - llegada) / 1_000_000L else -1L

	/** Cuánto audio se ha retenido en total para evitar cortes, en milisegundos. */
	var retenidoMs = 0L
		private set

	private fun abrir(): Boolean {
		if (empezado) return true
		empezado = callback.start(frecuencia, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS
		desde = System.nanoTime()
		return empezado
	}

	/** Audio guardado mientras se retiene, y hasta cuándo (System.nanoTime). */
	private val retenido = ArrayList<ShortArray>()
	private var sinRitmo = false
	private var retenerHasta = 0L

	/**
	 * Antes de una frase de [caracteres] letras: si a [velocidad] se oye más deprisa de lo que el
	 * motor genera ([generacion] segundos de voz por segundo), retiene el audio el tiempo justo
	 * para que la frase entera suene seguida. Lo que ya va por delante sonando cuenta a favor.
	 */
	fun retenerPara(caracteres: Float, velocidad: Float, generacion: Float) {
		// Unos 14 caracteres por segundo a la velocidad natural de las voces (ya dividido por lo
		// que acelera el propio modelo)
		val duracion = caracteres / 14f
		val sonara = duracion / velocidad.coerceAtLeast(0.1f)
		// Con un 12 % de margen: el procesador no genera siempre al mismo ritmo
		val tardara = duracion / (generacion * 0.88f).coerceAtLeast(0.1f)
		val adelantado = if (empezado) ((bytes * 1000L / (frecuencia * 2L)) - (System.nanoTime() - desde) / 1_000_000L).coerceAtLeast(0) else 0L
		// El margen de seguridad (80 ms) solo cuando de verdad falta: si el móvil genera más deprisa
		// de lo que se oye, no se retiene nada y la voz sale al momento
		val falta = ((tardara - sonara) * 1000f).toLong()
		// En los textos largos, un pequeño colchón al empezar (hasta 400 ms, el 10 % de lo que va a
		// durar): el motor se para un instante en cada unión entre trozos y, sin margen acumulado,
		// la primera se oiría. Las frases cortas de TalkBack salen al momento.
		val colchon = if (sonara > 2.5f && !empezado) (sonara * 100f).toLong().coerceAtMost(400L) else 0L
		val esperaMs = maxOf(if (falta > 0) falta + 80 - adelantado else 0L, colchon)
		// Si el motor no llega a la velocidad de lo que suena, nunca se le hace esperar: todo lo que
		// genere va por delante, que es justo lo que evita los cortes
		sinRitmo = generacion < velocidad * 1.2f
		retenerHasta = if (esperaMs > 0) System.nanoTime() + minOf(esperaMs, 4000L) * 1_000_000L else 0L
		if (esperaMs > 0) retenidoMs += minOf(esperaMs, 4000L)
	}

	/** Suelta lo retenido (al acabar la frase o al cumplirse el tiempo). */
	fun soltar(): Boolean {
		retenerHasta = 0L
		val todo = retenido.toList()
		retenido.clear()
		for (pcm in todo) if (!entregar(pcm)) return false
		return !parado()
	}

	/** Entrega PCM (o lo guarda si se está reteniendo). Devuelve false si hay que dejar de generar. */
	fun escribir(pcm: ShortArray): Boolean {
		if (pcm.isEmpty()) return !parado()
		if (parado()) return false
		if (retenerHasta != 0L) {
			if (System.nanoTime() < retenerHasta) {
				retenido += pcm
				return true
			}
			if (!soltar()) return false
		}
		return entregar(pcm)
	}

	private fun entregar(pcm: ShortArray): Boolean {
		if (pcm.isEmpty()) return !parado()
		if (parado() || !abrir()) return false
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
			if (callback.audioAvailable(datos, i, n) != TextToSpeech.SUCCESS) return false
			i += n
			bytes += n
			esperar()
		}
		return true
	}

	/** Silencio de [ms] milisegundos (solo entre cosas dichas, nunca al principio). */
	fun silencio(ms: Int) {
		if (!empezado || ms <= 0) return
		entregar(ShortArray(frecuencia * ms / 1000))
	}

	/** No se adelanta más de [adelantoMs] a lo que suena. */
	private fun esperar() {
		if (aArchivo || sinRitmo) return
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
		if (callback.start(frecuencia, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS) callback.done()
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
