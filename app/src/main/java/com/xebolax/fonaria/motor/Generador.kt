package com.xebolax.fonaria.motor

import android.content.Context
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Genera la voz de todas las frases de una lectura en su propio hilo y la deja en una cola para
 * que otro hilo la entregue.
 *
 * Por qué:
 * - Android bloquea a quien entrega audio en cuanto hay más de medio segundo pendiente de sonar.
 *   Si el que entrega fuese el mismo que genera, el motor se quedaría parado esperando.
 * - El motor sigue de una frase a la siguiente sin esperar a que la anterior acabe de sonar: en
 *   los textos largos, cada frase nueva cuesta un poco más al empezar (prepara el texto y la voz),
 *   y si el motor no llevase ventaja se oiría ese instante como un microcorte entre frases.
 *
 * La cola admite hasta [MAXIMO_MS] de voz por delante.
 */
class Generador(
	private val contexto: Context,
	private val peticiones: List<Motor.Peticion>,
	private val parar: () -> Boolean,
) {

	/** Cómo acabó una frase al recibirla. */
	enum class Resultado { BIEN, FALLO, CORTADA }

	private sealed class Pieza {
		class Audio(val muestras: FloatArray) : Pieza()
		/** Acabó una frase (con si salió bien). */
		class Fin(val ok: Boolean) : Pieza()
		/** El hilo generador ha terminado del todo y ya no toca el motor. */
		object Terminado : Pieza()
	}

	// Unos 80 ms por pieza
	private val cola = ArrayBlockingQueue<Pieza>(MAXIMO_MS / 80)
	@Volatile
	private var cancelado = false
	private var terminado = false

	private fun seguir() = !cancelado && !parar()

	/** Espera sitio en la cola, sin quedarse colgado si mandan parar. */
	private fun poner(pieza: Pieza): Boolean {
		while (!cola.offer(pieza, 50, TimeUnit.MILLISECONDS)) {
			if (!seguir()) return false
		}
		return true
	}

	fun empezar() {
		hilo.execute {
			Hilos.subir()
			try {
				for (p in peticiones) {
					if (!seguir()) break
					var primero = true
					val ok = try {
						Motor.hablar(contexto, p) { muestras, n ->
							// Los hilos del motor existen desde el primer audio
							if (primero) Hilos.subir()
							primero = false
							if (!seguir()) return@hablar false
							poner(Pieza.Audio(if (n == muestras.size) muestras else muestras.copyOf(n)))
						}
					} catch (e: Throwable) {
						Log.e("Fonaria.Generador", "Falló la generación", e)
						false
					}
					if (!seguir() || !poner(Pieza.Fin(ok)) || !ok) break
				}
			} finally {
				// El final siempre llega a la cola (si está llena y nadie la vacía, se descarta lo viejo)
				while (!cola.offer(Pieza.Terminado)) cola.poll()
			}
		}
	}

	/**
	 * Entrega a [alRecibir] el audio de la frase siguiente según llega, hasta que acaba. Si
	 * [alRecibir] devuelve false o mandan parar, devuelve [Resultado.CORTADA].
	 */
	fun recibir(alRecibir: (FloatArray) -> Boolean): Resultado {
		if (terminado) return Resultado.CORTADA
		while (true) {
			val pieza = cola.poll(50, TimeUnit.MILLISECONDS)
			if (pieza == null) {
				if (parar()) return Resultado.CORTADA
				continue
			}
			when (pieza) {
				is Pieza.Audio -> if (!alRecibir(pieza.muestras)) return Resultado.CORTADA
				is Pieza.Fin -> return if (pieza.ok) Resultado.BIEN else Resultado.FALLO
				Pieza.Terminado -> {
					terminado = true
					return if (parar()) Resultado.CORTADA else Resultado.FALLO
				}
			}
		}
	}

	/** Para el hilo generador y espera a que suelte el motor (para la lectura siguiente). */
	fun cerrar() {
		cancelado = true
		while (!terminado) {
			if (cola.poll(100, TimeUnit.MILLISECONDS) === Pieza.Terminado) terminado = true
		}
	}

	companion object {
		/** Voz que se puede llevar generada por delante de lo que suena. */
		const val MAXIMO_MS = 20_000
		/** Un solo hilo: el motor no admite dos frases a la vez. */
		private val hilo = Executors.newSingleThreadExecutor { r -> Thread(r, "fonaria-generador") }
	}
}
