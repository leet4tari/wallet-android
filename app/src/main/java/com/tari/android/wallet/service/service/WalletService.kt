/**
 * Copyright 2020 The Tari Project
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of
 * its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tari.android.wallet.service.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import com.orhanobut.logger.Logger
import com.tari.android.wallet.application.TariWalletApplication
import com.tari.android.wallet.application.WalletManager
import com.tari.android.wallet.application.WalletState
import com.tari.android.wallet.application.baseNodes.BaseNodes
import com.tari.android.wallet.data.WalletConfig
import com.tari.android.wallet.data.sharedPrefs.SharedPrefsRepository
import com.tari.android.wallet.data.sharedPrefs.baseNode.BaseNodeSharedRepository
import com.tari.android.wallet.di.DiContainer
import com.tari.android.wallet.event.EventBus
import com.tari.android.wallet.ffi.FFIWallet
import com.tari.android.wallet.infrastructure.backup.BackupManager
import com.tari.android.wallet.notification.NotificationHelper
import com.tari.android.wallet.service.ServiceRestartBroadcastReceiver
import com.tari.android.wallet.service.notification.NotificationService
import com.tari.android.wallet.service.service.WalletServiceLauncher.Companion.startAction
import com.tari.android.wallet.service.service.WalletServiceLauncher.Companion.stopAction
import com.tari.android.wallet.service.service.WalletServiceLauncher.Companion.stopAndDeleteAction
import com.tari.android.wallet.ui.common.domain.ResourceManager
import com.tari.android.wallet.ui.fragment.settings.logs.LogFilesManager
import com.tari.android.wallet.util.Constants
import com.tari.android.wallet.util.WalletUtil
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.Hours
import org.joda.time.Minutes
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Foreground wallet service.
 *
 * @author The Tari Development Team
 */
class WalletService : Service() {

    @Inject
    lateinit var walletConfig: WalletConfig

    @Inject
    lateinit var app: TariWalletApplication

    @Inject
    lateinit var resourceManager: ResourceManager

    @Inject
    lateinit var notificationService: NotificationService

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var sharedPrefsWrapper: SharedPrefsRepository

    @Inject
    lateinit var baseNodeSharedPrefsRepository: BaseNodeSharedRepository

    @Inject
    lateinit var walletManager: WalletManager

    @Inject
    lateinit var backupManager: BackupManager

    @Inject
    lateinit var baseNodes: BaseNodes

    private var lifecycleObserver: ServiceLifecycleCallbacks? = null
    private val stubProxy = TariWalletServiceStubProxy()

    @Suppress("unused")
    private val logFilesManager = LogFilesManager()
    private lateinit var wallet: FFIWallet

    private val logger
        get() = Logger.t(WalletService::class.simpleName)

    /**
     * Check for expired txs every 30 minutes.
     */
    private val expirationCheckPeriodMinutes = Minutes.minutes(30)

    /**
     * Timer to trigger the expiration checks.
     */
    private var txExpirationCheckSubscription: Disposable? = null

    override fun onCreate() {
        super.onCreate()
        DiContainer.appComponent.inject(this)
    }

