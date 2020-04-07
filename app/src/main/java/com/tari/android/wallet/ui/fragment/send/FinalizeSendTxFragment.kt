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
package com.tari.android.wallet.ui.fragment.send

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import android.widget.TextView
import android.widget.VideoView
import butterknife.BindString
import butterknife.BindView
import com.airbnb.lottie.LottieAnimationView
import com.daasuu.ei.Ease
import com.daasuu.ei.EasingInterpolator
import com.orhanobut.logger.Logger
import com.tari.android.wallet.R
import com.tari.android.wallet.event.Event
import com.tari.android.wallet.event.EventBus
import com.tari.android.wallet.extension.applyFontStyle
import com.tari.android.wallet.model.MicroTari
import com.tari.android.wallet.model.User
import com.tari.android.wallet.model.WalletError
import com.tari.android.wallet.model.WalletErrorCode
import com.tari.android.wallet.service.TariWalletService
import com.tari.android.wallet.ui.component.CustomFont
import com.tari.android.wallet.ui.extension.invisible
import com.tari.android.wallet.ui.extension.visible
import com.tari.android.wallet.ui.fragment.BaseFragment
import com.tari.android.wallet.ui.util.UiUtil.getResourceUri
import com.tari.android.wallet.util.Constants
import com.tari.android.wallet.util.WalletUtil
import org.matomo.sdk.Tracker
import org.matomo.sdk.extra.TrackHelper
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * Displays the successful outgoing transaction animation.
 *
 * @author The Tari Development Team
 */
