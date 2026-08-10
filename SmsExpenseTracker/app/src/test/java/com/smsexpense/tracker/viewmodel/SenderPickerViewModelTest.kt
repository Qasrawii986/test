package com.smsexpense.tracker.viewmodel

import com.smsexpense.tracker.domain.model.IncomingMessage
import com.smsexpense.tracker.fakes.FakeDeviceSmsSource
import com.smsexpense.tracker.fakes.FakeSettingsRepository
import com.smsexpense.tracker.ui.senderpicker.SenderPickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SenderPickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsRepository(senderIdsInitial = emptySet())
    private val source = FakeDeviceSmsSource(
        inbox = listOf(
            IncomingMessage("MYBANK", "تم خصم 12.50 JOD", 4),
            IncomingMessage("MYBANK", "تم خصم 5.00 JOD", 3),
            IncomingMessage("Friend", "hello", 2),
            IncomingMessage("OTHERBANK", "Purchase of JOD 9.00", 1),
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = SenderPickerViewModel(source, settings)

    @Test
    fun `load lists inbox messages newest first`() = runTest(dispatcher.scheduler) {
        val viewModel = vm()
        viewModel.onPermissionResult(true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.messages.size)
        assertEquals("MYBANK", viewModel.uiState.value.messages.first().sender)
    }

    @Test
    fun `single sender selection is added directly`() = runTest(dispatcher.scheduler) {
        val viewModel = vm()
        viewModel.onPermissionResult(true)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggle(0)
        viewModel.toggle(1) // same sender MYBANK twice → one distinct sender
        viewModel.addSelected()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("MYBANK"), settings.senderIds.first())
        assertNull(viewModel.uiState.value.pendingSenders)
    }

    @Test
    fun `multiple senders require confirmation before adding`() = runTest(dispatcher.scheduler) {
        val viewModel = vm()
        viewModel.onPermissionResult(true)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggle(0) // MYBANK
        viewModel.toggle(3) // OTHERBANK
        viewModel.addSelected()

        assertEquals(listOf("MYBANK", "OTHERBANK"), viewModel.uiState.value.pendingSenders)
        assertTrue(settings.senderIds.first().isEmpty()) // nothing added yet

        viewModel.confirmSenders(listOf("MYBANK"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf("MYBANK"), settings.senderIds.first())
    }

    @Test
    fun `adding an already configured sender does not duplicate it`() = runTest(dispatcher.scheduler) {
        settings.addSenderId("MYBANK")
        val viewModel = vm()
        viewModel.onPermissionResult(true)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.toggle(0)
        viewModel.addSelected()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("MYBANK"), settings.senderIds.first())
    }
}
