package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String, // "Comida", "Transporte", "Ocio", "Suscripción", "Salud", "Otros"
    val date: String, // YYYY-MM-DD
    val merchant: String,
    val notes: String = "",
    val isSubscription: Boolean = false,
    val subscriptionInterval: String = "", // "Mensual", "Anual", "Semanal"
    val friendCountToSplit: Int = 1, // 1 means only self
    val splitOwnerShare: Double = amount,
    val isScannedWithAi: Boolean = true
)
