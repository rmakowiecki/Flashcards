plugins {
    id("android-core-data")
}

android {
    namespace = "com.rossomak.flashcards.core.common"
}

dependencies {
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