class FinalizeSendTxFragment(private val walletService: TariWalletService)
    : BaseFragment(), Animator.AnimatorListener {

    @BindView(R.id.finalize_send_tx_vw_root)
    lateinit var rootView: View
    @BindView(R.id.finalize_send_tx_video_bg)
    lateinit var videoView: VideoView
    @BindView(R.id.finalize_send_tx_anim)
    lateinit var lottieAnimationView: LottieAnimationView
    @BindView(R.id.finalize_send_tx_txt_info)
    lateinit var infoTextView: TextView
    @BindView(R.id.finalize_send_tx_vw_info_container)
    lateinit var infoContainerView: View
    @BindString(R.string.finalize_send_tx_sending_info_format)
    lateinit var sendingInfoFormat: String
    @BindString(R.string.finalize_send_tx_sending_info_format_bold_part)
    lateinit var sendingInfoFormatBoldPart: String
    @BindString(R.string.finalize_send_tx_sucessful_info_format)
    lateinit var successfulInfoFormat: String
    @BindString(R.string.finalize_send_tx_sucessful_info_format_bold_part)
    lateinit var successfulInfoFormatBoldPart: String

    @Inject
    lateinit var tracker: Tracker

    /**
     * Tx properties.
     */
    private lateinit var recipientUser: User
    private lateinit var amount: MicroTari
    private lateinit var fee: MicroTari
    private lateinit var note: String

    private lateinit var listenerWR: WeakReference<Listener>
    private val wr = WeakReference(this)
    private lateinit var successfulInfoSpannable: SpannableString
    private val lottieAnimationPauseProgress = 0.3f

    private var successful = false
    private var sendingIsInProgress = false

    override val contentViewId: Int = R.layout.fragment_finalize_send_tx

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // prepare fonts for partial bold text
        val mActivity = activity ?: return

        // get tx properties
        recipientUser = arguments!!.getParcelable("recipientUser")!!
        amount = arguments!!.getParcelable("amount")!!
        fee = arguments!!.getParcelable("fee")!!
        note = arguments!!.getString("note")!!

        // format spannable string
        val formattedAmount = if (amount.tariValue.toDouble() % 1 == 0.toDouble()) {
            amount.tariValue.toBigInteger().toString()
        } else {
            WalletUtil.amountFormatter.format(amount.tariValue)
        }

        val sendingInfo = String.format(sendingInfoFormat, formattedAmount)
        val sendingInfoBoldPart = String.format(sendingInfoFormatBoldPart, formattedAmount)
        infoTextView.text = sendingInfo.applyFontStyle(
            mActivity,
            CustomFont.AVENIR_LT_STD_LIGHT,
            sendingInfoBoldPart,
            CustomFont.AVENIR_LT_STD_BLACK
        )
        infoTextView.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )
        infoTextView.invisible()

        val successfulInfo = String.format(successfulInfoFormat, formattedAmount)
        val successfulInfoBoldPart = String.format(successfulInfoFormatBoldPart, formattedAmount)
        successfulInfoSpannable = successfulInfo.applyFontStyle(
            mActivity,
            CustomFont.AVENIR_LT_STD_LIGHT,
            successfulInfoBoldPart,
            CustomFont.AVENIR_LT_STD_BLACK
        )

        lottieAnimationView.setMaxProgress(lottieAnimationPauseProgress)
        lottieAnimationView.addAnimatorListener(this)

        rootView.postDelayed(
            {
                wr.get()?.lottieAnimationView?.playAnimation()
                wr.get()?.playTextAppearAnimation()
            },
            Constants.UI.SendTxSuccessful.lottieAnimStartDelayMs
        )

        subscribeToEventBus()
        TrackHelper.track()
            .screen("/home/send_tari/finalize")
            .title("Send Tari - Finalize")
            .with(tracker)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listenerWR = WeakReference(context as Listener)
    }

    override fun onStart() {
        super.onStart()
        val mActivity = activity ?: return
        videoView.setVideoURI(mActivity.getResourceUri(R.raw.sending_background))
        videoView.setOnPreparedListener { mp -> mp.isLooping = true }
        videoView.start()
    }

    override fun onStop() {
        videoView.stopPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        lottieAnimationView.duration
        lottieAnimationView.removeAllAnimatorListeners()
        EventBus.unsubscribe(this)
        super.onDestroy()
    }

    private fun subscribeToEventBus() {
        EventBus.subscribe<Event.Wallet.DiscoveryComplete>(this) { event ->
            wr.get()?.rootView?.post {
                wr.get()?.onDiscoveryComplete(event.success)
            }
        }
    }

    private fun playTextAppearAnimation() {
        infoTextView.translationY = infoTextView.height.toFloat()
        infoTextView.visible()

        ObjectAnimator.ofFloat(
            infoTextView,
            "translationY",
            infoTextView.height.toFloat(),
            0f
        ).apply {
            duration = Constants.UI.longDurationMs
            interpolator = EasingInterpolator(Ease.QUART_IN_OUT)
            startDelay = Constants.UI.SendTxSuccessful.textAppearAnimStartDelayMs
            start()
        }
    }

    private fun sendTari() {
        listenerWR.get()?.sendTxStarted(this)
        sendingIsInProgress = true
        val error = WalletError()
        val success = walletService.sendTari(recipientUser, amount, fee, note, error)
        // if success, just wait for the callback to happen
        // if failed, just show the failed info & return
        if (!success || error.code != WalletErrorCode.NO_ERROR) {
            rootView.post {
                onFailure()
            }
        }
    }

    private fun onDiscoveryComplete(success: Boolean) {
        Logger.d("Discovery completed with result: $success.")
        if (success) {
            onSuccess()
        } else {
            onFailure()
        }
    }

    private fun onSuccess() {
        successful = true
        infoTextView.text = successfulInfoSpannable
        lottieAnimationView.setMaxProgress(1.0f)
        lottieAnimationView.playAnimation()
        lottieAnimationView.progress = lottieAnimationPauseProgress
        ObjectAnimator.ofFloat(
            infoTextView,
            "alpha",
            1f,
            0f
        ).apply {
            duration = Constants.UI.longDurationMs
            interpolator = EasingInterpolator(Ease.QUART_IN_OUT)
            startDelay = Constants.UI.SendTxSuccessful.successfulInfoFadeOutAnimStartDelayMs
            start()
        }
    }

    private fun onFailure() {
        successful = false
        lottieAnimationView.speed = -1f
        lottieAnimationView.addAnimatorListener(this)
        lottieAnimationView.playAnimation()
        lottieAnimationView.progress = lottieAnimationPauseProgress
        ObjectAnimator.ofFloat(
            infoTextView,
            "alpha",
            1f,
            0f
        ).apply {
            duration = Constants.UI.xLongDurationMs
            interpolator = EasingInterpolator(Ease.QUART_IN_OUT)
            start()
        }
    }

    // region listener for the Lottie animation
    override fun onAnimationStart(animation: Animator?) {
        // no-op
    }

    override fun onAnimationRepeat(animation: Animator?) {
        // no-op
    }

    override fun onAnimationCancel(animation: Animator?) {
        // no-op
    }

    override fun onAnimationEnd(animation: Animator?) {
        if (!sendingIsInProgress) {
            Thread {
                sendTari()
            }.start()
            return
        }
        lottieAnimationView.alpha = 0f
        listenerWR.get()?.sendTxCompleted(
            this,
            recipientUser,
            amount,
            fee,
            note,
            success = successful
        )
    }
    //endregion Animator Listener

    /**
     * Listener interface - to be implemented by the host activity.
     */
    interface Listener {

        fun sendTxStarted(sourceFragment: FinalizeSendTxFragment)

        /**
         * Recipient is user.
         */
        fun sendTxCompleted(
            sourceFragment: FinalizeSendTxFragment,
            recipientUser: User,
            amount: MicroTari,
            fee: MicroTari,
            note: String,
            success: Boolean
        )

    }

}