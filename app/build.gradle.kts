plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hqcard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hqcard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // MMDD：模块代码物化在 modules/<name>/impl/ 下，通过 sourceSets 挂载进 app 模块
    sourceSets {
        getByName("main") {
            java.srcDirs(
                "../modules/nfc/impl/src/main/java",
                "../modules/hce/impl/src/main/java",
                "../modules/detector/impl/src/main/java",
                "../modules/record/impl/src/main/java",
                "../modules/app_shell/impl/src/main/java",
            )
        }
        getByName("test") {
            java.srcDirs(
                "../modules/nfc/impl/src/test/java",
                "../modules/hce/impl/src/test/java",
                "../modules/detector/impl/src/test/java",
                "../modules/record/impl/src/test/java",
                "../modules/app_shell/impl/src/test/java",
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric + Room 内存库需要
        }
    }

    tasks.withType<Test> {
        // Robolectric 运行时从 Maven Central 拉取 android-all，改为阿里镜像
        systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/central")
        systemProperty("robolectric.dependency.repo.id", "aliyun")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
