package com.tari.android.wallet.ui.fragment.contact_book.root

import android.Manifest
import androidx.lifecycle.MutableLiveData
import com.tari.android.wallet.R
import com.tari.android.wallet.application.deeplinks.DeepLink
import com.tari.android.wallet.application.deeplinks.DeeplinkHandler
import com.tari.android.wallet.application.deeplinks.DeeplinkViewModel
import com.tari.android.wallet.infrastructure.bluetooth.TariBluetoothClient
import com.tari.android.wallet.infrastructure.bluetooth.TariBluetoothServer
import com.tari.android.wallet.infrastructure.nfc.TariNFCAdapter
import com.tari.android.wallet.ui.common.CommonViewModel
import com.tari.android.wallet.ui.common.SingleLiveEvent
import com.tari.android.wallet.ui.dialog.modular.DialogArgs
import com.tari.android.wallet.ui.dialog.modular.ModularDialogArgs
import com.tari.android.wallet.ui.dialog.modular.modules.body.BodyModule
import com.tari.android.wallet.ui.dialog.modular.modules.button.ButtonModule
import com.tari.android.wallet.ui.dialog.modular.modules.button.ButtonStyle
import com.tari.android.wallet.ui.dialog.modular.modules.head.HeadModule
import com.tari.android.wallet.ui.dialog.modular.modules.icon.IconModule
import com.tari.android.wallet.ui.fragment.contact_book.data.ContactsRepository
import com.tari.android.wallet.ui.fragment.contact_book.root.share.ShareType
import com.tari.android.wallet.ui.fragment.send.shareQr.ShareQrCodeModule
import javax.inject.Inject

class ShareViewModel : CommonViewModel() {

    @Inject
    lateinit var tariBluetoothClient: TariBluetoothClient

    @Inject
    lateinit var tariBluetoothServer: TariBluetoothServer

    @Inject
    lateinit var tariNFCAdapter: TariNFCAdapter

    @Inject
    lateinit var deeplinkHandler: DeeplinkHandler

    @Inject
    lateinit var contactsRepository: ContactsRepository

    val deeplinkViewModel = DeeplinkViewModel()

    val shareText = SingleLiveEvent<String>()

    val shareInfo = MutableLiveData<String>()

    val launchPermissionCheck = SingleLiveEvent<List<String>>()

    init {
        currentInstant = this
        component.inject(this)
        tariBluetoothServer.onReceived = this::onReceived
        tariBluetoothClient.onSuccessSharing = this::showShareSuccessDialog
        tariBluetoothClient.onFailedSharing = this::showShareErrorDialog

        tariNFCAdapter.onReceived = this::onReceived
        tariNFCAdapter.onSuccessSharing = this::showShareSuccessDialog
        tariNFCAdapter.onFailedSharing = this::showShareErrorDialog
    }

    fun share(type: ShareType, deeplink: String) {
        shareInfo.postValue(deeplink)
        when (type) {
            ShareType.QR_CODE -> doShareViaQrCode(deeplink)
            ShareType.LINK -> doShareViaLink(deeplink)
            ShareType.NFC -> doShareViaNFC()
            ShareType.BLE -> doShareViaBLE()
        }
    }

    fun startBLESharing() {
        val args = ModularDialogArgs(
            DialogArgs(canceledOnTouchOutside = false, cancelable = false) { tariBluetoothClient.stopSharing() }, listOf(
                IconModule(R.drawable.vector_sharing_via_ble),
                HeadModule(resourceManager.getString(R.string.share_via_bluetooth_title)),
                BodyModule(resourceManager.getString(R.string.share_via_bluetooth_message)),
                ButtonModule(resourceManager.getString(R.string.common_close), ButtonStyle.Close)
            )
        )
        modularDialog.postValue(args)
        tariBluetoothClient.startSharing(shareInfo.value.orEmpty())
    }

    private fun doShareViaQrCode(deeplink: String) {
        val args = ModularDialogArgs(
            DialogArgs(true, canceledOnTouchOutside = true), listOf(
                HeadModule(resourceManager.getString(R.string.share_via_qr_code_title)),
                ShareQrCodeModule(deeplink),
                ButtonModule(resourceManager.getString(R.string.common_close), ButtonStyle.Close)
            )
        )
        modularDialog.postValue(args)
    }

    private fun doShareViaLink(deeplink: String) {
        shareText.postValue(deeplink)
        showShareSuccessDialog()
    }

    private fun doShareViaNFC() {
        if (!tariNFCAdapter.isNFCAvailable()) {
            tariNFCAdapter.showNFCSettings()
            return
        }
        val args = ModularDialogArgs(
            DialogArgs(canceledOnTouchOutside = false, cancelable = false) { tariNFCAdapter.stopSharing() }, listOf(
                IconModule(R.drawable.vector_sharing_via_nfc),
                HeadModule(resourceManager.getString(R.string.share_via_nfc_title)),
                BodyModule(resourceManager.getString(R.string.share_via_nfc_message)),
                ButtonModule(resourceManager.getString(R.string.common_close), ButtonStyle.Close)
            )
        )
        modularDialog.postValue(args)
        tariNFCAdapter.startSharing(shareInfo.value.orEmpty())
    }

    private fun doShareViaBLE() {
        val permissions = (tariBluetoothServer.bluetoothPermissions + tariBluetoothServer.locationPermission).distinct().toMutableList()
        permissions.remove(Manifest.permission.BLUETOOTH)
        launchPermissionCheck.postValue(permissions)
    }

    private fun showShareSuccessDialog() {
        val args = ModularDialogArgs(
            DialogArgs(), listOf(
                IconModule(R.drawable.vector_sharing_success),
                HeadModule(resourceManager.getString(R.string.share_success_title)),
                BodyModule(resourceManager.getString(R.string.share_success_message)),
                ButtonModule(resourceManager.getString(R.string.common_close), ButtonStyle.Close)
            )
        )
        modularDialog.postValue(args)
    }

    private fun showShareErrorDialog(message: String) {
        val args = ModularDialogArgs(
            DialogArgs(), listOf(
                IconModule(R.drawable.vector_sharing_failed),
                HeadModule(resourceManager.getString(R.string.common_error_title)),
                BodyModule(message),
                ButtonModule(resourceManager.getString(R.string.common_close), ButtonStyle.Close)
            )
        )
        modularDialog.postValue(args)
    }

    private fun onReceived(data: List<DeepLink.Contacts.DeeplinkContact>) {
        deeplinkViewModel.addContacts(data)
    }

    companion object {
        var currentInstant: ShareViewModel? = null
            private set
    }
}