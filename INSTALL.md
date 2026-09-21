# Installation TouchMouse - Guide ultra rapide

Tu as un écran cassé à 75% ? Voici comment installer en 3 minutes même avec un tactile partiel.

## 1. Récupère l'APK

### Si tu lis ça sur le téléphone cassé (via souris USB OTG ou autre)
- Le projet est dans `/sdcard/TouchMouse` si tu l'as copié
- Sinon transfère le dossier via USB depuis un PC

### Build l'APK (choisis UNE méthode)

#### Méthode la plus simple : Android Studio
1. Installe Android Studio sur PC
2. `File > Open > dossier TouchMouse`
3. Attends synchro Gradle
4. Branche téléphone en USB + active Débogage USB
5. Clique ▶️ Run → choisis ton téléphone
6. Sinon `Build > Build APK` → récupère `app/build/outputs/apk/debug/app-debug.apk` → envoie sur téléphone → ouvre pour installer

#### Méthode sans PC : directement sur Android avec Termux (si tu peux encore taper un peu)
1. Installe Termux depuis F-Droid
2. Ouvre Termux et tape :
```bash
pkg update -y && pkg install openjdk-17 gradle wget unzip -y
termux-setup-storage
cd /sdcard/TouchMouse
./gradlew assembleDebug
```
3. APK généré → `/sdcard/TouchMouse/app/build/outputs/apk/debug/app-debug.apk`
4. Ouvre le avec ton gestionnaire de fichiers → Installer

#### Méthode ADB (PC + câble)
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 2. Autorisations (OBLIGATOIRE)
Ouvre TouchMouse après install :

1️⃣ **Accessibilité** → bouton "Activer TouchMouse" → tu arrives dans Paramètres > Accessibilité → trouve TouchMouse → Active → OK
   → revient dans l'app, doit passer en ✅

2️⃣ **Survol d'écran** → "Autoriser l'affichage par dessus" → active pour TouchMouse

3️⃣ **Micro** → "Autoriser" (pour dictée)

4️⃣ Active le switch **"Overlay actif"** → le curseur rouge + le PAD + le bouton 🎤 apparaissent

## 3. Utilisation immédiate
- **Le PAD gris** : place-le dans la zone qui marche (glisse sa barre "◉ PAD" pour le déplacer)
- **Glisse dans le PAD** → le curseur rouge bouge partout (même zone morte)
- **Tap dans PAD** → clic à la position du curseur
- **Boutons dans PAD** : CLIC / LONG / DRAG
- **Barre du haut** : Back, Home, Scroll etc.
- **🎤 Micro** : appui LONG → parle → relâche → texte tapé auto dans WhatsApp/SMS/Chrome...

## 4. Si tu n'arrives pas à activer Accessibilité à cause du tactile
- Branche une **souris USB via adaptateur OTG** (5€) → tu peux cliquer normalement pour activer
- Ou utilise `adb` :
```bash
adb shell settings put secure enabled_accessibility_services com.touchmouse/com.touchmouse.TouchMouseService
adb shell settings put secure accessibility_enabled 1
```

## 5. Raccourci clavier si tactile vraiment mort
- Volume +/- déplace curseur si option activée dans réglages

Besoin d'aide ? Ouvre une issue avec modèle téléphone + zone tactile qui marche.
