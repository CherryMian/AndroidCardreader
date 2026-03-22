package com.yumian.emvreader

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CardInfo(
    val type: String,
    val pan: String,
    val expiry: String,
    val standard: String,
    val transactions: List<CardTransaction> = emptyList(),
    val aid: String = "",
    val applicationLabel: String = "",
    val cardHolder: String = "",
    val leftPinTry: String = "",
    val atrDescription: String = ""
) : Parcelable

@Parcelize
data class CardTransaction(
    val date: String,
    val time: String,
    val amount: String,
    val currency: String,
    val type: String
) : Parcelable

