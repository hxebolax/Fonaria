package com.xebolax.fonaria.motor

import android.media.AudioFormat
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

	override fun onCreate() {
		super.onCreate()
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
		val p = ajustes.parametros(voz.id)
		val velocidad = p.velocidad / 100f * (if (ajustes.velocidadDelSistema) request.speechRate / 100f else 1f)
		val tono = p.tono / 100f * (if (ajustes.tonoDelSistema) request.pitch / 100f else 1f)
		val volumen = p.volumen / 100f
		val semilla = if (ajustes.semillaFija) SEMILLA else Random.nextLong()
		val normalizar = if (ajustes.normalizar) idioma else "none"

		for (t in trozos) {
			if (parar) break
			when (t) {
				// El silencio también se acelera con la velocidad
				is Trozo.Pausa -> salida.silencio((t.ms / velocidad.coerceAtLeast(0.25f)).toInt())
				is Trozo.Habla -> {
					val proc = Procesador(Motor.frecuencia, velocidad, tono * (1f + t.subirTono / 100f), volumen, ajustes.recortarSilencios)
					val peticion = Motor.Peticion(
						texto = t.texto,
						voz = voz.id,
						idioma = normalizar,
						temperatura = p.variacion / 100f,
						semilla = semilla,
						ritmo = p.ritmo / 100f,
						guia = ajustes.guia / 10f,
						tokensTrozo = ajustes.tokensTrozo,
						igualarVolumen = ajustes.igualarVolumen,
					)
					val ok = Motor.hablar(this, peticion) { muestras, n ->
						!parar && salida.escribir(proc.procesar(muestras, n))
					}
					if (parar) break
					if (!ok) {
						salida.fallo()
						return
					}
					if (!salida.escribir(proc.terminar())) break
				}
			}
		}
		salida.terminar()
	}

	companion object {
		private const val ETIQUETA = "Fonaria.Servicio"
		/** Semilla fija: la misma frase suena igual cada vez (como un lector de pantalla clásico). */
		const val SEMILLA = 4242424242424242L
		/** Parámetro de speak() con el id de la voz que debe sonar (lo usan las pantallas de la app). */
		const val PARAM_VOZ = "com.xebolax.fonaria.VOZ"
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

	private fun abrir(): Boolean {
		if (empezado) return true
		empezado = callback.start(frecuencia, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS
		desde = System.nanoTime()
		return empezado
	}

	/** Entrega PCM. Devuelve false si hay que dejar de generar. */
	fun escribir(pcm: ShortArray): Boolean {
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
		escribir(ShortArray(frecuencia * ms / 1000))
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
		if (callback.start(frecuencia, AudioFormat.ENCODING_PCM_16BIT, 1) == TextToSpeech.SUCCESS) callback.done()
	}

	fun fallo() {
		callback.error(TextToSpeech.ERROR_SYNTHESIS)
	}

	fun terminar() {
		when {
			empezado -> callback.done()
			// Mandaron callar antes de que sonara nada: no se debe nada (como RHVoice y evvdroid)
			parado() -> Unit
			else -> vacio()
		}
	}
}
