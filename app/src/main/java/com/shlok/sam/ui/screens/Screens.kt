@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.shlok.sam.ui.screens

import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shlok.sam.core.identity.SamIdentity
import com.shlok.sam.core.permissions.SamPermissions
import com.shlok.sam.domain.model.SamCoreState
import com.shlok.sam.domain.model.SystemMode
import com.shlok.sam.ui.SamViewModel
import com.shlok.sam.ui.orb.SamOrb
import com.shlok.sam.ui.theme.LocalSamPalette
import kotlinx.coroutines.launch

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalSamPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(p.glass)
            .padding(16.dp)
    ) { content() }
}

@Composable
fun BackRow(title: String, nav: NavController) {
    val p = LocalSamPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.Outlined.ArrowBack, "Back", tint = p.text)
        }
        Text(title, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    val profile by vm.profile.collectAsState()
    val orb by vm.orb.collectAsState()
    val ui by vm.runtime.ui.collectAsState()
    val online by vm.network.online.collectAsState()
    val haptic = LocalHapticFeedback.current
    val driving = profile.systemMode == SystemMode.DRIVING.name
    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("S.A.M.", color = p.aura, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (online) "ONLINE" else "OFFLINE MODE",
                    color = if (online) p.success else p.danger,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp
                )
            }
            Text(profile.displayName, color = p.muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(if (driving) 280.dp else 240.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(220.dp).blur(28.dp).background(p.aura.copy(0.18f), CircleShape))
            SamOrb(ui.state, ui.amplitude, orb, p.aura, Modifier.fillMaxSize())
        }
        Text(
            "How can I help you, ${profile.displayName}?",
            color = p.text,
            fontSize = if (driving) 22.sp else 16.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(ui.state.name, color = p.muted, fontSize = 11.sp, letterSpacing = 1.sp)
        if (ui.spoken.isNotBlank()) {
            Text(ui.spoken.take(180), color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(
                onClick = {
                    if (orb.haptic) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.mic()
                },
                modifier = Modifier.size(if (driving) 84.dp else 64.dp).clip(CircleShape).background(p.aura)
            ) { Icon(Icons.Outlined.Mic, "Listen", tint = Color.Black, modifier = Modifier.size(if (driving) 36.dp else 28.dp)) }
            IconButton(
                onClick = { vm.stop() },
                modifier = Modifier.size(if (driving) 84.dp else 64.dp).clip(CircleShape).background(Color(0x33FF8A8A))
            ) { Icon(Icons.Outlined.Stop, "Stop", tint = p.danger) }
        }
        val actions = listOf(
            "Voice Mode" to { vm.saveProfile { copy(systemMode = SystemMode.VOICE_ASSISTANT.name) }; vm.mic() },
            "Visual Lens" to { nav.navigate("lens") },
            "Phone Control" to { nav.navigate("permissions") },
            "Tasks" to { nav.navigate("tasks") },
            "Coding" to { nav.navigate("knowledge") },
            "Memory" to { nav.navigate("memory") },
            "Knowledge" to { nav.navigate("knowledge") },
            "Google Search" to { vm.submit("Search Google for today's news") },
            "Wikipedia" to { vm.submit("Search Wikipedia for India") },
            "Deep Research" to { nav.navigate("research") },
            "Image Search" to { vm.submit("Search Google for images of SAM AI orb") },
            "Connectors" to { nav.navigate("connectors") }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (label, act) ->
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x2218E6FF)).clickable { act() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) { Text(label, color = p.text, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: SamViewModel) {
    val p = LocalSamPalette.current
    val msgs by vm.chatMessages.collectAsState()
    val ui by vm.runtime.ui.collectAsState()
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Chat", color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        Text("Voice and text share this conversation.", color = p.muted, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp), reverseLayout = false) {
            items(msgs, key = { it.id }) { m ->
                val mine = m.role == "user"
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (mine) Color(0x332EE6FF) else Color(0x22101828))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(if (mine) "YOU" else "SAM", color = p.aura, fontSize = 10.sp, letterSpacing = 1.sp)
                            Text(m.text, color = p.text, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        if (ui.waitingConfirm) {
            Text("Waiting for confirmation…", color = p.aura, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.submit("yes") }) { Text("Confirm") }
                Button(onClick = { vm.submit("no") }) { Text("Cancel") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Talk to SAM") },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            IconButton(onClick = { vm.mic() }) { Icon(Icons.Outlined.Mic, null, tint = p.aura) }
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    vm.submit(text); text = ""
                }
            }) { Icon(Icons.Outlined.Send, null, tint = p.aura) }
        }
    }
}

