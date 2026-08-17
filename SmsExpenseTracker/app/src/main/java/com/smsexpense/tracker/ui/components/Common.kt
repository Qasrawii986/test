package com.smsexpense.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.Payment
import java.time.YearMonth
import androidx.compose.ui.res.stringResource
import com.smsexpense.tracker.R

@Composable
fun PaymentRow(
    payment: Payment,
    category: Category?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text = category?.icon ?: "❓", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = payment.merchant ?: category?.name ?: stringResource(R.string.uncategorized),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatDateTime(payment.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatAmount(payment.amount, payment.currency),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Simple horizontal bar visualization — no chart library needed. */
@Composable
fun CategoryBar(
    label: String,
    icon: String,
    amount: Double,
    currency: String,
    fraction: Float,
    color: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$icon $label", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = formatAmount(amount, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
fun MonthPicker(
    selected: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.dashboard_previous_month))
        }
        Text(
            text = monthTitle(selected.year, selected.monthValue),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = onNext, enabled = selected < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.dashboard_next_month))
        }
    }
}

val categoryFallbackColors = listOf(
    Color(0xFFEF6C00), Color(0xFF1565C0), Color(0xFF6A1B9A),
    Color(0xFF00695C), Color(0xFFC62828), Color(0xFF546E7A),
)

fun colorForCategory(category: Category?, index: Int): Color =
    category?.color?.let { Color(it) }
        ?: categoryFallbackColors[index % categoryFallbackColors.size]
