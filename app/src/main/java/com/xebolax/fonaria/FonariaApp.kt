package com.xebolax.fonaria

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.xebolax.fonaria.accesibilidad.DeslizadorAjuste
import com.xebolax.fonaria.accesibilidad.MemoriaFoco
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.voces.Almacen

class FonariaApp : Application() {

	override fun onCreate() {
		super.onCreate()
		instancia = this
		registerActivityLifecycleCallbacks(MemoriaFoco)
		aplicarTema(Ajustes(this).tema)
		formatosDeslizadores()
		// El modelo y las voces del APK se copian a su sitio en segundo plano la primera vez
		Thread({
			try {
				Almacen.preparar(this)
			} catch (e: Exception) {
				Log.e("Fonaria", "No se pudo preparar el almacén", e)
			}
		}, "fonaria-preparar").start()
	}

	private fun formatosDeslizadores() {
		val f = DeslizadorAjuste.formatos
		f[Ajustes.VELOCIDAD] = DeslizadorAjuste.porCiento
		f[Ajustes.TONO] = DeslizadorAjuste.porCiento
		f[Ajustes.VOLUMEN] = DeslizadorAjuste.porCiento
		f[Ajustes.RITMO] = { c, v -> c.getString(R.string.valor_ritmo, v) }
		f[Ajustes.VARIACION] = DeslizadorAjuste.porCiento
		f["pausa_frases"] = DeslizadorAjuste.milisegundos
		f["pausa_parrafos"] = DeslizadorAjuste.milisegundos
		f["tono_mayusculas"] = DeslizadorAjuste.porCiento
		f["guia"] = { c, v -> if (v <= 10) c.getString(R.string.desactivada) else c.getString(R.string.valor_guia, v / 10f) }
		f["hilos"] = { c, v -> if (v == 0) c.getString(R.string.automatico) else c.resources.getQuantityString(R.plurals.hilos, v, v) }
		f["pasos"] = { c, v -> c.resources.getQuantityString(R.plurals.pasos, v, v) }
		f["umbral_fin"] = { c, v -> if (v == 0) c.getString(R.string.del_modelo) else c.getString(R.string.valor_decimal, v / 10f) }
		f["tokens_trozo"] = { c, v -> if (v == 0) c.getString(R.string.automatico) else v.toString() }
		f["adelanto"] = DeslizadorAjuste.milisegundos
	}

	companion object {
		private lateinit var instancia: FonariaApp

		/** Texto de un recurso en el idioma de la app, desde cualquier sitio (errores). */
		fun texto(@StringRes recurso: Int, vararg argumentos: Any): String = instancia.getString(recurso, *argumentos)

		val contexto: Context get() = instancia

		/** sistema, claro u oscuro. */
		fun aplicarTema(tema: String) {
			AppCompatDelegate.setDefaultNightMode(
				when (tema) {
					"claro" -> AppCompatDelegate.MODE_NIGHT_NO
					"oscuro" -> AppCompatDelegate.MODE_NIGHT_YES
					else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
				},
			)
		}

		/** sistema, es o en (o cualquier traducción que se añada). */
		fun aplicarIdioma(idioma: String) {
			AppCompatDelegate.setApplicationLocales(
				if (idioma == "sistema") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(idioma),
			)
		}
	}
}
