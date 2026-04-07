package com.tickideas.appstore.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // Store self-update state
    private val _storeUpdate = MutableStateFlow<StoreVersion?>(null)
    val storeUpdate = _storeUpdate.asStateFlow()

    private val _updatingStore = MutableStateFlow(false)
    val updatingStore = _updatingStore.asStateFlow()

    // Track install state version to trigger recomposition
    private val _installStateVersion = MutableStateFlow(0)
    val installStateVersion = _installStateVersion.asStateFlow()

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

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val apps = app.repository.getApps()
                _state.value = AppListState.Success(apps)
            } catch (e: Exception) {
                _state.value = AppListState.Error(e.message ?: "Failed to load apps")
            }
            _isRefreshing.value = false
        }
    }

    fun refreshInstallStates() {
        _installStateVersion.value++
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
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val storeUpdate by viewModel.storeUpdate.collectAsState()
    val updatingStore by viewModel.updatingStore.collectAsState()
    val installStateVersion by viewModel.installStateVersion.collectAsState()

    // Refresh install states when returning to this screen
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshInstallStates()
    }

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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is AppListState.Loading -> {
                ShimmerGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            is AppListState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(12.dp))
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
                        FilledTonalButton(onClick = { viewModel.loadApps() }) {
                            Text("Try Again")
                        }
                    }
                }
            }

            is AppListState.Success -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Store self-update banner
                        AnimatedVisibility(
                            visible = storeUpdate != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            storeUpdate?.let { update ->
                                StoreUpdateBanner(
                                    newVersion = update.versionName,
                                    currentVersion = BuildConfig.VERSION_NAME,
                                    updating = updatingStore,
                                    onUpdate = { viewModel.downloadStoreUpdate() },
                                    onDismiss = { viewModel.dismissStoreUpdate() }
                                )
                            }
                        }

                        if (s.apps.isEmpty()) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.Inventory2,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "No apps yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Apps will appear here once published",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
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
                                    // Read installStateVersion to recompose when it changes
                                    @Suppress("UNUSED_EXPRESSION")
                                    installStateVersion

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
}

// --- Shimmer Loading Placeholder ---

@Composable
fun ShimmerGrid(modifier: Modifier = Modifier) {
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
        userScrollEnabled = false
    ) {
        items(6) {
            ShimmerCard(brush)
        }
    }
}

@Composable
fun ShimmerCard(brush: Brush) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Icon placeholder
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(12.dp))
            // Name placeholder
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(6.dp))
            // Version placeholder
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(10.dp))
            // Chip placeholder
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush)
            )
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
