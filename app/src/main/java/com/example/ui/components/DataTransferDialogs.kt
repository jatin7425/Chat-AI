package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SoulRepository
import com.example.ui.theme.customTextFieldColors
import com.example.util.LocalTransferServer
import kotlinx.coroutines.launch

@Composable
fun ExportBackupDialog(
    soulRepository: SoulRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var jsonText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        jsonText = soulRepository.exportAppDataJson()
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export App Backup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Copy or save your complete backup (personas, chat history, settings) as JSON format:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B1B22),
                        border = BorderStroke(1.dp, Color(0xFF2E2E3A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = jsonText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE0E0E0)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(jsonText))
                    Toast.makeText(context, "Backup JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                enabled = !isLoading && jsonText.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun ImportBackupDialog(
    soulRepository: SoulRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var jsonInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import App Backup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste a valid backup JSON string to restore all personas and chat conversations:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    placeholder = { Text("Paste JSON data here...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = customTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrEmpty()) {
                            jsonInput = clip
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste from Clipboard", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (jsonInput.isBlank()) {
                        Toast.makeText(context, "Please enter backup JSON", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isImporting = true
                    coroutineScope.launch {
                        val res = soulRepository.importAppDataJson(jsonInput.trim())
                        isImporting = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                            onSuccess()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Import failed: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isImporting && jsonInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importing...")
                } else {
                    Text("Restore Data")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

fun String.isNull_or_blank(): Boolean = this.isBlank()

@Composable
fun ReceiveLocalDataDialog(
    soulRepository: SoulRepository,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var senderIp by remember { mutableStateOf("") }
    var isReceiving by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    val myLocalIp = remember(context) { LocalTransferServer.getLocalIpAddress(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Receive via Local Wi-Fi / Hotspot", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "1. Ensure both devices are connected to the same Wi-Fi or Mobile Hotspot.\n2. On sender device, start Local Transfer in Settings.\n3. Enter the Sender's IP address shown below:",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = senderIp,
                    onValueChange = { senderIp = it },
                    label = { Text("Sender Device IP") },
                    placeholder = { Text("e.g. 192.168.43.1") },
                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = customTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your device IP: $myLocalIp",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (statusText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusText!!,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (senderIp.isBlank()) {
                        Toast.makeText(context, "Please enter sender IP address", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isReceiving = true
                    statusText = null
                    coroutineScope.launch {
                        val res = LocalTransferServer.receiveDataFromSender(senderIp, 8888, soulRepository)
                        isReceiving = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "Received and imported all data successfully!", Toast.LENGTH_SHORT).show()
                            onSuccess()
                            onDismiss()
                        } else {
                            statusText = "Connection failed: ${res.exceptionOrNull()?.localizedMessage}. Please check IP address."
                        }
                    }
                },
                enabled = !isReceiving && senderIp.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isReceiving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connecting...")
                } else {
                    Text("Connect & Receive")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun SenderServerCard(
    soulRepository: SoulRepository
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val transferServer = remember { LocalTransferServer(soulRepository) }
    var isServerRunning by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("Server Offline") }

    val localIp = remember(context) { LocalTransferServer.getLocalIpAddress(context) }

    DisposableEffect(Unit) {
        onDispose {
            transferServer.stopServer()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isServerRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isServerRunning) Icons.Default.WifiTethering else Icons.Default.PortableWifiOff,
                                contentDescription = null,
                                tint = if (isServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Wi-Fi / Hotspot Data Transfer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isServerRunning) "Sender Server ACTIVE" else "Share data to nearby device",
                            fontSize = 12.sp,
                            color = if (isServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isServerRunning,
                    onCheckedChange = { checked ->
                        if (checked) {
                            transferServer.startServer(8888) { status ->
                                serverStatus = status
                            }
                            isServerRunning = true
                        } else {
                            transferServer.stopServer()
                            isServerRunning = false
                            serverStatus = "Server Offline"
                        }
                    }
                )
            }

            if (isServerRunning) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1C1C24),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Sender IP Address:",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = localIp,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(localIp))
                                    Toast.makeText(context, "IP Address copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = Color.LightGray)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Instructions: Connect receiver device to same Wi-Fi/Hotspot. In Receiver's app (Onboarding/Settings), click 'Receive via Wi-Fi/Hotspot' and enter IP above.",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
