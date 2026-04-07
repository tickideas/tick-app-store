package com.tickideas.appstore.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.tickideas.appstore.BuildConfig
import com.tickideas.appstore.TickAppStore
import com.tickideas.appstore.data.AppInfo
import com.tickideas.appstore.data.StoreVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// --- ViewModel ---

sealed interface AppListState {
    data object Loading : AppListState
    data class Success(val apps: List<AppInfo>) : AppListState
    data class Error(val message: String) : AppListState
}

class AppListViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TickAppStore
    private val _state = MutableStateFlow<AppListState>(AppListState.Loading)
    val state = _state.asStateFlow()

    // Store self-update state
    private val _storeUpdate = MutableStateFlow<StoreVersion?>(null)
    val storeUpdate = _storeUpdate.asStateFlow()

    private val _updatingStore = MutableStateFlow(false)
    val updatingStore = _updatingStore.asStateFlow()

    init {
        loadApps()
        checkStoreUpdate()
    }

    fun loadApps() {
        viewModelScope.launch {
            _state.value = AppListState.Loading
            try {
                val apps = app.repository.getApps()
                _state.value = AppListState.Success(apps)
            } catch (e: Exception) {
                _state.value = AppListState.Error(e.message ?: "Failed to load apps")
            }
        }
    }

    private fun checkStoreUpdate() {
        viewModelScope.launch {
            try {
                val remote = app.repository.getStoreVersion()
                if (remote.versionCode > BuildConfig.VERSION_CODE) {
                    _storeUpdate.value = remote
                }
            } catch (_: Exception) {
                // Silently ignore — not critical
            }
        }
    }

    fun downloadStoreUpdate() {
        _updatingStore.value = true
        val url = "${BuildConfig.API_BASE_URL}/api/store/download"
        val id = app.downloadHelper.downloadAndInstall("Tick App Store", url) { error ->
            Toast.makeText(app, error, Toast.LENGTH_LONG).show()
            _updatingStore.value = false
        }
        if (id != -1L) {
            Toast.makeText(app, "Downloading store update...", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissStoreUpdate() {
        _storeUpdate.value = null
    }

    fun getInstalledVersionCode(packageName: String): Long? {
        return app.downloadHelper.getInstalledVersionCode(packageName)
    }
}

// --- Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    onAppClick: (String) -> Unit,
    viewModel: AppListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val storeUpdate by viewModel.storeUpdate.collectAsState()
    val updatingStore by viewModel.updatingStore.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Store,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Tick App Store", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is AppListState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AppListState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadApps() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is AppListState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Store self-update banner
                    storeUpdate?.let { update ->
                        StoreUpdateBanner(
                            newVersion = update.versionName,
                            currentVersion = BuildConfig.VERSION_NAME,
                            updating = updatingStore,
                            onUpdate = { viewModel.downloadStoreUpdate() },
                            onDismiss = { viewModel.dismissStoreUpdate() }
                        )
                    }

                    if (s.apps.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No apps available yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(s.apps, key = { it.id }) { app ->
                                AppCard(
                                    app = app,
                                    installedVersionCode = viewModel.getInstalledVersionCode(app.packageName),
                                    onClick = { onAppClick(app.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- App Card ---

@Composable
fun AppCard(
    app: AppInfo,
    installedVersionCode: Long?,
    onClick: () -> Unit
) {
    val latestCode = app.latestVersion?.versionCode?.toLong()
    val installState = when {
        installedVersionCode == null -> InstallState.NOT_INSTALLED
        latestCode != null && latestCode > installedVersionCode -> InstallState.UPDATE_AVAILABLE
        else -> InstallState.INSTALLED
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            if (app.iconUrl != null) {
                AsyncImage(
                    model = app.iconUrl,
                    contentDescription = app.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            app.name.take(2).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Name
            Text(
                app.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            // Version
            if (app.latestVersion != null) {
                Text(
                    "v${app.latestVersion.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Install state chip
            when (installState) {
                InstallState.INSTALLED -> {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Installed", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
                InstallState.UPDATE_AVAILABLE -> {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Update", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
                InstallState.NOT_INSTALLED -> {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Install", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
    }
}

enum class InstallState {
    NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE
}

// --- Store Self-Update Banner ---

@Composable
fun StoreUpdateBanner(
    newVersion: String,
    currentVersion: String,
    updating: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Store update available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "v$currentVersion → v$newVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            if (updating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onUpdate,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Update")
                }
            }
        }
    }
}
