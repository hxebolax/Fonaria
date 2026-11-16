package com.xebolax.fonaria.motor

import kotlin.math.abs
import kotlin.math.round

/**
 * Cuánto de la velocidad pedida hace el propio modelo con su ritmo natural (`duration_delta`).
 *
 * Medido en un Snapdragon 845 con la misma frase: ritmo -0,2 dura un 12 % menos, -0,4 un 17 % y
 * -0,6 un 25 %, y se entiende igual de bien (transcrito con Whisper). Generar menos audio es la
 * forma más barata de ir deprisa: el móvil tarda menos y Sonic estira menos la voz.
 */
object Acelerar {
	/** Ritmo nativo más rápido que se usa automáticamente. */
	const val MAXIMO = -0.6f

	/** Ritmo automático para una velocidad final [velocidad] (1 = normal), en pasos de 0,05. */
	fun ritmo(velocidad: Float): Float {
		if (velocidad <= 1.15f) return 0f
		val r = -((velocidad - 1f) * 0.45f).coerceAtMost(-MAXIMO)
		return round(r * 20f) / 20f
	}

	/** El ritmo con el que el modelo habla [factor] veces más rápido (lo contrario de [factor]). */
	fun ritmoDe(factor: Float): Float = if (factor <= 1f) 0f else (-(factor - 1f) / 0.55f).coerceAtLeast(MAXIMO)

	/** Cuántas veces más rápido habla el modelo con el ritmo [ritmo] (solo los negativos aceleran). */
	fun factor(ritmo: Float): Float = if (ritmo >= 0f) 1f else 1f + 0.55f * abs(ritmo)
}
