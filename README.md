# Budget Tracker

Application Android **personnelle**, **native Kotlin** et **100 % hors ligne** pour suivre
automatiquement les dépenses Mobile Money au Bénin (MTN MoMo, Moov Money, Celtiis).

Les transactions sont déduites des **SMS** et des **notifications** de paiement, stockées
localement dans Room, puis classées par catégorie avec un motif. Aucune donnée ne quitte
le téléphone : **aucune permission `INTERNET`, aucun backend, aucune bibliothèque réseau**.

## Pile technique

| Élément | Choix |
|---|---|
| Langage | Kotlin 2.1.21 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture légère (Data / Domain / UI) |
| Persistance | Room 2.7.1 (SQLite) |
| Asynchrone | Coroutines + Flow |
| Tâches de fond | WorkManager (alerte budgétaire quotidienne) |
| Build | AGP 8.11.1, Gradle 8.13, JDK 17+, compileSdk/targetSdk 36, minSdk 26 |

## Arborescence

```text
Budget-Tracker/
├── build.gradle.kts                 # plugins racine
├── settings.gradle.kts
├── gradle/libs.versions.toml        # catalogue de versions
├── gradle/wrapper/                  # wrapper Gradle 8.13
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── schemas/                     # schéma Room exporté (JSON)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/duduezn/budgettracker/
│       │   │   ├── BudgetTrackerApp.kt
│       │   │   ├── di/AppContainer.kt
│       │   │   ├── domain/
│       │   │   │   ├── model/Models.kt
│       │   │   │   ├── parser/MessageParser.kt
│       │   │   │   ├── repository/BudgetRepository.kt
│       │   │   │   ├── usecase/ (3 cas d'usage)
│       │   │   │   └── util/MonthRange.kt
│       │   │   ├── data/
│       │   │   │   ├── local/ (entités, DAO, base, catégories par défaut)
│       │   │   │   ├── mapper/Mappers.kt
│       │   │   │   ├── parser/MobileMoneyParser.kt
│       │   │   │   ├── repository/OfflineBudgetRepository.kt
│       │   │   │   ├── sms/SmsReceiver.kt
│       │   │   │   ├── notification/MomoNotificationListenerService.kt
│       │   │   │   └── work/ (worker + planificateur)
│       │   │   ├── ui/
│       │   │   │   ├── MainActivity.kt
│       │   │   │   ├── BudgetTrackerRoot.kt
│       │   │   │   ├── BudgetViewModelFactory.kt
│       │   │   │   ├── dashboard/ transactions/ categories/
│       │   │   │   ├── components/ theme/ model/ util/
│       │   │   └── util/NotificationHelper.kt
│       │   └── res/ (thème, icônes adaptatives, chaînes)
│       └── test/…/MobileMoneyParserTest.kt
└── README.md
```

## Base de données Room

### `transactions`

| Colonne | Type | Rôle |
|---|---|---|
| `id` | INTEGER PK | identifiant |
| `rawMessage` | TEXT | SMS / notification d'origine |
| `operator` | TEXT | `MTN_MOMO`, `MOOV_MONEY`, `CELTIIS`, `UNKNOWN` |
| `type` | TEXT | `DEPOT`, `RETRAIT`, `PAIEMENT` |
| `amount` | REAL | montant |
| `currency` | TEXT | `XOF` |
| `counterparty` | TEXT? | numéro / marchand |
| `timestamp` | INTEGER | horodatage |
| `rawReason` | TEXT? | libellé extrait du message |
| `detectedCategory` | INTEGER? FK | catégorie détectée |
| `finalReason` | TEXT? | motif final |
| `status` | TEXT | `PENDING_REASON` ou `CONFIRMED` |

Index unique `(rawMessage, timestamp)` + recherche de doublon (montant, opérateur,
±10 minutes) pour éviter qu'un même paiement reçu par SMS **et** par notification soit
compté deux fois.

### `categories`

`id`, `name` (unique), `monthlyBudgetLimit`, `colorHex`, `iconName`.

Catégories pré-remplies : **Restauration**, **Transport**, **Factures & Services**,
**Achats**, **Épargne**, **Transferts/Retraits**, **Autres** (avec des plafonds de départ
modifiables).

### `merchant_rules`

`id`, `counterpartyIdentifier` (unique, normalisé en majuscules), `defaultReason`,
`categoryId` (FK), `usageCount`.

Lorsqu'une transaction en attente est confirmée, une règle est créée pour le marchand.
Les paiements suivants vers ce marchand sont alors classés et confirmés automatiquement.

## Cycle d'une transaction

1. `SmsReceiver` (ou `MomoNotificationListenerService`) reçoit un message.
2. `MobileMoneyParser` détecte l'opérateur, le type, le montant, le bénéficiaire et une
   catégorie éventuelle par mots-clés.
3. `OfflineBudgetRepository.ingest()` déduplique puis applique les règles marchand.
4. Si une catégorie est connue → statut **`CONFIRMED`** ; sinon → **`PENDING_REASON`**.
5. L'écran Transactions permet de choisir une catégorie et un motif, ce qui passe la
   transaction à `CONFIRMED` et enrichit les règles marchand.

## Compiler l'APK

Prérequis : JDK 17 ou 21, Android SDK 36, plateforme `android-36` et build-tools installés.

```bash
# Depuis la racine du projet
export JAVA_HOME="/chemin/vers/jdk-17"     # ou JDK 21
echo "sdk.dir=/chemin/vers/Android/Sdk" > local.properties

./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease
# APK release minifié, signé avec la clé debug pour une installation personnelle :
# app/build/outputs/apk/release/app-release.apk
```

> La build `release` est signée avec la clé de debug pour permettre l'installation
> directe sur votre téléphone. Pour une distribution publique, créez un vrai keystore
> et remplacez `signingConfig = signingConfigs.getByName("debug")` dans
> `app/build.gradle.kts`.

Tests unitaires du parseur :

```bash
./gradlew testDebugUnitTest
```

Sous Windows, `local.properties` doit utiliser des antislashs échappés :

```properties
sdk.dir=C\:\\Android\\sdk
```

## Autorisations

| Autorisation | Usage |
|---|---|
| `RECEIVE_SMS` | capter les SMS de transaction |
| `READ_SMS` | lecture de l'historique (import ultérieur) |
| `POST_NOTIFICATIONS` | alerte locale à 80 % du budget |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | accès aux notifications, accordé par l'utilisateur dans les réglages |

L'accès aux notifications s'active depuis l'écran d'accueil ou
**Paramètres → Applications → Accès spécial → Accès aux notifications**.

## Vie privée

- Aucune permission `INTERNET` dans le manifeste.
- Aucune dépendance réseau (pas de Retrofit, Firebase, Analytics, etc.).
- Sauvegarde Android désactivée (`allowBackup="false"`).
- Les messages bruts restent dans la base SQLite privée de l'application.

## Limites connues (v1)

- Les libellés des opérateurs évoluent : le parseur est tolérant mais peut nécessiter des
  ajouts de mots-clés.
- L'import de l'historique SMS existant n'est pas encore exposé dans l'UI (la permission
  `READ_SMS` est déjà déclarée).
- Les virements entre ses propres comptes ne sont pas encore neutralisés.
