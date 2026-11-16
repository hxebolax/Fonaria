package com.xebolax.fonaria.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.xebolax.fonaria.BuildConfig
import com.xebolax.fonaria.FonariaApp
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.avisar
import com.xebolax.fonaria.accesibilidad.conEspera
import com.xebolax.fonaria.accesibilidad.confirmar
import com.xebolax.fonaria.accesibilidad.mensaje
import com.xebolax.fonaria.datos.Copias
import com.xebolax.fonaria.datos.motivo
import com.xebolax.fonaria.motor.Motor
import com.xebolax.fonaria.motor.ServicioVoz
import com.xebolax.fonaria.voces.Almacen
import com.xebolax.fonaria.voces.Catalogo
import com.xebolax.fonaria.voces.Descargas
import com.xebolax.fonaria.voces.ModeloDescargable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pestaña Voces: qué voz habla cada idioma y qué hacer si falta. */
class AjustesVocesFragment : AjustesBase() {
	override fun onCreatePreferences(estado: Bundle?, clave: String?) {
		prepararAlmacen()
		val c = requireContext()
		val pantalla = preferenceManager.createPreferenceScreen(c)
		val porIdioma = PreferenceCategory(c).apply {
			title = getString(R.string.cat_voz_por_idioma)
			isIconSpaceReserved = false
		}
		pantalla.addPreference(porIdioma)
		val instaladas = Almacen.instaladas(c)
		val locales = Catalogo.voces(c).filter { !it.personaje }.map { it.idioma to it.region }.distinct()
		for ((idioma, region) in locales) {
			val voces = instaladas.filter { it.idioma == idioma }
			val nombre = Catalogo.nombreIdioma(c, idioma, region)
			porIdioma.addPreference(ListPreference(c).apply {
				key = "voz_${idioma}_$region"
				title = getString(R.string.voz_para, nombre)
				isIconSpaceReserved = false
				entries = (listOf(getString(R.string.voz_automatica)) + voces.map { "${it.nombre}, ${Catalogo.nombreIdioma(c, it.idioma, it.region, it.personaje)}" }).toTypedArray()
				entryValues = (listOf("") + voces.map { it.id }).toTypedArray()
				setDefaultValue("")
				dialogTitle = title
				summaryProvider = Preference.SummaryProvider<ListPreference> { p ->
					if (voces.isEmpty()) getString(R.string.sin_voces_idioma) else p.entry ?: getString(R.string.voz_automatica)
				}
				isEnabled = voces.isNotEmpty()
			})
		}
		val comportamiento = PreferenceCategory(c).apply {
			title = getString(R.string.cat_comportamiento)
			isIconSpaceReserved = false
		}
		pantalla.addPreference(comportamiento)
		comportamiento.addPreference(SwitchPreferenceCompat(c).apply {
			key = "voz_reserva"
			title = getString(R.string.voz_reserva)
			summary = getString(R.string.voz_reserva_resumen)
			setDefaultValue(true)
			isIconSpaceReserved = false
		})
		comportamiento.addPreference(SwitchPreferenceCompat(c).apply {
			key = "respetar_voz_pedida"
			title = getString(R.string.respetar_voz_pedida)
			summary = getString(R.string.respetar_voz_pedida_resumen)
			setDefaultValue(false)
			isIconSpaceReserved = false
		})
		comportamiento.addPreference(Preference(c).apply {
			title = getString(R.string.gestionar_voces)
			summary = getString(R.string.gestionar_voces_resumen)
			isIconSpaceReserved = false
			setOnPreferenceClickListener {
				startActivity(Intent(c, PrincipalActivity::class.java).putExtra(PrincipalActivity.EXTRA_DESTINO, R.id.destino_voces).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
				true
			}
		})
		preferenceScreen = pantalla
	}
}

/** Pestaña Motor: modelo (integrado, ligero o máxima calidad), medir la velocidad y opciones avanzadas. */
class AjustesMotorFragment : AjustesBase() {
	override fun onCreatePreferences(estado: Bundle?, clave: String?) {
		prepararAlmacen()
		setPreferencesFromResource(R.xml.ajustes_motor, clave)
		val modelo = findPreference<ListPreference>("modelo")!!
		modelo.setOnPreferenceChangeListener { _, nuevo ->
			val m = Descargas.modelos(requireContext()).firstOrNull { it.id == nuevo }
			if (m == null || m.incluido || Almacen.modeloInstalado(requireContext(), m.id)) true
			else {
				ofrecerDescarga(m, modelo)
				false
			}
		}
		findPreference<Preference>("borrar_modelos")!!.setOnPreferenceClickListener {
			val c = requireContext()
			val bajados = Descargas.modelos(c).filter { !it.incluido && Almacen.modeloInstalado(c, it.id) }
			if (bajados.isEmpty()) {
				c.mensaje(getString(R.string.borrar_modelos), getString(R.string.sin_modelos_descargados))
			} else {
				c.confirmar(getString(R.string.confirmar_borrar_modelos), getString(R.string.borrar)) {
					bajados.forEach { Descargas.borrarModelo(c, it) }
					modelo.value = "q8"
					(activity as? AppCompatActivity)?.avisar(getString(R.string.modelos_borrados))
				}
			}
			true
		}
		findPreference<Preference>("medir")!!.setOnPreferenceClickListener {
			medir()
			true
		}
		findPreference<Preference>("bateria")!!.setOnPreferenceClickListener {
			pedirBateria()
			true
		}
	}

