package org.koitharu.kotatsu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koitharu.kotatsu.core.model.AppNavTab

/**
 * GLOBAL UI ARCHITECTURE LOCK — MASTER COMPONENTS
 *
 * 1. NonWrappingBadgeText: Prevents vertical mid-word wrapping ("Spoi / ler- / Free" or "Categorie / s")
 *    across every badge, tag, status chip, and button label in the application.
 * 2. UnifiedTopBar & UnifiedTopRightIconCluster: Enforces the strict 2-icon maximum on the right side
 *    of the header across all 5 main tabs:
 *    - LIBRARY, HISTORY, FEED -> [🎚️ Display/Filter Sliders] + [⚙ Settings]
 *    - EXPLORE, AI_STUDIO     -> [✦ AI Engine] + [⚙ Settings]
 * 3. DismissibleCard & UniversalSheet: Enforces full-width card alignment, top-right ✕ Close buttons,
 *    backdrop click-to-close, and 56.dp bottom gesture bar clearance across all inline cards and modals.
 */
@Composable
fun NonWrappingBadgeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    fontWeight: FontWeight? = FontWeight.Bold
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun UnifiedTopRightIconCluster(
    activeTab: AppNavTab,
    onOpenDisplayOrFilterSliders: () -> Unit = {},
    onOpenAiEngineSettings: () -> Unit = {},
    onOpenSettingsScreen: () -> Unit,
    isAiActive: Boolean = true,
    customizerTestTag: String = "header_display_sliders_button",
    aiButtonTestTag: String = "header_ai_engine_button",
    settingsTestTag: String = "header_settings_button"
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (activeTab) {
            AppNavTab.LIBRARY, AppNavTab.HISTORY, AppNavTab.FEED -> {
                // Strictly 2 icons: [🎚️ Display/Filter Sliders] + [⚙ Settings]
                IconButton(
                    onClick = onOpenDisplayOrFilterSliders,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .testTag(customizerTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Display & Filter Sliders",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            AppNavTab.EXPLORE, AppNavTab.AI_STUDIO, AppNavTab.BACKUPS_DB -> {
                // Strictly 2 icons: [✦ AI Engine] + [⚙ Settings]
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onOpenAiEngineSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .testTag(aiButtonTestTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Engine & LLM Router",
                            tint = if (isAiActive) Color(0xFF5CE1E6) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (isAiActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF5CE1E6))
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onOpenSettingsScreen,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .testTag(settingsTestTag)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings Menu",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
fun UnifiedTopBar(
    activeTab: AppNavTab,
    leftContent: @Composable RowScope.() -> Unit,
    onOpenDisplayOrFilterSliders: () -> Unit = {},
    onOpenAiEngineSettings: () -> Unit = {},
    onOpenSettingsScreen: () -> Unit,
    modifier: Modifier = Modifier,
    isAiActive: Boolean = true,
    customizerTestTag: String = "header_display_sliders_button",
    aiButtonTestTag: String = "header_ai_engine_button",
    settingsTestTag: String = "header_settings_button"
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            content = leftContent
        )
        Spacer(Modifier.width(8.dp))
        UnifiedTopRightIconCluster(
            activeTab = activeTab,
            onOpenDisplayOrFilterSliders = onOpenDisplayOrFilterSliders,
            onOpenAiEngineSettings = onOpenAiEngineSettings,
            onOpenSettingsScreen = onOpenSettingsScreen,
            isAiActive = isAiActive,
            customizerTestTag = customizerTestTag,
            aiButtonTestTag = aiButtonTestTag,
            settingsTestTag = settingsTestTag
        )
    }
}

@Composable
fun DismissibleCard(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF151A26),
    borderColor: Color = Color.White.copy(alpha = 0.10f),
    cornerRadius: Dp = 16.dp,
    closeButtonTestTag: String = "dismissible_card_close_button",
    headerLeadingContent: @Composable RowScope.() -> Unit,
    headerTrailingActions: (@Composable RowScope.() -> Unit)? = null,
    bodyContent: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = headerLeadingContent
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headerTrailingActions?.invoke(this)
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag(closeButtonTestTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Card",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            bodyContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSheet(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF121620),
    contentColor: Color = Color.White,
    sheetTestTag: String = "universal_modal_sheet",
    title: String? = null,
    subtitle: String? = null,
    closeButtonTestTag: String = "universal_sheet_close_button",
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier.testTag(sheetTestTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
        ) {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .testTag(closeButtonTestTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sheet",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            content()
        }
    }
}
