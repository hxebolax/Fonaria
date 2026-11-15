package com.xebolax.fonaria.datos

import android.content.Context
import androidx.annotation.StringRes
import com.xebolax.fonaria.FonariaApp
import com.xebolax.fonaria.R
import java.io.IOException

/**
 * Error con un texto para la persona usuaria. Su mensaje sale de los recursos y se traduce al
 * leerlo, en el idioma de la app: se puede mostrar tal cual.
 */
interface ConTextoUsuario

/** El texto de un recurso en el idioma de la app; sin la app arrancada (pruebas unitarias), su número. */
internal fun textoUsuario(@StringRes recurso: Int, argumentos: Array<out Any>): String = try {
	FonariaApp.texto(recurso, *argumentos)
} catch (e: RuntimeException) {
	"#$recurso ${argumentos.joinToString()}".trim()
}

/** Fallo de red o de archivos con un texto para la persona usuaria. */
open class FalloIo(@StringRes private val recurso: Int, private vararg val argumentos: Any) : IOException(), ConTextoUsuario {
	override val message: String get() = textoUsuario(recurso, argumentos)
}

/** Fallo que no es de red ni de archivos (datos no válidos, comprobaciones) con un texto para la persona usuaria. */
open class FalloEstado(@StringRes private val recurso: Int, private vararg val argumentos: Any) : IllegalStateException(), ConTextoUsuario {
	override val message: String get() = textoUsuario(recurso, argumentos)
}

/** Por qué falló algo, en lenguaje claro y nunca «null». */
fun Context.motivo(e: Throwable): String = when (e) {
	is ConTextoUsuario -> e.message ?: getString(R.string.error_inesperado)
	is IOException -> getString(R.string.sin_conexion_servidor)
	is org.json.JSONException -> getString(R.string.respuesta_no_valida)
	else -> e.message?.takeIf { it.isNotBlank() } ?: getString(R.string.error_inesperado)
}
