import java.util.Properties
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

val signingPropertiesPath = providers.environmentVariable("EDUESCALE_KEYSTORE_PROPERTIES").orNull
val releaseSigningProperties = Properties()

if (signingPropertiesPath != null) {
    val signingPropertiesFile = file(signingPropertiesPath)
    if (!signingPropertiesFile.isFile) {
        throw GradleException("No se encontró el archivo de propiedades de firma.")
    }
    signingPropertiesFile.inputStream().use { releaseSigningProperties.load(it) }

    val requiredSigningProperties = listOf(
        "storeFile",
        "storePassword",
        "keyAlias",
        "keyPassword",
    )
    val missingSigningProperties = requiredSigningProperties.filter {
        releaseSigningProperties.getProperty(it).isNullOrBlank()
    }
    if (missingSigningProperties.isNotEmpty()) {
        throw GradleException("Faltan propiedades requeridas para firmar release.")
    }
    if (!file(releaseSigningProperties.getProperty("storeFile")).isFile) {
        throw GradleException("No se encontró el keystore configurado para release.")
    }
}

android {
    namespace = "pe.eduescale.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "pe.eduescale.mobile"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "pe.eduescale.mobile.HiltTestRunner"
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("complete") {
            dimension = "edition"
        }
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (signingPropertiesPath != null) {
            create("release") {
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (signingPropertiesPath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("release") {
            if (signingPropertiesPath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

tasks.withType<Test>().configureEach {
    for (directory in listOf(
        rootProject.file("test-fixtures"),
        rootProject.file("../test-fixtures"),
    )) {
        inputs.files(fileTree(directory) {
            include("desktop-contract-*.base64", "desktop-encrypted-school-data.base64", "eduescale-v1-cases.json")
        })
    }
}

dependencies {
    val hiltVersion = "2.60.1"
    val navigationVersion = "2.9.5"
    val roomVersion = "2.7.2"

    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:$navigationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    kapt("com.google.dagger:hilt-android-compiler:$hiltVersion")
    kapt("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
    add("completeImplementation", "io.github.rallista:valhalla-mobile:0.6.6")
    add("completeImplementation", "io.github.rallista:valhalla-models:0.6.0")
    add("completeImplementation", "io.github.rallista:valhalla-models-config:0.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.navigation:navigation-testing:$navigationVersion")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("com.google.dagger:hilt-android-testing:$hiltVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16")
    kaptTest("com.google.dagger:hilt-android-compiler:$hiltVersion")
    androidTestImplementation("androidx.navigation:navigation-testing:$navigationVersion")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("app.cash.turbine:turbine:1.2.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:$hiltVersion")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:$hiltVersion")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
