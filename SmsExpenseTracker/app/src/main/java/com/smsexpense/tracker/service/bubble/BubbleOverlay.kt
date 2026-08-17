package com.smsexpense.tracker.service.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.R
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.Payer
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.domain.model.PaymentSplit
import com.smsexpense.tracker.ui.components.PayerChips
import com.smsexpense.tracker.ui.components.SplitEditor
import com.smsexpense.tracker.ui.components.formatAmount
import com.smsexpense.tracker.ui.components.trimAmount
import com.smsexpense.tracker.ui.theme.AppTheme
import kotlinx.coroutines.flow.StateFlow

/** Which face of the expanded panel is showing. */
enum class BubblePanelMode { MAIN, EDIT, SPLIT }

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
    appearanceFlow: StateFlow<com.smsexpense.tracker.domain.repository.BubbleSettings>,
    payersFlow: StateFlow<List<Payer>>,
    splitFlow: StateFlow<PaymentSplit?>,
    modeFlow: StateFlow<BubblePanelMode>,
    onModeChange: (BubblePanelMode) -> Unit = {},
    onChargeWholeTo: (Long) -> Unit = {},
    onSaveSplit: (Map<Long, Double>) -> Unit = {},
    onSaveDetails: (String?, Double) -> Unit = { _, _ -> },
) {
    AppTheme {
        val payment by paymentFlow.collectAsState()
        val expanded by expandedFlow.collectAsState()
        val categories by categoriesFlow.collectAsState()
        val queued by queuedCountFlow.collectAsState()

        val current = payment ?: return@AppTheme
        if (expanded) {
            val payers by payersFlow.collectAsState()
            val split by splitFlow.collectAsState()
            val mode by modeFlow.collectAsState()
            ExpandedPanel(
                payment = current,
                categories = categories,
                queued = queued,
                payers = payers,
                split = split ?: PaymentSplit(current.id, current.amount, current.currency, emptyList()),
                mode = mode,
                onModeChange = onModeChange,
                onChargeWholeTo = onChargeWholeTo,
                onSaveSplit = onSaveSplit,
                onSaveDetails = onSaveDetails,
                onCategorySelected = onCategorySelected,
                onDismiss = onDismiss,
                onCollapse = onCollapse,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
            )
        } else {
            val appearance by appearanceFlow.collectAsState()
            CollapsedBubble(
                payment = current,
                queued = queued,
                appearance = appearance,
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
    appearance: com.smsexpense.tracker.domain.repository.BubbleSettings,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragStart: () -> Unit = {},
) {
    Box {
        com.smsexpense.tracker.ui.components.BubbleVisual(
            settings = appearance,
            label = if (appearance.showAmount) {
                formatAmount(payment.amount, payment.currency, compact = true)
            } else {
                "💳"
            },
            modifier = Modifier
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
        )
        if (queued > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedPanel(
    payment: Payment,
    categories: List<Category>,
    queued: Int,
    payers: List<Payer>,
    split: PaymentSplit,
    mode: BubblePanelMode,
    onModeChange: (BubblePanelMode) -> Unit,
    onChargeWholeTo: (Long) -> Unit,
    onSaveSplit: (Map<Long, Double>) -> Unit,
    onSaveDetails: (String?, Double) -> Unit,
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
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
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
                        text = if (queued > 0) {
                            stringResource(R.string.bubble_purchase_more, queued)
                        } else {
                            stringResource(R.string.bubble_purchase)
                        },
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
                    // Only worth showing once part of the payment is someone else's.
                    if (!split.isFullyMine) {
                        Text(
                            text = "${stringResource(R.string.split_your_share)}: " +
                                formatAmount(split.myShare, split.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = {
                    onModeChange(
                        if (mode == BubblePanelMode.EDIT) BubblePanelMode.MAIN else BubblePanelMode.EDIT
                    )
                }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_details))
                }
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.bubble_collapse))
                }
            }

            Spacer(Modifier.height(12.dp))

            when (mode) {
                BubblePanelMode.EDIT -> DetailsEditor(
                    payment = payment,
                    onSave = { merchant, amount ->
                        onSaveDetails(merchant, amount)
                        onModeChange(BubblePanelMode.MAIN)
                    },
                    onCancel = { onModeChange(BubblePanelMode.MAIN) },
                )

                BubblePanelMode.SPLIT -> SplitEditor(
                    payers = payers,
                    split = split,
                    onSave = {
                        onSaveSplit(it)
                        onModeChange(BubblePanelMode.MAIN)
                    },
                    onCancel = { onModeChange(BubblePanelMode.MAIN) },
                )

                BubblePanelMode.MAIN -> {
                    if (payers.isNotEmpty()) {
                        Text(
                            stringResource(R.string.split_who_pays),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        PayerChips(
                            payers = payers,
                            split = split,
                            onChargeWholeTo = onChargeWholeTo,
                            onSplitClick = { onModeChange(BubblePanelMode.SPLIT) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    com.smsexpense.tracker.ui.components.CategoryChips(
                        categories = categories,
                        onSelected = onCategorySelected,
                        selectedId = payment.categoryId,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.bubble_later)) }
                    }
                }
            }
        }
    }
}

/** Fixes what the parser read: the counterparty name and the amount. */
@Composable
private fun DetailsEditor(
    payment: Payment,
    onSave: (String?, Double) -> Unit,
    onCancel: () -> Unit,
) {
    var merchant by remember(payment.id) { mutableStateOf(payment.merchant.orEmpty()) }
    var amount by remember(payment.id) { mutableStateOf(trimAmount(payment.amount)) }
    val parsedAmount = amount.trim().toDoubleOrNull()
    val amountValid = parsedAmount != null && parsedAmount > 0.0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text(stringResource(R.string.edit_merchant)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { text -> amount = text.filter { it.isDigit() || it == '.' } },
            label = { Text(stringResource(R.string.edit_amount)) },
            singleLine = true,
            isError = amount.isNotEmpty() && !amountValid,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (amount.isNotEmpty() && !amountValid) {
            Text(
                stringResource(R.string.edit_amount_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.edit_cancel)) }
            Button(
                onClick = { onSave(merchant.trim().ifEmpty { null }, parsedAmount ?: payment.amount) },
                enabled = amountValid,
            ) { Text(stringResource(R.string.edit_save)) }
        }
    }
}
