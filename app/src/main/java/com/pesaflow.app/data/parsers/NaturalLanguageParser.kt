package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.*
import java.util.Locale


object NaturalLanguageParser {


    fun parse(input: String): PendingTransaction? {
        // Last parseable token wins ("2 chapo 100" → 100). Understands
        // "1,250", "2k", "KSh 250" via the shared AmountParser.
        val derivedAmount = com.pesaflow.app.data.ledger.AmountParser.parseLast(input)
            ?: return null
        var derivedType = TransactionType.EXPENSE
        var matchedCategory = "Other"
        var matchedKeyword = false


        // Detect operational context semantic cues
        val inputLower = input.lowercase(Locale.getDefault())
        when {
            inputLower.contains("received") || inputLower.contains("nimetumiwa") || inputLower.contains("salary") || inputLower.contains("helb") || inputLower.contains("bonus") || inputLower.contains("refund") || inputLower.contains("allowance") -> {
                derivedType = TransactionType.INCOME
            }
            inputLower.contains("saved") || inputLower.contains("nimeweka") || inputLower.contains("savings") || inputLower.contains("chama") || inputLower.contains("mshwari") -> {
                derivedType = TransactionType.SAVING
            }
        }


        // Segment mapping evaluation parameters
        val catKeywords = mapOf(
            "Food" to listOf("food", "lunch", "dinner", "supper", "breakfast", "kibanda", "chips", "chapati", "mutura", "pilau", "ugali", "githeri", "eat", "kula", "chakula", "sherehe"),
            "Transport" to listOf("fare", "matatu", "mat", "bodaboda", "boda", "uber", "bolt", "stage", "train", "nauli", "parking"),
            "Airtime" to listOf("airtime", "credit", "safari", "credo", "bonga"),
            "Data" to listOf("bundles", "data", "net", "wi-fi", "wifi", "faiba", "unliminet"),
            "Printing" to listOf("printing", "print", "cyber", "photocopy", "assignment", "stationery"),
            "Shopping" to listOf("shopping", "supermarket", "naivas", "quickmart", "carrefour", "market", "duka", "nunua"),
            "Rent" to listOf("rent", "hostel", "house", "pango", "nyumba"),
            "School" to listOf("fees", "school", "tuition", "exam", "books", "shule", "kalamu"),
            "Electricity" to listOf("kplc", "token", "tokens", "stima", "electricity"),
            "Water" to listOf("water", "maji"),
            "Clothes" to listOf("clothes", "shirt", "shoe", "dress", "jacket", "jeans", "nguo", "kiatu"),
            "Kujibamba" to listOf("salon", "barber", "kinyozi", "hair", "nails", "plot", "movie", "game"),
            "Health" to listOf("hospital", "clinic", "pharmacy", "chemist", "medicine", "dawa", "daktari"),
            "Savings" to listOf("chama", "mshwari", "savings", "save")
        )


        outerLoop@ for ((category, keywords) in catKeywords) {
            for (keyword in keywords) {
                if (inputLower.contains(keyword)) {
                    matchedCategory = category
                    matchedKeyword = true
                    break@outerLoop
                }
            }
        }


        // Isolate dynamic target merchant signatures
        val merchant = when (matchedCategory) {
            "Food" -> "Food Joint/Kiosk"
            "Transport" -> "Matatu/Boda Stage"
            "Airtime" -> "Safaricom Airtime"
            "Data" -> "Internet Provider"
            "Shopping" -> "Shop/Market"
            "Rent" -> "Landlord/Hostel"
            "School" -> "School/College"
            "Electricity" -> "KPLC"
            "Water" -> "Water Vendor"
            "Clothes" -> "Clothes Shop"
            "Kujibamba" -> "Salon/Plot"
            "Health" -> "Clinic/Pharmacy"
            "Savings" -> "Savings Pot"
            else -> "General Merchant"
        }


        // Backdate words: "yesterday lunch 200" lands on yesterday's ledger.
        val dayMs = 24L * 60 * 60 * 1000
        val backDays = when {
            inputLower.contains("day before") || inputLower.contains("juzi") -> 2
            inputLower.contains("yesterday") || inputLower.contains("jana") -> 1
            else -> 0
        }
        val stamp = System.currentTimeMillis() - backDays * dayMs


        return PendingTransaction(
            amount = derivedAmount,
            type = derivedType,
            category = matchedCategory,
            merchant = merchant,
            dateTimestamp = stamp,
            paymentMethod = if (inputLower.contains("mpesa")) PaymentMethod.MPESA else PaymentMethod.CASH,
            source = TransactionSource.NLP,
            sourceTransactionId = null,
            rawText = input,
            // Honest confidence: keyword hit = high, fallback guess = review me.
            confidenceScore = if (matchedKeyword && matchedCategory != "Other") 0.9f else 0.55f
        )
    }
}