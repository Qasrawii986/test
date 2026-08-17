package com.smsexpense.tracker.domain.model

/**
 * A raw message entering the pipeline. The domain layer never knows whether it
 * came from SMS, a notification listener, or the debug screen.
 */
data class IncomingMessage(
    val sender: String,
    val body: String,
    val timestamp: Long,
)

/** What kind of bank message this is, by meaning — not just "contains an amount". */
enum class TransactionType {
    CARD_PURCHASE,
    CLIQ_TRANSFER_OUT,
    BANK_TRANSFER_OUT,
    CASH_WITHDRAWAL,
    REFUND,
    INCOMING_TRANSFER,
    OTP,
    BALANCE_UPDATE,
    NON_TRANSACTION,
    UNKNOWN,
}

enum class TransactionDirection { OUTGOING, INCOMING, NONE }

/** Only completed outgoing money movements count as expenses. */
val TransactionType.isExpense: Boolean
    get() = this == TransactionType.CARD_PURCHASE ||
        this == TransactionType.CLIQ_TRANSFER_OUT ||
        this == TransactionType.BANK_TRANSFER_OUT ||
        this == TransactionType.CASH_WITHDRAWAL

val TransactionType.direction: TransactionDirection
    get() = when {
        isExpense -> TransactionDirection.OUTGOING
        this == TransactionType.INCOMING_TRANSFER || this == TransactionType.REFUND ->
            TransactionDirection.INCOMING
        else -> TransactionDirection.NONE
    }

/**
 * A parsed payment extracted from an [IncomingMessage], before persistence.
 * [merchant] doubles as the counterparty: the store for card purchases, the
 * recipient for outgoing CliQ/bank transfers.
 */
data class PaymentCandidate(
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val sender: String,
    val originalMessage: String,
    val timestamp: Long,
    val confidence: Float,
    val type: TransactionType = TransactionType.CARD_PURCHASE,
)

enum class PaymentStatus { UNCATEGORIZED, CATEGORIZED }

enum class SyncStatus { PENDING, SYNCED, FAILED, DISABLED }

/** Where a payment entered the pipeline. */
enum class PaymentSource { SMS_REALTIME, SMS_HISTORICAL, NOTIFICATION }

data class Payment(
    val id: Long,
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val categoryId: Long?,
    val sender: String,
    val originalMessage: String,
    val timestamp: Long,
    val status: PaymentStatus,
    val syncStatus: SyncStatus,
    val confidence: Float,
    val createdAt: Long,
    val source: PaymentSource = PaymentSource.SMS_REALTIME,
)

/**
 * Someone an expense can be charged to: yourself, a family member, a shared pot.
 * Exactly one payer is marked [isSelf] — the share that is genuinely your cost.
 */
data class Payer(
    val id: Long,
    val name: String,
    val emoji: String,
    val color: Long?,
    val isSelf: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
)

/**
 * Part of a payment charged to someone who is not you.
 *
 * Deliberately never written for the [Payer.isSelf] payer: your own share is
 * always "whatever is left over". That keeps a payment with no allocation rows
 * — every payment that existed before this feature — correctly 100% yours, and
 * makes it impossible for the split to silently stop adding up to the total.
 */
data class Allocation(
    val id: Long,
    val paymentId: Long,
    val payerId: Long,
    val amount: Double,
    /** They have paid you back. Settling changes what you are owed, not your share. */
    val settled: Boolean,
)

/** How a single payment is divided, for the split editor. */
data class PaymentSplit(
    val paymentId: Long,
    val total: Double,
    val currency: String,
    /** Only the parts charged to other people. */
    val allocations: List<Allocation>,
) {
    val chargedToOthers: Double get() = allocations.sumOf { it.amount }

    /** The part that is genuinely your cost. Never negative, never above the total. */
    val myShare: Double get() = (total - chargedToOthers).coerceIn(0.0, total)

    val isFullyMine: Boolean get() = allocations.isEmpty()

    /** True once the whole payment is charged to other people. */
    val isFullyCharged: Boolean get() = myShare < CENT

    fun amountFor(payerId: Long): Double =
        allocations.filter { it.payerId == payerId }.sumOf { it.amount }

    /** More was assigned than the payment is worth — the editor blocks saving. */
    val isOverAllocated: Boolean get() = chargedToOthers - total > CENT

    companion object {
        /** Half a fils: amounts are money, so compare with a tolerance. */
        const val CENT = 0.005
    }
}

/** What one payer still owes you over a period. */
data class OwedTotal(
    val payerId: Long,
    val amount: Double,
    val count: Int,
)

/** One row in the import history log. */
data class ImportRecord(
    val id: Long,
    val importedAt: Long,
    val fromDate: Long,
    val toDate: Long,
    val transactionCount: Int,
    val totalAmount: Double,
    val currency: String,
)

/**
 * Expense category. The tree is deliberately two levels deep: a root category
 * ([parentId] == null) may have subcategories, a subcategory may not.
 */
data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Long?,
    val sortOrder: Int,
    val createdAt: Long,
    val parentId: Long? = null,
) {
    val isRoot: Boolean get() = parentId == null
}

/** A root category with its subcategories, ready for tree UI and reports. */
data class CategoryNode(
    val category: Category,
    val children: List<Category>,
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
}

/** Builds the two-level tree from a flat list, keeping sort order. */
fun List<Category>.toTree(): List<CategoryNode> {
    val childrenByParent = filter { !it.isRoot }.groupBy { it.parentId }
    return filter { it.isRoot }.map { root ->
        CategoryNode(root, childrenByParent[root.id].orEmpty())
    }
}

/** Maps every category id to the id of the root it rolls up into. */
fun List<Category>.rootIdOf(): Map<Long, Long> {
    val byId = associateBy { it.id }
    return associate { category ->
        val rootId = category.parentId?.let { byId[it]?.id } ?: category.id
        category.id to rootId
    }
}

data class CategoryTotal(
    val categoryId: Long?,
    val total: Double,
    val count: Int,
)

data class MonthlyStats(
    val year: Int,
    val month: Int, // 1..12
    val total: Double,
    val transactionCount: Int,
    val perCategory: List<CategoryTotal>,
)
