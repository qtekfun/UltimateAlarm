package com.qtekfun.ultimatealarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One entry of the floating navigation. */
data class NavEntry(val label: String, val icon: ImageVector)

/**
 * D-19: the sections as a compact floating pill instead of a full-width bar. The current one shows its name on the
 * accent's soft tone; the others are icons only (named for TalkBack). [trailing] sits beside the pill (the add button).
 */
@Composable
fun FloatingNav(entries: List<NavEntry>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp, tonalElevation = 0.dp) {
            Row(Modifier.padding(6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                entries.forEachIndexed { index, entry -> NavItem(entry, index == selected) { onSelect(index) } }
            }
        }
        trailing()
    }
}

@Composable
private fun NavItem(entry: NavEntry, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, label = "nav")
    val content = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    Row(
        Modifier.heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = entry.label }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(entry.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        AnimatedVisibility(visible = selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
            Text(entry.label, style = MaterialTheme.typography.labelLarge, color = content, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** The round add button beside the navigation, on the secondary colour. */
@Composable
fun NavAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shadowElevation = 6.dp,
        modifier = modifier.size(64.dp).semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp)) }
    }
}
