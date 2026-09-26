package com.pesaflow.app.ui.onboarding

import android.Manifest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.UniversityProfile
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.income.IncomeSourceStore
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.ui.income.IncomeSetupBlock
import com.pesaflow.app.ui.language.Copy4
import com.pesaflow.app.viewmodels.FinanceViewModel
import androidx.compose.ui.platform.LocalContext
import com.pesaflow.app.data.parsers.SmsScanResult
import com.pesaflow.app.data.parsers.buildDraft
import com.pesaflow.app.data.parsers.guessSemesterStart
import com.pesaflow.app.data.parsers.monthKey
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.data.parsers.Regime
import com.pesaflow.app.data.parsers.resolveCalendar
import com.pesaflow.app.data.parsers.scanRecentSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private val UNI_CHIPS = listOf("UoN", "KU", "JKUAT", "Maseno", "Egerton", "Other")
private const val DAY_MS = 24L * 60 * 60 * 1000


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(viewModel: FinanceViewModel, onDone: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    // Celebration beats the handoff: seeded → check + stars → home.
    var celebrate by rememberSaveable { mutableStateOf(false) }
    var welcomed by rememberSaveable { mutableStateOf(false) }

    // Grand opening gate: first impression, instructions, then the 4 steps.
    if (!welcomed) {
        GrandOpening(
            onBegin = { welcomed = true },
            onSkip = {
                viewModel.seedSampleData()
                onDone()
            }
        )
        return
    }

    // Step 1: identity
    var name by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var university by rememberSaveable { mutableStateOf("") }
    var campus by rememberSaveable { mutableStateOf("") }
    var semester by rememberSaveable { mutableStateOf("1") }

    // Step 2: funding
    var fundSource by rememberSaveable { mutableStateOf("HELB") }
    // HELB repeats every semester for most students — carry the tranches
    // forward instead of re-asking each term.
    var helbPerSem by rememberSaveable { mutableStateOf("Yes") }
    var helb1 by rememberSaveable { mutableStateOf("") }
    var helb2 by rememberSaveable { mutableStateOf("") }
    var pocket by rememberSaveable { mutableStateOf("") }
    var monthlyBudget by rememberSaveable { mutableStateOf("") }
    var foodBudget by rememberSaveable { mutableStateOf("") }
    var feesAmount by rememberSaveable { mutableStateOf("") }
    var endMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis() + 120 * DAY_MS) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = endMillis)

    // Step 3 (new): tell us about you — 10 quick questions that seed
    // budgets, goals and first-run advice. All optional, all skippable.
    var dailySpendGuess by rememberSaveable { mutableStateOf("") }
    var monthlyIncomeGuess by rememberSaveable { mutableStateOf("") }
    var sponsorMonthly by rememberSaveable { mutableStateOf("") }
    var rentGuess by rememberSaveable { mutableStateOf("") }
    var transportDaily by rememberSaveable { mutableStateOf("") }
    var cookStyle by rememberSaveable { mutableStateOf("Both") }
    var airtimeWeekly by rememberSaveable { mutableStateOf("") }
    var topWorry by rememberSaveable { mutableStateOf("Food") }
    var saveTarget by rememberSaveable { mutableStateOf("") }
    var inChama by rememberSaveable { mutableStateOf("No") }
    // Home setup wires the whole app: budgets, meal planner, transport.
    // Six real setups — rent is not universal, cooking moves Food most.
    var homeKind by rememberSaveable { mutableStateOf("Hostel") }
    var commuteLen by rememberSaveable { mutableStateOf("Near") }
    var cooksFood by rememberSaveable { mutableStateOf("Yes") }

    // Step 3: SMS priming
    var smsGranted by rememberSaveable { mutableStateOf(false) }
    val appContext = LocalContext.current
    val scanScope = rememberCoroutineScope()
    var scanResult by remember { mutableStateOf<SmsScanResult?>(null) }
    var scanProgress by remember { mutableStateOf(0) }
    // Transport-collapse months the detector proposes as break (one-tap confirm).
    var breakPrompt by remember { mutableStateOf<Set<String>?>(null) }
    // Scan-backfilled fields get upgraded by session-aware rhythms at finish
    // (user-typed values are never touched — flags tell them apart).
    var rentFromScan by rememberSaveable { mutableStateOf(false) }
    var transportFromScan by rememberSaveable { mutableStateOf(false) }
    // Explicit setup pick (enum name, "" = auto-detect). Stated beats derived.
    var personaOverride by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var scanQueued by rememberSaveable { mutableStateOf(0) }
    var showSmsRationale by rememberSaveable { mutableStateOf(false) }
    val smsPerm = rememberPermissionState(Manifest.permission.READ_SMS) { granted ->
        smsGranted = granted
    }
    // Rationale-first: explain, then ask. Cold prompts get auto-denied.
    fun askSms() {
        when {
            smsPerm.status.isGranted -> smsGranted = true
            smsPerm.status.shouldShowRationale -> showSmsRationale = true
            else -> smsPerm.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Karibu PesaPlanner 👋", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = {
                        viewModel.seedSampleData()
                        onDone()
                    }) { Text("Skip → sample data") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (step + 1) / 6f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                when (step) {
                    0 -> "Step 1 of 6 · Who are you?"
                    1 -> "Step 2 of 6 · Auto-tracking"
                    2 -> "Step 3 of 6 · Semester money"
                    3 -> "Step 4 of 6 · Income setup"
                    4 -> "Step 5 of 6 · Tell us about you"
                    else -> "Step 6 of 6 · Review & start"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Scan-first order: SMS auto-tracking lands on step 2 (the magic
            // moment — their own data populates the app), the questionnaire
            // moves to step 5. Finish logic is order-independent.
            val page = when (step) {
                1 -> 4
                4 -> 1
                else -> step
            }
            when (page) {
                0 -> {
                    StepArt(R.drawable.bg_university_campus)
                    Text("Kwanza, unaitwa nani? 🙂", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your name (e.g. Manu)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname, optional — what we call you daily (e.g. Comrade)") }, modifier = Modifier.fillMaxWidth())
                    Text("→ full name is only used when it is serious (warnings); daily talk uses the nickname.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("University", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UNI_CHIPS.take(3).forEach { u ->
                            FilterChip(selected = university == u, onClick = { university = u }, label = { Text(u) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UNI_CHIPS.drop(3).forEach { u ->
                            FilterChip(selected = university == u, onClick = { university = u }, label = { Text(u) })
                        }
                    }
                    OutlinedTextField(value = university, onValueChange = { university = it }, label = { Text("Or type university") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = campus, onValueChange = { campus = it }, label = { Text("Campus (e.g. Main)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = semester, onValueChange = { semester = it }, label = { Text("Current semester") }, modifier = Modifier.fillMaxWidth())
                }
                1 -> {
                    StepArt(R.drawable.ob_tellus)
                    Text("Tell us about you 📝", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "10 quick guesses — they pre-fill your budgets and goals. Skip anything, estimates are perfect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(value = dailySpendGuess, onValueChange = { dailySpendGuess = it }, label = { Text("1 · Spend in a day? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ becomes your monthly budget (×30) if you skip the money step.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = monthlyIncomeGuess, onValueChange = { monthlyIncomeGuess = it }, label = { Text("2 · Total in per month? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ opening upkeep in your ledger + the calculator's base.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = sponsorMonthly, onValueChange = { sponsorMonthly = it }, label = { Text("3 · From home/sponsors monthly? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ same — home money lands as ledger income you can track.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = rentGuess, onValueChange = { rentGuess = it }, label = { Text("4 · Rent/hostel per month? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ your Rent budget + Bills watch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = transportDaily, onValueChange = { transportDaily = it }, label = { Text("5 · Transport per day? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ Transport budget (×30) + commuter weight in the calculator.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Where do you stay? 🏠", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Parents", "Hostel", "Rented").forEach { h ->
                            FilterChip(
                                selected = homeKind == h,
                                onClick = { homeKind = h },
                                label = { Text(h) }
                            )
                        }
                    }
                    Text("Daily trip to campus?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Near", "Far").forEach { c ->
                            FilterChip(
                                selected = commuteLen == c,
                                onClick = { commuteLen = c },
                                label = { Text(if (c == "Far") "Far daily" else "Near / walk") }
                            )
                        }
                    }
                    Text("Do you cook?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Yes", "No").forEach { k ->
                            FilterChip(
                                selected = cooksFood == k,
                                onClick = { cooksFood = k },
                                label = { Text(if (k == "Yes") "I cook" else "I buy") }
                            )
                        }
                    }
                    Text(
                        if (homeKind == "Parents") "Home roof — budgets swap Rent for a small Home upkeep envelope."
                        else if (cooksFood == "No") "Bought meals cost most — Food gets protected first."
                        else if (commuteLen == "Far") "Long matatu daily — Transport becomes non-negotiable."
                        else "Light setup — more room for Savings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("6 · Mostly…?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Mostly cook", "Both", "Mostly buy").forEach { s ->
                            FilterChip(selected = cookStyle == s, onClick = { cookStyle = s }, label = { Text(s.take(8)) })
                        }
                    }
                    OutlinedTextField(value = airtimeWeekly, onValueChange = { airtimeWeekly = it }, label = { Text("7 · Airtime + data per week? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ your Airtime budget (×4).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("8 · Biggest money worry?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Fees", "Food", "Rent", "Saving").forEach { w ->
                            FilterChip(selected = topWorry == w, onClick = { topWorry = w }, label = { Text(w) })
                        }
                    }
                    OutlinedTextField(value = saveTarget, onValueChange = { saveTarget = it }, label = { Text("9 · Want to save monthly? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ creates your Monthly savings goal with a daily pace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("10 · In a chama?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("No", "Yes").forEach { c ->
                            FilterChip(selected = inChama == c, onClick = { inChama = c }, label = { Text(c) })
                        }
                    }
                    if (inChama == "Yes") {
                        Text("Nice — track it under More → Semester → Chama after setup. 🤝", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                2 -> {
                    StepArt(R.drawable.ob_money)
                    Text("Pesa ya semester 💰", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("HELB comes in tranches — enter what you expect, plus cash in pocket today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HELB", "SELF", "BOTH").forEach { s ->
                            FilterChip(
                                selected = fundSource == s,
                                onClick = { fundSource = s },
                                label = { Text(if (s == "SELF") "Self-sponsored" else s) }
                            )
                        }
                    }
                    if (fundSource != "SELF") {
                        OutlinedTextField(value = helb1, onValueChange = { helb1 = it }, label = { Text("HELB tranche 1 (KSh, optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = helb2, onValueChange = { helb2 = it }, label = { Text("HELB tranche 2 (KSh, optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Text("Same HELB every semester?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Yes", "No").forEach { h ->
                                FilterChip(selected = helbPerSem == h, onClick = { helbPerSem = h }, label = { Text(if (h == "Yes") "Yes, repeat" else "One-off") })
                            }
                        }
                        if (helbPerSem == "Yes") {
                            Text("Carried forward each term — no re-typing next semester.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("Self-sponsored 💪 — pocket cash + anything you log as income carries the semester.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(value = pocket, onValueChange = { pocket = it }, label = { Text("Pocket cash in hand (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ opening cash in your ledger — net worth, balance and planners all move.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = feesAmount, onValueChange = { feesAmount = it }, label = { Text("Fees owed (KSh, optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ fees bill + countdown. Part of HELB can cover it — we show the leftover.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = monthlyBudget, onValueChange = { monthlyBudget = it }, label = { Text("Monthly budget (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ your master budget — pace, alerts and safe-to-spend all read it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = foodBudget, onValueChange = { foodBudget = it }, label = { Text("Food budget (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ Food budget + the meal planner's spending figure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { showPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text("Semester ends: ${java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(endMillis))}")
                    }
                }
                3 -> {
                    StepArt(R.drawable.ob_income)
                    Text("Where does money come from? 💰", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "HELB, parents, hustle, job — declare each. HELB on M-Pesa? Fine, I'll parse SMS to see the amounts. Bank? Tell me which bank and the likely dates — not sure? You'll input manually, no stress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IncomeSetupBlock(viewModel = viewModel, highlightSelfSponsored = fundSource == "SELF")
                }
                4 -> {
                    StepArt(R.drawable.ob_sms)
                    Text("Track spending automatically ⚡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Allow SMS access and M-Pesa texts become pending transactions for you to confirm. Nothing enters your books unverified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { askSms() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text(if (smsGranted) "Enabled ✓" else "Enable SMS detection", color = MaterialTheme.colorScheme.onPrimary) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("First-run check: last 60 days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Reads your M-Pesa texts into pending approvals and uses the totals as editable starting figures. Nothing enters your books unverified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            if (!smsGranted) {
                                askSms()
                            } else {
                                // State stays on Main; only the inbox query drops to IO.
                                scanScope.launch {
                                    scanning = true
                                    scanProgress = 0
                                    val r = withContext(Dispatchers.IO) {
                                        // 5-month window: rhythms need history; the
                                        // session filter (at finish) quarantines break.
                                        scanRecentSms(appContext, 150, 1500, onProgress = { f, _ -> scanProgress = f })
                                    }
                                    var queued = 0
                                    r.parsed.forEach { if (viewModel.tryQueuePending(it)) queued++ }
                                    scanQueued = queued
                                    if (monthlyBudget.isBlank() && r.monthlyExpense > 0) monthlyBudget = r.monthlyExpense.toInt().toString()
                                    if (foodBudget.isBlank() && r.monthlyFor("Food") > 0) foodBudget = r.monthlyFor("Food").toInt().toString()
                                    if (rentGuess.isBlank() && r.monthlyFor("Rent") > 0) { rentGuess = r.monthlyFor("Rent").toInt().toString(); rentFromScan = true }
                                    if (transportDaily.isBlank() && r.monthlyFor("Transport") > 0) { transportDaily = (r.monthlyFor("Transport") / 30).toInt().toString(); transportFromScan = true }
                                    scanResult = r
                                    // Break proposal: collapsed-transport months surface
                                    // once for confirm-or-keep — never auto-excluded.
                                    val proposed = com.pesaflow.app.data.parsers.detectBreakMonths(
                                        com.pesaflow.app.data.parsers.monthlyTransportSeries(r.parsed)
                                    )
                                    if (proposed.isNotEmpty()) breakPrompt = proposed
                                    // Harvest the newest wallet balance for display everywhere.
                                    r.parsed.firstOrNull()?.rawText?.let { raw ->
                                        com.pesaflow.app.data.parsers.parseBalance(raw)?.let { bal ->
                                            com.pesaflow.app.data.parsers.saveMpesaBalance(appContext, bal)
                                        }
                                    }
                                    scanning = false
                                }
                            }
                        },
                        enabled = !scanning,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text(if (scanning) "Scanning... $scanProgress found" else if (scanResult == null) "Scan last 5 months" else "Rescan", color = MaterialTheme.colorScheme.onSecondary) }
                    scanResult?.let { r ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Found " + r.found + " texts, " + r.parsed.size + " readable", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("In KSh " + r.incomeTotal.toInt() + ", out KSh " + r.expenseTotal.toInt() + " over 150 days", style = MaterialTheme.typography.bodySmall)
                                Text("Monthly pace about KSh " + r.monthlyExpense.toInt() + ", food about KSh " + r.monthlyFor("Food").toInt(), style = MaterialTheme.typography.bodySmall)
                                Text(scanQueued.toString() + " queued to pending for Home approval. Placeholders only - edit anything.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Demo: what you'll see 👀", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("New SMS Detected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("KSh 250", fontWeight = FontWeight.Bold)
                            }
                    Text("Merchant: Kibanda", style = MaterialTheme.typography.bodySmall)
                    Text("Suggested: Food · Confirm / Ignore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                5 -> {
                    StepArt(R.drawable.ob_review)
                    Text("Review your setup ✅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Everything below becomes your default EVERYWHERE — budgets, net worth, bills, reports, planners. Go Back to edit anything; later, change it in Budgets / Profile. Nothing is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Same precedence as the finish block: typed > guess > M-Pesa scan.
                    val scan = scanResult
                    val revAll = monthlyBudget.toDoubleOrNull()?.takeIf { it > 0 }?.let { it to "you" }
                        ?: dailySpendGuess.toDoubleOrNull()?.takeIf { it > 0 }?.let { it * 30 to "guess" }
                        ?: scan?.monthlyExpense?.takeIf { it > 0 }?.let { it to "M-Pesa scan" }
                    val revFood = foodBudget.toDoubleOrNull()?.takeIf { it > 0 }?.let { it to "you" }
                        ?: scan?.monthlyFor("Food")?.takeIf { it > 0 }?.let { it to "M-Pesa scan" }
                    val revRent = rentGuess.toDoubleOrNull()?.takeIf { it > 0 }?.let { it to "you" }
                        ?: scan?.monthlyFor("Rent")?.takeIf { it > 0 }?.let { it to "M-Pesa scan" }
                    val revTransport = transportDaily.toDoubleOrNull()?.takeIf { it > 0 }?.let { it * 30 to "you" }
                        ?: scan?.monthlyFor("Transport")?.takeIf { it > 0 }?.let { it to "M-Pesa scan" }
                    val revAirtime = airtimeWeekly.toDoubleOrNull()?.takeIf { it > 0 }?.let { it * 4 to "you" }
                    val revPocket = pocket.toDoubleOrNull()?.takeIf { it > 0 }
                    val revUpkeep = sponsorMonthly.toDoubleOrNull()?.takeIf { it > 0 }
                        ?: monthlyIncomeGuess.toDoubleOrNull()?.takeIf { it > 0 }
                    val revSave = saveTarget.toDoubleOrNull()?.takeIf { it > 0 }
                    val revFees = feesAmount.toDoubleOrNull()?.takeIf { it > 0 }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Defaults from your answers", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            ReviewRow("Monthly budget", revAll?.first?.let { "KSh " + it.toInt() } ?: "—", revAll?.second ?: "skip")
                            ReviewRow("Food budget", revFood?.first?.let { "KSh " + it.toInt() } ?: "—", revFood?.second ?: "skip")
                            ReviewRow("Rent", revRent?.first?.let { "KSh " + it.toInt() } ?: "—", revRent?.second ?: "skip")
                            ReviewRow("Transport", revTransport?.first?.let { "KSh " + it.toInt() } ?: "—", revTransport?.second ?: "skip")
                            ReviewRow("Airtime", revAirtime?.first?.let { "KSh " + it.toInt() } ?: "—", revAirtime?.second ?: "skip")
                            ReviewRow("Pocket cash → ledger", revPocket?.let { "KSh " + it.toInt() } ?: "—", if (revPocket != null) "you" else "skip")
                            ReviewRow("Monthly upkeep → ledger", revUpkeep?.let { "KSh " + it.toInt() } ?: "—", if (revUpkeep != null) "you" else "skip")
                            ReviewRow("Savings goal", revSave?.let { "KSh " + it.toInt() } ?: "—", if (revSave != null) "you" else "skip")
                            ReviewRow("Fees bill", revFees?.let { "KSh " + it.toInt() } ?: "—", if (revFees != null) "you" else "skip")
                            val revSources = viewModel.incomeSources.value
                            ReviewRow(
                                "Income sources",
                                if (revSources.isEmpty()) "—" else revSources.size.toString() + " (" + revSources.joinToString(", ") { it.displayKind() } + ")",
                                if (revSources.isEmpty()) "skip" else "KSh " + revSources.sumOf { IncomeSourceStore.budgetedMonthly(it) }.toInt() + " expected"
                            )
                            ReviewRow("Reports", "Night · Sunday · Daily", "auto-armed")
                            val revPersona = com.pesaflow.app.ui.budgets.parsePersona(
                                "home=" + homeKind.uppercase() + "|commute=" + commuteLen.uppercase() + "|cooking=" + if (cooksFood == "Yes") "YES" else "NO"
                            )
                            val effPersona = personaOverride.takeIf { it.isNotBlank() }?.let {
                                com.pesaflow.app.ui.budgets.Persona.valueOf(it)
                            } ?: revPersona
                            ReviewRow("Setup", effPersona.label, if (personaOverride.isBlank()) effPersona.blurb + " · auto" else "your pick")
                            // One-tap correction: six researched setups, detected one
                            // preselected. Tapping the current pick clears back to auto.
                            com.pesaflow.app.ui.budgets.Persona.values().forEach { p ->
                                TextButton(onClick = { personaOverride = if (personaOverride == p.name) "" else p.name }) {
                                    Text(
                                        (if (effPersona == p) "✓ " else "○ ") + p.label + " — " + p.blurb,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (effPersona == p) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    // Safe-to-spend preview so day one never feels like guessing.
                    revAll?.first?.let { all ->
                        val calR = java.util.Calendar.getInstance()
                        val dimR = calR.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                        val domR = calR.get(java.util.Calendar.DAY_OF_MONTH)
                        val leftR = (dimR - domR + 1).coerceAtLeast(1)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("How safe-to-spend works 👀", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Day 1: KSh " + (all / leftR).toInt() + "/day safe (" + leftR + " days left of KSh " + all.toInt() + "). " +
                                        "Every expense you log lowers tomorrow's number — watch it move on Home.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (showSmsRationale) {
                AlertDialog(
                    onDismissRequest = { showSmsRationale = false },
                    title = { Text("Why SMS access?", fontWeight = FontWeight.Bold) },
                    text = { Text("Your M-Pesa texts become pending rows you confirm — that is the whole auto-tracking magic. No access, no magic. You can still log everything by hand.") },
                    confirmButton = { TextButton(onClick = { showSmsRationale = false; smsPerm.launchPermissionRequest() }) { Text("Allow SMS") } },
                    dismissButton = { TextButton(onClick = { showSmsRationale = false }) { Text("Not now") } }
                )
            }

            // Break confirmation: one tap excludes dead months from every
            // average; Keep leaves them training. Nothing auto-excludes.
            breakPrompt?.let { months ->
                AlertDialog(
                    onDismissRequest = { breakPrompt = null },
                    title = { Text("Break months?", fontWeight = FontWeight.Bold) },
                    text = { Text("Transport nearly vanished in ${months.sorted().joinToString(", ")} — long break, strike, or time away? Exclude them so they never warp your budgets.") },
                    confirmButton = {
                        TextButton(onClick = {
                            appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putStringSet("confirmed_break_months", months).apply()
                            breakPrompt = null
                        }) { Text("Exclude") }
                    },
                    dismissButton = { TextButton(onClick = { breakPrompt = null }) { Text("Keep") } }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (step > 0) step-- }, enabled = step > 0) { Text("Back") }
                Button(
                    onClick = {
                        if (step < 5) {
                            step++
                        } else {
                            val now = System.currentTimeMillis()
                            viewModel.setUserName(name)
                            viewModel.setNickname(nickname)
                            viewModel.saveUniversityProfile(
                                UniversityProfile(
                                    universityName = university.trim(),
                                    campus = campus.trim(),
                                    currentSemester = semester.toIntOrNull() ?: 1,
                                    academicYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString(),
                                    semesterStartTimestamp = now,
                                    semesterEndTimestamp = endMillis,
                                    startingFunding = pocket.toDoubleOrNull() ?: 0.0,
                                    helbExpected = if (fundSource == "SELF") 0.0 else (helb1.toDoubleOrNull() ?: 0.0) + (helb2.toDoubleOrNull() ?: 0.0),
                                    fundingSource = fundSource,
                                    feesAmount = feesAmount.toDoubleOrNull() ?: 0.0,
                                    feesDueDate = endMillis
                                )
                            )
                            // Smart seeding: HELB tranches + sponsor answers become Income
                            // sources, so More → Income, budgets and Buddy know payday
                            // without re-asking on opening. Never duplicates: kinds
                            // already declared (above or earlier) are left alone.
                            val helbTotal = if (fundSource == "SELF") 0.0
                            else (helb1.toDoubleOrNull() ?: 0.0) + (helb2.toDoubleOrNull() ?: 0.0)
                            val existingKinds = viewModel.incomeSources.value.map { it.kind }.toSet()
                            val seededIncome = mutableListOf<IncomeSource>()
                            if (helbTotal > 0 && "HELB_MPESA" !in existingKinds && "HELB_BANK" !in existingKinds) {
                                // Tranches land per semester (~4 months): monthly share keeps budget math honest.
                                seededIncome.add(IncomeSource(kind = "HELB_MPESA", label = "HELB upkeep", expectedAmount = helbTotal / 4, frequency = "MONTHLY", autoTrack = true))
                            }
                            sponsorMonthly.toDoubleOrNull()?.takeIf { it > 0 }?.let { sp ->
                                if ("PARENT" !in existingKinds && "GUARDIAN" !in existingKinds) {
                                    seededIncome.add(IncomeSource(kind = "GUARDIAN", label = "Sponsor", expectedAmount = sp, frequency = "MONTHLY"))
                                }
                            }
                            if (seededIncome.isNotEmpty()) {
                                viewModel.setIncomeSources(viewModel.incomeSources.value + seededIncome)
                            }
                            monthlyBudget.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("ALL", it, BudgetType.MONTHLY)
                            }
                            foodBudget.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("Food", it, BudgetType.MONTHLY)
                            }
                            // Seed from "tell us about you" guesses (only where the
                            // money step above didn't already set the same budget).
                            val dailyGuess = dailySpendGuess.toDoubleOrNull()?.takeIf { it > 0 }
                            if (monthlyBudget.toDoubleOrNull() == null && dailyGuess != null) {
                                viewModel.upsertBudget("ALL", dailyGuess * 30, BudgetType.MONTHLY)
                            }
                            rentGuess.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("Rent", it, BudgetType.MONTHLY)
                            }
                            transportDaily.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("Transport", it * 30, BudgetType.MONTHLY)
                            }
                            airtimeWeekly.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("Airtime", it * 4, BudgetType.MONTHLY)
                            }
                            saveTarget.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.addSavingsGoal("Monthly savings", it, 30)
                            }
                            // Session-aware rhythm upgrade: scan-backfilled (or blank)
                            // Transport/Rent are re-derived from fare/anchor rhythms
                            // trained on in-session months only — break months never
                            // train anything. User-typed values are sacred.
                            val semGuess = guessSemesterStart(university, endMillis, now)
                            val semCal = resolveCalendar(semGuess, endMillis, now, firstYear = (semester.toIntOrNull() ?: 1) <= 1)
                            // User-confirmed dead months (break dialog) leave the
                            // averages even when they fall inside stated spans.
                            val breakOverrides = appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                                .getStringSet("confirmed_break_months", emptySet()) ?: emptySet()
                            val draft = scanResult?.let { scan ->
                                buildDraft(
                                    scan.parsed
                                        .filter { semCal.regimeOf(it.dateTimestamp) == Regime.SESSION }
                                        .filter { monthKey(it.dateTimestamp) !in breakOverrides }
                                        .map { LedgerRow(it.amount, it.type, it.category, it.merchant, it.dateTimestamp) },
                                    isClassDay = { ts ->
                                        val d = java.util.Calendar.getInstance().apply { timeInMillis = ts }
                                            .get(java.util.Calendar.DAY_OF_WEEK)
                                        d in java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY
                                    }
                                )
                            }
                            if ((transportDaily.toDoubleOrNull() == null || transportFromScan) && draft?.transportMonthly != null && draft.transportMonthly > 0) {
                                transportDaily = (draft.transportMonthly / 30).toInt().toString()
                                viewModel.upsertBudget("Transport", draft.transportMonthly, BudgetType.MONTHLY)
                            }
                            if ((rentGuess.toDoubleOrNull() == null || rentFromScan) && draft?.rentMonthly != null && draft.rentMonthly > 0) {
                                rentGuess = draft.rentMonthly.toInt().toString()
                                viewModel.upsertBudget("Rent", draft.rentMonthly, BudgetType.MONTHLY)
                            }
                            // First-run M-Pesa figures: gentle re-adjustment where the user left blanks.
                            scanResult?.let { scan ->
                                if (monthlyBudget.toDoubleOrNull() == null && scan.monthlyExpense > 0) {
                                    viewModel.upsertBudget("ALL", scan.monthlyExpense, BudgetType.MONTHLY)
                                }
                                if (foodBudget.toDoubleOrNull() == null && scan.monthlyFor("Food") > 0) {
                                    viewModel.upsertBudget("Food", scan.monthlyFor("Food"), BudgetType.MONTHLY)
                                }
                            }
                            // From word go, everything works as a unit: arm the report loop
                            // (night + Sunday + daily digest + monthly) and seed the fees bill
                            // so Semester, Bills, Reports and planners all have true data.
                            val nprefs = appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                            nprefs.edit()
                                .putBoolean("night_report", true)
                                .putBoolean("sunday_report", true)
                                .putBoolean("daily_summary", true)
                                .putBoolean("lunch_scan", true)
                                .apply()
                            ReminderScheduler.scheduleNightReport(appContext)
                            ReminderScheduler.scheduleSundayReport(appContext)
                            ReminderScheduler.scheduleDailyDigest(appContext)
                            ReminderScheduler.scheduleMonthlyReport(appContext)
                            ReminderScheduler.scheduleDaily(appContext)
                            ReminderScheduler.scheduleLunchScan(appContext)
                            val feesAmt = feesAmount.toDoubleOrNull()?.takeIf { it > 0 }
                            if (feesAmt != null && viewModel.bills.value.none { it.name == "Semester fees" && it.status != "PAID" }) {
                                viewModel.addBill("Semester fees", feesAmt, endMillis, "School", "ONE_TIME")
                            }
                            // Opening money hits the ledger: pocket cash + monthly upkeep
                            // become INCOME rows so budgets, net worth, reports, planners update.
                            val upkeep = sponsorMonthly.toDoubleOrNull()?.takeIf { it > 0 }
                                ?: monthlyIncomeGuess.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
                            viewModel.seedOpeningMoney(
                                pocket.toDoubleOrNull() ?: 0.0,
                                upkeep
                            )
                            viewModel.saveOnboardingAnswers(
                                "daily=$dailySpendGuess|income=$monthlyIncomeGuess|sponsor=$sponsorMonthly" +
                                    "|rent=$rentGuess|transport=$transportDaily|cook=$cookStyle" +
                                    "|airtime=$airtimeWeekly|worry=$topWorry|save=$saveTarget|chama=$inChama" +
                                    "|living=" + if (commuteLen == "Far" && homeKind != "Parents") "COMMUTER" else "HOSTEL" +
                                    "|home=" + homeKind.uppercase() +
                                    "|commute=" + commuteLen.uppercase() +
                                    "|cooking=" + if (cooksFood == "Yes") "YES" else "NO" +
                                    "|persona=" + (personaOverride.takeIf { it.isNotBlank() } ?: com.pesaflow.app.ui.budgets.parsePersona(
                                        "home=" + homeKind.uppercase() + "|commute=" + commuteLen.uppercase() + "|cooking=" + if (cooksFood == "Yes") "YES" else "NO"
                                    ).name) +
                                    "|helb_per_sem=" + helbPerSem +
                                    (scanResult?.let { s -> "|scan_income=" + s.incomeTotal.toInt() + "|scan_expense=" + s.expenseTotal.toInt() + "|scan_food=" + s.monthlyFor("Food").toInt() } ?: "") +
                                    (draft?.let { d -> "|ded_transport=" + (d.transportMonthly?.toInt() ?: 0) + "|ded_rent=" + (d.rentMonthly?.toInt() ?: 0) + "|ded_recur=" + d.recurring.size } ?: "") +
                                    (breakOverrides.takeIf { it.isNotEmpty() }?.let { "|break_excluded=" + it.sorted().joinToString(",") } ?: "")
                            )
                            celebrate = true
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text(if (step < 5) "Next" else "Start Tracking 💰", fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Setup-complete celebration over everything, then home.
    if (celebrate) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                com.pesaflow.app.ui.motion.SuccessBurst(onDone = onDone)
                Text("Karibu nyumbani 🎉", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { endMillis = it }
                    showPicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}


// Grand opening page: full-bleed backdrop, welcome, what the app does for
// you, what happens next (4 quick steps), and the honest permissions note.
// House voice is Mixed — the user hasn't picked a language yet.
@Composable
private fun GrandOpening(onBegin: () -> Unit, onSkip: () -> Unit) {
    val lang = AppLanguage.MIXED
    fun t(en: String, sw: String, sh: String, mix: String) =
        Copy4(en, sw, sh, mix).pick(lang)
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_opening),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060D1A).copy(alpha = 0.55f),
                        Color(0xFF060D1A).copy(alpha = 0.15f),
                        Color(0xFF060D1A).copy(alpha = 0.88f)
                    )
                )
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(48.dp))
                Box(
                    Modifier.width(56.dp).height(4.dp)
                        .background(Color(0xFFD4AF37), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    t("Karibu PesaPlanner.", "Karibu PesaPlanner.", "Karibu PesaPlanner.", "Karibu PesaPlanner."),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    t(
                        "Your semester money, finally in one place. We read your M-Pesa texts so you never type them.",
                        "Pesa za muhula, sehemu moja. Tunasoma SMS zako za M-Pesa usiandike.",
                        "Mullah ya sem, place moja. Tuna-read texts zako za M-Pesa — hu-type kitu.",
                        "Pesa za sem ziko place moja. Tuna-read M-Pesa texts zako so hu-type."
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                OpeningRow("📩", t("Auto-tracking", "Kufuatilia", "Auto-track", "Auto-tracking"), t("M-Pesa texts become ledger rows. You just confirm.", "SMS za M-Pesa zinakuwa records. Wewe unaconfirm tu.", "Texts za M-Pesa zinakuwa rows. Confirm tu.", "M-Pesa texts zinakuwa rows — confirm tu."))
                OpeningRow("🎯", t("Budgets that update", "Bajeti zinazo-update", "Budget live", "Budgets live"), t("What you enter now flows into budgets, net worth and planners.", "Unachoingiza sasa kinaingia bajeti, net worth na planners.", "Kile unaingiza sai kinaingia budget, net worth na planners.", "Kile unaingiza sai kinaingia budgets, net worth, planners."))
                OpeningRow("🗣️", t("Your language", "Lugha yako", "Lugha yako", "Lugha yako"), t("English, Kiswahili, Sheng or Mix — reports included.", "Kingereza, Kiswahili, Sheng ama Mix — ripoti pia.", "English, Swahili, Sheng ama Mix — repoti pia.", "English, Swahili, Sheng ama Mix — reports pia."))
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    t(
                        "Next: 4 quick steps — you, your life, semester money, auto-tracking. ~2 minutes. Skip anything.",
                        "Ifuatayo: hatua 4 rahisi — wewe, maisha, pesa za muhula, auto-tracking. Dakika 2. Ruka chochote.",
                        "Next: steps 4 fasta — wewe, life, mullah ya sem, auto-track. 2 mins. Skip chochote.",
                        "Next: steps 4 quick — wewe, life, pesa za sem, auto-tracking. 2 mins. Skip anything."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                    t(
                        "Heads up: we will ask for SMS + notification access so auto-tracking works.",
                        "Kumbuka: tutaomba ruhusa ya SMS na notifications ili auto-tracking ifanye.",
                        "Heads up: tutaomba SMS + notifications ndio auto-track ifanye.",
                        "Heads up: tutaomba SMS + notifications ndio auto-tracking ifanye."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD4AF37)
                )
                Text(
                    t(
                        "Built for all six setups — home or hostel, walking or long commutes, cooks and buyers alike.",
                        "Imejengwa kwa wote sita — nyumbani ama hostel, kutembea ama safari ndefu, wapishi na wanunuzi.",
                        "Imetengenezwa kwa hali zote sita — nyumbani au hosteli, kutembea au safari ndefu, wapishi na wanunuzi.",
                        "Built for setups zote six — home or hostel, walking or long commutes, cooks na buyers."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Column {
                Button(
                    onClick = onBegin,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37), contentColor = Color(0xFF0A2342))
                ) { Text(t("Begin — 2 mins", "Anza — dakika 2", "Begin — 2 mins", "Begin — 2 mins"), fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text(t("Skip → explore with sample data", "Ruka → sample data", "Skip → sample data", "Skip → sample data"), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}


// Step illustration: full-width photo banner that makes each onboarding
// step feel designed instead of form-like. Light-tinted assets, safe in
// both themes.
@Composable
private fun StepArt(bg: Int) {
    Image(
        painter = painterResource(id = bg),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)),
        contentScale = ContentScale.Crop
    )
    Spacer(modifier = Modifier.height(12.dp))
}


@Composable
private fun OpeningRow(emoji: String, title: String, body: String) {    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}


@Composable
private fun ReviewRow(label: String, value: String, tag: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "$value · $tag",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (tag == "skip") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}
