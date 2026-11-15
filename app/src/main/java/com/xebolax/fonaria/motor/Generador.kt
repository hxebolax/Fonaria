package com.xebolax.fonaria.motor

import android.content.Context
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Genera la voz en su propio hilo y la deja en una cola para que otro hilo la entregue.
 *
 * Por qué: Android bloquea a quien entrega audio en cuanto hay más de medio segundo pendiente de
 * sonar. Si el que entrega es el mismo que genera, el motor se queda parado esperando y pierde
 * la ventaja que lleva; luego, cuando se detiene un instante a preparar el siguiente trozo de un
 * texto largo, no queda colchón y se oye un microcorte. Con este hilo aparte, el motor sigue
 * generando (hasta [MAXIMO_MS] por delante) mientras Android reproduce.
 */
class Generador(private val contexto: Context, private val peticion: Motor.Peticion, private val parar: () -> Boolean) {

	/** Un trozo de audio del modelo, o el final (con si salió bien). */
	private sealed class Pieza {
		class Audio(val muestras: FloatArray) : Pieza()
		class Fin(val ok: Boolean) : Pieza()
	}

	// 80 ms por pieza: 40 piezas son unos 3 segundos de voz por delante
	private val cola = ArrayBlockingQueue<Pieza>(MAXIMO_MS / 80)
	@Volatile
	private var cancelado = false

	fun empezar() {
		hilo.execute {
			var ok = false
			try {
				ok = Motor.hablar(contexto, peticion) { muestras, n ->
					if (cancelado || parar()) return@hablar false
					val pieza = Pieza.Audio(if (n == muestras.size) muestras else muestras.copyOf(n))
					// Espera a que haya sitio, pero sin quedarse colgado si mandan parar
					while (!cola.offer(pieza, 50, TimeUnit.MILLISECONDS)) {
						if (cancelado || parar()) return@hablar false
					}
					true
				}
			} catch (e: Throwable) {
				Log.e("Fonaria.Generador", "Falló la generación", e)
			} finally {
				// El final siempre llega a la cola (si está llena y nadie la vacía, se descarta lo viejo)
				while (!cola.offer(Pieza.Fin(ok))) cola.poll()
			}
		}
	}

	/**
	 * Entrega cada trozo de audio a [alRecibir] según llega. Devuelve si la generación salió bien;
	 * si [alRecibir] devuelve false o mandan parar, deja de esperar y avisa al hilo generador.
	 */
	fun recibir(alRecibir: (FloatArray) -> Boolean): Boolean {
		while (true) {
			val pieza = cola.poll(50, TimeUnit.MILLISECONDS)
			if (pieza == null) {
				if (parar()) {
					cancelar()
					return true
				}
				continue
			}
			when (pieza) {
				is Pieza.Fin -> return pieza.ok
				is Pieza.Audio -> if (!alRecibir(pieza.muestras)) {
					cancelar()
					return true
				}
			}
		}
	}

	/** Para el hilo generador y espera a que suelte el motor (para la frase siguiente). */
	fun cancelar() {
		cancelado = true
		while (true) {
			val p = cola.poll(100, TimeUnit.MILLISECONDS) ?: continue
			if (p is Pieza.Fin) return
		}
	}

	companion object {
		const val MAXIMO_MS = 3200
		/** Un solo hilo: el motor no admite dos frases a la vez. */
		private val hilo = Executors.newSingleThreadExecutor { r -> Thread(r, "fonaria-generador") }
	}
}
