package com.xebolax.fonaria.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.DeslizadorAjuste
import com.xebolax.fonaria.accesibilidad.avisar
import com.xebolax.fonaria.accesibilidad.configurarBarra
import com.xebolax.fonaria.databinding.ActividadSimpleBinding
import com.xebolax.fonaria.datos.Ajustes
import com.xebolax.fonaria.motor.Idiomas
import com.xebolax.fonaria.voces.Almacen
import com.xebolax.fonaria.voces.Catalogo
import com.xebolax.fonaria.voces.Voz

/**
 * Ajustes de una voz: su velocidad, tono, volumen, ritmo y variación. Lo que no se toca sigue a
 * los ajustes generales (Ajustes, Sonido); «Volver a los ajustes generales» quita lo propio.
 */
class VozActivity : AppCompatActivity() {
	private lateinit var b: ActividadSimpleBinding
	lateinit var voz: Voz
		private set
	private var locutor: Locutor? = null

	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		val id = intent.getStringExtra(EXTRA_VOZ)
		voz = Almacen.instaladas(this).firstOrNull { it.id == id } ?: run {
			finish()
			return
		}
		b = ActividadSimpleBinding.inflate(layoutInflater)
		setContentView(b.root)
		configurarBarra(b.barra, getString(R.string.ajustes_de_x, voz.nombre))
		locutor = Locutor(this)
		if (supportFragmentManager.findFragmentById(R.id.contenedor) == null) {
			supportFragmentManager.beginTransaction().replace(R.id.contenedor, AjustesVozFragment()).commit()
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menu.add(0, 1, 0, R.string.escuchar).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
		menu.add(0, 2, 0, R.string.detener).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			1 -> escuchar()
			2 -> locutor?.parar()
			else -> return super.onOptionsItemSelected(item)
		}
		return true
	}

	fun escuchar() {
		locutor?.decir(Idiomas.muestra(this, voz.idioma), voz.locale, voz.id)
	}

	override fun onDestroy() {
		locutor?.cerrar()
		super.onDestroy()
	}

	companion object {
		const val EXTRA_VOZ = "voz"
	}
}

class AjustesVozFragment : AjustesBase() {
	private val voz get() = (requireActivity() as VozActivity).voz

	override fun onCreatePreferences(estado: Bundle?, clave: String?) {
		prepararAlmacen()
		val c = requireContext()
		val v = voz
		val pantalla = preferenceManager.createPreferenceScreen(c)
		val info = PreferenceCategory(c).apply {
			title = v.nombre
			isIconSpaceReserved = false
		}
		pantalla.addPreference(info)
		info.addPreference(Preference(c).apply {
			title = Catalogo.nombreIdioma(c, v.idioma, v.region, v.personaje)
			summary = listOf(if (v.importada) "" else Catalogo.perfil(c, v), Catalogo.descripcion(c, v)).filter { it.isNotEmpty() }.joinToString(". ")
			isSelectable = false
			isIconSpaceReserved = false
		})
		info.addPreference(Preference(c).apply {
			title = getString(R.string.escuchar_x, v.nombre)
			isIconSpaceReserved = false
			setOnPreferenceClickListener {
				(activity as? VozActivity)?.escuchar()
				true
			}
		})
		val preferida = ajustes.vozPara(v.idioma, v.region) == v.id
		if (!preferida) info.addPreference(Preference(c).apply {
			title = getString(R.string.usar_para, Catalogo.nombreIdioma(c, v.idioma, v.region, v.personaje))
			isIconSpaceReserved = false
			setOnPreferenceClickListener {
				ajustes.ponerVoz(v.idioma, v.region.ifEmpty { null }, v.id)
				(activity as? AppCompatActivity)?.avisar(getString(R.string.voz_elegida, v.nombre, title))
				isVisible = false
				true
			}
		})

		val sonido = PreferenceCategory(c).apply {
			title = getString(R.string.cat_sonido_de_voz)
			summary = getString(R.string.cat_sonido_de_voz_resumen)
			isIconSpaceReserved = false
		}
		pantalla.addPreference(sonido)
		data class Def(val clave: String, val titulo: Int, val min: Int, val max: Int, val paso: Int, val defecto: Int)
		val defs = listOf(
			Def(Ajustes.VELOCIDAD, R.string.velocidad, 25, 400, 5, 100),
			Def(Ajustes.TONO, R.string.tono, 50, 200, 5, 100),
			Def(Ajustes.VOLUMEN, R.string.volumen, 10, 200, 5, 100),
			Def(Ajustes.RITMO, R.string.ritmo, -50, 50, 5, 0),
			Def(Ajustes.VARIACION, R.string.variacion, 0, 100, 5, 30),
		)
		for (d in defs) {
			sonido.addPreference(DeslizadorAjuste(c).apply {
				key = "voz.${v.id}.${d.clave}"
				title = getString(d.titulo)
				min = d.min
				max = d.max
				seekBarIncrement = d.paso
				showSeekBarValue = true
				isIconSpaceReserved = false
				formato = DeslizadorAjuste.formatos[d.clave]
				// Sin valor propio, muestra el general
				setDefaultValue(ajustes.entero(d.clave, d.defecto))
				summary = getString(if (ajustes.tienePropio(v.id, d.clave)) R.string.valor_propio else R.string.valor_general)
				setOnPreferenceChangeListener { p, _ ->
					p.summary = getString(R.string.valor_propio)
					true
				}
			})
		}
		sonido.addPreference(Preference(c).apply {
			title = getString(R.string.volver_a_generales)
			summary = getString(R.string.volver_a_generales_resumen)
			isIconSpaceReserved = false
			setOnPreferenceClickListener {
				ajustes.quitarPropios(v.id)
				(activity as? AppCompatActivity)?.avisar(getString(R.string.ajustes_quitados))
				activity?.recreate()
				true
			}
		})
		preferenceScreen = pantalla
	}
}
