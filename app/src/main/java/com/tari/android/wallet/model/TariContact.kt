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
package com.tari.android.wallet.model

import android.os.Parcel
import android.os.Parcelable
import com.tari.android.wallet.ui.extension.readP

/**
 * Contact is a user with an alias.
 *
 * @author The Tari Development Team
 */
class TariContact() : Parcelable {

    var walletAddress = TariWalletAddress()
    var isFavorite: Boolean = false
    var alias: String = ""

    constructor(
        tariWalletAddress: TariWalletAddress,
        alias: String = "",
        isFavorite: Boolean = false
    ) : this() {
        this.walletAddress = tariWalletAddress
        this.alias = alias
        this.isFavorite = isFavorite
    }

    fun filtered(text: String): Boolean = walletAddress.emojiId.contains(text, true) || alias.contains(text, true)

    override fun toString(): String = "Contact(alias='$alias') ${super.toString()}"

    // region Parcelable

    constructor(parcel: Parcel) : this() {
        readFromParcel(parcel)
    }

    companion object CREATOR : Parcelable.Creator<TariContact> {

        override fun createFromParcel(parcel: Parcel): TariContact {
            return TariContact(parcel)
        }

        override fun newArray(size: Int): Array<TariContact> {
            return Array(size) { TariContact() }
        }

    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(walletAddress, flags)
        parcel.writeString(alias)
        parcel.writeBoolean(isFavorite)
    }

    private fun readFromParcel(inParcel: Parcel) {
        walletAddress = inParcel.readP(TariWalletAddress::class.java)
        alias = inParcel.readString().orEmpty()
        isFavorite = inParcel.readBoolean()
    }

    override fun describeContents(): Int {
        return 0
    }

    // endregion

}