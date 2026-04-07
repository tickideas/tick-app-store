package com.tickideas.appstore.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.tickideas.appstore.BuildConfig
import com.tickideas.appstore.TickAppStore
import com.tickideas.appstore.data.AppDetail
import com.tickideas.appstore.data.AppVersionDetail
import android.widget.Toast
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
    val context = LocalContext.current

    LaunchedEffect(appId) {
        viewModel.loadApp(appId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? AppDetailState.Success)?.app?.name ?: "App Details"
                    Text(title)
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AppDetailState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadApp(appId) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is AppDetailState.Success -> {
                val app = s.app
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

                    // Version history
                    if (app.versions.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Version History",
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
            fontWeight = FontWeight.Bold
        )

        Text(
            app.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        app.versions.firstOrNull()?.let { latest ->
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "v${latest.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                latest.apkSize?.let { size ->
                    Text(
                        formatFileSize(size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

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
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Update")
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

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
