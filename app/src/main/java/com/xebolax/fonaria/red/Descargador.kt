package com.xebolax.fonaria.red

import android.util.Log
import com.xebolax.fonaria.R
import com.xebolax.fonaria.datos.FalloEstado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Descargas robustas: si la conexión se corta (datos móviles, cambio de red) se reintenta y se
 * sigue donde se quedó (petición Range), y al terminar se comprueban el tamaño y el SHA-256.
 * El archivo solo aparece en su sitio cuando está completo y verificado.
 */
object Descargador {
	private const val ETIQUETA = "Fonaria.Descargas"
	private const val AGENTE = "Fonaria-Android"
	private const val ESPERA_MS = 20_000
	/** Cortes seguidos SIN avanzar antes de rendirse (si avanza, el contador vuelve a cero). */
	private const val REINTENTOS = 8

	fun conexion(url: String, conectar: Int = ESPERA_MS): HttpURLConnection =
		(URL(url).openConnection() as HttpURLConnection).apply {
			connectTimeout = conectar
			readTimeout = ESPERA_MS
			useCaches = false
			instanceFollowRedirects = true
			setRequestProperty("User-Agent", AGENTE)
			setRequestProperty("Cache-Control", "no-cache")
			// Conexión nueva cada vez: una reutilizada que el servidor ya cerró corta la descarga
			setRequestProperty("Connection", "close")
		}

	/** Lee un archivo de texto pequeño (JSON). */
	suspend fun texto(url: String): String = withContext(Dispatchers.IO) {
		val c = conexion(url, conectar = 8_000)
		try {
			if (c.responseCode != 200) throw FalloEstado(R.string.servidor_respondio, c.responseCode)
			c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
		} finally {
			c.disconnect()
		}
	}

	/**
	 * Descarga [url] en [destino]. Si se da [tamano] o [sha256], se comprueban. [progreso]
	 * (bytes, total) se llama desde el hilo de fondo.
	 */
	suspend fun bajar(url: String, destino: File, tamano: Long = 0, sha256: String = "", progreso: (Long, Long) -> Unit = { _, _ -> }) =
		withContext(Dispatchers.IO) {
			destino.parentFile?.mkdirs()
			val parcial = File(destino.parentFile, destino.name + ".parte")
			try {
				var leidos = if (parcial.isFile) parcial.length() else 0L
				var total = tamano
				var intentos = 0
				while (true) {
					ensureActive()
					val c = conexion(url)
					val alEmpezar = leidos
					if (leidos > 0) c.setRequestProperty("Range", "bytes=$leidos-")
					try {
						val codigo = c.responseCode
						if (codigo == 200) {
							// El servidor manda el archivo entero (sin Range): se empieza de nuevo
							leidos = 0
							total = c.contentLengthLong.takeIf { it > 0 } ?: tamano
						} else if (codigo == 416 && tamano > 0 && leidos == tamano) {
							break // ya estaba entero
						} else if (codigo != 206 || leidos == 0L) {
							throw FalloEstado(R.string.servidor_respondio, codigo)
						}
						c.inputStream.use { entrada ->
							FileOutputStream(parcial, leidos > 0).use { salida ->
								val bufer = ByteArray(64 * 1024)
								while (true) {
									ensureActive()
									val n = entrada.read(bufer)
									if (n < 0) break
									salida.write(bufer, 0, n)
									leidos += n
									progreso(leidos, total)
								}
							}
						}
						if (total > 0 && leidos < total) throw IOException("Faltan ${total - leidos} bytes")
						break
					} catch (e: IOException) {
						Log.w(ETIQUETA, "Descarga de $url cortada en $leidos de $total bytes", e)
						if (leidos > alEmpezar) intentos = 0
						if (++intentos > REINTENTOS) throw FalloEstado(R.string.descarga_cortes)
						delay(2000L * minOf(intentos, 5))
					} finally {
						c.disconnect()
					}
				}
				if (tamano > 0 && parcial.length() != tamano) throw FalloEstado(R.string.descarga_incompleta)
				if (sha256.isNotEmpty() && sha256(parcial) != sha256.lowercase()) throw FalloEstado(R.string.descarga_sha)
				destino.delete()
				if (!parcial.renameTo(destino)) throw FalloEstado(R.string.descarga_no_guardada)
				destino
			} catch (e: FalloEstado) {
				// Un archivo que no es el publicado no se reanuda: se borra
				parcial.delete()
				throw e
			}
		}

	fun sha256(archivo: File): String {
		val resumen = MessageDigest.getInstance("SHA-256")
		archivo.inputStream().use { entrada ->
			val bufer = ByteArray(1024 * 1024)
			while (true) {
				val n = entrada.read(bufer)
				if (n < 0) break
				resumen.update(bufer, 0, n)
			}
		}
		return resumen.digest().joinToString("") { "%02x".format(it) }
	}
}
