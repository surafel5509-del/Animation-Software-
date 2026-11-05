# Project models are serialized through generated Kotlin serializers.
-keepclassmembers class * { @kotlinx.serialization.Serializable <init>(...); }
