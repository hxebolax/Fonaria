import java.util.Properties

plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

// Servidor de actualizaciones de la app y de las voces: ahí van version.json,
// Fonaria_v<versión>.apk y la carpeta voces/ con su catalogo.json.
// Se puede cambiar en Ajustes; para compilar con otro: gradle assembleRelease -PurlActualizaciones=https://...
val urlActualizaciones = (project.findProperty("urlActualizaciones") as String?) ?: "https://xebolax.synology.me/Fonaria"
// Repositorio de GitHub cuyas versiones publicadas (releases) también puede usar el actualizador.
// Para compilar con otro: gradle assembleRelease -PrepoGithub=usuario/repositorio
val repoGithub = (project.findProperty("repoGithub") as String?) ?: "hxebolax/Fonaria"

// Firma propia guardada en la carpeta "firma" (la crea compilar.bat).
val propiedadesFirma = Properties().apply {
	val archivo = rootProject.file("firma/firma.properties")
	if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
	namespace = "com.xebolax.fonaria"
	compileSdk = 35

	defaultConfig {
		applicationId = "com.xebolax.fonaria"
		// Android 5.0: también móviles antiguos
		minSdk = 21
		targetSdk = 35
		versionCode = 9
		versionName = "1.0.8"

		buildConfigField("String", "URL_ACTUALIZACIONES", "\"$urlActualizaciones\"")
		buildConfigField("String", "REPO_GITHUB", "\"$repoGithub\"")
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	// El modelo y las voces españolas que van dentro del APK los prepara
	// herramientas\preparar_modelo.ps1 en modelo\apk (no se guardan en git: pesan ~100 MB).
	sourceSets["main"].assets.srcDirs("src/main/assets", "../modelo/apk")

	androidResources {
		// Idiomas de la interfaz (Android 13+ los ofrece en Ajustes del sistema > Idioma de la app)
		generateLocaleConfig = true
		// Los pesos ya están cuantizados: comprimirlos apenas ahorra y hace más lenta la primera instalación
		noCompress += listOf("gguf")
	}

	signingConfigs {
		if (propiedadesFirma.isNotEmpty()) {
			create("propia") {
				storeFile = rootProject.file(propiedadesFirma.getProperty("archivo"))
				storePassword = propiedadesFirma.getProperty("clave")
				keyAlias = propiedadesFirma.getProperty("alias")
				keyPassword = propiedadesFirma.getProperty("clave")
			}
		}
	}

	buildTypes {
		debug {
			// Versión de pruebas: se instala junto a la normal
			applicationIdSuffix = ".pruebas"
		}
		release {
			isMinifyEnabled = false
			// Solo procesadores de móviles; x86_64 (emulador) solo en la versión de pruebas
			ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
			signingConfig = signingConfigs.findByName("propia") ?: signingConfigs.getByName("debug")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	buildFeatures {
		viewBinding = true
		buildConfig = true
	}

	packaging {
		resources.excludes += listOf("META-INF/*.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1")
	}

	testOptions {
		unitTests.isReturnDefaultValues = true
	}
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
	}
}

dependencies {
	implementation("androidx.core:core-ktx:1.15.0")
	implementation("androidx.appcompat:appcompat:1.7.0")
	implementation("androidx.activity:activity-ktx:1.9.3")
	implementation("androidx.fragment:fragment-ktx:1.8.5")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
	implementation("androidx.recyclerview:recyclerview:1.3.2")
	implementation("androidx.preference:preference-ktx:1.2.1")
	implementation("androidx.drawerlayout:drawerlayout:1.2.0")
	implementation("androidx.viewpager2:viewpager2:1.1.0")
	implementation("com.google.android.material:material:1.13.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

	testImplementation("junit:junit:4.13.2")
	testImplementation("org.json:json:20240303")

	// Prueba instrumentada de accesibilidad: recorrido con TalkBack y Accessibility Test Framework
	androidTestImplementation("androidx.test:runner:1.6.2")
	androidTestImplementation("androidx.test:core:1.6.1")
	androidTestImplementation("androidx.test.ext:junit:1.2.1")
	androidTestImplementation("com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework:4.1.1")
	androidTestImplementation("com.google.guava:guava:33.3.1-android")
}
