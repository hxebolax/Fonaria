package com.xebolax.fonaria.motor

import android.os.PowerManager
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.texto.Diccionario
import com.xebolax.fonaria.texto.Preparador
import com.xebolax.fonaria.texto.Trozo
import com.xebolax.fonaria.voces.Almacen
import kotlin.random.Random

/**
 * El motor de voz que ve Android: lo usan TalkBack y cualquier app que hable con TextToSpeech.
 *
 * [onSynthesizeText] llega en el hilo de síntesis del sistema. El motor genera en su propio hilo
 * ([Generador]) y este procesa y entrega ([Procesador], [Salida]). [onStop] llega desde otro hilo
 * y solo levanta [parar]: todos lo ven en el siguiente trozo y se deja de generar.
 */
class ServicioVoz : TextToSpeechService() {

	@Volatile
	private var parar = false
	private var revisionEscrita = -1

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
				// Las voces por defecto de ahora: quien ya estaba conectado las pidió antes de este arranque
				for (v in Almacen.instaladas(this).distinctBy { it.idioma + it.region }) {
					Idiomas.elegirVoz(this, v.idioma, v.region)?.let { PorDefecto.anotar(this, it.nombreSistema) }
				}
				if (Motor.asegurar(this)) calentar()
			} catch (e: Throwable) {
				Log.e(ETIQUETA, "No se pudo precargar el motor", e)
			}
		}, "fonaria-precarga").start()
	}

	/**
	 * Dice una palabra sin que se oiga, nada más cargar: la primera vez el motor lee del
	 * almacenamiento los pesos que usa y prepara la voz, y eso tardaba medio segundo de más en la
	 * primera frase de verdad.
	 */
	private fun calentar() {
		val voz = Idiomas.elegirVoz(this, "es", "ES") ?: Almacen.instaladas(this).firstOrNull() ?: return
		val ajustes = Ajustes(this)
		val inicio = System.nanoTime()
		Motor.hablar(
			this,
			Motor.Peticion("Hola.", voz.id, if (ajustes.normalizar) voz.idioma else "none", 0.3f, SEMILLA, 0f, ajustes.guia / 10f, ajustes.tokensTrozo, ajustes.igualarVolumen),
		) { _, _ -> true }
		Log.i(ETIQUETA, "Motor calentado con ${voz.id} en ${(System.nanoTime() - inicio) / 1_000_000L} ms")
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
		return Idiomas.elegirVoz(this, idioma, Idiomas.region(country))?.nombreSistema?.also { PorDefecto.anotar(this, it) }
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
		val llegada = System.nanoTime()
		val idioma = Idiomas.codigo(request.language)
		if (idioma == null) {
			callback.error(TextToSpeech.ERROR_INVALID_REQUEST)
			return
		}
		val region = Idiomas.region(request.country)
		// Las pantallas de Fonaria piden una voz concreta para probarla
		val forzada = request.params?.getString(PARAM_VOZ)?.let { id -> Almacen.instaladas(this).firstOrNull { it.id == id } }
		// La voz que «pide» quien habla casi nunca es una elección suya: Android le da el nombre de
		// la voz por defecto al conectar y la app lo repite en cada frase. Si es uno de esos nombres,
		// manda lo elegido en Fonaria; si no, al cambiar de voz seguiría sonando la de antes.
		val vozPedida = request.voiceName?.takeUnless { PorDefecto.es(this, it) }
		val voz = forzada ?: Idiomas.elegirVoz(this, idioma, region, vozPedida)
		if (voz == null) {
			callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
			return
		}
		val ajustes = Ajustes(this)
		val texto: CharSequence = request.charSequenceText ?: ""
		val salida = Salida(callback, ajustes.adelantoMs) { parar }
		salida.frecuencia = Motor.frecuencia
		PrimerPlano.mantener(this)
		Hilos.subir()

		// Para el registro; con «%» doblado porque va dentro de cadenas de formato
		val quien = voz.nombre.replace("%", "%%")
		val pedido = "${request.language}-${request.country}${request.voiceName?.let { ", voz $it" } ?: ""}".replace("%", "%%")
		// Los ajustes con los que se habla, una vez por cada cambio: para entender un fallo por el registro
		if (ajustes.revision != revisionEscrita) {
			revisionEscrita = ajustes.revision
			Log.i(ETIQUETA, "Ajustes: " + ajustes.p.all.toSortedMap().entries.joinToString(", ") { "${it.key}=${it.value}" })
		}
		val p = ajustes.parametros(voz.id)
		// Velocidad final pedida (la de Fonaria por la del sistema). Parte la hace el propio modelo
		// hablando más ligero (menos audio que generar: responde antes y no se entrecorta) y el
		// resto Sonic; el modelo suena más natural que estirar el audio.
		val pedida = p.velocidad / 100f * ajustes.factorSistema(request.speechRate)
		var ritmoAuto = if (ajustes.acelerarModelo) Acelerar.ritmo(pedida) else 0f
		var porModelo = Acelerar.factor(ritmoAuto)
		// Lo que el móvil genera (segundos de voz por segundo) pone el tope a lo que Sonic puede
		// acelerar: por encima, la voz se quedaría sin audio y se entrecortaría sin remedio.
		val generacion = Motor.velocidad(this)
		val tope = (generacion / MARGEN_GENERACION).coerceAtLeast(1f)
		val limitar = ajustes.limitarVelocidad && !salida.aArchivo
		if (limitar && pedida / porModelo > tope && ajustes.acelerarModelo) {
			// Antes de frenar la voz, que el modelo ponga más de su parte
			porModelo = (pedida / tope).coerceIn(porModelo, Acelerar.factor(Acelerar.MAXIMO))
			ritmoAuto = Acelerar.ritmoDe(porModelo)
		}
		var velocidad = pedida / porModelo
		val limitada = limitar && velocidad > tope
		if (limitada) velocidad = tope
		val ritmo = (p.ritmo / 100f + ritmoAuto).coerceIn(Acelerar.LIMITE, 1f)
		val tono = p.tono / 100f * (if (ajustes.tonoDelSistema) request.pitch / 100f else 1f)
		val volumen = p.volumen / 100f

		// ¿Ya se dijo esto mismo, con la misma voz y los mismos ajustes? Sale de memoria, al instante
		val clave = if (ajustes.memoriaFrases && ajustes.semillaFija && !salida.aArchivo && texto.length <= Memoria.MAXIMO_TEXTO) {
			val diccionario = Diccionario.archivo(this).lastModified()
			Memoria.clave(texto, "${voz.id}|$idioma|$region|${ajustes.revision}|$diccionario", velocidad, ritmo, tono, volumen)
		} else null
		val recordada = clave?.let { Memoria.buscar(it) }
		if (recordada != null) {
			salida.entera(recordada)
			salida.terminar()
			Log.i(ETIQUETA, "Frase de ${texto.length} car. con $quien: de memoria, %d ms%s".format((System.nanoTime() - llegada) / 1_000_000L, if (parar) ", cortada" else ""))
			return
		}

		val trozos = try {
			Preparador.preparar(this, texto, idioma, region, voz.id, ajustes)
		} catch (e: Exception) {
			Log.e(ETIQUETA, "No se pudo preparar el texto; se lee tal cual", e)
			listOf(Trozo.Habla(texto.toString()))
		}
		if (trozos.none { it is Trozo.Habla }) {
			salida.vacio()
			return
		}
		val semilla = if (ajustes.semillaFija) SEMILLA else Random.nextLong()
		val normalizar = if (ajustes.normalizar) idioma else "none"
		if (clave != null) salida.grabar()

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
		val preparado = System.nanoTime()
		var primeraPieza = 0L
		var completa = false
		generador.empezar()
		// Al empezar una frase el motor entrega la voz a menos de la mitad de su ritmo normal:
		// Sonic arranca a una velocidad que el motor aguanta desde el primer trozo y luego sube
		// (pero nunca a menos del 70 % de la pedida: empezar mucho más lento y acelerar se nota)
		val arranque = minOf(velocidad, maxOf(generacion / ARRANQUE_LENTO, velocidad * ARRANQUE_MINIMO, 1f))
		// La pista de audio se pone en marcha ya, mientras el motor prepara la frase
		salida.preparar(cebar = true, colchonMs = colchon(trozos, generacion, velocidad, arranque, porModelo))
		var primera = true
		val frenar = limitar
		var ventajaMinima = Long.MAX_VALUE
		try {
			var todas = true
			for (t in trozos) {
				if (parar) break
				when (t) {
					// El silencio también se acelera con la velocidad
					is Trozo.Pausa -> salida.silencio((t.ms / pedida.coerceAtLeast(0.25f)).toInt())
					is Trozo.Habla -> {
						// Solo la primera frase arranca despacio: las siguientes ya llevan ventaja
						val proc = Procesador(
							Motor.frecuencia, velocidad, tono * (1f + t.subirTono / 100f), volumen, ajustes.recortarSilencios,
							arranque = if (primera) arranque else velocidad,
						)
						primera = false
						val resultado = generador.recibir { muestras ->
							if (primeraPieza == 0L) primeraPieza = System.nanoTime()
							// Voz que queda por delante de lo que suena: la ya entregada y la que espera en
							// la cola. Si es poca (al empezar cada frase o cada trozo de un texto largo el
							// motor se retrasa), Sonic afloja hasta un 22 % para no quedarse sin audio: se
							// nota mucho menos que un hueco, que suena a chisporroteo.
							if (frenar) {
								val ventaja = salida.ventajaMs() + (generador.pendienteMs / velocidad).toLong()
								proc.frenar(Procesador.FRENO_MINIMO + (1f - Procesador.FRENO_MINIMO) * (ventaja / VENTAJA_COMODA_MS).coerceIn(0f, 1f))
								if (ventaja < ventajaMinima) ventajaMinima = ventaja
							}
							!parar && salida.escribir(proc.procesar(muestras, muestras.size))
						}
						if (parar || resultado == Generador.Resultado.CORTADA) {
							todas = false
							break
						}
						if (resultado == Generador.Resultado.FALLO) {
							Log.e(ETIQUETA, "El motor falló: ${Motor.error}")
							salida.fallo()
							return
						}
						if (!salida.escribir(proc.terminar()) || !salida.soltar()) {
							todas = false
							break
						}
					}
				}
			}
			completa = todas && !parar
		} finally {
			generador.cerrar()
		}
		salida.terminar()
		if (completa && clave != null) salida.grabado()?.let { Memoria.guardar(clave, it) }
		Log.i(
			ETIQUETA,
			"Frase de ${texto.length} car. con $quien ($pedido): velocidad %.2f%s (sistema ${request.speechRate}, modelo %.2f, Sonic %.2f), motor %.2fx, primera voz %d ms (preparar %d, motor %d, colchón %d), huecos %d (%d ms), ventaja mínima %d ms, total %d ms%s".format(
				pedida, if (limitada) " limitada" else "", ritmo, velocidad, Motor.velocidadGeneracion, salida.primerSonidoMs(llegada),
				(preparado - llegada) / 1_000_000L, if (primeraPieza == 0L) -1 else (primeraPieza - preparado) / 1_000_000L,
				salida.retenidoMs, salida.huecos, salida.huecosMs, if (ventajaMinima == Long.MAX_VALUE) 0 else ventajaMinima,
				(System.nanoTime() - llegada) / 1_000_000L, if (parar) ", cortada" else "",
			),
		)
	}

	/**
	 * Voz que hay que tener por delante de lo que suena para arrancar sin cortes, en milisegundos.
	 *
	 * Al empezar una frase el motor va más lento que después: los primeros cinco trozos (de 80 ms)
	 * tardan cada uno unas [ARRANQUE_LENTO] veces más. Si a la velocidad de [arranque] Sonic los
	 * gasta antes de que llegue el siguiente, hace falta guardar la diferencia; si no, basta medio
	 * trozo y la voz sale casi al momento. Y si ni con el tope de velocidad llega (móviles muy
	 * lentos), se guarda lo que le faltará a la frase entera.
	 */
	private fun colchon(trozos: List<Trozo>, generacion: Float, velocidad: Float, arranque: Float, porModelo: Float): Int {
		val holgura = generacion / velocidad.coerceAtLeast(0.1f)
		val tarda = ARRANQUE_LENTO * 80f / generacion.coerceAtLeast(0.1f)
		val dura = 80f / arranque.coerceAtLeast(0.1f)
		val inicio = 50f + 5f * (tarda - dura).coerceAtLeast(0f)
		// Unos 14 caracteres por segundo a la velocidad natural de las voces
		val caracteres = trozos.sumOf { if (it is Trozo.Habla) it.texto.length else 0 }
		val sonara = caracteres / 14f / porModelo / velocidad.coerceAtLeast(0.1f)
		val falta = (sonara * 1000f * (1f - holgura * 0.9f)).coerceIn(0f, 1500f)
		return (inicio.coerceAtMost(300f) + falta).toInt()
	}

	companion object {
		private const val ETIQUETA = "Fonaria.Servicio"
		/** Semilla fija: la misma frase suena igual cada vez (como un lector de pantalla clásico). */
		const val SEMILLA = 4242424242424242L
		/** Parámetro de speak() con el id de la voz que debe sonar (lo usan las pantallas de la app). */
		const val PARAM_VOZ = "com.xebolax.fonaria.VOZ"
		/** Tope de seguridad del bloqueo de suspensión (se suelta al acabar cada lectura). */
		private const val DESPIERTO_MAXIMO_MS = 10 * 60 * 1000L
		/** El motor debe generar al menos un 15 % más deprisa de lo que Sonic consume. */
		const val MARGEN_GENERACION = 1.15f
		/** Cuántas veces más tarda el motor en cada trozo al empezar una frase (medido: 2 a 2,5). */
		const val ARRANQUE_LENTO = 2.5f
		/** Con esta voz por delante de lo que suena (ms), Sonic va a toda la velocidad pedida. */
		const val VENTAJA_COMODA_MS = 800f
		/** Parte de la velocidad pedida con la que, como poco, empieza una frase. */
		const val ARRANQUE_MINIMO = 0.7f
	}
}
