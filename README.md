# TouchMouse 🖱️ + 🎤 Whisper Flow

## 📲 Installer en 1 clic

**Sur ton Android, ouvre ce lien et installe :**

👉 **https://github.com/dominiqueadinkpo0-cmd/TouchMouse/releases/latest/download/TouchMouse.apk**

1. Ouvre le lien sur le téléphone → télécharge `TouchMouse.apk`
2. Ouvre le fichier → Autoriser l'installation → Installer
3. Ouvre TouchMouse → active Accessibilité + Survol d'écran → active l'Overlay

> Le lien télécharge toujours la dernière version (build auto à chaque mise à jour).

**App Android pour utiliser un téléphone dont le tactile est cassé à 75% + dictée vocale partout (comme Wispr Flow).**

## Fonctionnalités

### 🖱️ Souris virtuelle pour écran cassé
- **Curseur rouge flottant** qui peut aller partout, même dans la zone morte
- **Mini touchpad** (pad) placé uniquement dans les 25% qui marchent encore → glisser dans le pad = déplacer le curseur sur tout l'écran
- Pad **déplaçable** (glisse sa barre de titre) et **redimensionnable** + position configurable (bas-gauche par défaut, idéal si seul le bas marche)
- **Clic / Long clic / Double clic / Drag & Drop / Scroll** via le pad ou la barre d'actions flottante
- **Barre d'actions** : Back, Home, Recents, Notifications, Scroll haut/bas
- **Volume +/-** = déplace curseur (secours si pad trop petit)
- **Gyroscope** optionnel : incliner le téléphone = déplacer curseur
- **Dwell click** : clic auto après immobilité (800ms par défaut)
- Vitesse réglable (0.6x à 3.5x), transparence, taille

### 🎤 Whisper Flow like
- **Bouton micro flottant 🎤** (déplaçable) visible par dessus toutes les apps
- **Appui long** → parle → relâche → **texte tapé automatiquement** dans le champ focus (SMS, WhatsApp, Chrome, etc.)
- **Tap court** → dictée 6 secondes
- 2 modes :
  - **Google offline (gratuit)** : `SpeechRecognizer` Android, rapide, fonctionne hors-ligne si pack FR installé
  - **OpenAI Whisper API** : plus précis, multilingue, nécessite clé `sk-...`
- Injection via **AccessibilityService** : `ACTION_SET_TEXT` ou clipboard + `ACTION_PASTE` → fonctionne partout, comme Wispr Flow
- Copie automatique en fallback si injection impossible

## Permissions requises
1. **Accessibilité** (BIND_ACCESSIBILITY_SERVICE) : injecte gestes + texte - Coeur de l'app
2. **Afficher par dessus d'autres apps** (SYSTEM_ALERT_WINDOW) : overlay curseur/pad/micro
3. **Micro** (RECORD_AUDIO) : dictée
4. **Service au premier plan** : garde l'overlay vivant

> Aucune donnée collectée. Tout reste local sauf si tu choisis Whisper API (audio envoyé à OpenAI uniquement pendant la dictée).

## Installation rapide

### Option A : Android Studio (recommandé, 2 min)
1. Ouvre `TouchMouse` dans Android Studio
2. `Build > Make Project` puis `Build > Build APK(s)` ou `Run` sur ton téléphone
3. APK généré : `app/build/outputs/apk/debug/app-debug.apk` → installe-le

### Option B : Termux sur le téléphone cassé lui-même
```bash
# Dans Termux
pkg install openjdk-17 gradle wget unzip -y
cd /sdcard/TouchMouse
chmod +x gradlew
./gradlew assembleDebug
# APK -> app/build/outputs/apk/debug/app-debug.apk
# Installe : cp app/build/outputs/apk/debug/app-debug.apk /sdcard/ && ouvre avec gestionnaire fichiers
```

### Option C : Ligne de commande (PC Linux/Mac)
```bash
# Installe Android SDK command line tools si pas déjà :
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip && mv cmdline-tools latest
export ANDROID_HOME=~/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Build
cd /chemin/vers/TouchMouse
chmod +x gradlew
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Première utilisation (après install)
1. Ouvre **TouchMouse**
2. **1️⃣ Active Accessibilité** → liste → TouchMouse → Autoriser
3. **2️⃣ Autorise Survol d'écran**
4. **3️⃣ Autorise Micro** (pour dictée)
5. Active **Overlay actif** → le curseur + pad + 🎤 apparaissent
6. **Glisse le PAD** dans la zone tactile qui marche (ex: coin bas-gauche si seul 25% répond)
7. Utilise le pad pour contrôler tout l'écran !

## Astuces écran cassé
- Si **seulement 25% en bas marche** : choisis `Bas-Gauche` ou `Bas-Droite` + déplace le pad au pixel près via sa barre de titre
- **Sensibilité** à 2.0x si tu veux moins bouger le doigt
- Active **Volume +/-** comme secours pour déplacer le curseur sans pad
- **Dwell click** utile si tu n'arrives pas à tapoter précisément

## Structure projet
```
TouchMouse/
├── app/src/main/java/com/touchmouse/
│   ├── MainActivity.kt          # UI réglages (Compose)
│   ├── TouchMouseService.kt     # AccessibilityService (gestures + injection texte)
│   ├── OverlayManager.kt        # Curseur + Pad + Controls + bouton micro
│   ├── WhisperHelper.kt         # STT Google / Whisper API
│   ├── OverlayService.kt        # Foreground service
│   └── Prefs.kt                 # DataStore
├── app/src/main/AndroidManifest.xml
└── gradle/wrapper/
```

## Dépannage
- **Clic ne marche pas** : vérifie que Accessibilité est bien activé (doit dire ✅) et que l'overlay est actif
- **Texte dicté ne s'insère pas** : mets le focus dans un champ texte avant de dicter, sinon le texte est copié (colle manuellement avec le curseur)
- **Pad trop petit/grand** : règle Taille + Sensibilité dans l'app
- **Bouton micro ne bouge pas** : glisse-le (pas un tap) pour le repositionner

## Build debug APK inclus ?
Lance `./gradlew assembleDebug` pour générer `app-debug.apk` installable directement (debug signé).

---
Fait pour dépanner un Android avec tactile 75% HS. Inspiré par Wispr Flow pour la dictée.
