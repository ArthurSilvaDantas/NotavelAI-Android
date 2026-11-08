import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.notavelai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.notavelai"
        // 26: canais de notificação e ícone adaptativo existem em todas as versões suportadas.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    androidResources {
        // O Interpreter mapeia o modelo direto do APK, o que exige o arquivo sem compressão.
        noCompress += "tflite"
    }
}

// Modelo de reconhecimento: não fica no Git (8,9 MB); é baixado do Hugging Face na primeira
// compilação, numa versão fixa e conferida pelo SHA-256, para quem clonar o projeto rodar direto.
// Para trocar de modelo, atualize a revisão e o hash (ou rode ml/src/models/export_android.py).
val modeloRevisao = "d892d5a31e87a44e88e9eebd03a229f560efcec3"
val modeloSha256 = "f656d5a9109f6a0ec32e84b52b6d759d708401e1caaa5462a273479fa299d609"

val baixarModelo = tasks.register("baixarModelo") {
    group = "notavelai"
    description = "Baixa notavelai.tflite do Hugging Face para src/main/assets, se ainda não existir."
    val destino = file("src/main/assets/notavelai.tflite")
    val endereco = "https://huggingface.co/arthursilvadantas/NotavelAI/resolve/$modeloRevisao/tflite/notavelai.tflite"
    val hashEsperado = modeloSha256
    outputs.file(destino)
    onlyIf { !destino.exists() }
    doLast {
        destino.parentFile.mkdirs()
        val temporario = File(destino.parentFile, "notavelai.tflite.part")
        logger.lifecycle("Baixando o modelo de $endereco")
        URI(endereco).toURL().openStream().use { entrada ->
            temporario.outputStream().use { saida -> entrada.copyTo(saida) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        temporario.inputStream().use { entrada ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val lidos = entrada.read(buffer)
                if (lidos < 0) break
                digest.update(buffer, 0, lidos)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (hash != hashEsperado) {
            temporario.delete()
            throw GradleException("Hash do modelo baixado não confere: $hash (esperado $hashEsperado)")
        }
        temporario.renameTo(destino)
        logger.lifecycle("Modelo salvo em $destino (SHA-256 conferido)")
    }
}

tasks.named("preBuild") {
    dependsOn(baixarModelo)
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Splash Screen com o fluxo de inicialização do Android (sem atraso artificial)
    implementation(libs.core.splashscreen)

    // Banco de dados local Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Hash de senhas com salt (BCrypt)
    implementation(libs.jbcrypt)

    // Execução do modelo .tflite que reconhece as cédulas
    implementation(libs.litert)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.accessibility)
    androidTestImplementation(libs.ext.junit)
}
