package com.smsexpense.tracker

import android.app.Application
import com.smsexpense.tracker.data.local.database.AppDatabase
import com.smsexpense.tracker.data.remote.api.ApiConfig
import com.smsexpense.tracker.data.remote.api.ApiConfigProvider
import com.smsexpense.tracker.data.remote.api.HttpPaymentApiClient
import com.smsexpense.tracker.data.remote.api.PaymentApiClient
import com.smsexpense.tracker.data.repository.DataStoreSettingsRepository
import com.smsexpense.tracker.data.repository.RoomCategoryRepository
import com.smsexpense.tracker.data.repository.RoomPaymentRepository
import com.smsexpense.tracker.domain.parser.SmsParser
import com.smsexpense.tracker.domain.repository.CategoryRepository
import com.smsexpense.tracker.domain.repository.PaymentRepository
import com.smsexpense.tracker.domain.repository.SettingsRepository
import com.smsexpense.tracker.domain.usecase.IngestPaymentMessageUseCase
import com.smsexpense.tracker.domain.usecase.SyncPaymentsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Manual dependency container. Small app, no DI framework needed; everything is
 * constructor-injected so tests can substitute fakes.
 */
class AppContainer(private val app: Application) {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AppDatabase by lazy { AppDatabase.get(app) }

    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(app) }

    val paymentRepository: PaymentRepository by lazy { RoomPaymentRepository(database.paymentDao()) }

    val categoryRepository: CategoryRepository by lazy {
        RoomCategoryRepository(database.categoryDao(), database.paymentDao())
    }

    val parser: SmsParser by lazy { SmsParser() }

    val apiClient: PaymentApiClient by lazy {
        HttpPaymentApiClient(
            ApiConfigProvider {
                val s = settingsRepository.apiSettings.first()
                ApiConfig(enabled = s.enabled, baseUrl = s.baseUrl, authToken = s.authToken)
            }
        )
    }

    val ingestPaymentMessage: IngestPaymentMessageUseCase by lazy {
        IngestPaymentMessageUseCase(parser, paymentRepository, settingsRepository)
    }

    val syncPayments: SyncPaymentsUseCase by lazy {
        SyncPaymentsUseCase(paymentRepository, categoryRepository, apiClient)
    }
}

class SmsExpenseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.applicationScope.launch {
            container.categoryRepository.seedDefaultsIfEmpty()
        }
    }
}

fun android.content.Context.appContainer(): AppContainer =
    (applicationContext as SmsExpenseApp).container
