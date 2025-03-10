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
import com.tari.android.wallet.ffi.FFICompletedTx
import com.tari.android.wallet.ffi.FFITxCancellationReason
import com.tari.android.wallet.ui.extension.readP
import com.tari.android.wallet.ui.extension.readS
import java.math.BigInteger

/**
 * Canceled tx model class.
 *
 * @author The Tari Development Team
 */
class CancelledTx() : Tx(), Parcelable {

    var fee: MicroTari = MicroTari(BigInteger("0"))
    var cancellationReason: FFITxCancellationReason = FFITxCancellationReason.NotCancelled

    constructor(tx: FFICompletedTx) : this() {
        this.id = tx.getId()
        this.direction = tx.getDirection()
        this.tariContact = tx.getContact()
        this.amount = MicroTari(tx.getAmount())
        this.fee = MicroTari(tx.getFee())
        this.timestamp = tx.getTimestamp()
        this.message = tx.getMessage()
        this.status = TxStatus.map(tx.getStatus())
        this.cancellationReason = tx.getCancellationReason()
        tx.destroy()
    }

    // region Parcelable

    constructor(parcel: Parcel) : this() {
        id = parcel.readS(BigInteger::class.java)
        direction = parcel.readS(Direction::class.java)
        tariContact = parcel.readP(TariContact::class.java)
        amount = parcel.readP(MicroTari::class.java)
        fee = parcel.readP(MicroTari::class.java)
        timestamp = parcel.readS(BigInteger::class.java)
        message = parcel.readString().orEmpty()
        status = parcel.readP(TxStatus::class.java)
    }

    override fun toString(): String = "CanceledTx(fee=$fee, status=$status) ${super.toString()}"

    companion object CREATOR : Parcelable.Creator<CancelledTx> {

        override fun createFromParcel(parcel: Parcel): CancelledTx = CancelledTx(parcel)

        override fun newArray(size: Int): Array<CancelledTx?> = arrayOfNulls(size)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(id)
        parcel.writeSerializable(direction)
        parcel.writeSerializable(tariContact.javaClass)
        parcel.writeParcelable(tariContact, flags)
        parcel.writeParcelable(amount, flags)
        parcel.writeParcelable(fee, flags)
        parcel.writeSerializable(timestamp)
        parcel.writeString(message)
        parcel.writeSerializable(status)
    }

    override fun describeContents(): Int = 0

    // endregion

}