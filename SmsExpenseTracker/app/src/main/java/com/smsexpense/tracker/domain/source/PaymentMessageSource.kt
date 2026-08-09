package com.smsexpense.tracker.domain.source

import com.smsexpense.tracker.domain.model.IncomingMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Abstraction over where payment messages come from (SMS today, a
 * NotificationListenerService or anything else tomorrow). The domain pipeline only
 * ever sees [IncomingMessage].
 */
interface PaymentMessageSource {
    val messages: Flow<IncomingMessage>
}

/**
 * Push-style source used by both the real SMS receiver and the debug screen's
 * "Simulate SMS" button, so simulated messages traverse the exact same pipeline.
 */
class ChannelPaymentMessageSource : PaymentMessageSource {
    private val _messages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 16)
    override val messages: Flow<IncomingMessage> = _messages

    fun emit(message: IncomingMessage): Boolean = _messages.tryEmit(message)
}
