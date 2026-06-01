package com.example.data.repository

import com.example.data.dao.ReceiptDao
import com.example.data.entity.Receipt
import kotlinx.coroutines.flow.Flow

class ReceiptRepository(private val receiptDao: ReceiptDao) {
    val allReceipts: Flow<List<Receipt>> = receiptDao.getAllReceipts()
    val subscriptions: Flow<List<Receipt>> = receiptDao.getSubscriptions()

    suspend fun insertReceipt(receipt: Receipt): Long {
        return receiptDao.insertReceipt(receipt)
    }

    suspend fun updateReceipt(receipt: Receipt) {
        receiptDao.updateReceipt(receipt)
    }

    suspend fun deleteReceipt(receipt: Receipt) {
        receiptDao.deleteReceipt(receipt)
    }

    suspend fun deleteReceiptById(id: Long) {
        receiptDao.deleteReceiptById(id)
    }
}
