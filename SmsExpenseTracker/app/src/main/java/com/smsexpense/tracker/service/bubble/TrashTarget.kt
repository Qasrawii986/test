package com.smsexpense.tracker.service.bubble

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.ui.theme.AppTheme
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

/**
 * Messenger-style drop target shown at the bottom of the screen while the
 * bubble is being dragged. Releasing over it dismisses the payment exactly like
 * the "Later" button — the payment stays UNCATEGORIZED and remains in the app.
 */
@Composable
fun TrashTarget(
    visibleFlow: StateFlow<Boolean>,
    activeFlow: StateFlow<Boolean>,
) {
    AppTheme {
        val visible by visibleFlow.collectAsState()
        val active by activeFlow.collectAsState()
        if (!visible) return@AppTheme

        val scale by animateFloatAsState(if (active) 1.25f else 1f, label = "trashScale")

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(64.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.bubble_dismiss),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
            Text(
                text = if (active) stringResource(R.string.bubble_release) else stringResource(R.string.bubble_drag_here),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
