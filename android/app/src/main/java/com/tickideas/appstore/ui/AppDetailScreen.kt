package com.tickideas.appstore.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.tickideas.appstore.BuildConfig
import com.tickideas.appstore.TickAppStore
import com.tickideas.appstore.data.AppDetail
import com.tickideas.appstore.data.AppVersionDetail
import com.tickideas.appstore.data.VersionHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// --- ViewModel ---

sealed interface AppDetailState {
    data object Loading : AppDetailState
    data class Success(val app: AppDetail) : AppDetailState
    data class Error(val message: String) : AppDetailState
}

class AppDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val tickApp = application as TickAppStore
    private val _state = MutableStateFlow<AppDetailState>(AppDetailState.Loading)
    val state = _state.asStateFlow()

    private val _installStateVersion = MutableStateFlow(0)
    val installStateVersion = _installStateVersion.asStateFlow()

    fun loadApp(id: String) {
        viewModelScope.launch {
            _state.value = AppDetailState.Loading
            try {
                val app = tickApp.repository.getApp(id)
                _state.value = AppDetailState.Success(app)
            } catch (e: Exception) {
                _state.value = AppDetailState.Error(e.message ?: "Failed to load app")
            }
        }
    }

    fun refreshInstallState() {
        _installStateVersion.value++
    }

    fun getInstalledVersionCode(packageName: String): Long? {
        return tickApp.downloadHelper.getInstalledVersionCode(packageName)
    }

    fun canInstallPackages(): Boolean {
        return tickApp.downloadHelper.canInstallPackages()
    }

    private val _downloadStarted = MutableStateFlow(false)
    val downloadStarted = _downloadStarted.asStateFlow()

    fun downloadApp(appName: String, appId: String) {
        val url = tickApp.repository.getDownloadUrl(BuildConfig.API_BASE_URL, appId)
        val id = tickApp.downloadHelper.downloadAndInstall(appName, url) { error ->
            Toast.makeText(tickApp, error, Toast.LENGTH_LONG).show()
            _downloadStarted.value = false
        }
        if (id != -1L) {
            _downloadStarted.value = true
            Toast.makeText(tickApp, "Downloading $appName...", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadVersion(appName: String, appId: String, versionId: String) {
        val url = tickApp.repository.getVersionDownloadUrl(BuildConfig.API_BASE_URL, appId, versionId)
        val id = tickApp.downloadHelper.downloadAndInstall(appName, url) { error ->
            Toast.makeText(tickApp, error, Toast.LENGTH_LONG).show()
        }
        if (id != -1L) {
            Toast.makeText(tickApp, "Downloading...", Toast.LENGTH_SHORT).show()
        }
    }
}

// --- Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    appId: String,
    onBack: () -> Unit,
    viewModel: AppDetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val installStateVersion by viewModel.installStateVersion.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(appId) {
        viewModel.loadApp(appId)
    }

    // Refresh install state when returning from installer
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshInstallState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? AppDetailState.Success)?.app?.name ?: ""
                    Text(title, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is AppDetailState.Loading -> {
                DetailShimmer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is AppDetailState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Something went wrong",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(onClick = { viewModel.loadApp(appId) }) {
                            Text("Try Again")
                        }
                    }
                }
            }

            is AppDetailState.Success -> {
                val app = s.app

                // Read installStateVersion to trigger recomposition
                @Suppress("UNUSED_EXPRESSION")
                installStateVersion

                val installedVersionCode = viewModel.getInstalledVersionCode(app.packageName)
                val latestVersion = app.versions.firstOrNull()
                val latestCode = latestVersion?.versionCode?.toLong()

                val installState = when {
                    installedVersionCode == null -> InstallState.NOT_INSTALLED
                    latestCode != null && latestCode > installedVersionCode -> InstallState.UPDATE_AVAILABLE
                    else -> InstallState.INSTALLED
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header: Icon + Name + Package
                    item {
                        AppHeader(app = app, installState = installState)
                    }

                    // Stats row
                    item {
                        StatsRow(
                            downloadCount = app.downloadCount,
                            versionName = latestVersion?.versionName,
                            apkSize = latestVersion?.apkSize
                        )
                    }

                    // Download button
                    item {
                        val downloadStarted by viewModel.downloadStarted.collectAsState()

                        DownloadButton(
                            installState = installState,
                            canInstall = viewModel.canInstallPackages(),
                            downloadStarted = downloadStarted,
                            onDownload = { viewModel.downloadApp(app.name, app.id) },
                            onRequestPermission = {
                                val intent = (context.applicationContext as TickAppStore)
                                    .downloadHelper.getInstallPermissionIntent()
                                context.startActivity(intent)
                            }
                        )
                    }

                    // Description
                    if (!app.description.isNullOrBlank()) {
                        item {
                            Text(
                                app.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Release history (from versionHistory, shows all past versions)
                    if (app.versionHistory.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Release History",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        items(
                            app.versionHistory,
                            key = { "${it.versionName}-${it.versionCode}" }
                        ) { entry ->
                            VersionHistoryRow(
                                entry = entry,
                                isCurrent = entry.versionCode == latestVersion?.versionCode
                            )
                        }
                    } else if (app.versions.isNotEmpty()) {
                        // Fallback: show current versions if no history yet
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Current Version",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(app.versions, key = { it.id }) { version ->
                            VersionItem(
                                version = version,
                                onDownload = {
                                    viewModel.downloadVersion(app.name, app.id, version.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Shimmer for Detail ---

@Composable
fun DetailShimmer(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value + 200f, 0f)
    )

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(brush)
        )
        Spacer(Modifier.height(16.dp))
        // Name
        Box(
            Modifier
                .width(140.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(Modifier.height(8.dp))
        // Package
        Box(
            Modifier
                .width(200.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(Modifier.height(24.dp))
        // Stats row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(60.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        // Button
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(brush)
        )
    }
}

// --- Stats Row ---

@Composable
fun StatsRow(downloadCount: Int, versionName: String?, apkSize: Long?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                value = formatDownloadCount(downloadCount),
                label = "Downloads"
            )
            if (versionName != null) {
                StatItem(value = "v$versionName", label = "Version")
            }
            if (apkSize != null) {
                StatItem(value = formatFileSize(apkSize), label = "Size")
            }
        }
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- Header ---

@Composable
fun AppHeader(app: AppDetail, installState: InstallState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        if (app.iconUrl != null) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.name,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        app.name.take(2).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            app.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            app.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- Download Button ---

@Composable
fun DownloadButton(
    installState: InstallState,
    canInstall: Boolean,
    downloadStarted: Boolean = false,
    onDownload: () -> Unit,
    onRequestPermission: () -> Unit
) {
    when (installState) {
        InstallState.INSTALLED -> {
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Installed")
            }
        }

        InstallState.UPDATE_AVAILABLE -> {
            Button(
                onClick = {
                    if (canInstall) onDownload() else onRequestPermission()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !downloadStarted
            ) {
                if (downloadStarted) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (downloadStarted) "Downloading..." else "Update")
            }
        }

        InstallState.NOT_INSTALLED -> {
            Button(
                onClick = {
                    if (canInstall) onDownload() else onRequestPermission()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !downloadStarted
            ) {
                if (downloadStarted) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (downloadStarted) "Downloading..." else "Download & Install")
            }
        }
    }

    if (!canInstall) {
        Text(
            "You need to allow installing apps from this source first",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// --- Version History Row (from versionHistory API) ---

@Composable
fun VersionHistoryRow(entry: VersionHistoryItem, isCurrent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "v${entry.versionName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "CURRENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (!entry.releaseNotes.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    entry.apkSize?.let { size ->
                        Text(
                            formatFileSize(size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    entry.publishedAt?.let { date ->
                        Text(
                            formatDate(date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --- Version Item (fallback, for current versions) ---

@Composable
fun VersionItem(version: AppVersionDetail, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "v${version.versionName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!version.releaseNotes.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        version.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                version.apkSize?.let { size ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatFileSize(size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Download this version",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// --- Utility ---

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

fun formatDownloadCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}

fun formatDate(isoDate: String): String {
    // Simple parsing — take the date part
    return try {
        isoDate.take(10) // "2024-01-15"
    } catch (_: Exception) {
        isoDate
    }
}
