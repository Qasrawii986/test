package com.smsexpense.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.R
import com.smsexpense.tracker.domain.model.Payer
import com.smsexpense.tracker.domain.model.PaymentSplit

/** The self payer is stored under a fixed name but always shown translated. */
@Composable
fun payerLabel(payer: Payer): String =
    if (payer.isSelf) stringResource(R.string.payer_you) else payer.name

@Composable
fun payerDisplay(payer: Payer): String = "${payer.emoji} ${payerLabel(payer)}"

/**
 * One-tap "who pays this": charges the whole payment to a single person. The
 * common case (all mine, or all on Dad) is a single tap; anything finer goes
 * through [SplitEditor].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayerChips(
    payers: List<Payer>,
    split: PaymentSplit,
    onChargeWholeTo: (Long) -> Unit,
    onSplitClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Highlight a payer only when they carry the entire payment; a real split is
    // shown as "Shared" instead so the row never lies about a partial charge.
    val soleOwner = payers.firstOrNull { payer ->
        if (payer.isSelf) split.isFullyMine
        else split.allocations.size == 1 &&
            split.allocations.single().payerId == payer.id &&
            split.isFullyCharged
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        payers.forEach { payer ->
            FilterChip(
                selected = payer.id == soleOwner?.id,
                onClick = { onChargeWholeTo(payer.id) },
                label = { Text(payerDisplay(payer)) },
            )
        }
        if (payers.size > 1) {
            AssistChip(
                onClick = onSplitClick,
                label = {
                    Text(
                        if (soleOwner == null) {
                            "✂️ ${stringResource(R.string.split_shared)}"
                        } else {
                            "✂️ ${stringResource(R.string.split_title)}"
                        }
                    )
                },
            )
        }
    }
}

/**
 * Assigns explicit amounts to other people; your own share is shown live as the
 * remainder. Used by both the bubble panel and the payment detail screen so the
 * two can never disagree about what a split means.
 */
@Composable
fun SplitEditor(
    payers: List<Payer>,
    split: PaymentSplit,
    onSave: (Map<Long, Double>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onManagePayers: (() -> Unit)? = null,
) {
    val others = remember(payers) { payers.filter { !it.isSelf } }
    // Text, not Double: the field has to tolerate a half-typed "12." while editing.
    val entered = remember(split.paymentId, others) {
        mutableStateMapOf<Long, String>().apply {
            others.forEach { payer ->
                val amount = split.amountFor(payer.id)
                put(payer.id, if (amount > PaymentSplit.CENT) trimAmount(amount) else "")
            }
        }
    }

    fun parsed(): Map<Long, Double> = entered
        .mapValues { (_, text) -> text.trim().toDoubleOrNull() ?: 0.0 }
        .filterValues { it > PaymentSplit.CENT }

    val assigned = parsed().values.sum()
    val myShare = split.total - assigned
    val overAllocated = assigned - split.total > PaymentSplit.CENT

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.split_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        if (others.isEmpty()) {
            Text(
                stringResource(R.string.split_no_payers),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        others.forEach { payer ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    payerDisplay(payer),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = entered[payer.id].orEmpty(),
                    onValueChange = { text ->
                        entered[payer.id] = text.filter { it.isDigit() || it == '.' }
                    },
                    placeholder = { Text("0", textAlign = TextAlign.End) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    modifier = Modifier.width(110.dp),
                )
                TextButton(onClick = {
                    // "All to them": give this person the whole amount, clear the rest.
                    others.forEach { entered[it.id] = "" }
                    entered[payer.id] = trimAmount(split.total)
                }) { Text("100%") }
            }
        }

        if (others.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    // Everyone present carries an equal part, you included.
                    val each = split.total / (others.size + 1)
                    others.forEach { entered[it.id] = trimAmount(each) }
                }) { Text(stringResource(R.string.split_evenly)) }
                TextButton(onClick = { others.forEach { entered[it.id] = "" } }) {
                    Text(stringResource(R.string.split_all_mine))
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.split_your_share),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatAmount(myShare.coerceAtLeast(0.0), split.currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (overAllocated) {
            Text(
                stringResource(R.string.split_over_allocated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onManagePayers?.let {
                TextButton(onClick = it) { Text(stringResource(R.string.split_manage_payers)) }
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.edit_cancel)) }
            Button(
                onClick = { onSave(parsed()) },
                enabled = !overAllocated,
            ) { Text(stringResource(R.string.split_save)) }
        }
    }
}

/** Shows how a saved payment is divided, without the editing controls. */
@Composable
fun SplitSummary(
    payers: List<Payer>,
    split: PaymentSplit,
    modifier: Modifier = Modifier,
) {
    val byId = remember(payers) { payers.associateBy { it.id } }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.split_your_share),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatAmount(split.myShare, split.currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        split.allocations.forEach { allocation ->
            val payer = byId[allocation.payerId]
            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                Text(
                    payer?.let { "${it.emoji} ${it.name}" } ?: "👤",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (allocation.settled) {
                    Text(
                        stringResource(R.string.split_settled_label) + " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    formatAmount(allocation.amount, split.currency),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** "12.5" rather than "12.500" so the editable field stays readable. */
internal fun trimAmount(amount: Double): String {
    val rounded = Math.round(amount * 1000.0) / 1000.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
