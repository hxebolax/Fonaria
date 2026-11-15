package com.xebolax.fonaria.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.xebolax.fonaria.R
import com.xebolax.fonaria.databinding.SeccionPestanasBinding

/** Voces: pestañas Instaladas, Descargar e Importadas. */
class VocesFragment : Fragment() {
	private var _b: SeccionPestanasBinding? = null
	private val b get() = _b!!

	override fun onCreateView(inflater: LayoutInflater, contenedor: ViewGroup?, estado: Bundle?): View {
		_b = SeccionPestanasBinding.inflate(inflater, contenedor, false)
		return b.root
	}

	override fun onViewCreated(vista: View, estado: Bundle?) {
		b.paginas.adapter = object : FragmentStateAdapter(this) {
			override fun getItemCount() = 3
			override fun createFragment(posicion: Int) = ListaVocesFragment.nuevo(posicion)
		}
		// Deslizar entre pestañas choca con los gestos de TalkBack: se cambia solo con las pestañas
		b.paginas.isUserInputEnabled = false
		TabLayoutMediator(b.pestanas, b.paginas) { pestana, i ->
			pestana.text = getString(
				when (i) {
					PESTANA_DESCARGAR -> R.string.pestana_descargar
					PESTANA_IMPORTADAS -> R.string.pestana_importadas
					else -> R.string.pestana_instaladas
				},
			)
		}.attach()
		if (estado == null) b.paginas.setCurrentItem(arguments?.getInt(ARG_PESTANA) ?: 0, false)
	}

	fun irA(pestana: Int) {
		_b?.paginas?.setCurrentItem(pestana, false)
	}

	override fun onDestroyView() {
		_b = null
		super.onDestroyView()
	}

	companion object {
		const val PESTANA_INSTALADAS = 0
		const val PESTANA_DESCARGAR = 1
		const val PESTANA_IMPORTADAS = 2
		private const val ARG_PESTANA = "pestana"

		fun nuevo(pestana: Int) = VocesFragment().apply { arguments = Bundle().apply { putInt(ARG_PESTANA, pestana) } }
	}
}
