package com.xebolax.fonaria.datos

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.ContextCompat
import com.xebolax.fonaria.BuildConfig

/**
 * Todos los ajustes de Fonaria, en un solo archivo de preferencias guardado en el
 * almacenamiento protegido del dispositivo: así el motor los lee también en la pantalla de
 * bloqueo justo después de reiniciar (Direct Boot), que es donde un lector de pantalla hace
 * falta primero.
 *
 * Los ajustes de sonido son generales y cada voz puede tener los suyos («voz.<id>.<clave>»):
 * lo que una voz no cambia, lo toma de los generales.
 *
 * [revision] cambia cada vez que se toca un ajuste, para que el servicio de voz sepa cuándo
 * volver a leerlos sin hacerlo en cada frase.
 */
class Ajustes(contexto: Context) {

	val p: SharedPreferences = preferencias(contexto)

	// --- Voces -------------------------------------------------------------------------

	/** Voz elegida para un idioma y región («es-ES»), o la del idioma («es»), o null. */
	fun vozPara(idioma: String, region: String): String? =
		texto("voz_${idioma}_${region.uppercase()}").ifEmpty { texto("voz_$idioma") }.ifEmpty { null }

	fun ponerVoz(idioma: String, region: String?, id: String) {
		p.edit().apply {
			if (region.isNullOrEmpty()) putString("voz_$idioma", id) else putString("voz_${idioma}_${region.uppercase()}", id)
		}.apply()
	}

	/** Si una app pide una voz de Fonaria concreta, se usa esa en vez de la elegida aquí. */
	val respetarVozPedida get() = p.getBoolean("respetar_voz_pedida", false)

	/** Si falta la voz de un idioma, se habla con una voz instalada en vez de quedarse callado. */
	val vozDeReserva get() = p.getBoolean("voz_reserva", true)

	// --- Sonido (generales o de una voz) -------------------------------------------------

	/** Valor de sonido de una voz: el suyo si lo tiene; si no, el general. */
	fun sonido(voz: String?, clave: String, defecto: Int): Int {
		if (voz != null) {
			val propia = "voz.$voz.$clave"
			if (p.contains(propia)) return entero(propia, defecto)
		}
		return entero(clave, defecto)
	}

	fun tienePropio(voz: String, clave: String) = p.contains("voz.$voz.$clave")

	fun ponerPropio(voz: String, clave: String, valor: Int?) {
		p.edit().apply { if (valor == null) remove("voz.$voz.$clave") else putInt("voz.$voz.$clave", valor) }.apply()
	}

	fun quitarPropios(voz: String) {
		val e = p.edit()
		p.all.keys.filter { it.startsWith("voz.$voz.") }.forEach { e.remove(it) }
		e.apply()
	}

	/** Lo que cambia según la voz. */
	fun parametros(voz: String?) = Parametros(
		velocidad = sonido(voz, VELOCIDAD, 100),
		tono = sonido(voz, TONO, 100),
		volumen = sonido(voz, VOLUMEN, 100),
		ritmo = sonido(voz, RITMO, 0),
		variacion = sonido(voz, VARIACION, 30),
	)

	/** Usar la velocidad que manda el sistema o la app que habla (además de la de Fonaria). */
	/**
	 * Cómo se usa la velocidad que manda TalkBack o Android: «suave» (por defecto) la suaviza,
	 * porque los motores clásicos no aceleran en línea recta y un 300 % pensado para ellos
	 * sería demasiado para una voz neuronal; «completa» la aplica tal cual; «no» la ignora.
	 * Migra el antiguo interruptor velocidad_sistema (1.0.0).
	 */
	val escalaVelocidad: String
		get() = texto("escala_velocidad").ifEmpty { if (p.getBoolean("velocidad_sistema", true)) "suave" else "no" }

	/** El factor de velocidad que corresponde a la velocidad del sistema [porCiento] (100 = normal). */
	fun factorSistema(porCiento: Int): Float = factorSistema(porCiento, escalaVelocidad)
	val tonoDelSistema get() = p.getBoolean("tono_sistema", true)
	val semillaFija get() = p.getBoolean("semilla_fija", true)
	val igualarVolumen get() = p.getBoolean("igualar_volumen", false)
	val recortarSilencios get() = p.getBoolean("recortar_silencios", true)
	val pausaFrases get() = entero("pausa_frases", 150)
	val pausaParrafos get() = entero("pausa_parrafos", 350)
	/** Guía sin clasificador ×10 (10 = desactivada). */
	val guia get() = entero("guia", 10)

	// --- Texto -------------------------------------------------------------------------

	val normalizar get() = p.getBoolean("normalizar", true)
	val puntuacion get() = texto("puntuacion", "ninguna")
	val emojis get() = p.getBoolean("emojis", true)
	val deletrear get() = p.getBoolean("deletrear", true)
	val tonoMayusculas get() = entero("tono_mayusculas", 20)
	val diccionarios get() = p.getBoolean("diccionarios", true)
	val dividirFrases get() = p.getBoolean("dividir_frases", true)
	val respuestaRapida get() = p.getBoolean("respuesta_rapida", false)

	// --- Motor -------------------------------------------------------------------------

