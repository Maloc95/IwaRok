# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class iwarok.app.core.** {
    *** Companion;
}
-keepclasseswithmembers class iwarok.app.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
