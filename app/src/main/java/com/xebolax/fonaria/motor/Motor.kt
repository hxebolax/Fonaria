package com.xebolax.fonaria.motor

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.voces.Almacen
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * El modelo Phonon cargado, uno para toda la app: lo usan el servicio de voz del sistema y las
 * pantallas de la app (que hablan a través del servicio, así que no se carga dos veces).
 *
 * Cargar el modelo es lo lento (de medio segundo a varios en un móvil antiguo); después se
 * queda en memoria. Si cambia un ajuste que obliga a recargarlo (modelo, hilos, pasos...), se
 * recarga en la siguiente frase.
 *
 * Todas las llamadas al motor nativo pasan por [cerrojo]: el motor no admite dos a la vez.
 */
object Motor {
	private const val ETIQUETA = "Fonaria.Motor"
	private const val VOZ_PRUEBA = "__prueba_importacion__"

	private val cerrojo = ReentrantLock()
	private var manejador = 0L
	private var claveCarga = ""
	private var vocesRegistradas = emptyMap<String, String>()

	/** Frecuencia del audio del modelo (24 000 Hz). */
	@Volatile
	var frecuencia = 24000
		private set

	/** Idiomas del modelo cargado. */
	@Volatile
	var idiomas: List<String> = listOf("es", "en", "fr", "de", "pt")
		private set

	/** Por qué falló la última carga, o null. */
	@Volatile
	var error: String? = null
		private set

	/**
	 * Segundos de voz que genera el motor por segundo de cálculo en este móvil (media de las
	 * últimas frases). Con él, [ServicioVoz] sabe cuánto audio guardar antes de empezar a sonar
	 * para que la voz no se entrecorte cuando se habla más rápido de lo que el móvil genera.
	 */
	@Volatile
	var velocidadGeneracion = 1.5f
		private set

	/** Cuánto tardó la última carga, en milisegundos. */
	@Volatile
	var msCarga = 0L
		private set

	val cargado get() = manejador != 0L

	/**
	 * Deja el modelo listo con los ajustes actuales y las voces instaladas registradas.
	 * Devuelve false si no se pudo (el motivo queda en [error]). Bloquea: nunca en el hilo principal.
	 */
	fun asegurar(contexto: Context): Boolean = cerrojo.withLock {
		val ajustes = Ajustes(contexto)
		val clave = ajustes.claveCarga()
		if (manejador != 0L && clave == claveCarga) {
			registrarVoces(contexto)
			return true
		}
		if (!Nativo.cargar()) {
			error = "No se pudo cargar la biblioteca nativa: ${Nativo.errorCarga}"
			return false
		}
		if (!Almacen.preparar(contexto)) {
			error = "No se pudo preparar el modelo en el almacenamiento del móvil"
			return false
		}
		cerrar()
		val carpeta = Almacen.carpetaModelo(contexto)
		// Si el modelo elegido no está descargado, se usa el integrado
		val pesos = Almacen.archivoModelo(ajustes.modelo).takeIf { File(carpeta, it).isFile } ?: Almacen.archivoModelo("q8")
		val umbral = if (ajustes.umbralFin > 0) ajustes.umbralFin / 10f else Float.NaN
		val inicio = SystemClock.elapsedRealtime()
		val h = Nativo.abrir(carpeta.absolutePath, pesos, ajustes.pasos, umbral, ajustes.mimiQ8, ajustes.hilos)
		if (h == 0L) {
			error = Nativo.ultimoError()
			Log.e(ETIQUETA, "No se pudo cargar el modelo: $error")
			return false
		}
		msCarga = SystemClock.elapsedRealtime() - inicio
		manejador = h
		claveCarga = clave
		error = null
		frecuencia = Nativo.frecuencia(h)
		idiomas = Nativo.idiomas(h).toList()
		vocesRegistradas = emptyMap()
		registrarVoces(contexto)
		Log.i(ETIQUETA, "Modelo $pesos cargado en $msCarga ms; idiomas $idiomas; ${vocesRegistradas.size} voces")
		true
	}

