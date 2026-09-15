plugins {
    id("android-core-data")
}

android {
    namespace = "com.rossomak.flashcards.core.common"
}

dependencies {
    implementation(libs.timber)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
