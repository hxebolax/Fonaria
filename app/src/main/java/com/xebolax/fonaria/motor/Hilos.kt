package com.xebolax.fonaria.motor

import android.os.Process
import android.util.Log
import java.io.File

/**
 * Da prioridad a los hilos que hacen la voz, para que no se queden esperando si el procesador
 * está ocupado con otras cosas (la app que se lee, animaciones, sincronizaciones...).
 *
 * - El hilo del sistema que reproduce el audio (...PlaybackThread) va primero: nunca debe esperar.
 * - El hilo que entrega el audio al sistema (SynthThread) y los del motor, después.
 *
 * Los hilos del motor los crea el código nativo y heredan el nombre de quien los crea
 * («fonaria-...»): se encuentran en /proc. Repetirlo es barato: los ya vistos se saltan.
 */
object Hilos {
	private val vistos = HashSet<Int>()
	private val hilo = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
		Thread(r, "prioridades").apply { isDaemon = true }
	}
	@Volatile
	private var ultima = 0L

	/**
	 * Revisa los hilos en segundo plano y como mucho una vez cada [CADA_MS]: recorrer /proc cuesta
	 * unos 20 ms con el procesador en reposo, y eso no puede retrasar el principio de cada frase.
	 * Los hilos que el motor crea en cada frase heredan la prioridad del que los crea.
	 */
	fun subir() {
		val ahora = android.os.SystemClock.elapsedRealtime()
		if (ultima != 0L && ahora - ultima < CADA_MS) return
		ultima = ahora
		hilo.execute { revisar() }
	}

	private const val CADA_MS = 3000L

	@Synchronized
	private fun revisar() {
		val tareas = File("/proc/self/task").list() ?: return
		for (tarea in tareas) {
			val tid = tarea.toIntOrNull() ?: continue
			if (tid in vistos) continue
			val nombre = try {
				File("/proc/self/task/$tarea/comm").readText().trim()
			} catch (e: Exception) {
				continue
			}
			val prioridad = when {
				// Android recorta los nombres largos por el principio: «SynthesisPlaybackThread» o
				// «TTS.AudioPlaybackThread» se ven como «oPlaybackThread»
				nombre.endsWith("PlaybackThread") || nombre.startsWith("TTS.") -> Process.THREAD_PRIORITY_URGENT_AUDIO
				nombre.startsWith("SynthThread") -> Process.THREAD_PRIORITY_AUDIO
				nombre.startsWith("fonaria") -> Process.THREAD_PRIORITY_VIDEO
				else -> continue
			}
			if (vistos.size > 4096) vistos.clear()
			vistos += tid
			try {
				Process.setThreadPriority(tid, prioridad)
			} catch (e: Exception) {
				Log.d("Fonaria.Hilos", "No se pudo subir la prioridad de $nombre", e)
			}
		}
	}
}
