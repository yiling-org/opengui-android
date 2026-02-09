plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "top.yling.ozx.guiagent.hidden_api"
    compileSdk = 36
    
    defaultConfig {
        minSdk = 24
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly(libs.androidx.annotation)
    compileOnly(libs.rikka.refine.annotation)
    annotationProcessor(libs.rikka.refine.processor)
}