@Composable
fun DiscoverScreen(nav: NavController) {
    val p = LocalSamPalette.current
    val items = listOf(
        Triple("Visual Lens", "Camera + OCR", "lens"),
        Triple("Deep Research", "Wikipedia + files + web", "research"),
        Triple("Knowledge", "Upload personal data", "knowledge"),
        Triple("Memory", "Approved notes", "memory"),
        Triple("Task Center", "Live automation steps", "tasks"),
        Triple("Connectors", "AI, voice, search", "connectors")
    )
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("Discover", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        items.forEach { (t, d, r) ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { nav.navigate(r) }) {
                Text(t, color = p.aura, fontWeight = FontWeight.Medium)
                Text(d, color = p.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SettingsHub(nav: NavController) {
    val p = LocalSamPalette.current
    val rows = listOf(
        "Profile" to "profile",
        "Voice" to "profile",
        "Language" to "profile",
        "SAM Engine" to "profile",
        "Wake Word" to "profile",
        "AI Models" to "connectors",
        "Connectors" to "connectors",
        "Memory" to "memory",
        "Permissions" to "permissions",
        "Overlay" to "permissions",
        "Accessibility" to "accessibility",
        "Notifications" to "permissions",
        "Orb Customization" to "core",
        "Aura Control" to "aura",
        "System Modes" to "aura",
        "Privacy" to "privacy",
        "Developer Mode" to "developer",
        "About SAM" to "about"
    )
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("SAM's name is protected and cannot be changed.", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        rows.forEach { (t, r) ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { nav.navigate(r) }) {
                Text(t, color = p.text)
            }
        }
    }
}

@Composable
fun ProfileScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    val profile by vm.profile.collectAsState()
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackRow("Your profile", nav)
        OutlinedTextField(name, { name = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.saveProfile { copy(displayName = name.ifBlank { "there" }) } }) { Text("Save name") }
        Text("Language", color = p.muted, modifier = Modifier.padding(top = 16.dp))
        Row {
            listOf("auto", "en", "hi", "mr").forEach { lang ->
                FilterChip(selected = profile.preferredLanguage == lang, onClick = { vm.saveProfile { copy(preferredLanguage = lang) } }, label = { Text(lang) }, modifier = Modifier.padding(4.dp))
            }
        }
        Text("Voice", color = p.muted, modifier = Modifier.padding(top = 12.dp))
        Row {
            listOf("ANDROID_DEFAULT", "FEMALE", "MALE", "ELEVENLABS").forEach { v ->
                FilterChip(selected = profile.voicePreference == v, onClick = { vm.saveProfile { copy(voicePreference = v) } }, label = { Text(v.take(8), fontSize = 10.sp) }, modifier = Modifier.padding(3.dp))
            }
        }
        Text("Response style", color = p.muted)
        Row {
            listOf("CONCISE", "BALANCED", "DETAILED").forEach { s ->
                FilterChip(selected = profile.responseStyle == s, onClick = { vm.saveProfile { copy(responseStyle = s) } }, label = { Text(s, fontSize = 11.sp) }, modifier = Modifier.padding(4.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Wake word (Sam / Hey Sam)", color = p.text)
            Switch(profile.wakeWordEnabled, { vm.saveProfile { copy(wakeWordEnabled = it) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SAM Engine (background mic)", color = p.text, modifier = Modifier.weight(1f))
            Switch(profile.engineEnabled, { vm.setEngine(it) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Memory", color = p.text)
            Switch(profile.memoryEnabled, { vm.saveProfile { copy(memoryEnabled = it) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Speak notifications", color = p.text)
            Switch(profile.speakNotifications, { vm.saveProfile { copy(speakNotifications = it) } })
        }
        Text("System mode", color = p.muted)
        FlowRow {
            SystemMode.entries.forEach { m ->
                FilterChip(selected = profile.systemMode == m.name, onClick = { vm.saveProfile { copy(systemMode = m.name) } }, label = { Text(m.name, fontSize = 11.sp) }, modifier = Modifier.padding(4.dp))
            }
        }
        Text("Assistant identity is locked to SAM.", color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun OrbCustomizeScreen(vm: SamViewModel) {
    val p = LocalSamPalette.current
    val orb by vm.orb.collectAsState()
    val ui by vm.runtime.ui.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SAM Core", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        SamOrb(ui.state, ui.amplitude, orb, p.aura, Modifier.size(200.dp))
        Text("Orb type")
        FlowRow {
            listOf("CLASSIC", "ENERGY", "NEON", "GALAXY", "MINIMAL", "CUSTOM").forEach { t ->
                FilterChip(orb.orbType == t, { vm.saveOrb { copy(orbType = t) } }, { Text(t, fontSize = 11.sp) }, modifier = Modifier.padding(4.dp))
            }
        }
        Text("Size")
        Slider(orb.sizeSlider, { v -> vm.saveOrb { copy(sizeSlider = v) } }, valueRange = 0.3f..1f)
        Text("Aura")
        FlowRow {
            listOf("BLUE", "CYAN", "PURPLE", "GREEN", "RED", "CUSTOM").forEach { a ->
                FilterChip(orb.aura == a, { vm.saveOrb { copy(aura = a) } }, { Text(a, fontSize = 11.sp) }, modifier = Modifier.padding(4.dp))
            }
        }
        listOf(
            "Aura Border" to orb.auraBorder,
            "Voice Visualizer" to orb.voiceVisualizer,
            "Particles" to orb.particles,
            "Glow" to orb.glow,
            "Rotating Rings" to orb.rotatingRings,
            "Haptic Feedback" to orb.haptic
        ).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = p.text)
                Switch(value, {
                    vm.saveOrb {
                        when (label) {
                            "Aura Border" -> copy(auraBorder = it)
                            "Voice Visualizer" -> copy(voiceVisualizer = it)
                            "Particles" -> copy(particles = it)
                            "Glow" -> copy(glow = it)
                            "Rotating Rings" -> copy(rotatingRings = it)
                            else -> copy(haptic = it)
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun PermissionsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val p = LocalSamPalette.current
    val runtimePerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackRow("SAM Permissions", nav)
        Text("Nothing is granted silently. Connect a capability only when you need it.", color = p.muted, fontSize = 13.sp)
        SamPermissions.all(ctx).forEach { item ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = p.text)
                        Text(if (item.connected) "CONNECTED" else "NOT CONNECTED", color = if (item.connected) p.success else p.danger, fontSize = 11.sp)
                    }
                    if (item.settingsAction != "runtime") {
                        Button(onClick = { SamPermissions.open(ctx, item.settingsAction) }, colors = ButtonDefaults.buttonColors(containerColor = p.aura)) {
                            Text("Open", color = Color.Black)
                        }
                    } else {
                        Button(onClick = {
                            runtimePerms.launch(
                                arrayOf(
                                    android.Manifest.permission.RECORD_AUDIO,
                                    android.Manifest.permission.CAMERA,
                                    android.Manifest.permission.READ_CONTACTS,
                                    android.Manifest.permission.CALL_PHONE,
                                    android.Manifest.permission.READ_CALENDAR,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        }, colors = ButtonDefaults.buttonColors(containerColor = p.aura)) {
                            Text("Ask", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccessibilityScreen(nav: NavController) {
    val ctx = LocalContext.current
    val p = LocalSamPalette.current
    val on = SamPermissions.accessibility(ctx)
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Accessibility", nav)
        Text(if (on) "CONNECTED" else "NOT CONNECTED", color = if (on) p.success else p.danger)
        Text("SAM uses Accessibility only for requested taps, scrolls, typing, and navigation. It cannot bypass lock screens or payments.", color = p.muted, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = { SamPermissions.open(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Enable SAM Accessibility") }
    }
}

@Composable
fun ConnectorsScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    val providers by vm.providers.collectAsState(emptyList())
    val profile by vm.profile.collectAsState()
    var key by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("gemini") }
    var status by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackRow("Connectors", nav)
        Text("AI & Models", color = p.aura)
        providers.filter { it.id != "elevenlabs" }.forEach { pr ->
            GlassCard(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { selected = pr.id }) {
                Text(pr.id, color = p.text, fontWeight = FontWeight.Medium)
                Text(if (pr.lastOk || pr.id == "local") pr.lastStatus else "Not connected", color = if (pr.lastOk || pr.id == "local") p.success else p.muted, fontSize = 12.sp)
            }
        }
        Text("Selected default: ${profile.aiProvider}", color = p.muted, fontSize = 12.sp)
        OutlinedTextField(model, { model = it }, label = { Text("Model (optional)") }, modifier = Modifier.fillMaxWidth())
        if (selected == "custom") {
            OutlinedTextField(base, { base = it }, label = { Text("OpenAI-compatible base URL") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(key, { key = it }, label = { Text("API key (stored encrypted, never logged)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = {
                if (key.isNotBlank()) vm.saveKey(selected, key)
                key = ""
                status = "Key saved locally."
            }) { Text("Save key") }
            Button(onClick = {
                vm.testProvider(selected, model, base) { status = it }
            }) { Text("Test") }
            Button(onClick = { vm.saveProfile { copy(aiProvider = selected) } }) { Text("Use as default") }
        }
        if (status.isNotBlank()) Text(status, color = p.aura)
        Text("Voice", color = p.aura, modifier = Modifier.padding(top = 16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Android TTS — always available")
            Text("ElevenLabs — optional; uses encrypted key id elevenlabs", color = p.muted, fontSize = 12.sp)
        }
        Text("Knowledge / Search / Local", color = p.aura, modifier = Modifier.padding(top = 12.dp))
        Text("Wikipedia (free API), Google Search via device intents, uploaded files, local command engine.", color = p.muted, fontSize = 13.sp)
    }
}

@Composable
fun MemoryScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    val items by vm.memories.collectAsState(emptyList())
    val profile by vm.profile.collectAsState()
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) items else items.filter { it.title.contains(query, true) || it.content.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Memory", nav)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Memory ${if (profile.memoryEnabled) "ON" else "OFF"}", color = p.text)
            Switch(profile.memoryEnabled, { vm.saveProfile { copy(memoryEnabled = it) } })
        }
        OutlinedTextField(query, { query = it }, label = { Text("Search memory") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        Row {
            Button(onClick = { if (body.isNotBlank()) { vm.saveMemory(title.ifBlank { "Note" }, body); title = ""; body = "" } }) { Text("Save") }
            Button(onClick = { vm.clearMemory() }, modifier = Modifier.padding(start = 8.dp)) { Text("Clear all") }
        }
        LazyColumn {
            items(filtered, key = { it.id }) { m ->
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(m.title, color = p.aura)
                    Text(m.content, color = p.text)
                    Text("Delete", color = p.danger, modifier = Modifier.clickable { vm.deleteMemory(m) })
                }
            }
        }
    }
}

@Composable
fun KnowledgeScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    val docs by vm.docs.collectAsState(emptyList())
    var msg by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.ingest(uri) { msg = it }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Personal knowledge", nav)
        Button(onClick = {
            picker.launch(arrayOf("application/pdf", "text/plain", "text/csv", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*", "*/*"))
        }) { Text("Upload data") }
        if (msg.isNotBlank()) Text(msg, color = p.aura)
        LazyColumn {
            items(docs, key = { it.id }) { d ->
                GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(d.name, color = p.text)
                    Text(d.mime, color = p.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TaskCenterScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    val live by vm.tasks.live.collectAsState()
    val list by vm.taskList.collectAsState(emptyList())
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Task Center", nav)
        live?.let { t ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(t.title, color = p.aura)
                Text(t.state.name, color = p.muted)
                t.steps.forEach { s ->
                    Text("${s.state}  ${s.label}", color = p.text, fontSize = 13.sp)
                }
                Button(onClick = { vm.stop() }) { Text("Stop task") }
            }
        } ?: Text("No running task.", color = p.muted)
        LazyColumn {
            items(list, key = { it.id }) { t ->
                Text("${t.state} — ${t.title}", color = p.text, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
fun VisualLensScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) scope.launch {
            val r = vm.lens.analyze(bmp, null, "Describe visible content")
            result = r.description + if (r.ocr.isNotBlank()) "\n\nOCR:\n${r.ocr}" else ""
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val r = vm.lens.analyze(null, uri, null)
            result = r.description
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackRow("Visual Lens", nav)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { photo.launch(null) }) { Text("Camera") }
            Button(onClick = { gallery.launch("image/*") }) { Text("Gallery") }
        }
        Text(result, color = p.text, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun ResearchScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Deep Research", nav)
        OutlinedTextField(q, { q = it }, label = { Text("Topic") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (q.isNotBlank()) vm.submit("deep research $q") }) { Text("Research") }
        Text("SAM will use Wikipedia, uploaded files, and a device Google search. It will not claim a source that failed.", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun AuraScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    val orb by vm.orb.collectAsState()
    val profile by vm.profile.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackRow("Aura Control", nav)
        Text("Visual identity", color = p.aura)
        listOf("CYAN" to "SAM Cyan", "BLUE" to "SAM Blue", "PURPLE" to "SAM Purple", "GREEN" to "SAM Neon", "RED" to "SAM Dark").forEach { (id, name) ->
            FilterChip(orb.aura == id, { vm.saveOrb { copy(aura = id) } }, { Text(name) }, modifier = Modifier.padding(4.dp))
        }
        Text("Core interaction", color = p.aura, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Haptic feedback", color = p.text)
            Switch(orb.haptic, { vm.saveOrb { copy(haptic = it) } })
        }
        Text("System modes", color = p.aura)
        SystemMode.entries.forEach { m ->
            FilterChip(profile.systemMode == m.name, { vm.saveProfile { copy(systemMode = m.name) } }, { Text(m.name) }, modifier = Modifier.padding(4.dp))
        }
    }
}

@Composable
fun AboutScreen(nav: NavController) {
    val p = LocalSamPalette.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("About SAM", nav)
        Text(SamIdentity.aboutText(), color = p.text)
        Text("The assistant name cannot be renamed.", color = p.muted, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun PrivacyScreen(nav: NavController) {
    val p = LocalSamPalette.current
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        BackRow("Privacy", nav)
        Text(
            "Microphone is used only while you talk or while SAM Engine is on (shown in a persistent notification). Audio is not uploaded unless you connect a cloud speech/AI provider. API keys stay in encrypted storage. Personal files stay on-device. Disable Engine, overlay, notification access, and accessibility any time from Settings.",
            color = p.text
        )
    }
}

@Composable
fun DeveloperScreen(vm: SamViewModel, nav: NavController) {
    val p = LocalSamPalette.current
    val logs by vm.logs.collectAsState(emptyList())
    val ui by vm.runtime.ui.collectAsState()
    val live by vm.tasks.live.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        BackRow("Developer Mode", nav)
        Text("State: ${ui.state}", color = p.text)
        Text("Accessibility: ${SamPermissions.accessibility(ctx)}", color = p.muted)
        Text("Overlay: ${SamPermissions.overlay(ctx)}", color = p.muted)
        Text("Network: ${vm.network.isOnline()}", color = p.muted)
        Text("Task: ${live?.state}", color = p.muted)
        LazyColumn {
            items(logs, key = { it.id }) { l ->
                Text("${l.tag}: ${l.message}", color = p.muted, fontSize = 11.sp)
            }
        }
    }
}
