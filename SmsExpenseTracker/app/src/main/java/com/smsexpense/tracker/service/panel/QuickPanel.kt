package com.smsexpense.tracker.service.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.Payment
import com.smsexpense.tracker.ui.components.CategoryChips
import com.smsexpense.tracker.ui.components.formatAmount
import com.smsexpense.tracker.ui.theme.AppTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * The quick-actions sheet raised by any quick-launch method: this month at a
 * glance, plus inline categorization of anything still pending.
 */
@Composable
fun QuickPanel(
    monthTotalFlow: StateFlow<Double>,
    currencyFlow: StateFlow<String>,
    uncategorizedFlow: StateFlow<List<Payment>>,
    categoriesFlow: StateFlow<List<Category>>,
    onCategorize: (Long, Long) -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    AppTheme {
        val total by monthTotalFlow.collectAsState()
        val currency by currencyFlow.collectAsState()
        val pending by uncategorizedFlow.collectAsState()
        val categories by categoriesFlow.collectAsState()

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("This month", style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatAmount(total, currency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (pending.isEmpty()) {
                    Text(
                        "Nothing waiting to be categorized ✅",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    HorizontalDivider()
                    Text(
                        "Needs categorizing (${pending.size})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    pending.take(MAX_PENDING_SHOWN).forEach { payment ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${formatAmount(payment.amount, payment.currency)}" +
                                    (payment.merchant?.let { " • $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(4.dp))
                            CategoryChips(
                                categories = categories,
                                onSelected = { categoryId -> onCategorize(payment.id, categoryId) },
                            )
                        }
                    }
                    if (pending.size > MAX_PENDING_SHOWN) {
                        Text(
                            "+${pending.size - MAX_PENDING_SHOWN} more in the app",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
                    Text("Open full app")
                }
            }
        }
    }
}

private const val MAX_PENDING_SHOWN = 3
