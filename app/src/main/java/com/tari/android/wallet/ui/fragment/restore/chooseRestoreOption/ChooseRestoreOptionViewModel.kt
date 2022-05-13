package com.tari.android.wallet.ui.fragment.restore.chooseRestoreOption

import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.orhanobut.logger.Logger
import com.tari.android.wallet.R
import com.tari.android.wallet.application.WalletManager
import com.tari.android.wallet.application.WalletState
import com.tari.android.wallet.event.EventBus
import com.tari.android.wallet.infrastructure.backup.*
import com.tari.android.wallet.model.WalletError
import com.tari.android.wallet.service.WalletServiceLauncher
import com.tari.android.wallet.ui.common.CommonViewModel
import com.tari.android.wallet.ui.common.SingleLiveEvent
import com.tari.android.wallet.ui.dialog.error.ErrorDialogArgs
import com.tari.android.wallet.ui.dialog.error.WalletErrorArgs
import com.tari.android.wallet.ui.fragment.settings.backup.data.BackupOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

internal class ChooseRestoreOptionViewModel : CommonViewModel() {

    @Inject
    lateinit var backupManager: BackupManager

    @Inject
    lateinit var walletManager: WalletManager

    @Inject
    lateinit var walletServiceLauncher: WalletServiceLauncher

    private var currentOption: BackupOptions? = null

    init {
        component.inject(this)
    }

    private val _state = SingleLiveEvent<ChooseRestoreOptionState>()
    val state: LiveData<ChooseRestoreOptionState> = _state

    private val _navigation = SingleLiveEvent<ChooseRestoreOptionNavigation>()
    val navigation: LiveData<ChooseRestoreOptionNavigation> = _navigation

    fun startRestore(options: BackupOptions) {
        currentOption = options
        _state.postValue(ChooseRestoreOptionState.BeginProgress(options))
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupManager.onSetupActivityResult(requestCode, resultCode, data)
                restoreFromBackup()
            } catch (exception: Exception) {
                Logger.e("Backup storage setup failed: $exception")
                backupManager.signOut()
                _state.postValue(ChooseRestoreOptionState.EndProgress(currentOption!!))
                showAuthFailedDialog()
            }
        }
    }

    private suspend fun restoreFromBackup() {
        try {
            // try to restore with no password
            backupManager.restoreLatestBackup()
            EventBus.walletState.subscribe(this) {
                when (it) {
                    WalletState.Initializing,
                    WalletState.NotReady -> Unit
                    WalletState.Running -> _navigation.postValue(ChooseRestoreOptionNavigation.OnRestoreCompleted)
                    is WalletState.Failed -> viewModelScope.launch(Dispatchers.IO) {
                        handleException(WalletStartFailedException(it.exception))
                    }
                }
            }
            viewModelScope.launch(Dispatchers.Main) {
                walletServiceLauncher.start()
            }
        } catch (exception: Exception) {
            handleException(exception)
        }
    }

    private suspend fun handleException(exception: java.lang.Exception) {
        when (exception) {
            is BackupStorageAuthRevokedException -> {
                Logger.e(exception, "Auth revoked.")
                backupManager.signOut()
                showAuthFailedDialog()
            }
            is BackupStorageTamperedException -> { // backup file not found
                Logger.e(exception, "Backup file not found.")
                backupManager.signOut()
                showBackupFileNotFoundDialog()
            }
            is BackupFileIsEncryptedException -> {
                //todo check for launch wallet after relaunch app
                _navigation.postValue(ChooseRestoreOptionNavigation.ToEnterRestorePassword)
            }
            is WalletStartFailedException -> {
                Logger.e(exception, "Restore failed: wallet start failed")
                viewModelScope.launch(Dispatchers.Main) {
                    walletServiceLauncher.stopAndDelete()
                }
                val cause = WalletError.createFromException(exception.cause)
                if (cause == WalletError.DatabaseDataError) {
                    showRestoreFailedDialog(resourceManager.getString(R.string.restore_wallet_error_file_not_supported))
                } else if (cause != WalletError.NoError) {
                    _modularDialog.postValue(WalletErrorArgs(resourceManager, cause).getErrorArgs().getModular(resourceManager))
                } else {
                    showRestoreFailedDialog(exception.cause?.message)
                }
            }
            is IOException -> {
                Logger.e(exception, "Restore failed: network connection.")
                backupManager.signOut()
                showRestoreFailedDialog(resourceManager.getString(R.string.error_no_connection_title))
            }
            else -> {
                Logger.e(exception, "Restore failed: $exception")
                backupManager.signOut()
                showRestoreFailedDialog(exception.message)
            }
        }

        _state.postValue(ChooseRestoreOptionState.EndProgress(currentOption!!))
    }

    private fun showBackupFileNotFoundDialog() {
        val args = ErrorDialogArgs(
            resourceManager.getString(R.string.restore_wallet_error_title),
            resourceManager.getString(R.string.restore_wallet_error_file_not_found),
            onClose = { _backPressed.call() })
        _modularDialog.postValue(args.getModular(resourceManager))
    }

    private fun showRestoreFailedDialog(message: String? = null) {
        val args = ErrorDialogArgs(
            resourceManager.getString(R.string.restore_wallet_error_title),
            message ?: resourceManager.getString(R.string.restore_wallet_error_desc)
        )
        _modularDialog.postValue(args.getModular(resourceManager))
    }

    private fun showAuthFailedDialog() {
        val args = ErrorDialogArgs(
            resourceManager.getString(R.string.restore_wallet_error_title),
            resourceManager.getString(R.string.back_up_wallet_storage_setup_error_desc)
        )
        _modularDialog.postValue(args.getModular(resourceManager))
    }
}