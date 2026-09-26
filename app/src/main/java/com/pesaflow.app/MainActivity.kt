package com.pesaflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.data.parsers.CsvImporter
import com.pesaflow.app.ui.income.IncomeScreen
import com.pesaflow.app.ui.NavRoutes
import com.pesaflow.app.ui.review.ReviewScreen
import com.pesaflow.app.ui.savings.SavingsScreen
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintSettingsNeutral
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.ui.bills.BillsScreen
import com.pesaflow.app.ui.budgets.BudgetsScreen
import com.pesaflow.app.ui.dashboard.DashboardScreen
import com.pesaflow.app.ui.dashboard.PesaBuddyAssistant
import com.pesaflow.app.ui.dashboard.QuickAddDialog
import com.pesaflow.app.ui.debt.DebtTrackingScreen
import com.pesaflow.app.ui.insights.InsightsScreen
import com.pesaflow.app.ui.onboarding.OnboardingScreen
import com.pesaflow.app.ui.reports.NetWorthScreen
import com.pesaflow.app.ui.reports.ReportsScreen
import com.pesaflow.app.ui.search.SearchScreen
import androidx.compose.material3.FloatingActionButton
import com.pesaflow.app.ui.semester.SemesterScreen
import com.pesaflow.app.ui.transactions.TransactionsScreen
import com.pesaflow.app.ui.settings.SettingsScreen
import com.pesaflow.app.ui.stock.BelongingsScreen
import com.pesaflow.app.ui.stock.KitchenScreen
import com.pesaflow.app.ui.theme.PesaFlowTheme
import com.pesaflow.app.ui.university.MealPlannerScreen
import com.pesaflow.app.ui.university.UniversityScreen
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.analytics.AnalyticsScreen
import com.pesaflow.app.ui.dashboard.weekdayProfile
import com.pesaflow.app.ui.dashboard.factorForToday
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.TransactionType as TxType
import com.pesaflow.app.ui.notifications.NotificationChecker
import com.pesaflow.app.ui.exports.ExportScreen
import com.pesaflow.app.ui.recurring.RecurringScreen
import com.pesaflow.app.ui.goals.GoalsScreen


class MainActivity : ComponentActivity() {


    private val viewModel: FinanceViewModel by viewModels()


    private var onboarded by mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded launch splash (navy + icon) instead of a color flash.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Edge-to-edge: cinematic canvases draw behind status + nav bars;
        // content layers take inset padding themselves.
        WindowCompat.setDecorFitsSystemWindows(window, false)


        // Process intents from external clipboard sources or system shares
        handleIncomingSharedText(intent)


        // NOTE: no permission requests here on purpose. SMS is requested on the
        // onboarding SMS step (with rationale), notifications on first report
        // preview tap. Cold-start prompts get auto-denied and hurt Play review.


        val prefs = getPreferences(MODE_PRIVATE)
        onboarded = prefs.getBoolean("onboarding_done", false)
        viewModel.setUserName(prefs.getString("user_name", "") ?: "")


        // Re-arm one-time reminder chains (breakfast/lunch/night/Sunday): if Android killed
        // them, every app open restores the next firing. Idempotent by unique name.
        val nprefs = getSharedPreferences("pesaflow_prefs", MODE_PRIVATE)
        // Backfill for existing users: lunch_scan was introduced after they
        // onboarded, so the key is absent (not off) — default it ON once.
        if (!nprefs.contains("lunch_scan")) {
            nprefs.edit().putBoolean("lunch_scan", true).apply()
        }
        if (nprefs.getBoolean("breakfast_reminder", false)) ReminderScheduler.scheduleBreakfast(this)
        if (nprefs.getBoolean("lunch_reminder", false)) ReminderScheduler.scheduleLunch(this)
        if (nprefs.getBoolean("night_report", false)) ReminderScheduler.scheduleNightReport(this)
        if (nprefs.getBoolean("sunday_report", false)) ReminderScheduler.scheduleSundayReport(this)
        if (nprefs.getBoolean("lunch_scan", false)) ReminderScheduler.scheduleLunchScan(this)
        // Core chains have no off-switch: re-arm every cold start so the app
        // works for years, not one day. Daily/weekly stay behind user toggles.
        ReminderScheduler.scheduleDailyDigest(this)
        ReminderScheduler.scheduleMonthlyReport(this)
        ReminderScheduler.scheduleBudgetCrossingAlert(this)


        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            PesaFlowTheme(mode = themeMode) {
                if (onboarded) {
                    PesaFlowAppNav(viewModel = viewModel)
                } else {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onDone = {
                            getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("onboarding_done", true)
                                .putString("user_name", viewModel.userName.value)
                                .apply()
                            onboarded = true
                        }
                    )
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingSharedText(intent)
    }


