# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.nofs.desk.** {
    *** Companion;
}
-keepclasseswithmembers class com.nofs.desk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
