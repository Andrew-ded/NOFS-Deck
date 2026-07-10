package com.nofs.desk.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nofs.desk.data.DeskSettings
import com.nofs.desk.net.DiscoveredAgent
import com.nofs.desk.ui.theme.DeskCard
import com.nofs.desk.ui.theme.DeskMuted
import com.nofs.desk.ui.theme.DeskText
import com.nofs.desk.ui.theme.Sage
import kotlinx.coroutines.launch

/** Настройки подключения: демо-режим, адрес ПК, автопоиск по UDP. */
@Composable
fun SettingsDialog(
    current: DeskSettings,
    onDismiss: () -> Unit,
    onApply: (DeskSettings) -> Unit,
    discover: suspend () -> DiscoveredAgent?
) {
    var demoMode by remember { mutableStateOf(current.demoMode) }
    var host by remember { mutableStateOf(current.host) }
    var portText by remember { mutableStateOf(current.port.toString()) }
    var searching by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeskCard,
        title = {
            Text(
                text = "Подключение",
                style = MaterialTheme.typography.titleMedium,
                color = DeskText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Демо-режим",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeskText
                        )
                        Text(
                            text = "фейковые данные, без ПК",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeskMuted
                        )
                    }
                    Switch(
                        checked = demoMode,
                        onCheckedChange = { demoMode = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Sage.bar,
                            checkedThumbColor = DeskCard
                        )
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP ПК") },
                        singleLine = true,
                        enabled = !demoMode,
                        modifier = Modifier.weight(1.6f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { c -> c.isDigit() } },
                        label = { Text("Порт") },
                        singleLine = true,
                        enabled = !demoMode,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !demoMode && !searching,
                        onClick = {
                            searching = true
                            searchResult = null
                            scope.launch {
                                val found = discover()
                                searching = false
                                if (found != null) {
                                    host = found.host
                                    portText = found.port.toString()
                                    searchResult = "Найден: ${found.name} (${found.host})"
                                } else {
                                    searchResult = "ПК не найден — проверь, что агент запущен"
                                }
                            }
                        }
                    ) { Text("Найти ПК в сети", color = DeskText) }
                    if (searching) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            strokeWidth = 2.dp,
                            color = Sage.bar
                        )
                    }
                }
                searchResult?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = DeskMuted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    DeskSettings(
                        demoMode = demoMode,
                        host = host.trim(),
                        port = portText.toIntOrNull() ?: 48484
                    )
                )
            }) { Text("Применить", color = DeskText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = DeskMuted) }
        }
    )
}
