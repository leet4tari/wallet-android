package com.tari.android.wallet.ui.fragment.restore.inputSeedWords

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.tari.android.wallet.R
import com.tari.android.wallet.databinding.ViewRecoveryWordBinding
import com.tari.android.wallet.ui.extension.setVisible

class WordTextView : FrameLayout {

    constructor(context: Context) : super(context, null) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    lateinit var ui: ViewRecoveryWordBinding

    fun init() {
        ui = ViewRecoveryWordBinding.inflate(LayoutInflater.from(context), this, false).apply {
            text.background = null
            text.setPadding(0, 0, 0, 0)
        }
        updateState(false)
        addView(ui.root)
    }

    fun updateState(isFocused: Boolean) {
        ui.removeView.setVisible(!isFocused)
        val background = if (isFocused) null else ContextCompat.getDrawable(context, R.drawable.restoring_seed_phrase_word_background)
        ui.root.background = background
    }
}