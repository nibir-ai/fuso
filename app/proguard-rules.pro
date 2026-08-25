-keepattributes *Annotation*, InnerClasses, Signature

-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.fuso.**$$serializer { *; }

-keepclassmembers class com.fuso.** {
    *** Companion;
}

-keepclasseswithmembers class com.fuso.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
