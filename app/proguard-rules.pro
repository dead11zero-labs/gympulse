# Projekt-spezifische R8-Regeln. Navigation-Routen (kotlinx.serialization) bringen ihre Regeln selbst mit.

# Ktor (supabase-kt, Cloud-Backup) verweist optional auf JVM-Klassen, die es auf Android nicht gibt.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.util.debug.**
