package com.smsexpense.tracker.viewmodel

import com.smsexpense.tracker.fakes.FakeSettingsRepository
import com.smsexpense.tracker.fakes.FakeSplitRepository
import com.smsexpense.tracker.ui.payers.PayersViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PayersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val splits = FakeSplitRepository()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun collect(vm: PayersViewModel, scope: kotlinx.coroutines.CoroutineScope): Job =
        scope.launch { vm.uiState.collect { } }

    @Test
    fun `the self payer is seeded and listed first`() = runTest(dispatcher.scheduler) {
        val vm = PayersViewModel(splits, settings)
        val job = collect(vm, this)
        dispatcher.scheduler.advanceUntilIdle()

        val rows = vm.uiState.value.rows
        assertEquals(1, rows.size)
        assertTrue(rows.single().payer.isSelf)
        job.cancel()
    }

    @Test
    fun `adding a payer shows them with nothing outstanding`() = runTest(dispatcher.scheduler) {
        val vm = PayersViewModel(splits, settings)
        val job = collect(vm, this)
        dispatcher.scheduler.advanceUntilIdle()

        vm.add("Dad", "👨")
        dispatcher.scheduler.advanceUntilIdle()

        val dad = vm.uiState.value.rows.first { !it.payer.isSelf }
        assertEquals("Dad", dad.payer.name)
        assertEquals(0.0, dad.outstanding, 0.0001)
        job.cancel()
    }

    @Test
    fun `a blank name is rejected`() = runTest(dispatcher.scheduler) {
        val vm = PayersViewModel(splits, settings)
        val job = collect(vm, this)
        dispatcher.scheduler.advanceUntilIdle()

        vm.add("   ", "👨")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.rows.size)
        job.cancel()
    }

    @Test
    fun `outstanding follows charges and clears on settle`() = runTest(dispatcher.scheduler) {
        val vm = PayersViewModel(splits, settings)
        val job = collect(vm, this)
        dispatcher.scheduler.advanceUntilIdle()

        vm.add("Dad", "👨")
        dispatcher.scheduler.advanceUntilIdle()
        val dadId = vm.uiState.value.rows.first { !it.payer.isSelf }.payer.id

        splits.registerPayment(paymentId = 7L, timestamp = 1_000L)
        splits.setSplit(7L, mapOf(dadId to 80.0))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(80.0, vm.uiState.value.rows.first { it.payer.id == dadId }.outstanding, 0.0001)
        assertEquals(1, vm.uiState.value.rows.first { it.payer.id == dadId }.outstandingCount)

        vm.settle(dadId)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.0, vm.uiState.value.rows.first { it.payer.id == dadId }.outstanding, 0.0001)

        vm.reopen(dadId)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(80.0, vm.uiState.value.rows.first { it.payer.id == dadId }.outstanding, 0.0001)
        job.cancel()
    }

    @Test
    fun `deleting a payer drops their charges`() = runTest(dispatcher.scheduler) {
        val vm = PayersViewModel(splits, settings)
        val job = collect(vm, this)
        dispatcher.scheduler.advanceUntilIdle()
        vm.add("Dad", "👨")
        dispatcher.scheduler.advanceUntilIdle()
        val dadId = vm.uiState.value.rows.first { !it.payer.isSelf }.payer.id
        splits.registerPayment(paymentId = 7L, timestamp = 1_000L)
        splits.setSplit(7L, mapOf(dadId to 80.0))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.chargedCount(dadId))

        vm.delete(dadId)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.rows.none { it.payer.id == dadId })
        assertTrue(splits.allocationsOf(7L).isEmpty())
        job.cancel()
    }
}