	/** Da a conocer al motor las voces instaladas (las nuevas) y olvida las borradas. */
	private fun registrarVoces(contexto: Context) {
		val ahora = Almacen.instaladas(contexto).associate { it.id to Almacen.archivo(contexto, it).absolutePath }
		if (ahora == vocesRegistradas) return
		for ((id, _) in vocesRegistradas) if (id !in ahora) Nativo.olvidar(manejador, id)
		for ((id, ruta) in ahora) {
			if (vocesRegistradas[id] == ruta) continue
			if (!Nativo.registrar(manejador, id, ruta)) Log.w(ETIQUETA, "No se pudo registrar la voz $id: ${Nativo.ultimoError()}")
		}
		vocesRegistradas = ahora
	}

	/** Lo que hace falta para una frase. */
	data class Peticion(
		val texto: String,
		val voz: String,
		/** Idioma con el que se normaliza el texto, o «none». */
		val idioma: String,
		val temperatura: Float,
		val semilla: Long,
		val ritmo: Float,
		val guia: Float,
		val tokensTrozo: Int,
		val igualarVolumen: Boolean,
	)

	/**
	 * Habla una frase y entrega el audio a [receptor] según sale. Devuelve false si el motor
	 * falló (el motivo en [error]); si el receptor pidió parar, devuelve true.
	 */
	fun hablar(contexto: Context, p: Peticion, receptor: Nativo.Receptor): Boolean = cerrojo.withLock {
		if (!asegurar(contexto)) return false
		var muestras = 0L
		var primero = 0L
		var ultimo = 0L
		// Lo que el receptor tarda en aceptar el audio (cuando va muy por delante) no es del motor
		var esperando = 0L
		val medidor = Nativo.Receptor { pcm, n ->
			val ahora = SystemClock.elapsedRealtime()
			if (primero == 0L) primero = ahora
			muestras += n
			val sigue = receptor.audio(pcm, n)
			val despues = SystemClock.elapsedRealtime()
			esperando += despues - ahora
			ultimo = despues
			sigue
		}
		val ok = Nativo.hablar(
			manejador, p.texto, p.voz, p.idioma, p.temperatura, p.semilla, p.ritmo, p.guia,
			p.tokensTrozo, p.igualarVolumen, medidor,
		)
		// Se mide desde el primer audio (sin la preparación de la voz) y solo en frases de más de un segundo
		val segundos = muestras.toFloat() / frecuencia
		val trabajo = ultimo - primero - esperando
		if (ok && segundos > 1f && trabajo > 0) {
			val medida = (segundos / (trabajo / 1000f)).coerceIn(0.2f, 50f)
			velocidadGeneracion = velocidadGeneracion * 0.7f + medida * 0.3f
		}
		if (!ok) {
			error = Nativo.ultimoError()
			Log.e(ETIQUETA, "El motor no pudo decir ${p.texto.length} caracteres con ${p.voz}: $error")
		}
		ok
	}

	/**
	 * Comprueba que un archivo es una voz que este modelo entiende, cargándolo de verdad.
	 * Devuelve null si vale o el motivo si no.
	 */
	fun validarVoz(contexto: Context, archivo: File): String? = cerrojo.withLock {
		if (!asegurar(contexto)) return error ?: "motor no disponible"
		val ok = Nativo.cargar(manejador, VOZ_PRUEBA, archivo.absolutePath)
		Nativo.olvidar(manejador, VOZ_PRUEBA)
		if (ok) null else Nativo.ultimoError() ?: "archivo de voz no válido"
	}

	/** Libera el modelo (por ejemplo, para recargarlo con otros pesos). */
	fun cerrar() = cerrojo.withLock {
		if (manejador != 0L) {
			Nativo.liberar(manejador)
			manejador = 0L
			claveCarga = ""
			vocesRegistradas = emptyMap()
		}
	}

	/** Diagnóstico para la pantalla Acerca de. */
	fun diagnostico(): String = buildString {
		append("Biblioteca: ").append(Nativo.cargada ?: "sin cargar")
		if (Nativo.cargada != null) append(" · ").append(runCatching { Nativo.version() }.getOrDefault("?"))
		append("\nModelo: ").append(if (cargado) "cargado en $msCarga ms" else "sin cargar")
		error?.let { append("\nÚltimo error: ").append(it) }
	}
}
