package com.smsexpense.tracker.viewmodel

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.usecase.IngestOutcome
import com.smsexpense.tracker.domain.usecase.IngestPaymentMessageUseCase
import com.smsexpense.tracker.fakes.FakeCategoryRepository
import com.smsexpense.tracker.fakes.FakePaymentRepository
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import com.smsexpense.tracker.fakes.FakeSplitRepository
import com.smsexpense.tracker.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val payments = FakePaymentRepository()
    private val categories = FakeCategoryRepository()
    private val settings = FakeSettingsRepository()
    private val splits = FakeSplitRepository()
    private val ingest = IngestPaymentMessageUseCase(SmsParser(), payments, settings)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun collectState(viewModel: DashboardViewModel, scope: kotlinx.coroutines.CoroutineScope): Job =
        scope.launch { viewModel.uiState.collect { } }

    @Test
    fun `starts in loading state`() = runTest(dispatcher.scheduler) {
        val viewModel = DashboardViewModel(payments, categories, splits)
        assertTrue(viewModel.uiState.value.loading)
    }

    @Test
    fun `shows ingested payments and totals`() = runTest(dispatcher.scheduler) {
        val outcome = ingest(IncomingMessage("MYBANK", "تم خصم 12.50 JOD من بطاقتك", 1_722_988_800_000))
        assertTrue(outcome is IngestOutcome.PaymentSaved)
        ingest(IncomingMessage("MYBANK", "Purchase of JOD 25.00 at SuperMart", 1_722_990_000_000))

        val viewModel = DashboardViewModel(payments, categories, splits)
        val job = collectState(viewModel, this)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.loading)
        assertEquals(2, state.payments.size)
        assertEquals(37.5, state.stats!!.total, 0.0001)
        assertEquals(2, state.stats!!.transactionCount)
        job.cancel()
    }

    @Test
    fun `uncategorized payments are listed until classified`() = runTest(dispatcher.scheduler) {
        val outcome = ingest(IncomingMessage("MYBANK", "تم خصم 12.50 JOD من بطاقتك", 1_722_988_800_000))
        val paymentId = (outcome as IngestOutcome.PaymentSaved).paymentId
        val categoryId = categories.add("Food", "🍔", null)

        val viewModel = DashboardViewModel(payments, categories, splits)
        val job = collectState(viewModel, this)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.uncategorized.size)

        payments.categorize(paymentId, categoryId)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.uncategorized.size)
        assertEquals(categoryId, viewModel.uiState.value.payments.single().categoryId)
        job.cancel()
    }

    @Test
    fun `month navigation updates selected month`() = runTest(dispatcher.scheduler) {
        val viewModel = DashboardViewModel(payments, categories, splits)
        val job = collectState(viewModel, this)
        dispatcher.scheduler.advanceUntilIdle()

        val current = viewModel.uiState.value.month
        viewModel.previousMonth()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(current.minusMonths(1), viewModel.uiState.value.month)

        viewModel.nextMonth()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(current, viewModel.uiState.value.month)

        // Cannot navigate into the future.
        viewModel.nextMonth()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(current, viewModel.uiState.value.month)
        job.cancel()
    }

    @Test
    fun `sharing an expense makes the headline your own share`() = runTest(dispatcher.scheduler) {
        val outcome = ingest(IncomingMessage("MYBANK", "Purchase of JOD 100.00 at Electric Co", 1_722_990_000_000))
        val paymentId = (outcome as IngestOutcome.PaymentSaved).paymentId
        splits.seedSelfIfEmpty()
        val dad = splits.addPayer("Dad", "\uD83D\uDC68", null)
        splits.setSplit(paymentId, mapOf(dad to 60.0))

        val viewModel = DashboardViewModel(payments, categories, splits)
        val job = collectState(viewModel, this)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasSharing)
        // Gross spending is unchanged; what it cost you is not.
        assertEquals(100.0, state.stats!!.total, 0.0001)
        assertEquals(60.0, state.chargedToOthers, 0.0001)
        assertEquals(40.0, state.myShare, 0.0001)
        assertEquals(60.0, state.owed.single().amount, 0.0001)
        assertEquals(setOf(paymentId), state.sharedPaymentIds)
        job.cancel()
    }

    @Test
    fun `settling a debt leaves your share unchanged`() = runTest(dispatcher.scheduler) {
        val outcome = ingest(IncomingMessage("MYBANK", "Purchase of JOD 100.00 at Electric Co", 1_722_990_000_000))
        val paymentId = (outcome as IngestOutcome.PaymentSaved).paymentId
        splits.seedSelfIfEmpty()
        val dad = splits.addPayer("Dad", "\uD83D\uDC68", null)
        splits.setSplit(paymentId, mapOf(dad to 60.0))

        val viewModel = DashboardViewModel(payments, categories, splits)
        val job = collectState(viewModel, this)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.settle(dad)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.owed.isEmpty())
        assertEquals(40.0, state.myShare, 0.0001)
        job.cancel()
    }

    @Test
    fun `with nothing shared the headline stays the gross total`() = runTest(dispatcher.scheduler) {
        ingest(IncomingMessage("MYBANK", "Purchase of JOD 25.00 at SuperMart", 1_722_990_000_000))
        splits.seedSelfIfEmpty()

        val viewModel = DashboardViewModel(payments, categories, splits)
        val job = collectState(viewModel, this)
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasSharing)
        assertEquals(25.0, state.myShare, 0.0001)
        job.cancel()
    }
}