	/** q8 (integrado), q4k (ligero) o f32 (máxima calidad). */
	val modelo get() = texto("modelo", "q8")
	val hilos get() = entero("hilos", 0)
	val pasos get() = entero("pasos", 1)
	/** Umbral de fin de frase ×10; 0 = el del modelo. */
	val umbralFin get() = entero("umbral_fin", 0)
	val mimiQ8 get() = p.getBoolean("mimi_q8", false)
	val tokensTrozo get() = entero("tokens_trozo", 0)
	/** Cuánto audio puede ir por delante de lo que se oye. */
	val adelantoMs get() = entero("adelanto", 400)

	/** Lo que obliga a recargar el modelo si cambia. */
	fun claveCarga() = "$modelo|$hilos|$pasos|$umbralFin|$mimiQ8"

	// --- Actualizaciones y descargas -------------------------------------------------------

	val buscarActualizaciones get() = p.getBoolean("buscar_actualizaciones", true)
	val urlActualizaciones get() = texto("url_actualizaciones", BuildConfig.URL_ACTUALIZACIONES).trim().trimEnd('/')
	/** servidor, github, ambos o personalizada. */
	val fuenteActualizaciones get() = texto("fuente_actualizaciones", "ambos")
	val urlPersonalizada get() = texto("url_personalizada").trim().trimEnd('/')
	/** De dónde se bajan las voces: servidor, huggingface o ambos (el servidor y, si falla, Hugging Face). */
	val origenVoces get() = texto("origen_voces", "ambos")

	// --- General -----------------------------------------------------------------------

	/** sistema, es o en. */
	val idiomaApp get() = texto("idioma_app", "sistema")
	/** sistema, claro u oscuro. */
	val tema get() = texto("tema", "sistema")
	var avisoIdiomaVisto: String
		get() = texto("aviso_idioma_visto")
		set(v) = p.edit().putString("aviso_idioma_visto", v).apply()
	var bienvenidaVista: Boolean
		get() = p.getBoolean("bienvenida_vista", false)
		set(v) = p.edit().putBoolean("bienvenida_vista", v).apply()

	// --- Utilidades ------------------------------------------------------------------------

	/** Cambia con cada ajuste tocado (lo aumenta el escuchador de [preferencias]). */
	val revision get() = Companion.revision

	fun texto(clave: String, defecto: String = ""): String = try {
		p.getString(clave, defecto) ?: defecto
	} catch (e: ClassCastException) {
		p.all[clave]?.toString() ?: defecto
	}

	/** Los deslizadores guardan Int y las listas guardan texto: se acepta cualquiera de los dos. */
	fun entero(clave: String, defecto: Int): Int = when (val v = p.all[clave]) {
		is Int -> v
		is Long -> v.toInt()
		is String -> v.toIntOrNull() ?: defecto
		is Float -> v.toInt()
		else -> defecto
	}

	companion object {
		const val ARCHIVO = "ajustes"
		const val VELOCIDAD = "velocidad"
		const val TONO = "tono"
		const val VOLUMEN = "volumen"
		const val RITMO = "ritmo"
		const val VARIACION = "variacion"
		/** Ajustes de sonido que cada voz puede tener propios. */
		val SONIDO = listOf(VELOCIDAD, TONO, VOLUMEN, RITMO, VARIACION)

		@Volatile
		var revision = 0
			private set

		private var escuchador: SharedPreferences.OnSharedPreferenceChangeListener? = null

		/** Exponente de la escala «suave»: 200 % → 1,5 ×, 310 % → 2 ×, 500 % → 2,6 ×. */
		const val EXPONENTE_SUAVE = 0.6

		fun factorSistema(porCiento: Int, escala: String): Float {
			val f = (porCiento.coerceIn(10, 600)) / 100.0
			return when (escala) {
				"no" -> 1f
				"completa" -> f.toFloat()
				else -> Math.pow(f, EXPONENTE_SUAVE).toFloat()
			}
		}

		/** Contexto de almacenamiento protegido del dispositivo (Android 7+); antes, el normal. */
		fun contextoProtegido(contexto: Context): Context =
			if (Build.VERSION.SDK_INT >= 24) ContextCompat.createDeviceProtectedStorageContext(contexto) ?: contexto else contexto

		@Synchronized
		fun preferencias(contexto: Context): SharedPreferences {
			val prefs = contextoProtegido(contexto.applicationContext ?: contexto).getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
			if (escuchador == null) {
				// Referencia fuerte: SharedPreferences guarda los escuchadores con referencias débiles
				escuchador = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
				prefs.registerOnSharedPreferenceChangeListener(escuchador)
			}
			return prefs
		}
	}
}

/** Los valores de sonido de una voz, en las unidades de la pantalla de ajustes. */
data class Parametros(
	/** Por ciento de la velocidad normal. */
	val velocidad: Int,
	/** Por ciento del tono normal. */
	val tono: Int,
	/** Por ciento del volumen normal. */
	val volumen: Int,
	/** Ritmo natural del modelo, de -50 (más rápido) a 50 (más pausado). */
	val ritmo: Int,
	/** Variación de la entonación, de 0 a 100 (temperatura × 100). */
	val variacion: Int,
)
