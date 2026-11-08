
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)

    androidTarget()


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3) // Using Material 3!
                implementation(compose.materialIconsExtended) // For professional media icons
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.documentfile)
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.session)
                implementation(libs.media3.ui)
                implementation(libs.androidx.palette)
                implementation(libs.jaudiotagger)
            }
        }

    }
}

android {
    namespace = "com.tushar.voidplayer"
    compileSdk = 36 // Android 16 (Baklava)

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Published package on F-Droid and GitHub Releases. Changing it breaks updates for
        // every installed user: F-Droid rejects a build whose package differs from its app ID.
        applicationId = "com.tushar.voidplayer"
        minSdk = 26
        targetSdk = 36 // Android 16
        versionCode = 7
        versionName = "2.4.0"
    }

    flavorDimensions += "distribution"
    productFlavors {
        // F-Droid builds this flavor (`gradle: [fdroid]` in fdroiddata). Its signed release
        // APK is also the GitHub Releases asset that F-Droid compares for reproducibility.
        create("fdroid") {
            dimension = "distribution"
            applicationId = "com.tushar.voidplayer"
            buildConfigField("String", "DISTRIBUTION", "\"fdroid\"")
        }
        // Google Play build, published under the Play package name.
        create("full") {
            dimension = "distribution"
            applicationId = "com.orbitwork.VoidPlayer"
            buildConfigField("String", "DISTRIBUTION", "\"full\"")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { localProps.load(it) }
    }

    signingConfigs {
        create("release") {
            val envFile: String? = System.getenv("VOID_KEYSTORE_FILE")
            val propFile = project.findProperty("VOID_KEYSTORE_FILE") as? String
            val localFile: String? = localProps.getProperty("VOID_KEYSTORE_FILE")
            val keystorePath = envFile ?: propFile ?: localFile ?: "keystore.jks"
            val keystoreFile = rootProject.file(keystorePath)

            val envPass: String? = System.getenv("VOID_KEYSTORE_PASSWORD")
            val propPass = project.findProperty("VOID_KEYSTORE_PASSWORD") as? String
            val localPass: String? = localProps.getProperty("VOID_KEYSTORE_PASSWORD")
            val keystorePass = envPass ?: propPass ?: localPass

            val envAlias: String? = System.getenv("VOID_KEY_ALIAS")
            val propAlias = project.findProperty("VOID_KEY_ALIAS") as? String
            val localAlias: String? = localProps.getProperty("VOID_KEY_ALIAS")
            val keyAliasName = envAlias ?: propAlias ?: localAlias ?: "voidplayer"

            val envKeyPass: String? = System.getenv("VOID_KEY_PASSWORD")
            val propKeyPass = project.findProperty("VOID_KEY_PASSWORD") as? String
            val localKeyPass: String? = localProps.getProperty("VOID_KEY_PASSWORD")
            val keyPass = envKeyPass ?: propKeyPass ?: localKeyPass ?: keystorePass

            if (keystoreFile.exists() && !keystorePass.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = keystorePass
                keyAlias = keyAliasName
                keyPassword = keyPass
            }
        }
    }
    
    buildTypes {
        getByName("release") {
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
        }
    }

    packaging {
        resources {
            excludes += "META-INF/version-control-info.textproto"
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val outputImpl = this as? com.android.build.gradle.internal.api.ApkVariantOutputImpl
            if (outputImpl != null) {
                val versionName = defaultConfig.versionName
                // fdroidRelease is named exactly like the asset fdroiddata's `Binaries:` URL
                // expects (V%v/VoidPlayer-%v-release.apk), so it can be uploaded as is.
                outputImpl.outputFileName = if (variant.flavorName == "fdroid") {
                    "VoidPlayer-${versionName}-${variant.buildType.name}.apk"
                } else {
                    "VoidPlayer-${versionName}-${variant.flavorName}-${variant.buildType.name}.apk"
                }
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    dependencies {
        debugImplementation(compose.uiTooling)
    }
}


