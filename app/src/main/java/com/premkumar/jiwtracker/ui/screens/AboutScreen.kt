package com.premkumar.jiwtracker.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.premkumar.jiwtracker.ui.theme.Corners
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.premkumar.jiwtracker.ui.utils.DonationLauncher
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    isJp: Boolean,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val primaryColor = MaterialTheme.colorScheme.primary

    var versionName by remember { mutableStateOf("Unknown") }
    var versionCode by remember { mutableStateOf(0L) }
    var installDateStr by remember { mutableStateOf("Unknown") }

    LaunchedEffect(Unit) {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            versionName = packageInfo.versionName ?: "Unknown"
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            val installTime = packageInfo.firstInstallTime
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            installDateStr = sdf.format(Date(installTime))
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isJp) "アプリについて" else "About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "JIW Tracker",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = Corners.card
            ) {
                Text(
                    text = "v$versionName • STABLE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Support Methods Row
            Text(
                text = if (isJp) "サポート" else "SUPPORT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SupportChip(
                    text = "PayPal",
                    icon = Icons.Default.Payment,
                    modifier = Modifier.weight(1f)
                ) {
                    DonationLauncher.launch(context, "https://www.paypal.com/paypalme/my/profile", primaryColor)
                }
                SupportChip(
                    text = "UPI",
                    icon = Icons.Default.AccountBalanceWallet,
                    modifier = Modifier.weight(1f)
                ) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("upi://pay?pa=premkumargara@axl&pn=Prem%20Kumar%20Gara&cu=INR")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No UPI app found. Please install Google Pay, PhonePe, or similar.")
                        }
                    }
                }
                SupportChip(
                    text = "Ko-fi",
                    icon = Icons.Default.LocalCafe,
                    modifier = Modifier.weight(1f)
                ) {
                    DonationLauncher.launch(context, DonationLauncher.KOFI_URL, primaryColor)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Developer Card
            Text(
                text = if (isJp) "開発者" else "DEVELOPER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    InfoRow(
                        title = "Prem Kumar Gara",
                        subtitle = "Lead Developer",
                        icon = Icons.Default.Person
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(
                        title = "GitHub Repository",
                        subtitle = "View source code",
                        icon = Icons.Default.Code,
                        onClick = { DonationLauncher.launch(context, "https://github.com/premkumar-1122/Japanese-Interval-Walking", primaryColor) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(
                        title = "Telegram",
                        subtitle = "Join our Telegram channel",
                        icon = Icons.Default.Chat,
                        onClick = { DonationLauncher.launch(context, "https://t.me/gucamoleb", primaryColor) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Info Card
            Text(
                text = if (isJp) "アプリ情報" else "APP INFO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    InfoRow(
                        title = if (isJp) "インストール日" else "Installed Date",
                        subtitle = installDateStr,
                        icon = Icons.Default.CalendarToday
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(
                        title = if (isJp) "バージョンコード" else "Version Code",
                        subtitle = versionCode.toString(),
                        icon = Icons.Default.Info
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    InfoRow(
                        title = "GNU General Public License v3.0",
                        subtitle = "GPL-3.0 • Free Open Source Software",
                        icon = Icons.Default.Gavel,
                        onClick = { DonationLauncher.launch(context, "https://github.com/premkumar-1122/Japanese-Interval-Walking/blob/main/LICENSE", primaryColor) }
                    )
                }
            }
        }
    }
}

@Composable
fun SupportChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = Corners.card,
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun InfoRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