    /**
     * Called when a component decides to start or stop the foreground wallet service.
     */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForeground()
        when (intent.action) {
            startAction -> startService()
            stopAction -> stopService(startId)
            stopAndDeleteAction -> {
                //todo total crutch. Service is auto-creating during the bind func. Need to refactor this first
                DiContainer.appComponent.inject(this)
                stopService(startId)
                deleteWallet()
            }

            else -> throw RuntimeException("Unexpected intent action: ${intent.action}")
        }
        return START_NOT_STICKY
    }

    private fun startService() {
        //todo total crutch. Service is auto-creating during the bind func. Need to refactor this first
        DiContainer.appComponent.inject(this)
        // start wallet manager on a separate thread & listen to events
        EventBus.walletState.subscribe(this, this::onWalletStateChanged)
        walletManager.start()
        logger.i("Wallet service started")
    }

    private fun startForeground() {
        // start service & post foreground service notification
        startForeground(NOTIFICATION_ID, notificationHelper.buildForegroundServiceNotification())
    }

    private fun stopService(startId: Int) {
        // stop service
        stopForeground(STOP_FOREGROUND_REMOVE)
        walletManager.stop()
        stopSelfResult(startId)
        // stop wallet manager on a separate thread & unsubscribe from events
        EventBus.walletState.unsubscribe(this)
        lifecycleObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
    }

    private fun deleteWallet() {
        WalletUtil.clearWalletFiles(walletConfig.getWalletFilesDirPath())
        sharedPrefsWrapper.clear()
        backupManager.turnOffAll()
    }

    private fun onWalletStateChanged(walletState: WalletState) {
        if (walletState == WalletState.Started) {
            wallet = FFIWallet.instance!!
            lifecycleObserver = ServiceLifecycleCallbacks(wallet)
            val impl =
                FFIWalletListenerImpl(wallet, backupManager, notificationHelper, notificationService, app, baseNodeSharedPrefsRepository, baseNodes)
            stubProxy.stub = TariWalletServiceStubImpl(wallet, baseNodeSharedPrefsRepository, impl)
            wallet.listener = impl
            EventBus.walletState.unsubscribe(this)
            scheduleExpirationCheck()
            Handler(Looper.getMainLooper()).post { ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver!!) }
            EventBus.walletState.post(WalletState.Running)
        }
    }

    private fun scheduleExpirationCheck() {
        txExpirationCheckSubscription =
            Observable
                .timer(expirationCheckPeriodMinutes.minutes.toLong(), TimeUnit.MINUTES)
                .repeat()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnError { logger.i("error during sheduled expiration check $it") }
                .subscribe {
                    try {
                        logger.i("scheduled expiration check")
                        cancelExpiredPendingInboundTxs()
                        cancelExpiredPendingOutboundTxs()
                    } catch (e: Exception) {
                        logger.e("error during sheduled expiration check $e")
                    }
                }
    }

    override fun onBind(intent: Intent?): IBinder {
        logger.i("Wallet service bound")
        return stubProxy
    }

    override fun onUnbind(intent: Intent?): Boolean {
        logger.i("Wallet service unbound")
        return super.onUnbind(intent)
    }

    /**
     * A broadcast is made on destroy to get the service running again.
     */
    override fun onDestroy() {
        logger.i("Wallet service destroyed")
        txExpirationCheckSubscription?.dispose()
        sendBroadcast(Intent(this, ServiceRestartBroadcastReceiver::class.java))
        super.onDestroy()
    }

    /**
     * Cancels expired pending inbound transactions.
     * Expiration period is defined by Constants.Wallet.pendingTxExpirationPeriodHours
     */
    private fun cancelExpiredPendingInboundTxs() {
        val pendingInboundTxs = wallet.getPendingInboundTxs()
        val pendingInboundTxsLength = pendingInboundTxs.getLength()
        val now = DateTime.now().toLocalDateTime()
        for (i in 0 until pendingInboundTxsLength) {
            val tx = pendingInboundTxs.getAt(i)
            val txDate = DateTime(tx.getTimestamp().toLong() * 1000L).toLocalDateTime()
            val hoursPassed = Hours.hoursBetween(txDate, now).hours
            if (hoursPassed >= Constants.Wallet.pendingTxExpirationPeriodHours) {
                wallet.cancelPendingTx(tx.getId())
            }
            tx.destroy()
        }
        pendingInboundTxs.destroy()
    }

    /**
     * Cancels expired pending outbound transactions.
     * Expiration period is defined by Constants.Wallet.pendingTxExpirationPeriodHours
     */
    private fun cancelExpiredPendingOutboundTxs() {
        val pendingOutboundTxs = wallet.getPendingOutboundTxs()
        val pendingOutboundTxsLength = wallet.getPendingOutboundTxs().getLength()
        val now = DateTime.now().toLocalDateTime()
        for (i in 0 until pendingOutboundTxsLength) {
            val tx = pendingOutboundTxs.getAt(i)
            val txDate = DateTime(tx.getTimestamp().toLong() * 1000L).toLocalDateTime()
            val hoursPassed = Hours.hoursBetween(txDate, now).hours
            if (hoursPassed >= Constants.Wallet.pendingTxExpirationPeriodHours) {
                wallet.cancelPendingTx(tx.getId())
            }
            tx.destroy()
        }

        pendingOutboundTxs.destroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1

        object KeyValueStorageKeys {
            const val NETWORK = "SU7FM2O6Q3BU4XVN7HDD"
            const val version = "version"
        }
    }
}