package com.smsexpense.tracker.service.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.ui.components.formatAmount
import com.smsexpense.tracker.ui.theme.AppTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun BubbleOverlay(
    paymentFlow: StateFlow<Payment?>,
    categoriesFlow: StateFlow<List<Category>>,
    expandedFlow: StateFlow<Boolean>,
    queuedCountFlow: StateFlow<Int>,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onCategorySelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    onCollapse: () -> Unit,
    onDragStart: () -> Unit = {},
) {
    AppTheme {
        val payment by paymentFlow.collectAsState()
        val expanded by expandedFlow.collectAsState()
        val categories by categoriesFlow.collectAsState()
        val queued by queuedCountFlow.collectAsState()

        val current = payment ?: return@AppTheme
        if (expanded) {
            ExpandedPanel(
                payment = current,
                categories = categories,
                queued = queued,
                onCategorySelected = onCategorySelected,
                onDismiss = onDismiss,
                onCollapse = onCollapse,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
        } else {
            CollapsedBubble(
                payment = current,
                queued = queued,
                onTap = onTap,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragStart = onDragStart,
            )
        }
    }
}

@Composable
private fun CollapsedBubble(
    payment: Payment,
    queued: Int,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragStart: () -> Unit = {},
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(64.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.x, amount.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = formatAmount(payment.amount, payment.currency, compact = true),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (queued > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${queued + 1}",
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedPanel(
    payment: Payment,
    categories: List<Category>,
    queued: Int,
    onCategorySelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    onCollapse: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.x, amount.y)
                            },
                            onDragEnd = { onDragEnd() },
                        )
                    },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (queued > 0) "Purchase (+$queued more)" else "Purchase",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = formatAmount(payment.amount, payment.currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    payment.merchant?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.Close, contentDescription = "Collapse")
                }
            }
            Spacer(Modifier.height(12.dp))
            com.smsexpense.tracker.ui.components.CategoryChips(
                categories = categories,
                onSelected = onCategorySelected,
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    }
}
