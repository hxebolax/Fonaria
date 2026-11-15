package com.xebolax.fonaria.voces

import android.content.Context
import android.util.Log
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.red.Descargador
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File

/** Un modelo que se puede descargar (assets/modelos.json). */
data class ModeloDescargable(val id: String, val archivo: String, val tamano: Long, val sha256: String, val incluido: Boolean)

/**
 * Descarga de voces de otros idiomas y de los modelos opcionales.
 *
 * Las voces vienen del servidor de Fonaria (carpeta voces/) o, si falla o se elige así, del
 * catálogo público de Gradium en Hugging Face, que es de donde salen. Los modelos opcionales
 * (ligero y máxima calidad) solo del servidor: en Hugging Face están tras una aceptación de
 * licencia que la app no puede hacer.
 *
 * Todo se comprueba con el SHA-256 del catálogo antes de usarlo.
 */
object Descargas {
	private const val ETIQUETA = "Fonaria.Descargas"
	const val HUGGING_FACE = "https://huggingface.co/gradium/phonon-voices/resolve/main/voices"

	private fun servidor(contexto: Context) = Ajustes(contexto).urlActualizaciones

	/** Direcciones de una voz, en el orden en que se prueban. */
	fun urls(contexto: Context, voz: Voz): List<String> {
		val propio = "${servidor(contexto)}/voces/${voz.archivo}"
		val hf = "$HUGGING_FACE/${voz.archivo}"
		return when (Ajustes(contexto).origenVoces) {
			"servidor" -> listOf(propio)
			"huggingface" -> listOf(hf)
			else -> listOf(propio, hf)
		}
	}

	/** Descarga una voz del catálogo. Si la primera dirección falla, prueba la siguiente. */
	suspend fun descargar(contexto: Context, voz: Voz, progreso: (Long, Long) -> Unit = { _, _ -> }): File {
		val destino = File(Almacen.carpetaVoces(contexto), voz.archivo)
		var ultimo: Exception? = null
		for (url in urls(contexto, voz)) {
			try {
				return Descargador.bajar(url, destino, voz.tamano, voz.sha256, progreso)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.w(ETIQUETA, "No se pudo bajar ${voz.nombre} de $url", e)
				ultimo = e
			}
		}
		throw ultimo ?: IllegalStateException("sin direcciones")
	}

	fun modelos(contexto: Context): List<ModeloDescargable> = try {
		val o = JSONObject(contexto.assets.open("modelos.json").bufferedReader().use { it.readText() })
		val lista = o.getJSONArray("modelos")
		(0 until lista.length()).map {
			val m = lista.getJSONObject(it)
			ModeloDescargable(m.getString("id"), m.getString("archivo"), m.optLong("tamano"), m.optString("sha256"), m.optBoolean("incluido"))
		}
	} catch (e: Exception) {
		Log.e(ETIQUETA, "No se pudo leer modelos.json", e)
		emptyList()
	}

	suspend fun descargarModelo(contexto: Context, m: ModeloDescargable, progreso: (Long, Long) -> Unit): File {
		val destino = File(Almacen.carpetaModelo(contexto), m.archivo)
		return Descargador.bajar("${servidor(contexto)}/modelos/${m.archivo}", destino, m.tamano, m.sha256, progreso)
	}

	fun borrarModelo(contexto: Context, m: ModeloDescargable): Boolean =
		!m.incluido && File(Almacen.carpetaModelo(contexto), m.archivo).delete()
}
