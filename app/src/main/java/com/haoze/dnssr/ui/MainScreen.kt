package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.ui.components.DnsProtocolBadge
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.ui.effect.ServiceLightEffect
import com.haoze.dnssr.vpn.DnsProvider

private const val MANAGE_PROVIDER_ID = "__manage__"
private const val PROVIDER_VISIBILITY_ID = "__provider_visibility__"

internal fun raceProviderSummary(providerNames: List<String>): String {
    if (providerNames.isEmpty()) return "未选择服务商"
    val names = providerNames.take(2).joinToString("、")
    val suffix = if (providerNames.size > 2) " 等" else ""
    return "已选 ${providerNames.size} 个：$names$suffix"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onToggle: (isRunning: Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToProviderManagement: () -> Unit,
    onNavigateToHomeProviderVisibility: () -> Unit,
    onNavigateToRaceModeSettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    var serviceLightEffectEnabled by remember {
        mutableStateOf(AppSettings.isServiceLightEffectEnabled(context))
    }
    var powerButtonCenter by remember { mutableStateOf(Offset.Unspecified) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceLightEffectEnabled = AppSettings.isServiceLightEffectEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ServiceLightEffect(
            visible = serviceLightEffectEnabled && uiState.isRunning,
            revealOrigin = powerButtonCenter,
            modifier = Modifier.fillMaxSize()
        )
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text("DNSSR") },
                actions = {
                    TextButton(onClick = onNavigateToLogs) {
                        Text("日志")
                    }
                    TextButton(onClick = onNavigateToSettings) {
                        Text("设置")
                    }
                }
            )
        }
    ) { innerPadding ->
        MainContent(
            uiState = uiState,
            onToggle = { onToggle(uiState.isRunning) },
            onPowerButtonCenterChanged = { powerButtonCenter = it },
            onNavigateToProviderManagement = onNavigateToProviderManagement,
            onNavigateToHomeProviderVisibility = onNavigateToHomeProviderVisibility,
            onNavigateToRaceModeSettings = onNavigateToRaceModeSettings,
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    uiState: MainUiState,
    onToggle: () -> Unit,
    onPowerButtonCenterChanged: (Offset) -> Unit,
    onNavigateToProviderManagement: () -> Unit,
    onNavigateToHomeProviderVisibility: () -> Unit,
    onNavigateToRaceModeSettings: () -> Unit,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val raceModeEnabled by viewModel.raceModeEnabled.collectAsStateWithLifecycle()
    val resolutionMode by viewModel.resolutionMode.collectAsStateWithLifecycle()
    val raceProviderIds by viewModel.raceProviderIds.collectAsStateWithLifecycle()
    val homeProviderVisibility by viewModel.homeProviderVisibility.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadProviders()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val raceProviders = providers.filter { it.id in raceProviderIds }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PowerToggleButton(
            isRunning = uiState.isRunning,
            isBusy = uiState.isBusy,
            enabled = !uiState.isBusy && selectedProvider != null &&
                (resolutionMode == DnsResolutionMode.SINGLE || raceProviderIds.size >= 2),
            onCenterChanged = onPowerButtonCenterChanged,
            onToggle = onToggle
        )

        Text(
            text = if (uiState.isRunning) {
                "云途一线通鹏翼，万里长风任远驰"
            } else {
                "尘途断路羁鹏翼，空待长风不得驰"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 24.dp)
        )

        val filteredProviders = providers.filter(homeProviderVisibility::isVisible)
        val displayProviders = buildList {
            selectedProvider?.takeIf { selected -> filteredProviders.none { it.id == selected.id } }?.let(::add)
            addAll(filteredProviders)
            add(DnsProvider(id = MANAGE_PROVIDER_ID, name = "管理服务...", isPreset = true))
            add(DnsProvider(id = PROVIDER_VISIBILITY_ID, name = "服务显示...", isPreset = true))
        }
        val selectedIndex = displayProviders.indexOfFirst { it.id == selectedProvider?.id }
            .coerceAtLeast(0)
        val raceDisplayValue = raceProviderSummary(raceProviders.map { it.name })
        var expanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Crossfade(
                    targetState = resolutionMode != DnsResolutionMode.SINGLE,
                    animationSpec = tween(160),
                    label = "RaceModeProviderContent"
                ) { enabled ->
                    if (enabled) {
                        OutlinedTextField(
                            value = raceDisplayValue,
                            onValueChange = {},
                            readOnly = true,
                            enabled = true,
                            singleLine = true,
                            label = { Text("解析服务（${resolutionMode.displayName}）") },
                            shape = SettingsCornerShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToRaceModeSettings() }
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = displayProviders.getOrNull(selectedIndex)?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("解析服务") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                shape = SettingsCornerShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                displayProviders.forEachIndexed { _, provider ->
                                    DropdownMenuItem(
                                        text = {
                                            ProviderDropdownText(
                                                provider = provider,
                                                showProtocolBadge = provider.id != MANAGE_PROVIDER_ID &&
                                                    provider.id != PROVIDER_VISIBILITY_ID
                                            )
                                        },
                                        onClick = {
                                            expanded = false
                                            when (provider.id) {
                                                MANAGE_PROVIDER_ID -> onNavigateToProviderManagement()
                                                PROVIDER_VISIBILITY_ID -> onNavigateToHomeProviderVisibility()
                                                else -> viewModel.selectProvider(provider.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (resolutionMode != DnsResolutionMode.SINGLE) {
                ProviderEndpointList(providers = raceProviders)
            } else {
                selectedProvider?.let { provider ->
                    Text(
                        text = provider.endpointLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        val raceButtonContainerColor by animateColorAsState(
            targetValue = if (resolutionMode != DnsResolutionMode.SINGLE) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            animationSpec = tween(200),
            label = "RaceModeButtonContainerColor"
        )
        val raceButtonContentColor by animateColorAsState(
            targetValue = if (resolutionMode != DnsResolutionMode.SINGLE) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            animationSpec = tween(200),
            label = "RaceModeButtonContentColor"
        )
        Button(
            onClick = onNavigateToRaceModeSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = raceButtonContainerColor,
                contentColor = raceButtonContentColor
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            enabled = !uiState.isBusy,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(text = "模式切换 · ${resolutionMode.displayName}")
        }
    }
}

@Composable
private fun PowerToggleButton(
    isRunning: Boolean,
    isBusy: Boolean,
    enabled: Boolean,
    onCenterChanged: (Offset) -> Unit,
    onToggle: () -> Unit
) {
    val glowColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        },
        animationSpec = tween(250),
        label = "PowerToggleGlowColor"
    )
    val haloColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(250),
        label = "PowerToggleHaloColor"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(250),
        label = "PowerToggleContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(250),
        label = "PowerToggleContentColor"
    )
    val glowSize by animateDpAsState(
        targetValue = if (isRunning) 128.dp else 108.dp,
        animationSpec = tween(250),
        label = "PowerToggleGlowSize"
    )
    val haloSize by animateDpAsState(
        targetValue = if (isRunning) 148.dp else 124.dp,
        animationSpec = tween(250),
        label = "PowerToggleHaloSize"
    )
    val buttonSize by animateDpAsState(
        targetValue = if (isRunning) 92.dp else 84.dp,
        animationSpec = tween(250),
        label = "PowerToggleButtonSize"
    )
    val buttonAlpha = if (enabled) 1f else 0.5f
    val description = when {
        isBusy -> "连接中"
        isRunning -> "断开"
        else -> "开启"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(156.dp)
            .onGloballyPositioned { coordinates ->
                onCenterChanged(coordinates.boundsInRoot().center)
            }
            .alpha(buttonAlpha)
    ) {
        Box(
            modifier = Modifier
                .size(haloSize)
                .background(haloColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(glowSize)
                .background(glowColor, CircleShape)
        )
        FilledIconButton(
            onClick = onToggle,
            enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = description,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun ProviderEndpointList(providers: List<DnsProvider>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        providers.forEach { provider ->
            Text(
                text = provider.endpointLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProviderDropdownText(
    provider: DnsProvider,
    showProtocolBadge: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = provider.name,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showProtocolBadge) {
            DnsProtocolBadge(protocol = provider.protocol)
        }
    }
}
