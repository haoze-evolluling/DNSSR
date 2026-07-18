package com.haoze.dnssr.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsLoadingContent
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val normalizedLabel: String,
    val normalizedPackageName: String
)

internal suspend fun loadInstalledApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val packageManager = context.packageManager
    @Suppress("DEPRECATION")
    packageManager.getInstalledApplications(0)
        .asSequence()
        .filter { it.packageName != context.packageName }
        .map { info ->
            val label = info.loadLabel(packageManager).toString()
            InstalledApp(
                label = label,
                packageName = info.packageName,
                icon = runCatching { info.loadIcon(packageManager) }.getOrNull(),
                isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                normalizedLabel = label.lowercase(Locale.ROOT),
                normalizedPackageName = info.packageName.lowercase(Locale.ROOT)
            )
        }
        .sortedWith(compareBy<InstalledApp> { it.normalizedLabel }.thenBy { it.packageName })
        .toList()
}

@Composable
internal fun InstalledAppCheckboxItem(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            app.icon?.let { icon ->
                Image(
                    painter = rememberDrawablePainter(drawable = icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } ?: Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

internal data class AppListAccessState<T>(
    val apps: List<T>?,
    val unavailable: Boolean,
    val showDisclosure: Boolean,
    val allowAccess: () -> Unit,
    val dismissDisclosure: () -> Unit,
    val retry: () -> Unit
)

@Composable
internal fun <T> rememberAppListAccessState(loader: suspend () -> List<T>): AppListAccessState<T> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var apps by remember { mutableStateOf<List<T>?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var showDisclosure by remember {
        mutableStateOf(!PermissionDisclosureSettings.isAppListExplained(context))
    }
    var loadGeneration by remember { mutableIntStateOf(0) }
    var awaitingSystemResult by remember { mutableStateOf(false) }

    fun requestLoad() {
        awaitingSystemResult = true
        unavailable = false
        apps = null
        loadGeneration++
    }

    LaunchedEffect(loadGeneration, showDisclosure) {
        if (showDisclosure) return@LaunchedEffect
        awaitingSystemResult = true
        val loaded = loader()
        if (loaded.isNotEmpty()) {
            awaitingSystemResult = false
            PermissionDisclosureSettings.markAppListAvailable(context)
            apps = loaded
            unavailable = false
        } else {
            apps = emptyList()
            unavailable = true
            if (PermissionDisclosureSettings.wasAppListAvailable(context)) {
                PermissionDisclosureSettings.setAppListExplained(context, false)
                showDisclosure = true
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingSystemResult) {
                awaitingSystemResult = false
                loadGeneration++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return AppListAccessState(
        apps = apps,
        unavailable = unavailable,
        showDisclosure = showDisclosure,
        allowAccess = {
            PermissionDisclosureSettings.setAppListExplained(context, true)
            showDisclosure = false
            requestLoad()
        },
        dismissDisclosure = {
            showDisclosure = false
            unavailable = true
            apps = emptyList()
        },
        retry = {
            if (PermissionDisclosureSettings.isAppListExplained(context)) {
                requestLoad()
            } else {
                showDisclosure = true
            }
        }
    )
}

@Composable
internal fun AppListDisclosureDialog(state: AppListAccessState<*>) {
    if (!state.showDisclosure) return
    AlertDialog(
        onDismissRequest = state.dismissDisclosure,
        title = { Text("应用列表访问") },
        text = {
            Text("为了让你选择需要排除或进行 HTTP(S) 检查的应用，DNSSR 需要读取设备上的应用列表。不会读取应用数据，也不会上传应用列表。")
        },
        confirmButton = {
            TextButton(onClick = state.allowAccess) { Text("继续") }
        },
        dismissButton = {
            TextButton(onClick = state.dismissDisclosure) { Text("暂不允许") }
        }
    )
}

@Composable
internal fun AppListUnavailableContent(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SettingsInfoText("需要应用列表访问权限才能选择应用。未授权不会影响其他功能。")
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("允许访问")
        }
    }
}

@Composable
internal fun AppListLoadingContent(modifier: Modifier) {
    SettingsLoadingContent(modifier)
}
