package com.xebolax.fonaria.voces

import android.content.Context
import android.util.Log
import com.xebolax.fonaria.BuildConfig
import com.xebolax.fonaria.datos.Ajustes
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Dónde viven el modelo y las voces en el móvil, en el almacenamiento protegido del dispositivo
 * (se puede leer en la pantalla de bloqueo nada más reiniciar):
 *
 *     fonaria/modelo/       config.json, tokenizer.json y los pesos (model.q8.gguf y los que se bajen)
 *     fonaria/voces/        <id>.safetensors: las españolas (sacadas del APK) y las descargadas
 *     fonaria/importadas/   <id>/voz.safetensors y voz.json: voces propias de Gradium Studio
 *
 * El modelo y las voces del APK se copian a su sitio la primera vez y tras cada actualización
 * de la app ([preparar]); el motor nativo solo sabe leer archivos.
 */
object Almacen {
	private const val ETIQUETA = "Fonaria.Almacen"
	private const val MARCA = "preparado.txt"

	fun base(contexto: Context) = File(Ajustes.contextoProtegido(contexto).filesDir, "fonaria")
	fun carpetaModelo(contexto: Context) = File(base(contexto), "modelo")
	fun carpetaVoces(contexto: Context) = File(base(contexto), "voces")
	fun carpetaImportadas(contexto: Context) = File(base(contexto), "importadas")

	/** ¿Están el modelo y las voces del APK en su sitio y son de esta versión de la app? */
	fun preparado(contexto: Context): Boolean {
		val marca = File(carpetaModelo(contexto), MARCA)
		return marca.isFile && marca.readText().trim() == BuildConfig.VERSION_CODE.toString() &&
			File(carpetaModelo(contexto), "config.json").isFile
	}

	/**
	 * Copia el modelo y las voces del APK si falta algo o la app se ha actualizado. Tarda un
	 * par de segundos la primera vez; después no hace nada. Seguro desde varios hilos.
	 */
	@Synchronized
	fun preparar(contexto: Context): Boolean {
		if (preparado(contexto)) return true
		val modelo = carpetaModelo(contexto).apply { mkdirs() }
		val voces = carpetaVoces(contexto).apply { mkdirs() }
		return try {
			val am = contexto.assets
			for (nombre in am.list("modelo").orEmpty()) copiarAsset(contexto, "modelo/$nombre", File(modelo, nombre))
			for (nombre in am.list("voces").orEmpty()) copiarAsset(contexto, "voces/$nombre", File(voces, nombre))
			File(modelo, MARCA).writeText(BuildConfig.VERSION_CODE.toString())
			Log.i(ETIQUETA, "Modelo y voces del APK listos en ${base(contexto)}")
			true
		} catch (e: IOException) {
			Log.e(ETIQUETA, "No se pudo copiar el modelo o las voces del APK", e)
			false
		}
	}

	private fun copiarAsset(contexto: Context, ruta: String, destino: File) {
		val tamano = try {
			contexto.assets.openFd(ruta).use { it.length }
		} catch (e: IOException) {
			-1L // comprimido en el APK: no se sabe sin leerlo
		}
		if (destino.isFile && tamano > 0 && destino.length() == tamano) return
		val temporal = File(destino.parentFile, destino.name + ".parte")
		contexto.assets.open(ruta).use { entrada -> temporal.outputStream().use { entrada.copyTo(it, 1 shl 20) } }
		if (!temporal.renameTo(destino)) {
			destino.delete()
			if (!temporal.renameTo(destino)) throw IOException("No se pudo guardar $destino")
		}
	}

	/** Archivo de pesos de un modelo (q8, q4k, f32). */
	fun archivoModelo(id: String) = when (id) {
		"q4k" -> "model.q4k.gguf"
		"f32" -> "model.safetensors"
		else -> "model.q8.gguf"
	}

	fun modeloInstalado(contexto: Context, id: String) = File(carpetaModelo(contexto), archivoModelo(id)).isFile

	// --- Voces instaladas --------------------------------------------------------------

	@Volatile
	private var cacheInstaladas: Pair<String, List<Voz>>? = null
	@Volatile
	private var cambios = 0

