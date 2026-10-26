package com.example.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.BuildConfig

/** Informational only: this app has no self-update or install route. */
@Composable fun UpdateCheckCard(modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) {
        Text("Version ${BuildConfig.VERSION_NAME}")
        Text("Updates are available only through Netra Ecosystem. This app does not download or install updates.")
    } }
}
