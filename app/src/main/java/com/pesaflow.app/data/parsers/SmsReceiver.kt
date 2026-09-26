package com.pesaflow.app.data.parsers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.notifications.NotificationHelper
import com.pesaflow.app.data.notifications.handleDetectedTransaction
import com.pesaflow.app.data.repositories.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (msg in messages) {
                val body = msg.messageBody ?: continue
                val sender = msg.originatingAddress ?: continue


                // Wallet balance rides on raw bodies — harvest even when parsing fails.
                parseBalance(body)?.let { saveMpesaBalance(context, it) }

                // Explicit validation targeting transactional communication headers.
                // MPESA = money moves; Safaricom = bundles/airtime notices without a code;
                // banks + telcos ride on sender names (bodies often omit them).
                if (MpesaParser.isTransactionalSender(sender) || body.contains("Confirmed")) {
                    val pendingTx = MpesaParser.parseMessage(body, sender)
                    if (pendingTx != null) {
                        scope.launch {
                            handleDetectedTransaction(context, pendingTx)
                        }
                    }
                }
            }
        }
    }
}