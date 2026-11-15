package com.xebolax.fonaria.actualizaciones

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.xebolax.fonaria.BuildConfig
import com.xebolax.fonaria.R
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.datos.FalloEstado
import com.xebolax.fonaria.datos.FalloIo
import com.xebolax.fonaria.red.Descargador
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest

/** Versión publicada en el servidor (version.json) o en GitHub (la última release). */
data class VersionPublicada(
	val version: String,
	val versionCode: Int,
	val archivo: String,
	val sha256: String,
	val tamano: Long,
	val fecha: String,
	val notas: String,
	val base: String,
	/** Dirección del APK si no es base/archivo (GitHub). */
	val urlDirecta: String = "",
	/** De dónde viene, para decirlo al ofrecerla: el servidor o «GitHub». */
	val origen: String = "",
) {
	val url: String get() = urlDirecta.ifEmpty { "$base/$archivo" }
}

/**
 * Actualizaciones desde el servidor propio, desde GitHub, desde los dos (la versión más nueva;
 * si uno no responde, el otro) o desde una dirección personalizada (Ajustes, Actualizaciones):
 *
 * 1. Se lee `version.json` (o la última release de GitHub, con su version.json adjunto si lo
 *    tiene) y se compara con la versión instalada.
 * 2. Se descarga `Fonaria_v<versión>.apk` a la caché de la app.
 * 3. Se comprueba que el archivo es el publicado (tamaño y SHA-256) y que es Fonaria, más nueva
 *    y firmada con la MISMA clave que la instalada. Android además rechaza instalar encima una
 *    app con otra firma, así que un servidor manipulado no puede colar otra aplicación.
 * 4. El instalador del sistema pide la confirmación al usuario.
 */
object Actualizador {
	private const val ETIQUETA = "Fonaria.Actualizador"

	fun carpeta(contexto: Context) = File(contexto.cacheDir, "actualizaciones").apply { mkdirs() }

	/** "1.2.10" > "1.2.9": compara número a número. */
	fun compararVersiones(a: String, b: String): Int {
		val x = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
		val y = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
		for (i in 0 until maxOf(x.size, y.size)) {
			val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
			if (c != 0) return c
		}
		return 0
	}

	/**
	 * La versión publicada en la fuente elegida en Ajustes (con un reintento si la red falla).
	 * Con «Los dos», la más nueva de las que respondan. Lanza excepción si no se puede leer ninguna.
	 */
	suspend fun versionPublicada(contexto: Context): VersionPublicada = withContext(Dispatchers.IO) {
		val p = Ajustes(contexto)
		val servidor = p.urlActualizaciones
		when (p.fuenteActualizaciones) {
			"servidor" -> conReintento { leerVersion(servidor) }
			"github" -> conReintento { leerGitHub(BuildConfig.REPO_GITHUB) }
			"personalizada" -> {
				val url = p.urlPersonalizada
				if (!url.startsWith("http")) throw FalloEstado(R.string.act_sin_direccion)
				val repo = repoDeGitHub(url)
				conReintento { if (repo != null) leerGitHub(repo) else leerVersion(url) }
			}
			else -> {
				val pedidas = listOf(
					async { runCatching { conReintento { leerVersion(servidor) } } },
					async { runCatching { conReintento { leerGitHub(BuildConfig.REPO_GITHUB) } } },
				).awaitAll()
				pedidas.mapNotNull { it.getOrNull() }.maxWithOrNull(::compararPublicadas)
					?: throw (pedidas.first().exceptionOrNull() ?: IllegalStateException())
			}
		}
	}

	private fun compararPublicadas(a: VersionPublicada, b: VersionPublicada): Int =
		if (a.versionCode > 0 && b.versionCode > 0) a.versionCode.compareTo(b.versionCode) else compararVersiones(a.version, b.version)

	private suspend fun <T> conReintento(leer: () -> T): T {
		try {
			return leer()
		} catch (e: IOException) {
			Log.w(ETIQUETA, "No se pudo leer la versión publicada", e)
			delay(1500)
		}
		return leer()
	}

