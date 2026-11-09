package iwarok.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.annotation.ExperimentalCoilApi
import coil.compose.LocalImageLoader
import iwarok.app.AppGraph
import iwarok.app.R
import iwarok.app.core.AppSettings
import iwarok.app.core.AuthState
import iwarok.app.core.FeedSort
import iwarok.app.core.IwaraConfig
import iwarok.app.core.ProxyConfig
import iwarok.app.core.ProxyType
import iwarok.app.core.QualityPreference
import iwarok.app.core.RatingFilter
import iwarok.app.data.ProxySettingsStore
import iwarok.app.ui.components.FlowCard
import iwarok.app.ui.components.FlowTextField
import iwarok.app.ui.components.ScreenTopBar
import iwarok.app.ui.components.UserAvatar
import iwarok.app.ui.theme.ChampagneGradient
import iwarok.app.ui.util.localizedLabel
import iwarok.app.ui.theme.FlowBackground
import iwarok.app.ui.theme.FlowError
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowSurface
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowTextTertiary
import iwarok.app.ui.util.appVersionName
import iwarok.app.ui.util.openUrl
import iwarok.app.ui.util.restartApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Settings — Obsidian × Champagne card layout: account / playback / content /
 * network / general. No notices, disclaimers or long descriptions.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    graph: AppGraph,
    settings: AppSettings,
    authState: AuthState,
    onBack: () -> Unit,
    onRequestLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageLoader = LocalImageLoader.current

    var localSort by remember { mutableStateOf(settings.sort) }
    var localRating by remember { mutableStateOf(settings.rating) }
    var localQuality by remember { mutableStateOf(settings.quality) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(settings.sort) { localSort = settings.sort }
    LaunchedEffect(settings.rating) { localRating = settings.rating }
    LaunchedEffect(settings.quality) { localQuality = settings.quality }

    fun selectSort(sort: FeedSort) {
        localSort = sort
        scope.launch { graph.repository.setSort(sort) }
    }

    fun selectRating(rating: RatingFilter) {
        localRating = rating
        scope.launch { graph.repository.setRating(rating) }
    }

    fun selectQuality(quality: QualityPreference) {
        localQuality = quality
        scope.launch { graph.repository.setQuality(quality) }
    }

    // ------------------------------------------------------------ proxy state
    val initialProxy = remember { ProxySettingsStore.load(context) }
    var proxyEnabled by remember { mutableStateOf(initialProxy.enabled) }
    var proxyHost by remember { mutableStateOf(initialProxy.host.ifBlank { "127.0.0.1" }) }
    var proxyPort by remember {
        mutableStateOf(initialProxy.port.takeIf { it in 1..65535 }?.toString() ?: "7890")
    }
    var proxyTesting by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    fun proxyPortOrNull(): Int? = proxyPort.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    fun saveProxySettings() {
        val port = proxyPortOrNull()
        if (proxyEnabled && (proxyHost.isBlank() || port == null)) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_proxy_invalid))
            }
            return
        }
        val config = ProxyConfig(
            enabled = proxyEnabled,
            type = ProxyType.HTTP,
            host = proxyHost.trim(),
            port = port ?: 0,
        )
        ProxySettingsStore.save(context, config)
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.settings_proxy_saved))
        }
        showRestartDialog = true
    }

    fun testProxyConnection() {
        val port = proxyPortOrNull()
        if (proxyHost.isBlank() || port == null) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_proxy_invalid))
            }
            return
        }
        proxyTesting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val javaProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost.trim(), port))
                    val testClient = OkHttpClient.Builder()
                        .proxy(javaProxy)
                        .connectTimeout(12, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .callTimeout(20, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url("${IwaraConfig.API_FALLBACK}/videos?page=0&limit=1")
                        .header("User-Agent", IwaraConfig.USER_AGENT)
                        .header("Accept", "application/json")
                        .header("Referer", "${IwaraConfig.SITE_ORIGIN}/")
                        .header("Origin", IwaraConfig.SITE_ORIGIN)
                        .header("x-site", IwaraConfig.SITE_NAME)
                        .get()
                        .build()
                    val startedAt = System.currentTimeMillis()
                    testClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                    }
                    System.currentTimeMillis() - startedAt
                }
            }
            proxyTesting = false
            result.onSuccess { elapsedMs ->
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_proxy_test_ok, "$elapsedMs ms"),
                )
            }.onFailure { error ->
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.settings_proxy_test_fail,
                        iwarok.app.ui.util.localizedErrorMessage(context, error.message ?: context.getString(R.string.unknown_error)),
                    ),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FlowBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
            )
            Spacer(Modifier.height(4.dp))

            // ---------------------------------------------------- account
            SettingsSection(title = stringResource(R.string.settings_group_account)) {
                FlowCard {
                    when (authState) {
                        is AuthState.LoggedIn -> LoggedInAccountRow(
                            authState = authState,
                            onLogout = { showLogoutDialog = true },
                        )

                        else -> GuestAccountRow(onRequestLogin = onRequestLogin)
                    }
                }
            }

            // ---------------------------------------------------- playback
            SettingsSection(title = stringResource(R.string.settings_group_playback)) {
                FlowCard {
                    SettingRow(
                        label = stringResource(R.string.settings_quality),
                        value = localQuality.localizedLabel(),
                        onClick = { showQualityDialog = true },
                    )
                }
            }

            // ---------------------------------------------------- content
            SettingsSection(title = stringResource(R.string.settings_group_content)) {
                FlowCard {
                    Column {
                        SettingRow(
                            label = stringResource(R.string.settings_sort),
                            value = localSort.localizedLabel(),
                            onClick = { showSortDialog = true },
                        )
                        RowDivider()
                        SettingRow(
                            label = stringResource(R.string.settings_rating),
                            value = localRating.localizedLabel(),
                            onClick = { showRatingDialog = true },
                        )
                    }
                }
            }

            // ---------------------------------------------------- network
            SettingsSection(title = stringResource(R.string.settings_group_network)) {
                FlowCard {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_proxy_switch),
                                color = FlowTextPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = proxyEnabled,
                                onCheckedChange = { proxyEnabled = it },
                            )
                        }
                        if (proxyEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            ) {
                                FlowTextField(
                                    value = proxyHost,
                                    onValueChange = { proxyHost = it.trim() },
                                    label = stringResource(R.string.settings_proxy_host),
                                    singleLine = true,
                                )
                                Spacer(Modifier.height(10.dp))
                                FlowTextField(
                                    value = proxyPort,
                                    onValueChange = { value ->
                                        proxyPort = value.filter { it.isDigit() }.take(5)
                                    },
                                    label = stringResource(R.string.settings_proxy_port),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CompactButton(
                                        text = stringResource(R.string.settings_proxy_test),
                                        loading = proxyTesting,
                                        onClick = { testProxyConnection() },
                                        modifier = Modifier.weight(1f),
                                    )
                                    CompactButton(
                                        text = stringResource(R.string.settings_proxy_save),
                                        primary = true,
                                        onClick = { saveProxySettings() },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------- general
            SettingsSection(title = stringResource(R.string.settings_group_general)) {
                FlowCard {
                    Column {
                        SettingRow(
                            label = stringResource(R.string.settings_clear_cache),
                            leading = {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = FlowTextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                scope.launch {
                                    imageLoader.memoryCache?.clear()
                                    imageLoader.diskCache?.clear()
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_clear_cache_done),
                                    )
                                }
                            },
                        )
                        RowDivider()
                        SettingRow(
                            label = stringResource(R.string.settings_open_website),
                            leading = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = FlowTextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = { openUrl(context, IwaraConfig.SITE_ORIGIN) },
                        )
                        RowDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_version_label),
                                color = FlowTextPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = appVersionName(context),
                                color = FlowTextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = FlowSurface,
            titleContentColor = FlowTextPrimary,
            textContentColor = FlowTextSecondary,
            title = { Text(stringResource(R.string.settings_logout_confirm_title)) },
            text = { Text(stringResource(R.string.settings_logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    scope.launch {
                        graph.repository.logout()
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.settings_logout_done),
                        )
                    }
                }) {
                    Text(stringResource(R.string.settings_logout), color = FlowError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel), color = FlowTextSecondary)
                }
            },
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            containerColor = FlowSurface,
            titleContentColor = FlowTextPrimary,
            textContentColor = FlowTextSecondary,
            title = { Text(stringResource(R.string.settings_proxy_restart_title)) },
            text = { Text(stringResource(R.string.settings_proxy_restart_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    restartApp(context)
                }) {
                    Text(stringResource(R.string.settings_proxy_restart_now), color = FlowPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(R.string.settings_proxy_restart_later), color = FlowTextSecondary)
                }
            },
        )
    }

    if (showSortDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_dialog_sort_title),
            options = FeedSort.entries,
            selected = localSort,
            labelOf = { it.localizedLabel() },
            onPick = {
                selectSort(it)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false },
        )
    }

    if (showRatingDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_dialog_rating_title),
            options = RatingFilter.entries,
            selected = localRating,
            labelOf = { it.localizedLabel() },
            onPick = {
                selectRating(it)
                showRatingDialog = false
            },
            onDismiss = { showRatingDialog = false },
        )
    }

    if (showQualityDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_dialog_quality_title),
            options = QualityPreference.entries,
            selected = localQuality,
            labelOf = { it.localizedLabel() },
            onPick = {
                selectQuality(it)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false },
        )
    }
}