	/** Avisa de que algo de las voces ha cambiado sin tocar las carpetas (nombre o idioma de una importada). */
	fun cambiado() {
		cambios++
	}

	/**
	 * Todas las voces que se pueden usar ahora: del catálogo con su archivo presente, e importadas.
	 * Se guarda en memoria y solo se vuelve a mirar el disco si cambian las carpetas (al descargar,
	 * borrar o importar): el servicio de voz lo pregunta en cada frase.
	 */
	fun instaladas(contexto: Context): List<Voz> {
		val carpeta = carpetaVoces(contexto)
		val clave = "${carpeta.lastModified()}|${carpetaImportadas(contexto).lastModified()}|$cambios"
		cacheInstaladas?.let { (k, lista) -> if (k == clave) return lista }
		val delCatalogo = Catalogo.voces(contexto).filter { File(carpeta, it.archivo).isFile }
		val lista = delCatalogo + importadas(contexto)
		cacheInstaladas = clave to lista
		return lista
	}

	fun archivo(contexto: Context, voz: Voz): File =
		if (voz.importada) File(File(carpetaImportadas(contexto), voz.id), "voz.safetensors")
		else File(carpetaVoces(contexto), voz.archivo)

	fun instalada(contexto: Context, voz: Voz) = archivo(contexto, voz).isFile

	/** Borra una voz descargada o importada. Las del APK no se pueden borrar. */
	fun borrar(contexto: Context, voz: Voz): Boolean {
		if (voz.incluida) return false
		cambiado()
		return if (voz.importada) File(carpetaImportadas(contexto), voz.id).deleteRecursively()
		else File(carpetaVoces(contexto), voz.archivo).delete()
	}

	// --- Voces importadas ----------------------------------------------------------------

	fun importadas(contexto: Context): List<Voz> {
		val raiz = carpetaImportadas(contexto)
		return raiz.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { carpeta ->
			try {
				val o = JSONObject(File(carpeta, "voz.json").readText())
				val tensor = File(carpeta, "voz.safetensors")
				if (!tensor.isFile) return@mapNotNull null
				Voz(
					id = carpeta.name,
					nombre = o.getString("nombre"),
					idioma = o.getString("idioma"),
					region = o.optString("region"),
					personaje = false,
					genero = o.optString("genero"),
					edad = "",
					descripcion = "",
					descripcionEs = "",
					archivo = tensor.name,
					tamano = tensor.length(),
					sha256 = "",
					incluida = false,
					importada = true,
				)
			} catch (e: Exception) {
				Log.w(ETIQUETA, "Voz importada ilegible en $carpeta", e)
				null
			}
		}.sortedBy { it.nombre.lowercase() }
	}

	/** Guarda una copia de un archivo de voz con su nombre e idioma. Devuelve la voz nueva. */
	fun importar(contexto: Context, datos: ByteArray, nombre: String, idioma: String, region: String, genero: String): Voz {
		require(datos.size in 1..MAX_VOZ) { "Tamaño de archivo de voz no válido" }
		cambiado()
		val id = "importada-" + UUID.randomUUID().toString().replace("-", "")
		val carpeta = File(carpetaImportadas(contexto), id).apply { mkdirs() }
		File(carpeta, "voz.safetensors").writeBytes(datos)
		File(carpeta, "voz.json").writeText(
			JSONObject().put("nombre", nombre).put("idioma", idioma).put("region", region).put("genero", genero).toString(),
		)
		return importadas(contexto).first { it.id == id }
	}

	/** Cambia el nombre o el idioma de una voz importada. */
	fun editarImportada(contexto: Context, voz: Voz, nombre: String, idioma: String, region: String) {
		val f = File(File(carpetaImportadas(contexto), voz.id), "voz.json")
		val o = JSONObject(f.readText()).put("nombre", nombre).put("idioma", idioma).put("region", region)
		f.writeText(o.toString())
		cambiado()
	}

	/** Las voces de Gradium pesan 256 KB; se admite hasta 4 MB, como Phonon para NVDA. */
	const val MAX_VOZ = 4 * 1024 * 1024
}
