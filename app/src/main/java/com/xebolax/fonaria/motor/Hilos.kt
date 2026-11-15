package com.xebolax.fonaria.motor

import android.os.Process
import android.util.Log
import java.io.File

/**
 * Da prioridad a los hilos que hacen la voz, para que no se queden esperando si el procesador
 * está ocupado con otras cosas (la app que se lee, animaciones, sincronizaciones...).
 *
 * - El hilo del sistema que reproduce el audio (TTS.Audio...) va primero: nunca debe esperar.
 * - El hilo que entrega el audio al sistema (SynthThread) y los del motor, después.
 *
 * Los hilos del motor los crea el código nativo y heredan el nombre de quien los crea
 * («fonaria-...»): se encuentran en /proc. Repetirlo es barato: los ya vistos se saltan.
 */
object Hilos {
	private val vistos = HashSet<Int>()

	@Synchronized
	fun subir() {
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
				nombre.startsWith("TTS.Audio") -> Process.THREAD_PRIORITY_URGENT_AUDIO
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
