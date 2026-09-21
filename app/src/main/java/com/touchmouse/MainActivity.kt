package com.touchmouse

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val overlayPermLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        // retour
    }
    private val recordPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()){}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                TouchMouseApp(
                    onRequestOverlay = { requestOverlay() },
                    onRequestRecord = { recordPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                    onOpenAccessibility = { openAccessibility() }
                )
            }
        }
    }

    private fun requestOverlay(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)){
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermLauncher.launch(intent)
        }
    }
    private fun openAccessibility(){
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchMouseApp(
    onRequestOverlay: ()->Unit,
    onRequestRecord: ()->Unit,
    onOpenAccessibility: ()->Unit
){
    val ctx = LocalContext.current
    var prefs by remember { mutableStateOf(AppPrefs()) }
    var overlayGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }
    var hasRecord by remember { mutableStateOf(false) }

    LaunchedEffect(Unit){
        ctx.prefsFlow().collectLatest { prefs = it }
    }
    // poll permissions
    LaunchedEffect(Unit){
        while(true){
            overlayGranted = Settings.canDrawOverlays(ctx)
            // accessibility check via enabled services list
            val acc = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            accessibilityGranted = acc.contains("com.touchmouse")
            hasRecord = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            kotlinx.coroutines.delay(1000)
        }
    }

    fun save(block: suspend androidx.datastore.preferences.core.MutablePreferences.()->Unit){
        (ctx as ComponentActivity).lifecycleScope.launch { ctx.dataStore.edit(block) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("TouchMouse 🖱️ + 🎤 Flow", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E88E5), titleContentColor = Color.White)
            )
        }
    ){ pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ){

            Text("App souris pour écran cassé à 75% + dictée Whisper Flow", fontWeight = FontWeight.SemiBold)
            Text("Place le mini touchpad dans la zone qui marche encore (ex: 25% en bas à gauche). Le curseur rouge se déplace partout.", fontSize = 13.sp, color = Color.DarkGray)

            // ETAPE 1
            Card(colors = CardDefaults.cardColors(containerColor = if(accessibilityGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))){
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)){
                    Text("1️⃣ Service d'accessibilité (OBLIGATOIRE)", fontWeight = FontWeight.Bold)
                    Text("Permet d'injecter les clics/scrolls partout et d'insérer le texte dicté.", fontSize=12.sp)
                    Text(if(accessibilityGranted) "✅ Activé" else "❌ Désactivé", color = if(accessibilityGranted) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                    Button(onClick = onOpenAccessibility, modifier=Modifier.fillMaxWidth()){
                        Text(if(accessibilityGranted) "Vérifier / Désactiver" else "Activer TouchMouse")
                    }
                    Text("Chemin: Paramètres > Accessibilité > TouchMouse > Activer", fontSize=11.sp, color=Color.Gray)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = if(overlayGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))){
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)){
                    Text("2️⃣ Autorisation survol d'écran", fontWeight = FontWeight.Bold)
                    Text("Affiche le curseur + pad + bouton micro par dessus toutes les apps.", fontSize=12.sp)
                    Text(if(overlayGranted) "✅ Autorisé" else "❌ Non autorisé", fontWeight=FontWeight.Bold, color = if(overlayGranted) Color(0xFF2E7D32) else Color.Red)
                    Button(onClick = onRequestOverlay, modifier=Modifier.fillMaxWidth()){
                        Text("Autoriser l'affichage par dessus")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = if(hasRecord) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))){
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)){
                    Text("3️⃣ Micro pour Whisper Flow", fontWeight = FontWeight.Bold)
                    Text("Dictée vocale qui tape à ta place dans n'importe quelle app (comme Wispr Flow).", fontSize=12.sp)
                    Text(if(hasRecord) "✅ Autorisé" else "⏳ Non autorisé (optionnel mais recommandé)", fontWeight=FontWeight.Bold)
                    Button(onClick=onRequestRecord, modifier=Modifier.fillMaxWidth()){ Text("Autoriser le micro") }
                }
            }

            // Activation overlay
            val canEnable = overlayGranted && accessibilityGranted
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))){
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)){
                    Text("🚀 Lancer la souris", fontWeight=FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()){
                        Text("Overlay actif")
                        Switch(checked = prefs.overlayEnabled, onCheckedChange = { enabled ->
                            save { this[PrefsKeys.OVERLAY_ENABLED]=enabled }
                            if(enabled){
                                if(!canEnable){
                                    save { this[PrefsKeys.OVERLAY_ENABLED]=false }
                                    return@Switch
                                }
                                // start service
                                val svc = TouchMouseService.instance
                                if(svc != null) svc.tryShowOverlayIfPermitted()
                                else {
                                    // Lance foreground service qui déclenchera l'overlay quand accessibility sera prêt
                                    val i = Intent(ctx, OverlayService::class.java)
                                    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
                                    // aussi tente via accessibility si déjà connecté
                                }
                            } else {
                                TouchMouseService.instance?.hideOverlay()
                                try{ ctx.stopService(Intent(ctx, OverlayService::class.java)) }catch(_:Exception){}
                            }
                        }, enabled = canEnable)
                    }
                    if(!canEnable) Text("Active 1 et 2 d'abord", color=Color.Red, fontSize=12.sp)
                    if(prefs.overlayEnabled && canEnable) Text("Curseur + pad + micro flottant affichés. Glisse le pad dans la zone tactile qui marche.", fontSize=12.sp, color=Color(0xFF1565C0))
                }
            }

            HorizontalDivider()

            Text("⚙️ Réglages souris", fontWeight=FontWeight.Bold, fontSize=18.sp)

            // Sensibilité
            Text("Sensibilité: ${"%.1f".format(prefs.sensitivity)}x")
            Slider(value=prefs.sensitivity, onValueChange={v-> save{ this[PrefsKeys.SENSITIVITY]=v }}, valueRange=0.6f..3.5f, steps=5)

            Text("Taille pad: ${prefs.padSize} dp (100 = tient dans 1/4 gauche)")
            Slider(value=prefs.padSize.toFloat(), onValueChange={v-> save{ this[PrefsKeys.PAD_SIZE]=v.toInt()}}, valueRange=100f..320f, steps=8)

            Text("Transparence pad: ${(prefs.padAlpha*100).toInt()}%")
            Slider(value=prefs.padAlpha, onValueChange={v-> save{ this[PrefsKeys.PAD_ALPHA]=v }}, valueRange=0.25f..1f)

            Text("Position initiale pad")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier=Modifier.fillMaxWidth()){
                listOf("middle_left" to "⬅ Gauche (ton cas)", "middle_right" to "Droite ➡", "bottom_left" to "Bas-Gauche", "bottom_right" to "Bas-Droite", "top_left" to "Haut-Gauche", "top_right" to "Haut-Droite").forEach{ (k,label)->
                    FilterChip(selected = prefs.padPosition==k, onClick={ save{ this[PrefsKeys.PAD_POSITION]=k; this[PrefsKeys.PAD_X]=-1; this[PrefsKeys.PAD_Y]=-1 } }, label={ Text(label, fontSize=11.sp)})
                }
            }
            Text("Ton cas = 1/4 gauche qui marche : choisis ⬅ Gauche. Mets taille 110-140 dp pour que le pad tienne dans la bande. Tu peux aussi glisser le pad via sa barre de titre au pixel près.", fontSize=11.sp, color=Color.Gray)

            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()){
                Text("Clic auto (dwell) après immobilité")
                Switch(checked=prefs.dwellClick, onCheckedChange={v-> save{ this[PrefsKeys.DWELL_CLICK]=v }})
            }
            if(prefs.dwellClick){
                Text("Délai dwell: ${prefs.dwellTime} ms")
                Slider(value=prefs.dwellTime.toFloat(), onValueChange={v-> save{ this[PrefsKeys.DWELL_TIME]=v.toInt()}}, valueRange=400f..1800f)
            }

            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()){
                Text("Contrôle volume (+/- déplace curseur)")
                Switch(checked=prefs.volumeControl, onCheckedChange={v-> save{ this[PrefsKeys.VOLUME_CONTROL]=v }})
            }
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()){
                Text("Contrôle gyroscope (inclinaison)")
                Switch(checked=prefs.gyroControl, onCheckedChange={v-> save{ this[PrefsKeys.GYRO_CONTROL]=v }})
            }

            HorizontalDivider()
            Text("🎤 Whisper Flow", fontWeight=FontWeight.Bold, fontSize=18.sp)
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()){
                Text("Activer bouton micro flottant")
                Switch(checked=prefs.whisperEnabled, onCheckedChange={v-> save{ this[PrefsKeys.WHISPER_ENABLED]=v }})
            }
            Text("Mode transcription", fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(selected=prefs.whisperMode=="offline_google", onClick={ save{ this[PrefsKeys.WHISPER_MODE]="offline_google"}}, label={ Text("Google offline (gratuit)")})
                FilterChip(selected=prefs.whisperMode=="whisper_api", onClick={ save{ this[PrefsKeys.WHISPER_MODE]="whisper_api"}}, label={ Text("OpenAI Whisper")})
            }
            Text("Google offline = rapide, gratuit, fonctionne sans internet si pack FR installé. Whisper = plus précis, payant à l'usage.", fontSize=11.sp, color=Color.Gray)

            if(prefs.whisperMode=="whisper_api"){
                var key by remember(prefs.whisperApiKey){ mutableStateOf(prefs.whisperApiKey) }
                OutlinedTextField(value=key, onValueChange={key=it}, label={ Text("Clé API OpenAI sk-...")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
                Button(onClick={ save{ this[PrefsKeys.WHISPER_API_KEY]=key } }, modifier=Modifier.fillMaxWidth()){ Text("Sauvegarder clé")}
                Text("La clé reste sur l'appareil. Audio envoyé à api.openai.com uniquement lors de la dictée.", fontSize=11.sp, color=Color.Gray)
            }

            Text("Langue dictée")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("fr" to "Français", "en" to "English", "auto" to "Auto").forEach{ (k,l) ->
                    FilterChip(selected=prefs.whisperLanguage==k, onClick={ save{ this[PrefsKeys.WHISPER_LANGUAGE]=k }}, label={ Text(l) })
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))){
                Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("Comment utiliser:", fontWeight=FontWeight.Bold)
                    Text("• Souris: glisse dans le PAD → curseur bouge partout. Tap dans PAD → clic à la position du curseur rouge.", fontSize=12.sp)
                    Text("• Déplacer PAD: glisse sa barre de titre (\"◉ PAD\") pour le mettre dans la zone tactile valide.", fontSize=12.sp)
                    Text("• Scroll: boutons ↑/↓ ou swipe 2 doigts dans PAD (selon apps).", fontSize=12.sp)
                    Text("• Micro: appui long sur 🎤 → parle → relâche → texte tapé auto. Tap court = dictée 6s.", fontSize=12.sp)
                    Text("• Volume: si activé, Volume +/- déplace curseur (secours si pad trop petit).", fontSize=12.sp)
                }
            }

            OutlinedButton(onClick={
                // reset
                (ctx as ComponentActivity).lifecycleScope.launch{
                    ctx.dataStore.edit{ it.clear() }
                }
            }, modifier=Modifier.fillMaxWidth()){ Text("Réinitialiser réglages") }

            Text("TouchMouse v1.0 – 100% local sauf si Whisper API choisi. Aucune donnée collectée.", fontSize=11.sp, color=Color.Gray, modifier=Modifier.padding(top=12.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}
