plugins {
    id("android-core-data")
}

android {
    namespace = "com.rossomak.flashcards.core.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidsvg)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.functions.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.google.guava.listenablefuture)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

// work-runtime's own module metadata strictly constrains com.google.guava:listenablefuture to an
// empty artifact (it assumes full Guava supplies the real class elsewhere). This project has no
// other Guava dependency, so Dagger/Hilt's generated Java stubs for the @HiltWorker class in this
// module fail `compileDebugJavaWithJavac` without the real, tiny stub forced back in.
configurations.all {
    resolutionStrategy.force(libs.google.guava.listenablefuture.get())
}