	/** «https://github.com/usuario/repositorio» → «usuario/repositorio» (null si no es de GitHub). */
	fun repoDeGitHub(url: String): String? =
		Regex("^https?://(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?(?:/.*)?$").find(url.trim())
			?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }

	/**
	 * La última release publicada de un repositorio de GitHub: su etiqueta (v1.2.3), sus notas y
	 * el APK adjunto. Si la release lleva también un version.json, de él salen el versionCode y
	 * las novedades.
	 */
	private fun leerGitHub(repo: String): VersionPublicada {
		val c = Descargador.conexion("https://api.github.com/repos/$repo/releases/latest", conectar = 8_000)
		c.setRequestProperty("Accept", "application/vnd.github+json")
		c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
		val o = try {
			when (val codigo = c.responseCode) {
				200 -> JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
				404 -> throw FalloEstado(R.string.act_github_sin_versiones, repo)
				403, 429 -> throw FalloIo(R.string.act_github_limite)
				else -> throw FalloEstado(R.string.servidor_respondio, codigo)
			}
		} finally {
			c.disconnect()
		}
		val adjuntos = o.optJSONArray("assets") ?: JSONArray()
		val lista = (0 until adjuntos.length()).map { adjuntos.getJSONObject(it) }
		val apk = lista.firstOrNull { Regex("[A-Za-z0-9._-]+\\.apk").matches(it.optString("name")) }
			?: throw FalloEstado(R.string.act_github_sin_apk)
		val urlApk = apk.optString("browser_download_url")
		if (!urlApk.startsWith("https://github.com/")) throw FalloEstado(R.string.act_github_sin_apk)
		val json = lista.firstOrNull { it.optString("name") == "version.json" }?.let { a ->
			runCatching {
				val cj = Descargador.conexion(a.optString("browser_download_url"), conectar = 8_000)
				try {
					if (cj.responseCode == 200) JSONObject(cj.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }) else null
				} finally {
					cj.disconnect()
				}
			}.getOrNull()
		}
		val version = json?.optString("version")?.trim()?.takeIf { it.isNotEmpty() }
			?: o.optString("tag_name").trim().removePrefix("v").removePrefix("V")
		if (version.isEmpty()) throw FalloEstado(R.string.act_version_no_valida)
		val digest = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
		return VersionPublicada(
			version = version,
			versionCode = json?.optInt("versionCode", 0) ?: 0,
			archivo = apk.optString("name"),
			sha256 = (digest ?: json?.optString("sha256")).orEmpty().trim().lowercase(),
			tamano = apk.optLong("size", 0L),
			fecha = o.optString("published_at").take(10),
			notas = json?.optString("notas")?.trim()?.takeIf { it.isNotEmpty() } ?: o.optString("body").trim(),
			base = "https://github.com/$repo",
			urlDirecta = urlApk,
			origen = "GitHub",
		)
	}

	private fun leerVersion(base: String): VersionPublicada {
		val c = Descargador.conexion("$base/version.json?t=${System.currentTimeMillis()}", conectar = 8_000)
		try {
			if (c.responseCode != 200) throw FalloEstado(R.string.servidor_respondio, c.responseCode)
			val o = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
			val version = o.optString("version").trim()
			val archivo = o.optString("file").trim()
			// Solo un nombre de archivo, nunca una ruta ni otra web
			if (version.isEmpty() || !Regex("[A-Za-z0-9._-]+\\.apk").matches(archivo)) throw FalloEstado(R.string.act_version_no_valida)
			return VersionPublicada(
				version = version,
				versionCode = o.optInt("versionCode", 0),
				archivo = archivo,
				sha256 = o.optString("sha256").trim().lowercase(),
				tamano = o.optLong("size", 0L),
				fecha = o.optString("date"),
				notas = o.optString("notas").trim(),
				base = base,
				origen = runCatching { URL(base).host }.getOrDefault(base),
			)
		} finally {
			c.disconnect()
		}
	}

	/** ¿La versión publicada es más nueva que la instalada? */
	fun esNueva(v: VersionPublicada): Boolean =
		if (v.versionCode > 0) v.versionCode > BuildConfig.VERSION_CODE
		else compararVersiones(v.version, BuildConfig.VERSION_NAME) > 0

	/** Descarga y verifica el APK. Devuelve el archivo listo para instalar. */
	suspend fun descargar(contexto: Context, v: VersionPublicada, progreso: (Long, Long) -> Unit): File {
		val carpeta = carpeta(contexto)
		carpeta.listFiles()?.filter { !it.name.endsWith(".parte") }?.forEach { it.delete() }
		val destino = Descargador.bajar(v.url, File(carpeta, v.archivo), v.tamano, v.sha256, progreso)
		try {
			comprobarApk(contexto, destino)
		} catch (e: Exception) {
			destino.delete()
			throw e
		}
		return destino
	}

	/** El APK debe ser Fonaria, más nueva y con la misma firma que la instalada. */
	private fun comprobarApk(contexto: Context, apk: File) {
		val pm = contexto.packageManager
		val banderas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
		else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
		val nueva = pm.getPackageArchiveInfo(apk.absolutePath, banderas) ?: throw FalloEstado(R.string.act_no_app)
		if (nueva.packageName != contexto.packageName) throw FalloEstado(R.string.act_no_fonaria)
		val codigoNuevo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) nueva.longVersionCode
		else @Suppress("DEPRECATION") nueva.versionCode.toLong()
		if (codigoNuevo <= BuildConfig.VERSION_CODE) throw FalloEstado(R.string.act_no_nueva)
		val instalada = pm.getPackageInfo(contexto.packageName, banderas)
		val firmasNuevas = firmas(nueva)
		// Si el sistema no da las firmas del archivo, el instalador las comprobará igualmente
		if (firmasNuevas.isNotEmpty() && firmasNuevas.intersect(firmas(instalada)).isEmpty()) throw FalloEstado(R.string.act_firma)
	}

	private fun firmas(info: PackageInfo): Set<String> {
		val lista = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.let {
			if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
		} else @Suppress("DEPRECATION") info.signatures
		return lista.orEmpty().map { firma ->
			MessageDigest.getInstance("SHA-256").digest(firma.toByteArray()).joinToString("") { "%02x".format(it) }
		}.toSet()
	}
}
