package com.tari.android.wallet.ui.fragment.send.addAmount

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.tari.android.wallet.R
import com.tari.android.wallet.extension.getWithError
import com.tari.android.wallet.ffi.FFIWallet
import com.tari.android.wallet.model.MicroTari
import com.tari.android.wallet.model.WalletError
import com.tari.android.wallet.ui.common.CommonViewModel
import com.tari.android.wallet.ui.dialog.modular.DialogArgs
import com.tari.android.wallet.ui.dialog.modular.ModularDialogArgs
import com.tari.android.wallet.ui.dialog.modular.modules.body.BodyModule
import com.tari.android.wallet.ui.dialog.modular.modules.button.ButtonModule
import com.tari.android.wallet.ui.dialog.modular.modules.button.ButtonStyle
import com.tari.android.wallet.ui.dialog.modular.modules.head.HeadModule
import com.tari.android.wallet.ui.fragment.send.addAmount.feeModule.FeeModule
import com.tari.android.wallet.ui.fragment.send.addAmount.feeModule.NetworkSpeed
import com.tari.android.wallet.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigInteger
import kotlin.math.min

class AddAmountViewModel : CommonViewModel() {

    private val _isOneSidePaymentEnabled: MutableLiveData<Boolean> = MutableLiveData()
    val isOneSidePaymentEnabled: LiveData<Boolean> = _isOneSidePaymentEnabled

    private val _feePerGrams = MutableLiveData<FeePerGramOptions>()
    val feePerGrams: LiveData<FeePerGramOptions> = _feePerGrams

    private val _serviceConnected: MutableLiveData<Unit> = MutableLiveData()
    val serviceConnected: LiveData<Unit> = _serviceConnected

    var selectedFeeData: FeeData? = null
    private var selectedSpeed: NetworkSpeed = NetworkSpeed.Medium

    private var feeData: List<FeeData> = listOf()

    init {
        component.inject(this)

        doOnConnected { _serviceConnected.postValue(Unit) }

        loadFees()
        _isOneSidePaymentEnabled.postValue(tariSettingsSharedRepository.isOneSidePaymentEnabled)
    }

    private fun loadFees() = doOnConnected {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stats = FFIWallet.instance!!.getFeePerGramStats()
                val elements = (0 until stats.getLength()).map { stats.getAt(it) }.toList()

                val elementsCount = min(stats.getLength(), 3)
                val slowOption: BigInteger
                val mediumOption: BigInteger
                val fastOption: BigInteger
                val networkSpeed: NetworkSpeed

                when (elementsCount) {
                    1 -> {
                        networkSpeed = NetworkSpeed.Slow
                        slowOption = elements[0].getMin()
                        mediumOption = elements[0].getAverage()
                        fastOption = elements[0].getMax()
                    }
                    2 -> {
                        networkSpeed = NetworkSpeed.Medium
                        slowOption = elements[1].getAverage()
                        mediumOption = elements[0].getMin()
                        fastOption = elements[0].getMax()
                    }
                    3 -> {
                        networkSpeed = NetworkSpeed.Fast
                        slowOption = elements[2].getAverage()
                        mediumOption = elements[1].getAverage()
                        fastOption = elements[0].getMax()
                    }
                    else -> throw Exception("Unexpected block count")
                }
                _feePerGrams.postValue(FeePerGramOptions(networkSpeed, MicroTari(slowOption), MicroTari(mediumOption), MicroTari(fastOption)))
            } catch (e: Throwable) {
                logger.e(e, "load fees")
            }
        }
    }

    fun toggleOneSidePayment() {
        val newValue = !tariSettingsSharedRepository.isOneSidePaymentEnabled
        tariSettingsSharedRepository.isOneSidePaymentEnabled = newValue
    }

    fun showFeeDialog() {
        val feeModule = FeeModule(MicroTari(), feeData, selectedSpeed)
        val args = ModularDialogArgs(
            DialogArgs(),
            listOf(
                HeadModule(resourceManager.getString(R.string.add_amount_modify_fee_title)),
                BodyModule(resourceManager.getString(R.string.add_amount_modify_fee_description)),
                feeModule,
                ButtonModule(resourceManager.getString(R.string.add_amount_modify_fee_use), ButtonStyle.Normal) {
                    selectedSpeed = feeModule.selectedSpeed
                    selectedFeeData = feeModule.feePerGram
                    dismissDialog.postValue(Unit)
                },
                ButtonModule(resourceManager.getString(R.string.common_cancel), ButtonStyle.Close)
            )
        )
        modularDialog.postValue(args)
    }

    fun calculateFee(amount: MicroTari, walletError: WalletError) {
        try {
            val grams = feePerGrams.value
            if (grams == null) {
                calculateDefaultFees(amount, walletError)
                return
            }

            val slowFee = walletService.getWithError(this::showFeeError) { _, wallet -> wallet.estimateTxFee(amount, walletError, grams.slow) }
            val mediumFee =
                walletService.getWithError(this::showFeeError) { _, wallet -> wallet.estimateTxFee(amount, walletError, grams.medium) }
            val fastFee = walletService.getWithError(this::showFeeError) { _, wallet -> wallet.estimateTxFee(amount, walletError, grams.fast) }

            if (slowFee == null || mediumFee == null || fastFee == null) {
                calculateDefaultFees(amount, walletError)
                return
            }

            feeData = listOf(FeeData(grams.slow, slowFee), FeeData(grams.medium, mediumFee), FeeData(grams.fast, fastFee))
            selectedFeeData = feeData[1]
        } catch (e: Throwable) {
            logger.e(e, "calculate fees")
        }
    }

    private fun calculateDefaultFees(amount: MicroTari, walletError: WalletError) {
        val calculatedFee = walletService.getWithError(this::showFeeError) { _, wallet ->
            wallet.estimateTxFee(amount, walletError, Constants.Wallet.defaultFeePerGram)
        } ?: return
        selectedFeeData = FeeData(Constants.Wallet.defaultFeePerGram, calculatedFee)
    }

    private fun showFeeError(walletError: WalletError) {
        if (walletError != WalletError.NoError) {
            showFeeError()
        }
    }

    private fun showFeeError() = Unit
}