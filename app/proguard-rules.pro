# Room : les entités sont instanciées par réflexion par le code généré.
-keep class com.duduezn.budgettracker.data.local.entity.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# Les parseurs sont référencés directement, aucune règle supplémentaire n'est requise.
