package iwarok.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import iwarok.app.R
import iwarok.app.core.IwaraUser
import iwarok.app.ui.components.BrandLogo
import iwarok.app.ui.components.UserAvatar
import iwarok.app.ui.theme.BrandGradient
import iwarok.app.ui.theme.FlowPink
import iwarok.app.ui.theme.FlowSurface
import iwarok.app.ui.theme.FlowTextPrimary
import iwarok.app.ui.theme.FlowTextSecondary
import iwarok.app.ui.theme.FlowTextTertiary
import iwarok.app.ui.util.AppLanguage

/**
 * Left navigation drawer for the home feed: account area, favorites and settings.
 *
 * Rendered by [HomeFeedScreen] above the feed with a tap-outside scrim; tapping
 * an entry closes the drawer and navigates.
 */
@Composable
fun FeedDrawerPanel(
    user: IwaraUser?,
    onClose: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp))
            .background(FlowSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 18.dp),
    ) {
        // ---------------------------------------------------------- account
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (user != null) {
                UserAvatar(user = user, size = 52.dp, borderWidth = 1.5.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = FlowTextTertiary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.displayName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.settings_guest_title),
                    color = FlowTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                if (user != null) {
                    Text(
                        text = "@${user.username}",
                        color = FlowTextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (user == null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandGradient)
                        .clickable {
                            onClose()
                            onOpenLogin()
                        }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.go_login),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        Spacer(Modifier.height(6.dp))

        // --------------------------------------------------------- entries
        DrawerEntry(
            icon = Icons.Rounded.BookmarkBorder,
            label = stringResource(R.string.favorites_title),
            onClick = {
                onClose()
                onOpenFavorites()
            },
        )
        DrawerEntry(
            icon = Icons.Rounded.History,
            label = stringResource(R.string.history_title),
            onClick = {
                onClose()
                onOpenHistory()
            },
        )
        DrawerEntry(
            icon = Icons.Rounded.Language,
            label = stringResource(R.string.language_title),
            onClick = {
                onClose()
                onOpenLanguage()
            },
        )
        DrawerEntry(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.settings_title),
            onClick = {
                onClose()
                onOpenSettings()
            },
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DrawerEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FlowTextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = FlowTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Language picker: follow system / 简体中文 / English / 日本語 / 한국어. */
@Composable
fun AppLanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FlowSurface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.language_title),
                color = FlowTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            AppLanguage.entries.forEach { language ->
                val selected = language == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) FlowPink.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onSelect(language) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(language.labelRes),
                        color = if (selected) FlowPink else FlowTextPrimary,
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
            }
        }
    }
}
