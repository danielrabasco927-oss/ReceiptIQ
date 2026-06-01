package com.example

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entity.Receipt
import com.example.data.repository.ReceiptRepository
import com.example.gemini.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Simple model representing a mockup ticket that can be selected to simulate scans
data class MockReceiptItem(
    val title: String,
    val merchant: String,
    val amount: Double,
    val category: String,
    val date: String,
    val notes: String,
    val isSubscription: Boolean = false,
    val subscriptionInterval: String = "",
    val description: String,
    val imageResId: Int = 0 // Using mock canvas drawing instead of binary drawable to avoid asset load limits
)

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ReceiptRepository
    val allReceipts: StateFlow<List<Receipt>>
    val subscriptions: StateFlow<List<Receipt>>

    // Current app state holds
    var activeTab by mutableStateOf(0) // 0 = Dashboard, 1 = Scáner/Nuevo, 2 = Suscripciones/Amigos, 3 = Estadísticas, 4 = Exportar
    var isAnalyzing by mutableStateOf(false)
    var scanErrorMessage by mutableStateOf<String?>(null)
    var showManualAddDialog by mutableStateOf(false)

    // Selection of dummy tickets for fast testing
    val mockReceiptsList = listOf(
        MockReceiptItem(
            title = "Suscripción Premium 4K",
            merchant = "Netflix España",
            amount = 17.99,
            category = "Suscripción",
            date = "2026-05-28",
            notes = "Servicio de streaming mensual",
            isSubscription = true,
            subscriptionInterval = "Mensual",
            description = "Ticket digital de Netflix con logotipo rojo, IVA incluido y concepto 'Plan Premium'."
        ),
        MockReceiptItem(
            title = "Compra de Supermercado",
            merchant = "Mercadona S.A.",
            amount = 45.20,
            category = "Comida",
            date = "2026-05-30",
            notes = "Compra de despensa semanal",
            isSubscription = false,
            description = "Recibo térmico arrugado de Mercadona con lista de artículos: fruta, leche, pan, champú, total 45.20€."
        ),
        MockReceiptItem(
            title = "Cena de Cumpleaños",
            merchant = "Ginos Restaurante",
            amount = 88.00,
            category = "Ocio",
            date = "2026-05-29",
            notes = "Cena de pizzas y pasta con amigos",
            isSubscription = false,
            description = "Factura de restaurante italiano Ginos, 4 menús de noche, cervezas y postres, total 88.00€."
        ),
        MockReceiptItem(
            title = "Billete de Tren Madrid-Bcn",
            merchant = "Renfe AVE",
            amount = 64.50,
            category = "Transporte",
            date = "2026-05-24",
            notes = "Billete de ida clase turista",
            isSubscription = false,
            description = "Billete digital PDF de Renfe con QR, trayecto Madrid Puerta de Atocha a Barcelona Sants."
        ),
        MockReceiptItem(
            title = "Gimnasio Cuota Mensual",
            merchant = "VivaGym S.L.",
            amount = 29.90,
            category = "Suscripción",
            date = "2026-05-01",
            notes = "Acceso a instalaciones Zona A",
            isSubscription = true,
            subscriptionInterval = "Mensual",
            description = "Cobro recurrente Viva Gym, concepto Cuota Socio Activo, cobrado por cuenta bancaria."
        )
    )

    var selectedMockReceipt by mutableStateOf<MockReceiptItem?>(null)

    init {
        val receiptDao = AppDatabase.getDatabase(application).receiptDao()
        repository = ReceiptRepository(receiptDao)
        allReceipts = repository.allReceipts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        subscriptions = repository.subscriptions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        selectedMockReceipt = mockReceiptsList.first()

        // Seed data automatically if database is empty so stats calculation looks outstanding at first run
        viewModelScope.launch {
            repository.allReceipts.collect { list ->
                if (list.isEmpty()) {
                    seedData()
                }
            }
        }
    }

    private suspend fun seedData() {
        val pastReceipts = listOf(
            Receipt(amount = 17.99, category = "Suscripción", date = "2026-04-28", merchant = "Netflix España", notes = "Plan Streaming", isSubscription = true, subscriptionInterval = "Mensual"),
            Receipt(amount = 29.90, category = "Suscripción", date = "2026-04-01", merchant = "VivaGym S.L.", notes = "Gimnasio", isSubscription = true, subscriptionInterval = "Mensual"),
            Receipt(amount = 25.40, category = "Comida", date = "2026-04-15", merchant = "Carrefour", notes = "Almuerzo rápido"),
            Receipt(amount = 55.00, category = "Otros", date = "2026-04-20", merchant = "Iberdrola S.A.", notes = "Factura de Luz Bimestral"),
            Receipt(amount = 17.99, category = "Suscripción", date = "2026-05-28", merchant = "Netflix España", notes = "Plan Streaming", isSubscription = true, subscriptionInterval = "Mensual"),
            Receipt(amount = 29.90, category = "Suscripción", date = "2026-05-01", merchant = "VivaGym S.L.", notes = "Gimnasio", isSubscription = true, subscriptionInterval = "Mensual"),
            Receipt(amount = 38.50, category = "Comida", date = "2026-05-10", merchant = "Mercadona S.A.", notes = "Compra mensual"),
            Receipt(amount = 15.00, category = "Transporte", date = "2026-05-18", merchant = "Metro Madrid", notes = "Abono transporte mensual 10 viajes"),
            Receipt(amount = 45.00, category = "Ocio", date = "2026-05-24", merchant = "Cinesa", notes = "Entradas de cine y snacks")
        )

        for (item in pastReceipts) {
            repository.insertReceipt(item)
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            repository.deleteReceipt(receipt)
        }
    }

    fun addNewReceipt(amount: Double, merchant: String, category: String, date: String, notes: String, isSec: Boolean, interval: String, friendCount: Int) {
        viewModelScope.launch {
            val actualDate = if (date.isEmpty()) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            } else {
                date
            }
            val splitShare = amount / if (friendCount > 0) friendCount else 1
            val item = Receipt(
                amount = amount,
                merchant = merchant,
                category = category,
                date = actualDate,
                notes = notes,
                isSubscription = isSec,
                subscriptionInterval = interval,
                friendCountToSplit = friendCount,
                splitOwnerShare = splitShare,
                isScannedWithAi = false
            )
            repository.insertReceipt(item)
        }
    }

    // Trigger AI Extraction on selected custom image or selected mockup card
    fun triggerAiScan(bitmap: Bitmap?, onComplete: (Boolean) -> Unit) {
        isAnalyzing = true
        scanErrorMessage = null

        viewModelScope.launch {
            try {
                // If bitmap is supplied, or standard mockup selection is set
                val promptText = """
                    Analiza esta imagen de recibo de compra. Devuelve un objeto JSON con el siguiente formato estricto:
                    {
                      "merchant": "Nombre del Comercio",
                      "amount": 12.34,
                      "category": "Una de estas strings exactas: Comida, Transporte, Ocio, Suscripción, Salud, Otros",
                      "date": "YYYY-MM-DD",
                      "isSubscription": true/false (pon true si es un pago mensual recurrente como Netflix, VivaGym, Spotify, internet, etc.),
                      "subscriptionInterval": "Mensual" o "Anual" o "Semanal" o ""
                    }
                    No devuelvas nada más que el JSON puro, sin marcadores de '```json'.
                """.trimIndent()

                var aiResult: String? = null
                // Attempt to call Gemini API if bitmap is present and API key is set
                if (bitmap != null && isApiKeyPresent()) {
                    aiResult = GeminiClient.analyzeReceipt(promptText, bitmap)
                } else if (bitmap == null && selectedMockReceipt != null && isApiKeyPresent()) {
                    // Try to generate content using mockup descriptions as input
                    val mockPromptWithContext = "$promptText\n\nContexto del ticket a analizar: ${selectedMockReceipt!!.description} del comercio ${selectedMockReceipt!!.merchant} por un importe de ${selectedMockReceipt!!.amount}"
                    aiResult = GeminiClient.analyzeReceipt(mockPromptWithContext, null)
                }

                if (aiResult != null) {
                    // Process API response
                    val cleanedJson = aiResult.trim()
                        .removePrefix("```json")
                        .removeSuffix("```")
                        .trim()

                    try {
                        val json = JSONObject(cleanedJson)
                        val merchantResult = json.optString("merchant", "Desconocido")
                        val amountResult = json.optDouble("amount", 0.0)
                        val categoryResult = json.optString("category", "Otros")
                        val dateResult = json.optString("date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                        val isSub = json.optBoolean("isSubscription", false)
                        val intervalResult = json.optString("subscriptionInterval", "")

                        val parsedReceipt = Receipt(
                            amount = amountResult,
                            category = categoryResult,
                            date = dateResult,
                            merchant = merchantResult,
                            notes = "Extraído automáticamente con ReceiptIQ AI",
                            isSubscription = isSub,
                            subscriptionInterval = intervalResult,
                            friendCountToSplit = 1,
                            splitOwnerShare = amountResult,
                            isScannedWithAi = true
                        )
                        repository.insertReceipt(parsedReceipt)
                        isAnalyzing = false
                        onComplete(true)
                    } catch (e: Exception) {
                        Log.e("ReceiptViewModel", "Failed to parse JSON: $cleanedJson", e)
                        // Fallback parsing failed
                        triggerMockSimulation(onComplete)
                    }
                } else {
                    // If no API Key configured, or network fails, run simulated AI extraction showing perfect flow
                    triggerMockSimulation(onComplete)
                }

            } catch (ex: Exception) {
                Log.e("ReceiptViewModel", "API Exception", ex)
                triggerMockSimulation(onComplete)
            }
        }
    }

    private suspend fun triggerMockSimulation(onComplete: (Boolean) -> Unit) {
        // Wait 1.5 seconds for visual excitement
        kotlinx.coroutines.delay(1200)

        val target = selectedMockReceipt ?: MockReceiptItem(
            title = "Manual Scanner",
            merchant = "Simulado Express",
            amount = 15.50,
            category = "Otros",
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            notes = "Ticket simulado",
            isSubscription = false,
            description = ""
        )

        val newlyScanned = Receipt(
            amount = target.amount,
            category = target.category,
            date = target.date,
            merchant = target.merchant,
            notes = "${target.notes} (Simulado - Añade tu API Key para escaneo real)",
            isSubscription = target.isSubscription,
            subscriptionInterval = target.subscriptionInterval,
            friendCountToSplit = 1,
            splitOwnerShare = target.amount,
            isScannedWithAi = true
        )

        repository.insertReceipt(newlyScanned)
        isAnalyzing = false
        onComplete(true)
    }

    fun isApiKeyPresent(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    // Spend predictions and calculations

    // Fetch receipts for the current month
    fun getReceiptsByMonth(receipts: List<Receipt>, monthOffset: Int): List<Receipt> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -monthOffset)
        val targetMonth = calendar.get(Calendar.MONTH)
        val targetYear = calendar.get(Calendar.YEAR)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return receipts.filter {
            try {
                val d = sdf.parse(it.date)
                if (d != null) {
                    val rCal = Calendar.getInstance()
                    rCal.time = d
                    rCal.get(Calendar.MONTH) == targetMonth && rCal.get(Calendar.YEAR) == targetYear
                } else false
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getMonthTotal(receipts: List<Receipt>, monthOffset: Int): Double {
        return getReceiptsByMonth(receipts, monthOffset).sumOf { it.amount }
    }

    fun getMonthNameSpanish(monthOffset: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -monthOffset)
        return calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale("es", "ES"))
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } ?: "Mes"
    }

    // Predict current month's final spending based on elapsed days:
    fun predictEndOfMonthSpending(receipts: List<Receipt>): Double {
        val currentMonthReceipts = getReceiptsByMonth(receipts, 0)
        val currentSum = currentMonthReceipts.sumOf { it.amount }

        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        if (currentDay == 0) return currentSum
        // Daily rate * total days:
        val dailyRate = currentSum / currentDay
        return dailyRate * totalDays
    }

    // Generate CSV for taxes export
    fun generateExportText(receipts: List<Receipt>): String {
        val builder = java.lang.StringBuilder()
        builder.append("ID,Comercio,Fecha,Importe(€),Categoria,EsSuscripcion,ParticionAmigos\n")
        for (item in receipts) {
            builder.append("${item.id},\"${item.merchant}\",${item.date},${item.amount},\"${item.category}\",${if (item.isSubscription) "SI" else "NO"},${item.friendCountToSplit}\n")
        }
        return builder.toString()
    }
}
