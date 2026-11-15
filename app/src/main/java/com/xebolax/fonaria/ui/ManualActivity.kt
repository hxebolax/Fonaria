package com.xebolax.fonaria.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.xebolax.fonaria.R
import com.xebolax.fonaria.accesibilidad.configurarBarra
import com.xebolax.fonaria.databinding.ActividadSimpleBinding
import com.xebolax.fonaria.voces.idiomaInterfaz

/**
 * El manual de uso (assets/manual/manual_<idioma>.html, o la página de licencias). Es HTML con
 * encabezados: TalkBack navega por ellos con el control de lectura «Encabezados». Los enlaces
 * externos se abren en el navegador.
 */
class ManualActivity : AppCompatActivity() {
	private lateinit var b: ActividadSimpleBinding

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(estado: Bundle?) {
		super.onCreate(estado)
		b = ActividadSimpleBinding.inflate(layoutInflater)
		setContentView(b.root)
		val pagina = intent.getStringExtra(EXTRA_PAGINA) ?: "manual"
		configurarBarra(b.barra, getString(if (pagina == "licencias") R.string.licencias else R.string.manual))
		val idioma = idiomaInterfaz(this).language
		val disponibles = assets.list("manual").orEmpty()
		val archivo = "${pagina}_$idioma.html".takeIf { it in disponibles } ?: "${pagina}_es.html"
		val web = WebView(this).apply {
			settings.javaScriptEnabled = false
			settings.allowFileAccess = false
			webViewClient = object : WebViewClient() {
				override fun shouldOverrideUrlLoading(vista: WebView, peticion: WebResourceRequest): Boolean {
					val url = peticion.url
					if (url.scheme == "file") return false
					startActivity(Intent(Intent.ACTION_VIEW, url))
					return true
				}

				@Deprecated("API 21-23")
				override fun shouldOverrideUrlLoading(vista: WebView, url: String): Boolean {
					if (url.startsWith("file:")) return false
					startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
					return true
				}
			}
			loadUrl("file:///android_asset/manual/$archivo")
		}
		b.contenedor.addView(web)
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (web.canGoBack()) web.goBack() else finish()
			}
		})
	}

	companion object {
		const val EXTRA_PAGINA = "pagina"
	}
}