	private fun ofrecerDescarga(m: ModeloDescargable, preferencia: ListPreference) {
		val c = requireContext()
		val nombre = preferencia.entries[preferencia.findIndexOfValue(m.id)]
		c.confirmar(getString(R.string.descargar_modelo_pregunta, nombre, Formatter.formatShortFileSize(c, m.tamano)), getString(R.string.descargar)) {
			descargarModelo(m, preferencia)
		}
	}

	private fun descargarModelo(m: ModeloDescargable, preferencia: ListPreference) {
		val actividad = activity as? AppCompatActivity ?: return
		val margen = (20 * resources.displayMetrics.density).toInt()
		val estado = TextView(actividad).apply {
			text = getString(R.string.descargando_inicio)
			accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
		}
		val barra = LinearProgressIndicator(actividad).apply {
			max = 100
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}
		val vista = LinearLayout(actividad).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(margen, margen / 2, margen, 0)
			addView(estado)
			addView(barra)
		}
		var trabajo: kotlinx.coroutines.Job? = null
		val dialogo = MaterialAlertDialogBuilder(actividad)
			.setTitle(R.string.descargando_modelo)
			.setView(vista)
			.setCancelable(false)
			.setNegativeButton(R.string.cancelar) { _, _ -> trabajo?.cancel() }
			.show()
		var ultimo = 0L
		trabajo = actividad.lifecycleScope.launch {
			try {
				Descargas.descargarModelo(actividad, m) { leidos, total ->
					val ahora = SystemClock.uptimeMillis()
					if (ahora - ultimo > 300 && total > 0) {
						ultimo = ahora
						val pct = (leidos * 100 / total).toInt()
						actividad.runOnUiThread {
							barra.progress = pct
							estado.text = getString(R.string.descargando_progreso, pct, Formatter.formatShortFileSize(actividad, leidos), Formatter.formatShortFileSize(actividad, total))
						}
					}
				}
				dialogo.dismiss()
				preferencia.value = m.id
				actividad.avisar(getString(R.string.modelo_descargado))
			} catch (e: CancellationException) {
				dialogo.dismiss()
				actividad.avisar(getString(R.string.descarga_cancelada))
				throw e
			} catch (e: Exception) {
				dialogo.dismiss()
				Log.e("Fonaria.Ajustes", "No se pudo descargar el modelo ${m.id}", e)
				actividad.mensaje(getString(R.string.descargando_modelo), actividad.motivo(e))
			}
		}
	}

	/** Mide cuánto audio genera el motor por segundo de cálculo con la voz preferida. */
	override fun onResume() {
		super.onResume()
		// Al volver de la pantalla de Android, el resumen dice cómo ha quedado
		if (sinRestricciones()) findPreference<Preference>("bateria")?.summary = getString(R.string.bateria_estado_si)
	}

	private fun sinRestricciones(): Boolean {
		if (android.os.Build.VERSION.SDK_INT < 23) return true
		val energia = requireContext().getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
		return energia.isIgnoringBatteryOptimizations(requireContext().packageName)
	}