/* ------------------------------------------------------------------------- *
 * Card layout building blocks
 * ------------------------------------------------------------------------- */

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        text = title.uppercase(),
        color = FlowTextTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = FlowOutline.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 16.dp),
    )
}

/** 56dp settings row: optional leading icon, label and trailing value. */
@Composable
private fun SettingRow(
    label: String,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(clickModifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            color = FlowTextPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                color = FlowTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = FlowTextTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Compact card button: champagne (dark text) or neutral (warm white text). */
@Composable
private fun CompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (primary) Modifier.background(ChampagneGradient)
                else Modifier.background(Color.White.copy(alpha = 0.06f)),
            )
            .border(
                width = 1.dp,
                color = if (primary) Color.Transparent else FlowOutline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = FlowTextPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                color = if (primary) Color(0xFF17120A) else FlowTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LoggedInAccountRow(
    authState: AuthState.LoggedIn,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user = authState.user, size = 46.dp)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = authState.user.displayName,
                color = FlowTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "@${authState.user.username}",
                color = FlowTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Text(
            text = stringResource(R.string.settings_logout),
            color = FlowError,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, FlowOutline, RoundedCornerShape(10.dp))
                .clickable(onClick = onLogout)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun GuestAccountRow(onRequestLogin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, FlowOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = FlowTextTertiary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Text(
            text = stringResource(R.string.settings_guest_title),
            color = FlowTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(ChampagneGradient)
                .clickable(onClick = onRequestLogin)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_login_now),
                color = Color(0xFF17120A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun <T> SettingsChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FlowSurface,
        titleContentColor = FlowTextPrimary,
        textContentColor = FlowTextSecondary,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    ChoiceRow(
                        label = labelOf(option),
                        selected = option == selected,
                        showDivider = index != options.lastIndex,
                        onClick = { onPick(option) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = FlowTextSecondary)
            }
        },
    )
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (selected) FlowTextPrimary else FlowTextSecondary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = FlowPink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = FlowOutline.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