    private fun handleIncomingSharedText(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                // Everything shared lands in Pending for Confirm / Ignore —
                // nothing unverified ever touches the permanent ledger.
                val parsedMpesa = MpesaParser.parseMessage(sharedText)
                if (parsedMpesa != null) {
                    viewModel.queueSharedTransaction(parsedMpesa)
                } else {
                    viewModel.queueSharedText(sharedText)
                }
            }
        }
    }
}


// Bottom navigation across the feature screens. State-based (no nav library)
// so no new dependency is required.
@Composable
private fun PesaFlowAppNav(viewModel: FinanceViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var moreSection by remember { mutableStateOf<String?>(null) }
    var showSpeedDial by remember { mutableStateOf(false) }
    var quickAddType by remember { mutableStateOf<TransactionType?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val rows = CsvImporter.parseCsvDataAuto(text)
                viewModel.importTransactions(rows) { added, skipped ->
                    importResult = "Imported $added transaction(s)" +
                        (if (skipped > 0) " · $skipped duplicate(s) skipped" else "") + " ✅"
                }
            } catch (e: Exception) {
                importResult = "Import failed: ${e.message}"
            }
        }
    }

    BackHandler(enabled = selectedTab == 4 && moreSection != null) {
        moreSection = null
    }

    val transactions by viewModel.allTransactions.collectAsState()

    val budgets by viewModel.budgets.collectAsState()

    val goals by viewModel.savingsGoals.collectAsState()

    val bills by viewModel.bills.collectAsState()

    val debts by viewModel.debts.collectAsState()

    val profile by viewModel.universityProfile.collectAsState()

    val notifMetrics = remember(transactions, budgets) {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val dayStart = (cal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000
        val expenses = transactions.filter { it.type == com.pesaflow.app.data.models.TransactionType.EXPENSE && !it.isSample }
        val todaySpend = expenses.filter { it.dateTimestamp >= dayStart }.sumOf { it.amount }
        val yesterdaySpend = expenses.filter { it.dateTimestamp >= dayStart - dayMs && it.dateTimestamp < dayStart }.sumOf { it.amount }
        val spentByCategory = expenses.filter { it.dateTimestamp >= dayStart - 30L * dayMs }
            .groupBy { it.category.lowercase() }
            .mapValues { e -> e.value.sumOf { it.amount } }
        val monthlyAll = budgets.firstOrNull { it.category == "ALL" }?.limitAmount?.takeIf { it > 0 }
        val dailyExplicit = budgets.filter { it.type == com.pesaflow.app.data.models.BudgetType.DAILY }
            .sumOf { it.limitAmount }.takeIf { it > 0 }
        val dailyTarget = dailyExplicit ?: (monthlyAll?.div(30.0) ?: 500.0)
        val pace = weekdayProfile(
            expenses.map { LedgerRow(it.amount, it.type, it.category, it.merchant, it.dateTimestamp) },
            now
        )
        val weekdayFactor = factorForToday(pace, now)
        Triple(todaySpend, yesterdaySpend, spentByCategory) to (dailyTarget to weekdayFactor)
    }
    val notifTodaySpend = notifMetrics.first.first
    val notifYesterdaySpend = notifMetrics.first.second
    val notifSpentByCategory = notifMetrics.first.third
    val notifDailyTarget = notifMetrics.second.first
    val notifWeekdayFactor = notifMetrics.second.second


    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSpeedDial = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Menu, contentDescription = "Transactions") },
                    label = { Text("Transactions") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = "Budgets") },
                    label = { Text("Budgets") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.Info, contentDescription = "Insights") },
                    label = { Text("Insights") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4; moreSection = null },
                    icon = { Icon(Icons.Filled.MoreVert, contentDescription = "More") },
                    label = { Text("More") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onQuickAdd = { quickAddType = it },
                    onNavigate = { route ->
                        when (route) {
                            NavRoutes.SEARCH -> { selectedTab = 4; moreSection = NavRoutes.SEARCH }
                            NavRoutes.INSIGHTS -> { selectedTab = 3 }
                            NavRoutes.TRANSACTIONS -> { selectedTab = 1 }
                            NavRoutes.BUDGETS -> { selectedTab = 2 }
                            NavRoutes.SAVINGS -> { selectedTab = 4; moreSection = NavRoutes.SAVINGS }
                            NavRoutes.BILLS -> { selectedTab = 4; moreSection = NavRoutes.BILLS }
                            NavRoutes.MEALS -> { selectedTab = 4; moreSection = NavRoutes.MEALS }
                            NavRoutes.REPORTS -> { selectedTab = 4; moreSection = NavRoutes.REPORTS }
                            NavRoutes.NETWORTH -> { selectedTab = 4; moreSection = NavRoutes.NETWORTH }
                            NavRoutes.BUDDY -> { selectedTab = 4; moreSection = NavRoutes.BUDDY }
                            NavRoutes.REVIEW -> { selectedTab = 4; moreSection = NavRoutes.REVIEW }
                            NavRoutes.ADD_EXPENSE -> { quickAddType = TransactionType.EXPENSE }
                        }
                    }
                )
                1 -> TransactionsScreen(
                    viewModel = viewModel,
                    onQuickAdd = { quickAddType = it }
                )
                2 -> BudgetsScreen(viewModel = viewModel)
                3 -> InsightsScreen(viewModel = viewModel)
                else -> when (moreSection) {
                    NavRoutes.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    NavRoutes.DEBT -> DebtTrackingScreen(viewModel = viewModel)
                    NavRoutes.SEARCH -> SearchScreen(viewModel = viewModel)
                    NavRoutes.UNIVERSITY -> UniversityScreen(viewModel = viewModel)
                    NavRoutes.SEMESTER -> SemesterScreen(viewModel = viewModel, onNavigate = { route ->
                        when (route) {
                            NavRoutes.BILLS -> { moreSection = NavRoutes.BILLS }
                            NavRoutes.DEBT -> { moreSection = NavRoutes.DEBT }
                            NavRoutes.MEALS -> { moreSection = NavRoutes.MEALS }
                            NavRoutes.UNIVERSITY -> { moreSection = NavRoutes.UNIVERSITY }
                        }
                    })
                    NavRoutes.MEALS -> MealPlannerScreen(viewModel = viewModel)
                    NavRoutes.BILLS -> BillsScreen(viewModel = viewModel)
                    NavRoutes.INCOME -> IncomeScreen(viewModel = viewModel)
                    NavRoutes.BUDDY -> PesaBuddyAssistant(viewModel = viewModel)
                    NavRoutes.NETWORTH -> NetWorthScreen(viewModel = viewModel)
                    NavRoutes.SAVINGS -> SavingsScreen(viewModel = viewModel)
                    NavRoutes.REPORTS -> ReportsScreen(viewModel = viewModel)
                    NavRoutes.INSIGHTS -> InsightsScreen(viewModel = viewModel)
                    NavRoutes.THINGS -> BelongingsScreen(viewModel = viewModel)
                    NavRoutes.KITCHEN -> KitchenScreen(viewModel = viewModel)
                    NavRoutes.REVIEW -> ReviewScreen(viewModel = viewModel)
                    NavRoutes.ANALYTICS -> AnalyticsScreen(transactions = transactions)
                    NavRoutes.NOTIFICATIONS -> NotificationChecker(
                        bills = bills, debts = debts, budgets = budgets, goals = goals,
                        spentByCategory = notifSpentByCategory,
                        todaySpend = notifTodaySpend,
                        yesterdaySpend = notifYesterdaySpend,
                        dailyTarget = notifDailyTarget,
                        weekdayFactor = notifWeekdayFactor
                    )
                    NavRoutes.EXPORT -> ExportScreen(
                        transactions = transactions, budgets = budgets, goals = goals,
                        bills = bills, debts = debts, profile = profile
                    )
                    NavRoutes.RECURRING -> RecurringScreen(transactions = transactions)
                    NavRoutes.GOALS -> GoalsScreen(goals = goals, transactions = transactions)
                    else -> MoreScreen(onSelect = { moreSection = it })
                }
            }

            quickAddType?.let { type ->
                QuickAddDialog(viewModel = viewModel, defaultType = type, onDismiss = { quickAddType = null })
            }

            if (showSpeedDial) {
                AlertDialog(
                    onDismissRequest = { showSpeedDial = false },
                    title = { Text("Add") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showSpeedDial = false; quickAddType = TransactionType.EXPENSE }, modifier = Modifier.fillMaxWidth()) {
                                Text("− Spend", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; quickAddType = TransactionType.INCOME }, modifier = Modifier.fillMaxWidth()) {
                                Text("+ Income", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; selectedTab = 3 }, modifier = Modifier.fillMaxWidth()) {
                                Text("Parse text (NLP)", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; csvPicker.launch("text/*") }, modifier = Modifier.fillMaxWidth()) {
                                Text("Import CSV", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showSpeedDial = false }) { Text("Close") } }
                )
            }

            importResult?.let { msg ->
                AlertDialog(
                    onDismissRequest = { importResult = null },
                    title = { Text("CSV Import") },
                    text = { Text(msg) },
                    confirmButton = { TextButton(onClick = { importResult = null }) { Text("OK") } }
                )
            }
        }
    }
}