	/**
	 * Pide a Android que no restrinja a Fonaria en segundo plano. Sin esto, Android 12 y
	 * posteriores no dejan que el motor pase a primer plano cuando lo usa TalkBack, y con la
	 * pantalla apagada puede quedarse en los núcleos lentos.
	 */
	@android.annotation.SuppressLint("BatteryLife")
	private fun pedirBateria() {
		val c = requireContext()
		when {
			android.os.Build.VERSION.SDK_INT < 23 -> c.mensaje(getString(R.string.bateria), getString(R.string.bateria_no_disponible))
			sinRestricciones() -> c.mensaje(getString(R.string.bateria), getString(R.string.bateria_ya))
			else -> try {
				startActivity(
					android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + c.packageName)),
				)
			} catch (e: Exception) {
				// Algunos fabricantes quitan esa pantalla: se abre la lista general
				try {
					startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
				} catch (e2: Exception) {
					c.mensaje(getString(R.string.bateria), c.motivo(e2))
				}
			}
		}
	}

	private fun medir() {
		val actividad = activity as? AppCompatActivity ?: return
		actividad.lifecycleScope.launch {
			try {
				val resultado = actividad.conEspera(getString(R.string.medir_motor), getString(R.string.midiendo)) {
					withContext(Dispatchers.IO) {
						val voz = com.xebolax.fonaria.motor.Idiomas.elegirVoz(actividad, "es", "ES") ?: error(getString(R.string.sin_voces))
						val carga = SystemClock.elapsedRealtime()
						if (!Motor.asegurar(actividad)) error(Motor.error ?: getString(R.string.error_inesperado))
						val msCarga = if (Motor.msCarga > 0) Motor.msCarga else SystemClock.elapsedRealtime() - carga
						var muestras = 0L
						var primero = 0L
						val inicio = SystemClock.elapsedRealtime()
						val ok = Motor.hablar(
							actividad,
							Motor.Peticion(getString(R.string.texto_medida), voz.id, "es", 0.3f, ServicioVoz.SEMILLA, 0f, 1f, 0, false),
						) { _, n ->
							if (primero == 0L) primero = SystemClock.elapsedRealtime() - inicio
							muestras += n
							true
						}
						if (!ok) error(Motor.error ?: getString(R.string.error_inesperado))
						val ms = SystemClock.elapsedRealtime() - inicio
						val segundos = muestras.toFloat() / Motor.frecuencia
						getString(R.string.resultado_medida, segundos * 1000f / ms.coerceAtLeast(1), primero, msCarga, Motor.diagnostico())
					}
				}
				actividad.mensaje(getString(R.string.medir_motor), resultado)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				actividad.mensaje(getString(R.string.medir_motor), actividad.motivo(e))
			}
		}
	}
}

class AjustesActualizacionesFragment : AjustesBase() {
	override fun onCreatePreferences(estado: Bundle?, clave: String?) {
		prepararAlmacen()
		if (!ajustes.p.contains("url_actualizaciones")) ajustes.p.edit().putString("url_actualizaciones", BuildConfig.URL_ACTUALIZACIONES).apply()
		setPreferencesFromResource(R.xml.ajustes_actualizaciones, clave)
		findPreference<EditTextPreference>("url_actualizaciones")?.setOnPreferenceChangeListener { _, v ->
			(v as String).trim().let { it.isEmpty() || it.startsWith("http") }
		}
		findPreference<Preference>("buscar_ahora")?.setOnPreferenceClickListener {
			(activity as? AjustesActivity)?.actualizaciones?.comprobarAMano()
			true
		}
	}
}

/** Pestaña General: idioma, tema, copia de la configuración y restablecer. */
class AjustesGeneralFragment : AjustesBase() {
	private val exportar = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { guardarCopia(it) } }
	private val importar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { leerCopia(it) } }

	override fun onCreatePreferences(estado: Bundle?, clave: String?) {
		prepararAlmacen()
		setPreferencesFromResource(R.xml.ajustes_general, clave)
		findPreference<ListPreference>("idioma_app")?.setOnPreferenceChangeListener { _, v ->
			FonariaApp.aplicarIdioma(v as String)
			true
		}
		findPreference<ListPreference>("tema")?.setOnPreferenceChangeListener { _, v ->
			FonariaApp.aplicarTema(v as String)
			true
		}
		findPreference<Preference>("exportar")?.setOnPreferenceClickListener {
			exportar.launch("Fonaria-${BuildConfig.VERSION_NAME}.json")
			true
		}
		findPreference<Preference>("importar")?.setOnPreferenceClickListener {
			importar.launch(arrayOf("application/json", "text/*", "*/*"))
			true
		}
		findPreference<Preference>("restablecer")?.setOnPreferenceClickListener {
			requireContext().confirmar(getString(R.string.confirmar_restablecer), getString(R.string.restablecer)) {
				Copias.restablecer(requireContext())
				FonariaApp.aplicarTema("sistema")
				FonariaApp.aplicarIdioma("sistema")
				activity?.recreate()
			}
			true
		}
		findPreference<Preference>("ajustes_sistema")?.setOnPreferenceClickListener {
			PrincipalActivity.abrirAjustesVoz(requireContext())
			true
		}
	}

	private fun guardarCopia(uri: Uri) {
		val c = requireContext()
		try {
			c.contentResolver.openOutputStream(uri)?.use { it.write(Copias.exportar(c).toByteArray()) }
			(activity as? AppCompatActivity)?.avisar(getString(R.string.config_exportada))
		} catch (e: Exception) {
			Log.e("Fonaria.Ajustes", "No se pudo exportar la configuración", e)
			c.mensaje(getString(R.string.exportar_config), c.motivo(e))
		}
	}

	private fun leerCopia(uri: Uri) {
		val c = requireContext()
		try {
			val texto = c.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
			val n = Copias.importar(c, texto)
			c.mensaje(getString(R.string.importar_config), getString(R.string.config_importada, n)) { activity?.recreate() }
		} catch (e: Exception) {
			Log.e("Fonaria.Ajustes", "No se pudo importar la configuración", e)
			c.mensaje(getString(R.string.importar_config), getString(R.string.config_no_valida))
		}
	}
}
