package iwarok.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iwarok.app.R
import iwarok.app.core.IwaraRepository
import iwarok.app.core.IwaraTag
import iwarok.app.core.tagLabel
import iwarok.app.ui.theme.FlowOutline
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowSurface
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.util.localizedLabel
import iwarok.app.ui.util.TagCatalog
import iwarok.app.ui.util.TagChannel
import iwarok.app.ui.util.TagChannelsStore
import kotlinx.coroutines.launch

/**
 * "管理频道" dialog (tall, bounded layout):
 *  - compact manual slug input (validated through `repository.fetchTag`),
 *  - fixed-height list of the selected channels (delete),
 *  - the complete bundled Iwara tag catalogue (assets/iwara_tags.json) with an
 *    id search field; clicking an unselected tag adds it as a channel,
 *  - restore defaults / done. Channels persist via [TagChannelsStore].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagChannelManagerDialog(
    store: TagChannelsStore,
    channels: List<TagChannel>,
    repository: IwaraRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var manualTag by remember { mutableStateOf("") }
    var manualAdding by remember { mutableStateOf(false) }
    var manualError by remember { mutableStateOf<String?>(null) }

    var allTags by remember { mutableStateOf<List<IwaraTag>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf(false) }
    var tagQuery by remember { mutableStateOf("") }

    val emptyErrorText = stringResource(R.string.tag_channels_manual_empty)
    val duplicateErrorText = stringResource(R.string.tag_channels_manual_duplicate)
    val invalidErrorText = stringResource(R.string.tag_channels_manual_invalid)

    fun addManualTag() {
        if (manualAdding) return
        val id = manualTag.trim()
        if (id.isEmpty()) {
            manualError = emptyErrorText
            return
        }
        if (channels.any { it.id.equals(id, ignoreCase = true) }) {
            manualError = duplicateErrorText
            return
        }
        manualAdding = true
        manualError = null
        scope.launch {
            repository.fetchTag(id)
                .onSuccess { tag ->
                    val newId = tag.id.takeIf { it.isNotBlank() } ?: id
                    if (!store.add(newId, tagLabel(newId))) {
                        manualError = duplicateErrorText
                    } else {
                        manualTag = ""
                    }
                }
                .onFailure { error ->
                    manualError = iwarok.app.ui.util.localizedErrorMessage(context, error.message ?: invalidErrorText)
                }
            manualAdding = false
        }
    }

    LaunchedEffect(Unit) {
        TagCatalog.load(context)
            .onSuccess { allTags = it }
            .onFailure { catalogError = true }
        catalogLoading = false
    }

    val filteredTags = remember(allTags, tagQuery) {
        val query = tagQuery.trim()
        if (query.isEmpty()) {
            allTags
        } else {
            allTags.filter { it.id.contains(query, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.84f),
                shape = RoundedCornerShape(20.dp),
                color = FlowSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // ------------------------------------------------ header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.tag_channels_manage),
                            color = FlowTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = FlowTextSecondary,
                            )
                        }
                    }

                    // ------------------------------------- manual slug input
                    Text(
                        text = stringResource(R.string.tag_channels_manual_title),
                        color = FlowTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (manualTag.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.tag_channels_manual_hint),
                                    color = FlowTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = manualTag,
                                onValueChange = {
                                    manualTag = it
                                    manualError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(color = FlowTextPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(FlowPink),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addManualTag() }),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { addManualTag() },
                            enabled = manualTag.isNotBlank() && !manualAdding,
                            modifier = Modifier.height(36.dp),
                        ) {
                            if (manualAdding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = FlowPink,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.tag_channels_add),
                                    color = if (manualTag.isNotBlank()) FlowPink else FlowTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    if (manualError != null) {
                        Text(
                            text = manualError.orEmpty(),
                            color = FlowPink,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    // ---------------------------------------- selected list
                    Text(
                        text = "${stringResource(R.string.tag_channels_selected)} (${channels.size})",
                        color = FlowTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = FlowOutline.copy(alpha = 0.4f))
                    Spacer(Modifier.height(6.dp))
                    if (channels.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tag_channels_empty),
                            color = FlowTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 132.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                channels.forEach { channel ->
                                    Surface(
                                        shape = RoundedCornerShape(percent = 50),
                                        color = Color.White.copy(alpha = 0.10f),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .clickable { store.remove(channel.id) }
                                                .padding(start = 11.dp, end = 7.dp, top = 5.dp, bottom = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = channel.localizedLabel(),
                                                color = FlowTextPrimary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.tag_channels_delete),
                                                tint = FlowTextSecondary,
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    // ----------------------------------------- tag catalogue
                    Text(
                        text = stringResource(R.string.tag_channels_all, filteredTags.size),
                        color = FlowTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = FlowTextSecondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (tagQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.tag_channels_search_hint),
                                    color = FlowTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                            BasicTextField(
                                value = tagQuery,
                                onValueChange = { tagQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(color = FlowTextPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(FlowPink),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val catalogListModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                    when {
                        catalogLoading -> {
                            Box(modifier = catalogListModifier, contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = FlowPink,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }

                        catalogError -> {
                            Box(modifier = catalogListModifier, contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.tag_channels_catalog_failed),
                                    color = FlowPink,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        filteredTags.isEmpty() -> {
                            Box(modifier = catalogListModifier, contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.tag_channels_no_match),
                                    color = FlowTextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        else -> {
                            LazyColumn(modifier = catalogListModifier) {
                                items(filteredTags, key = { it.id }) { tag ->
                                    val selected = channels.any { it.id == tag.id }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = !selected) {
                                                store.add(tag.id, tagLabel(tag.id))
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tagLabel(tag.id),
                                                color = if (selected) {
                                                    FlowTextSecondary
                                                } else {
                                                    FlowTextPrimary
                                                },
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = if (tag.type.isNullOrBlank()) {
                                                    tag.id
                                                } else {
                                                    "${tag.id} · ${tag.type}"
                                                },
                                                color = FlowTextSecondary.copy(alpha = 0.7f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Icon(
                                            imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.Add,
                                            contentDescription = if (selected) {
                                                stringResource(R.string.tag_channels_selected)
                                            } else {
                                                stringResource(R.string.tag_channels_add)
                                            },
                                            tint = if (selected) FlowTextSecondary else FlowPink,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = FlowOutline.copy(alpha = 0.4f))

                    // ------------------------------------------------ footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = {
                            store.reset()
                            manualError = null
                        }) {
                            Text(
                                text = stringResource(R.string.tag_channels_restore),
                                color = FlowTextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(R.string.tag_channels_done),
                                color = FlowPink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