@Composable
private fun MoreScreen(onSelect: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSettingsNeutral, bgRes = R.drawable.bg_settings_neutral)
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("More Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Everything else, one tap away", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoreRow(icon = Icons.Filled.Info, title = "Insights", subtitle = "Charts, trends and advice") { onSelect(NavRoutes.INSIGHTS) }
        MoreRow(icon = Icons.Filled.DateRange, title = "Semester", subtitle = "Semester plan, runway and fees") { onSelect(NavRoutes.SEMESTER) }
        MoreRow(icon = Icons.Filled.Menu, title = "Reports", subtitle = "Daily to annual summaries") { onSelect(NavRoutes.REPORTS) }
        MoreRow(icon = Icons.Filled.Settings, title = "Settings", subtitle = "Language, notifications, data") { onSelect(NavRoutes.SETTINGS) }
        MoreRow(icon = Icons.Filled.Person, title = "Net Worth", subtitle = "Cash, savings, investments, debts") { onSelect(NavRoutes.NETWORTH) }
        MoreRow(icon = Icons.Filled.Savings, title = "Savings", subtitle = "Goals that grow with your ledger") { onSelect(NavRoutes.SAVINGS) }
        MoreRow(icon = Icons.Filled.Home, title = "Bills", subtitle = "Upcoming, recurring, repeats") { onSelect(NavRoutes.BILLS) }
        MoreRow(icon = Icons.Filled.AccountBalance, title = "Income", subtitle = "HELB, parents, hustle — where money comes from") { onSelect(NavRoutes.INCOME) }
        MoreRow(icon = Icons.Filled.AccountBox, title = "Debt Tracking", subtitle = "Money owed and borrowed") { onSelect(NavRoutes.DEBT) }
        MoreRow(icon = Icons.Filled.Search, title = "Search", subtitle = "Find any transaction") { onSelect(NavRoutes.SEARCH) }
        MoreRow(icon = Icons.Filled.Star, title = "University", subtitle = "Semester planner and allowance") { onSelect(NavRoutes.UNIVERSITY) }
        MoreRow(icon = Icons.Filled.Favorite, title = "Meal Planner", subtitle = "Food menus under your budget") { onSelect(NavRoutes.MEALS) }
        MoreRow(icon = Icons.Filled.Face, title = "PesaBuddy", subtitle = "Ask about your money") { onSelect(NavRoutes.BUDDY) }
        MoreRow(icon = Icons.Filled.ShoppingCart, title = "My Things", subtitle = "Have it, need it, save for it") { onSelect(NavRoutes.THINGS) }
        MoreRow(icon = Icons.Filled.DateRange, title = "Kitchen Stock", subtitle = "Unga levels, refills, restock cost") { onSelect(NavRoutes.KITCHEN) }
        MoreRow(icon = Icons.Filled.CheckCircle, title = "Weekly review", subtitle = "Uncategorized and possible duplicates") { onSelect(NavRoutes.REVIEW) }
        MoreRow(icon = Icons.Filled.Info, title = "Analytics", subtitle = "Trends, shares, heatmap and insights") { onSelect(NavRoutes.ANALYTICS) }
        MoreRow(icon = Icons.Filled.Favorite, title = "Notifications", subtitle = "Bills, debts, budgets and rituals") { onSelect(NavRoutes.NOTIFICATIONS) }
        MoreRow(icon = Icons.Filled.Star, title = "Export & Backup", subtitle = "CSV, JSON backup and share") { onSelect(NavRoutes.EXPORT) }
        MoreRow(icon = Icons.Filled.DateRange, title = "Recurring", subtitle = "Patterns and monthly commitment") { onSelect(NavRoutes.RECURRING) }
        MoreRow(icon = Icons.Filled.Savings, title = "Goals Pro", subtitle = "Pace, risk and suggestions") { onSelect(NavRoutes.GOALS) }
    }
    }
}


@Composable
private fun MoreRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            leadingContent = { Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
