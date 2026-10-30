package com.lumen.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Erststart-Bildschirm: ganz zuerst die Sprache wählen (Deutsch/Englisch).
 * Danach wird [Prefs.langPicked] gesetzt und der Dialog erscheint nie wieder.
 */
@Composable
fun LanguagePick(vm: HomeViewModel) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0B0E16), Color(0xFF07080C))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.ic_lumen), contentDescription = "Lumen", modifier = Modifier.size(96.dp))
            Text("Lumen Launcher", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            // Sprachneutral, da noch keine Sprache feststeht:
            Text("Sprache wählen · Choose your language", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Pill("Deutsch", Modifier.focusRequester(first)) { vm.update { it.copy(lang = 0, langPicked = true) } }
                Pill("English") { vm.update { it.copy(lang = 1, langPicked = true) } }
            }
        }
    }
}
