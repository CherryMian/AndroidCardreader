plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.tapcard.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":tapcard"))
    compileOnly("io.reactivex:rxjava:1.3.8")
    compileOnly("io.reactivex.rxjava2:rxjava:2.2.21")
}

