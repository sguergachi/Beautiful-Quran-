# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.beautifulquran.** {
    *** Companion;
}
-keepclasseswithmembers class com.beautifulquran.** {
    kotlinx.serialization.KSerializer serializer(...);
}
