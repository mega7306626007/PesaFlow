package com.pesaflow.app.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.prefs.AppPrefs
import com.pesaflow.app.ui.NavRoutes
import com.pesaflow.app.ui.theme.categoryEmoji
import com.pesaflow.app.ui.theme.ExplainChip
import com.pesaflow.app.ui.theme.toKSh
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.AtmoType
import com.pesaflow.app.ui.theme.HeroFinanceCard
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintDashboardForest
import com.pesaflow.app.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onQuickAdd: (TransactionType) -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val availableBalance by viewModel.availableBalance.collectAsState()
    val mpesaBal by viewModel.mpesaBalance.collectAsState()
    val cashBal by viewModel.cashBalance.collectAsState()
    val bankBal by viewModel.bankBalance.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val pendingTransactions by viewModel.pendingTransactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val hideBalances by viewModel.hideBalances.collectAsState()
    val hiddenSections by viewModel.hiddenSections.collectAsState()
    val profile by viewModel.universityProfile.collectAsState()

    // Weekday pattern defaults to THIS week — all-time is opt-in, never the
    // default. A 5000-SMS history must not masquerade as "this week".
    var weekdayScope by remember { mutableStateOf("week") }
    val weekdaySpending = remember(transactions, weekdayScope) {
        val cal = Calendar.getInstance()
        val weekStart = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val map = mutableMapOf("Mon" to 0.0, "Tue" to 0.0, "Wed" to 0.0, "Thu" to 0.0, "Fri" to 0.0, "Sat" to 0.0, "Sun" to 0.0)
        transactions.filter {
            it.type == TransactionType.EXPENSE && !it.isSample &&
                (weekdayScope == "all" || it.dateTimestamp >= weekStart)
        }.forEach { tx ->
            cal.timeInMillis = tx.dateTimestamp
            val dayStr = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                Calendar.SUNDAY -> "Sun"
                else -> null
            }
            if (dayStr != null) {
                map[dayStr] = (map[dayStr] ?: 0.0) + tx.amount
            }
        }
        map
    }


    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var showAllPending by remember { mutableStateOf(false) }
    var ledgerFilter by remember { mutableStateOf<String?>(null) }
    var showCustomize by remember { mutableStateOf(false) }
    // First-run coach: 3 steps, then never again (typed DataStore flag).
    val coachContext = LocalContext.current
    val coachDoneByPrefs by AppPrefs.coachDone(coachContext).collectAsState(initial = null)
    var coachStep by remember(coachDoneByPrefs) { mutableStateOf(if (coachDoneByPrefs == true) 99 else 0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current


    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintDashboardForest, bgRes = R.drawable.bg_dashboard_forest)
        Scaffold(
            containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("PesaPlanner Hub ⚡", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showCustomize = true }) { Text("Tune") }
                    TextButton(onClick = { viewModel.setHideBalances(!hideBalances) }) {
                        Text(if (hideBalances) "Show" else "Hide")
                    }
                    IconButton(onClick = { onNavigate(NavRoutes.SEARCH) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search transactions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        when (currentLanguage) {
                            AppLanguage.ENGLISH -> "EN"
                            AppLanguage.KISWAHILI -> "SW"
                            AppLanguage.SHENG -> "SH"
                            else -> "MIX"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            // Global Add lives in MainActivity Scaffold — no duplicate FAB here.
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(Modifier.height(PesaSpacing.xs))
            }


            // Time-aware greeting + quick actions
            item {
                HomeHeader(
                    viewModel = viewModel,
                    userName = userName,
                    onQuickAdd = onQuickAdd
                )
            }


            // Hero financial card — canonical snapshot drives it: safe-to-spend
            // hero (primary horizon), Held + Flexible supporting, Why? unfolds
            // the engine's own explanation. One hero, two supports, no dumps.
            item {
                val snap by viewModel.financialSnapshot.collectAsState()
                val heroLabel = when (snap.primaryHorizon) {
                    com.pesaflow.app.data.finance.Horizon.TODAY -> "Safe to spend · today"
                    com.pesaflow.app.data.finance.Horizon.WEEK -> "Safe to spend · this week"
                    com.pesaflow.app.data.finance.Horizon.UNTIL_NEXT_INCOME -> "Safe to spend · to next income"
                    com.pesaflow.app.data.finance.Horizon.MONTH -> "Safe to spend · this month"
                    com.pesaflow.app.data.finance.Horizon.SEMESTER -> "Safe to spend · semester"
                }
                val heroValue = when (snap.primaryHorizon) {
                    com.pesaflow.app.data.finance.Horizon.TODAY -> snap.safeToday
                    com.pesaflow.app.data.finance.Horizon.WEEK -> snap.safeWeek
                    com.pesaflow.app.data.finance.Horizon.UNTIL_NEXT_INCOME -> snap.safeUntilIncome
                    com.pesaflow.app.data.finance.Horizon.MONTH -> snap.safeMonth
                    com.pesaflow.app.data.finance.Horizon.SEMESTER -> snap.safeSemester
                }
                val fmt = com.pesaflow.app.data.finance.MoneyFormatter
                val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                val greeting = remember(userName) {
                    val name = userName.ifBlank { "there" }
                    when (hour) {
                        in 5..11 -> "Good morning, $name"
                        in 12..16 -> "Good afternoon, $name"
                        in 17..21 -> "Good evening, $name"
                        else -> "Hello, $name"
                    }
                }
                val dateLine = remember {
                    java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date())
                }
                HeroFinanceCard(
                    greeting = greeting,
                    dateLine = dateLine,
                    availableLabel = heroLabel,
                    availableValue = if (hideBalances) "KSh ••••" else fmt.compact(heroValue),
                    stats = listOf(
                        "Held" to if (hideBalances) "••••" else fmt.compact(snap.liquid),
                        "Flexible" to if (hideBalances) "••••" else fmt.compact(snap.flexible)
                    ),
                    onHideToggle = { viewModel.setHideBalances(!hideBalances) },
                    hideLabel = if (hideBalances) "Show" else "Hide"
                )
                var showWhy by remember { mutableStateOf(false) }
                TextButton(onClick = { showWhy = !showWhy }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showWhy) "Hide why ▴" else "Why? ▾", style = MaterialTheme.typography.bodySmall)
                }
                if (showWhy && !hideBalances) {
                    val why = snap.explanations["safeToday"]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                why?.why ?: "Based on your ledger.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            (why?.contributors.orEmpty()).forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }


            // M-Pesa wallet balance from the last SMS (display-only; the ledger
            // stays the source of truth for all math).
            item {
                val dashContext = LocalContext.current
                com.pesaflow.app.data.parsers.readMpesaBalance(dashContext)?.let { (amt, at) ->
                    Text(
                        if (hideBalances) "📲 M-Pesa wallet: KSh ••••"
                        else "📲 M-Pesa wallet: KSh ${amt.toInt()} · " + com.pesaflow.app.data.parsers.balanceAgeText(at, System.currentTimeMillis()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            // Money positions: ledger balance vs M-Pesa wallet side by side.
            item {
                val dashContext = LocalContext.current
                val wallet = com.pesaflow.app.data.parsers.readMpesaBalance(dashContext)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Money positions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ledger", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••" else availableBalance.toKSh(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("M-Pesa wallet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••"
                                    else wallet?.let { "KSh ${it.first.toInt()}" } ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            wallet?.let { "Wallet read " + com.pesaflow.app.data.parsers.balanceAgeText(it.second, System.currentTimeMillis()) + " · wallet is M-Pesa only, ledger covers everything." }
                                ?: "No wallet reading yet — first M-Pesa text sets it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Per-method split: which pocket holds the money.
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("M-Pesa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••" else "KSh ${mpesaBal.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cash", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••" else "KSh ${cashBal.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bank", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••" else "KSh ${bankBal.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Drift check: ledger's M-Pesa pocket vs the last SMS
                        // reading. Non-zero means unlogged rows on one side.
                        wallet?.let { (wamt, _) ->
                            val drift = mpesaBal - wamt
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (hideBalances) "Drift check: KSh ••••"
                                else if (kotlin.math.abs(drift) < 1) "Drift check: ledger matches SMS ✓"
                                else "Drift check: KSh ${drift.toInt()} " + (if (drift > 0) "(ledger higher — spending missing?)" else "(SMS higher — income missing?)"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (kotlin.math.abs(drift) < 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }


            // Feature shortcuts: icon rail, not another list — distinct from More.
            item {
                ShortcutRail(onNavigate = onNavigate)
            }


            // Smart insight lives here too, not only under Insights.
            item {
                SmartInsightsCard(
                    transactions = transactions,
                    budgets = budgets,
                    lang = currentLanguage,
                    name = userName,
                    bills = bills,
                    debts = debts,
                    goals = savingsGoals,
                    incomeSources = viewModel.incomeSources.collectAsState().value,
                    persona = com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers())
                )
            }


            // Money rhythms (from PesaFlow main): fare/rent/payday hypotheses
            // learned from the ledger — confirm once, they stop asking.
            item {
                val rhythms by viewModel.userRhythms.collectAsState()
                val storedKinds = remember(rhythms) { rhythms.map { it.kind to it.category }.toSet() }
                val hyps = remember(transactions, storedKinds) {
                    RhythmEngine().propose(
                        transactions.filter { !it.isSample },
                        com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers())
                    ).filter { (it.kind.name to it.category) !in storedKinds }.take(2)
                }
                if (hyps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        hyps.forEach { h ->
                            RhythmConfirmCard(
                                hypothesis = h,
                                onConfirm = {
                                    viewModel.upsertRhythm(
                                        com.pesaflow.app.data.models.UserRhythm(
                                            kind = h.kind.name,
                                            category = h.category,
                                            confidence = h.confidence,
                                            hint = h.hint,
                                            dayOfMonth = h.dayOfMonth,
                                            amount = h.amount,
                                            sourceCode = h.supportingCodes.firstOrNull().orEmpty(),
                                            confirmed = true
                                        )
                                    )
                                },
                                onDismiss = {
                                    viewModel.upsertRhythm(
                                        com.pesaflow.app.data.models.UserRhythm(
                                            kind = h.kind.name,
                                            category = h.category,
                                            confidence = h.confidence,
                                            hint = h.hint,
                                            dayOfMonth = h.dayOfMonth,
                                            amount = h.amount,
                                            dismissed = true
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }


            // Warning banners (80%/100% monthly budget thresholds)
            budgets.filter { it.type == BudgetType.MONTHLY && it.limitAmount > 0 }.mapNotNull { b ->
                val spent = transactions
                    .filter {
                        it.type == TransactionType.EXPENSE && !it.isSample &&
                            it.dateTimestamp in b.startTimestamp..b.endTimestamp
                    }
                    .sumOf { it.amount }
                val pct = (spent / b.limitAmount * 100).toInt()
                if (pct >= 80) Triple(b, spent, pct) else null
            }.take(2).forEach { (b, spent, pct) ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            if (pct >= 100) "⛔ ${b.category} budget crossed: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()}."
                            else "⚠️ ${b.category} at $pct%: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()}.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }


            // Smart Analyzer Tab - Weekday breakdown + Semester Runway + Motivational messages
            val motivationalMessages = listOf(
                "Every coin saved is a step closer to your degree! 🎓",
                "Small cuts today, big freedom tomorrow. 💪",
                "Your future self will thank you for this decision. ✨",
                "Consistent small savings beat sporadic big wins. 🌟",
                "Don't let today's spending steal tomorrow's opportunities. 🚀"
            )
            val todayCal = Calendar.getInstance()
            val todayDayStr = when (todayCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                else -> "Sun"
            }
            val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Smart Analyzer 🧠", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        // Weekday spending breakdown — this week by default,
                        // all-time only on request.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Weekday Spending Breakdown", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(selected = weekdayScope == "week", onClick = { weekdayScope = "week" }, label = { Text("This week") })
                                FilterChip(selected = weekdayScope == "all", onClick = { weekdayScope = "all" }, label = { Text("All time") })
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            weekdays.forEach { day ->
                                val spent = weekdaySpending[day] ?: 0.0
                                val isToday = day == todayDayStr
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .background(
                                            color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(6.dp)
                                ) {
                                    Column {
                                        Text(day, style = MaterialTheme.typography.bodySmall, color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                        Text("KSh ${spent.toInt()}", style = MaterialTheme.typography.bodySmall, color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Semester runway
                        Text("Semester Runway 🛤️", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val p = profile
                        val now = System.currentTimeMillis()
                        val semStart = p?.semesterStartTimestamp?.takeIf { it > 0 } ?: 0L
                        val semEnd = p?.semesterEndTimestamp?.takeIf { it > 0 } ?: 0L
                        if (p != null && semStart > 0 && semEnd > now) {
                            val daysLeft = ((semEnd - now) / (24 * 60 * 60 * 1000L)).coerceAtLeast(1)
                            val startingFunding = p.startingFunding
                            val semIncome = transactions.filter { it.type == TransactionType.INCOME && !it.isSample && it.dateTimestamp >= semStart }.sumOf { it.amount }
                            val semSpent = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= semStart }.sumOf { it.amount }
                            val remainingFunds = (startingFunding + semIncome - semSpent).coerceAtLeast(0.0)
                            val safeDaily = remainingFunds / daysLeft
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("Days left in semester: $daysLeft", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Safe daily spend: KSh ${safeDaily.toInt()}/day", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Remaining funds: KSh ${remainingFunds.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text("Set your university profile under More → University to view semester runway.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Motivational message
                        val msgIndex = ((System.currentTimeMillis() / (1000 * 60 * 60 * 24)) % motivationalMessages.size).toInt()
                        Text(
                            motivationalMessages[msgIndex],
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }


            // Safe-to-spend Engine Section (hero position under balance)
            if ("safe" !in hiddenSections) {
            item {
                ExplainChip(
                    label = "What is safe-to-spend?",
                    body = "Income minus budgets, bills due and goals — the amount actually okay to use today. It moves as you log spending."
                )
            }
            item {
                SafeToSpendCard(transactions = transactions, budgets = budgets, goals = savingsGoals, bills = bills, hide = hideBalances)
            }
            }


            // Pending Verification Engine List View (max 3, expandable)
            if (pendingTransactions.isNotEmpty() && "pending" !in hiddenSections) {
                item {
                    // Bulk bar: every "sure" row (history vouches ≥85%) confirms
                    // in one tap; unsure rows stay for human eyes.
                    val dashPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                    val sureRows = remember(pendingTransactions) {
                        pendingTransactions.filter {
                            com.pesaflow.app.data.ledger.ConfidenceMemory.effective(dashPrefs, it.merchant, it.confidenceScore) >= 0.85f
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pending (${pendingTransactions.size}) 🔔", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pendingTransactions.size >= 2) {
                                TextButton(onClick = {
                                    scope.launch {
                                        val n = viewModel.removeDuplicatePending()
                                        snackbar.showSnackbar(
                                            if (n == 0) "No duplicates — queue is clean."
                                            else "$n duplicate${if (n == 1) "" else "s"} removed."
                                        )
                                    }
                                }) { Text("Remove duplicates") }
                            }
                            if (sureRows.isNotEmpty()) {
                                TextButton(onClick = {
                                    viewModel.approveAllPending(sureRows)
                                    scope.launch {
                                        val r = snackbar.showSnackbar("${sureRows.size} confirmed — history vouched.", "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
                                        if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
                                    }
                                }) { Text("Confirm all sure (${sureRows.size})") }
                            }
                            TextButton(onClick = { showAllPending = !showAllPending }) {
                                Text(if (showAllPending) "Less" else "View all")
                            }
                        }
                    }
                }
                items(pendingTransactions.take(if (showAllPending) Int.MAX_VALUE else 3)) { pending ->
                    var editCat by remember(pending.id) { mutableStateOf(pending.category) }
                    // Type can be wrong at parse time (P2P-to-self, withdrawals) —
                    // fix it here instead of delete-and-retype.
                    var editType by remember(pending.id) { mutableStateOf(pending.type) }
                    fun snack(msg: String) {
                        scope.launch {
                            val r = snackbar.showSnackbar(msg, "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
                            if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("New SMS Detected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("KSh ${pending.amount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Merchant: ${pending.merchant}", style = MaterialTheme.typography.bodyMedium)
                            Text("Suggested Category: ${pending.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // Calibrated badge: your history vouches for this merchant.
                            val calPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                            val effConf = remember(pending) {
                                com.pesaflow.app.data.ledger.ConfidenceMemory.effective(calPrefs, pending.merchant, pending.confidenceScore)
                            }
                            if (effConf >= 0.85f) {
                                Text("✓ Sure — matches your history (${(effConf * 100).toInt()}%).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            } else if (pending.confidenceScore < 0.75f) {
                                Text("⚠️ ${(pending.confidenceScore * 100).toInt()}% sure — check category and type before confirming.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            OutlinedTextField(
                                value = editCat,
                                onValueChange = { editCat = it },
                                label = { Text("Confirm as category") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    TransactionType.EXPENSE to "Spent",
                                    TransactionType.INCOME to "Received",
                                    TransactionType.SAVING to "Saved",
                                    TransactionType.TRANSFER to "Moved"
                                ).forEach { (t, label) ->
                                    FilterChip(selected = editType == t, onClick = { editType = t }, label = { Text(label) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    viewModel.rejectPending(pending)
                                    snack("Ignored — pending removed.")
                                }) { Text("Ignore", color = MaterialTheme.colorScheme.error) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.approvePending(pending, editCat.ifBlank { pending.category }, editType)
                                    snack("Saved to ledger.")
                                }) { Text("Confirm") }
                            }
                        }
                    }
                }
            }


            // Budget progress bars
            if (budgets.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Budget Progress 📊", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(12.dp))
                            budgets.filter { it.limitAmount > 0 && it.category != "ALL" }.take(5).forEach { b ->
                                val spent = transactions.filter {
                                    it.type == TransactionType.EXPENSE && !it.isSample &&
                                        it.dateTimestamp in b.startTimestamp..b.endTimestamp &&
                                        it.category.equals(b.category, ignoreCase = true)
                                }.sumOf { it.amount }
                                val pct = (spent / b.limitAmount * 100).coerceIn(0.0, 100.0).toFloat()
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${categoryEmoji(b.category)} ${b.category}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("KSh ${spent.toInt()}/${b.limitAmount.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    LinearProgressIndicator(
                                        progress = { pct / 100f },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp),
                                        color = when {
                                            pct >= 100 -> MaterialTheme.colorScheme.error
                                            pct >= 60 -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Category spending breakdown (top 5)
            if (transactions.any { it.type == TransactionType.EXPENSE && !it.isSample }) {
                item {
                    val catSpending = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample }
                        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                        .entries.sortedByDescending { it.value }.take(5)
                    if (catSpending.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Spending by Category 🔍", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(12.dp))
                                val maxCat = catSpending.firstOrNull()?.value ?: 1.0
                                catSpending.forEach { (cat, amt) ->
                                    val barWidth = (amt / maxCat).coerceIn(0.0, 1.0).toFloat()
                                    Column(modifier = Modifier.padding(vertical = 3.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${categoryEmoji(cat)} $cat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text("KSh ${amt.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        LinearProgressIndicator(
                                            progress = { barWidth },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // See insights teaser
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Spending Insights 💡", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Charts, trends and advice from your data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { onNavigate(NavRoutes.INSIGHTS) }) { Text("See insights") }
                    }
                }
            }


            // Recent 5 Ledgers
            if ("recent" !in hiddenSections) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onNavigate(NavRoutes.TRANSACTIONS) }) { Text("Full history →") }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ledgerFilter == null,
                            onClick = { ledgerFilter = null },
                            label = { Text("All") }
                        )
                        listOf("INCOME" to "Income", "EXPENSE" to "Expenses", "SAVING" to "Savings", "INVESTMENT" to "Investments").forEach { (v, label) ->
                            FilterChip(
                                selected = ledgerFilter == v,
                                onClick = { ledgerFilter = if (ledgerFilter == v) null else v },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                if (transactions.isEmpty()) {
                    item {
                        Column {
                            Text("No transactions yet — tap + below to log your first one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { onNavigate(NavRoutes.ADD_EXPENSE) }) { Text("Add your first expense") }
                        }
                    }
                } else {
                    items(transactions.filter { ledgerFilter == null || it.type.name == ledgerFilter }.take(5), key = { it.id }) { tx ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { v ->
                                when (v) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.deleteTransactionWithUndo(tx)
                                        scope.launch {
                                            val r = snackbar.showSnackbar("Deleted ${tx.merchant}.", "Undo", duration = SnackbarDuration.Long)
                                            if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
                                        }
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> { editingTx = tx; false }
                                    else -> false
                                }
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val dir = dismissState.dismissDirection
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        when (dir) {
                                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                            else -> Color.Transparent
                                        },
                                        RoundedCornerShape(12.dp)
                                    ).padding(16.dp),
                                    contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Icon(
                                        if (dir == SwipeToDismissBoxValue.StartToEnd) Icons.Filled.Edit else Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = if (dir == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${categoryEmoji(tx.category)} ${tx.merchant}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    Text(
                                        "${tx.category} · ${java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(tx.dateTimestamp))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${when (tx.type) { TransactionType.INCOME -> "+"; TransactionType.TRANSFER -> "↔"; else -> "-" }} KSh ${tx.amount.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    editingTx?.let {
        QuickAddDialog(viewModel = viewModel, defaultType = it.type, onDismiss = { editingTx = null }, existing = it)
    }
    if (showCustomize) {
        AlertDialog(
            onDismissRequest = { showCustomize = false },
            title = { Text("Tune your home", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pick the sections you want to see.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("safe" to "Safe-to-spend", "pending" to "Pending approvals", "recent" to "Recent activity").forEach { (key, label) ->
                        FilterChip(selected = key !in hiddenSections, onClick = { viewModel.toggleSection(key) }, label = { Text(label) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCustomize = false }) { Text("Done") } }
        )
    }
    // Coach marks: log → approve → safe-spend. Dismissed forever after step 3.
    // Gated on an explicit false: while DataStore is still loading (null),
    // show nothing rather than flashing the tour at opted-out users.
    if (coachDoneByPrefs == false && coachStep < 3) {
        val coachTitle = when (coachStep) {
            0 -> "1 · Log in seconds ⚡"
            1 -> "2 · Approve, don't type ✅"
            else -> "3 · Spend what's safe 🎯"
        }
        val coachBody = when (coachStep) {
            0 -> "Tap + below for any expense. Amount, where, done — under 5 seconds."
            1 -> "M-Pesa texts land here as pending. Sure ones confirm all at once — the rest get your eyes, one by one."
            else -> "Safe-to-spend is your one number: what's actually okay to use today."
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.BottomCenter) {
            Card(Modifier.fillMaxWidth().padding(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(coachTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(coachBody, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { scope.launch { AppPrefs.setCoachDone(coachContext) }; coachStep = 99 }) { Text("Skip tour") }
                        Button(onClick = {
                            if (coachStep >= 2) { scope.launch { AppPrefs.setCoachDone(coachContext) }; coachStep = 99 }
                            else coachStep++
                        }) { Text(if (coachStep >= 2) "Start" else "Next") }
                    }
                }
            }
        }
    }
    }
}


// Horizontal feature rail: glanceable icons with labels — deliberately not a
// list like More. Every icon is a real button for screen readers.
@Composable
private fun ShortcutRail(onNavigate: (String) -> Unit) {
    val shortcuts = listOf(
        Triple(NavRoutes.TRANSACTIONS, "Ledger", Icons.Filled.List),
        Triple(NavRoutes.BUDGETS, "Budgets", Icons.Filled.Star),
        Triple(NavRoutes.SAVINGS, "Savings", Icons.Filled.Savings),
        Triple(NavRoutes.BILLS, "Bills", Icons.Filled.Home),
        Triple(NavRoutes.MEALS, "Meals", Icons.Filled.Favorite),
        Triple(NavRoutes.REPORTS, "Reports", Icons.Filled.Info),
        Triple(NavRoutes.NETWORTH, "Net Worth", Icons.Filled.AccountBox),
        Triple(NavRoutes.BUDDY, "Buddy", Icons.Filled.Face),
        Triple(NavRoutes.REVIEW, "Review", Icons.Filled.CheckCircle)
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Jump to", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shortcuts) { (route, label, icon) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Open $label",
                        onClick = { onNavigate(route) }
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}