# kotlinx.serialization: keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.personal.workoutcoach.**$$serializer { *; }
-keepclassmembers class com.personal.workoutcoach.** { *** Companion; }
-keepclasseswithmembers class com.personal.workoutcoach.** { kotlinx.serialization.KSerializer serializer(...); }
