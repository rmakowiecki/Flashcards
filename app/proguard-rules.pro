# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Serializable domain enums travel in nav routes and in the pending session queue file. core:domain is
# pure Kotlin and cannot carry @Keep, so their constants and members are kept by name here.
# The MissingKeepAnnotation entries in lint-baseline.xml for these enums are covered by this rule.
-keepclassmembers enum com.rossomak.flashcards.core.domain.model.** { *; }
