package com.smsexpense.tracker.viewmodel

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.usecase.ImportHistoricalTransactionsUseCase
import com.smsexpense.tracker.fakes.FakeCategoryRepository
import com.smsexpense.tracker.fakes.FakeDeviceSmsSource
import com.smsexpense.tracker.fakes.FakeImportHistoryRepository
import com.smsexpense.tracker.fakes.FakePaymentRepository
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import com.smsexpense.tracker.ui.import_.HistoricalImportViewModel
import com.smsexpense.tracker.ui.import_.ImportStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class HistoricalImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val payments = FakePaymentRepository()
    private val settings = FakeSettingsRepository(senderIdsInitial = setOf("MYBANK"))
    private val categories = FakeCategoryRepository()
    private val history = FakeImportHistoryRepository()
    private val smsSource = FakeDeviceSmsSource()

    private var importedCallbackRuns = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = HistoricalImportViewModel(
        importUseCase = ImportHistoricalTransactionsUseCase(
            smsSource, SmsParser(), payments, settings, history, dispatcher,
        ),
        settingsRepository = settings,
        categoryRepository = categories,
        importHistoryRepository = history,
        onImported = { importedCallbackRuns++ },
    )

    private val base = 1_720_000_000_000L

    private fun seedInbox(count: Int) {
        smsSource.inbox = (0 until count).map {
            IncomingMessage("MYBANK", "تم خصم ${it + 1}.00 JOD من بطاقتك", base + it)
        }
    }

    private fun HistoricalImportViewModel.scanAndAwait(): ImportStep.Review {
        setFromDate(0)
        setToDate(Long.MAX_VALUE - HistoricalImportViewModel.DAY_MS)
        scan()
        dispatcher.scheduler.advanceUntilIdle()
        return uiState.value.step as ImportStep.Review
    }

    @Test
    fun `scan produces review items all selected by default`() = runTest(dispatcher.scheduler) {
        seedInbox(3)
        val viewModel = vm()
        dispatcher.scheduler.advanceUntilIdle()

        val review = viewModel.scanAndAwait()
        assertEquals(3, review.items.size)
        assertEquals(3, review.selectedCount)
        assertEquals(1.0 + 2.0 + 3.0, review.selectedTotal, 0.0001)
    }

    @Test
    fun `toggle item and select all work`() = runTest(dispatcher.scheduler) {
        seedInbox(3)
        val viewModel = vm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.scanAndAwait()

        viewModel.toggleItem(0)
        var review = viewModel.uiState.value.step as ImportStep.Review
        assertEquals(2, review.selectedCount)
        assertFalse(review.allSelected)

        viewModel.toggleSelectAll() // -> all selected
        review = viewModel.uiState.value.step as ImportStep.Review
        assertEquals(3, review.selectedCount)

        viewModel.toggleSelectAll() // -> none selected
        review = viewModel.uiState.value.step as ImportStep.Review
        assertEquals(0, review.selectedCount)
    }

    @Test
    fun `bulk category applies only to selected items`() = runTest(dispatcher.scheduler) {
        seedInbox(3)
        val viewModel = vm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.scanAndAwait()

        viewModel.toggleItem(2) // deselect the third
        viewModel.setCategoryForSelected(42L)

        val review = viewModel.uiState.value.step as ImportStep.Review
        assertEquals(42L, review.items[0].categoryId)
        assertEquals(42L, review.items[1].categoryId)
        assertEquals(null, review.items[2].categoryId)
    }

    @Test
    fun `import goes through confirmation and saves only selected`() = runTest(dispatcher.scheduler) {
        seedInbox(3)
        val viewModel = vm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.scanAndAwait()

        viewModel.toggleItem(0) // 2 remain selected
        viewModel.requestImport()
        assertTrue(viewModel.uiState.value.showConfirmDialog)

        viewModel.confirmImport()
        dispatcher.scheduler.advanceUntilIdle()

        val done = viewModel.uiState.value.step as ImportStep.Done
        assertEquals(2, done.summary.imported)
        assertEquals(2, payments.all().size)
        assertEquals(1, importedCallbackRuns) // sync hook fired
    }

    @Test
    fun `individual category edit is applied on import`() = runTest(dispatcher.scheduler) {
        seedInbox(1)
        val viewModel = vm()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.scanAndAwait()

        viewModel.setItemCategory(0, 9L)
        viewModel.requestImport()
        viewModel.confirmImport()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(9L, payments.all().single().categoryId)
    }
}
