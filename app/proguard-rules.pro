# Add project specific ProGuard rules here.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.captasoluciones.signage.**$$serializer { *; }
-keepclassmembers class com.captasoluciones.signage.** {
    *** Companion;
}
-keepclasseswithmembers class com.captasoluciones.signage.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Media3 / ExoPlayer
-dontwarn com.google.android.exoplayer2.**
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**

# Keep model classes used by kotlinx.serialization reflection-free codegen
-keepclassmembers,allowobfuscation class com.captasoluciones.signage.data.model.** {
    <fields>;
}
