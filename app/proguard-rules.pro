# Projekt-spezifische R8-Regeln. Navigation-Routen (kotlinx.serialization) bringen ihre Regeln selbst mit.

# Ktor (supabase-kt, Cloud-Backup) verweist optional auf JVM-Klassen, die es auf Android nicht gibt.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.util.debug.**

# LiteRT-LM (lokaler KI-Coach): Die native Bibliothek (JNI) ruft Kotlin/Java-Klassen und Felder per Name auf.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.google.ai.edge.litertlm.**
