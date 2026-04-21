# ProGuard / R8 rules for the release build of composeApp.
#
# Baseline: the default rules from AGP (see `proguard-android-optimize.txt`,
# applied together with this file in composeApp/build.gradle.kts). The rules
# below cover things that reflect or annotate at runtime and would otherwise
# be stripped or renamed by R8 in a release build.
#
# If you see a `NoClassDefFoundError` / `SerializationException` / missing
# Koin binding only on release, it almost always means a class needs to be
# added here. Add a minimal `-keep` rule pointing to the concrete package.

########################################
# Kotlin / Coroutines
########################################
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes Signature

# kotlinx.coroutines keeps `MainDispatcherLoader` working on Android.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-dontwarn kotlinx.coroutines.debug.**

########################################
# kotlinx.serialization
########################################
# Keep generated `$serializer` companions for every @Serializable class.
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.folga.**$$serializer { *; }
-keepclassmembers class app.folga.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

########################################
# Koin
########################################
# Koin resolves beans by class literal at runtime; keep our own DI surface.
-keep class org.koin.** { *; }
-keep class app.folga.di.** { *; }
-dontwarn org.koin.**

########################################
# Firebase (Google) + GitLive Kotlin SDK
########################################
# The GitLive SDK delegates to the Google Firebase Android SDK via reflection,
# and the Google SDK itself reads annotations at runtime for Firestore.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class dev.gitlive.firebase.** { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn dev.gitlive.firebase.**

# Firestore maps between documents and POJOs using reflection over
# @PropertyName / @IgnoreExtraProperties. We don't use that path (everything
# is @Serializable strings), but keep the attributes for safety.
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.PropertyName <fields>;
}

########################################
# App-specific DTOs
########################################
# Every DTO used for Firestore encoding lives under data/FirestoreDto.kt;
# keep their fields so R8 doesn't strip/rename them (they are serialized by
# their property names).
-keep class app.folga.data.** { *; }
-keep class app.folga.domain.** { *; }

########################################
# Compose / Android
########################################
# Default Compose rules are shipped by AGP. These extras just silence noise
# from optional deps that are not on the classpath for the Android target.
-dontwarn org.jetbrains.compose.resources.**
-dontwarn androidx.compose.**
