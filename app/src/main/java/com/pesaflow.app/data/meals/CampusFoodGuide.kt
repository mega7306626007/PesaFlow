package com.pesaflow.app.data.meals

// Campus food guide: where to eat near your school, what plate, what price.
// Starter prices in student bands (same scale as the planner's staples pack)
// — every inserted row stays editable, so the crowd corrects the guide.
// Pure data, zero Android deps.
data class CampusSpot(
    val university: String,
    val spot: String,
    val item: String,
    val price: Double,
    val mealType: String,
    val component: String
)

private val GUIDE = listOf(
    // UoN Main
    CampusSpot("UoN", "Club 36", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("UoN", "Club 36", "Githeri", 50.0, "Lunch", "Complete"),
    CampusSpot("UoN", "Club 36", "Chai + mandazi", 40.0, "Breakfast", "Complete"),
    CampusSpot("UoN", "Mama Njoroge", "Ugali + sukuma + omena", 80.0, "Supper", "Complete"),
    // KU
    CampusSpot("KU", "Main Gate kibanda", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("KU", "Main Gate kibanda", "Pilau", 100.0, "Lunch", "Complete"),
    CampusSpot("KU", "Main Gate kibanda", "Githeri", 60.0, "Supper", "Complete"),
    CampusSpot("KU", "Main Gate kibanda", "Chai + mandazi", 50.0, "Breakfast", "Complete"),
    // JKUAT Juja
    CampusSpot("JKUAT", "Juja kibanda", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("JKUAT", "Juja kibanda", "Ugali + omena", 80.0, "Supper", "Complete"),
    CampusSpot("JKUAT", "Juja kibanda", "Chapati + ndengu", 60.0, "Lunch", "Complete"),
    CampusSpot("JKUAT", "Juja kibanda", "Githeri", 50.0, "Supper", "Complete"),
    // Maseno
    CampusSpot("Maseno", "Maseno Town kibanda", "Githeri", 50.0, "Lunch", "Complete"),
    CampusSpot("Maseno", "Maseno Town kibanda", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("Maseno", "Maseno Town kibanda", "Ugali + sukuma", 60.0, "Supper", "Complete"),
    CampusSpot("Maseno", "Maseno Town kibanda", "Chai + mandazi", 40.0, "Breakfast", "Complete"),
    // Egerton Njoro
    CampusSpot("Egerton", "Njoro kibanda", "Githeri", 50.0, "Lunch", "Complete"),
    CampusSpot("Egerton", "Njoro kibanda", "Chapati + ndengu", 60.0, "Lunch", "Complete"),
    CampusSpot("Egerton", "Njoro kibanda", "Smocha", 70.0, "Lunch", "Complete"),
    CampusSpot("Egerton", "Njoro kibanda", "Chai + mandazi", 40.0, "Breakfast", "Complete")
)

// Matches the onboarding university text ("UoN Main", "Kenyatta", ...) to a
// guide. Kenyatta checks before Nairobi to avoid city-name collisions.
fun spotsFor(universityName: String): List<CampusSpot> {
    val low = universityName.trim().lowercase()
    if (low.isEmpty()) return emptyList()
    // Tokens catch bare abbreviations ("UoN", "KU"); substrings catch full
    // names ("Kenyatta University"). Kenyatta checks before Nairobi to avoid
    // city-name collisions.
    val tokens = low.split(Regex("[^a-z]+")).toSet()
    val key = when {
        low.contains("kenyatta") || "ku" in tokens -> "KU"
        low.contains("uon") || "uon" in tokens || low.contains("nairobi") -> "UoN"
        low.contains("jkuat") || "jkuat" in tokens || low.contains("juja") -> "JKUAT"
        low.contains("maseno") || "maseno" in tokens -> "Maseno"
        low.contains("egerton") || "egerton" in tokens || low.contains("njoro") -> "Egerton"
        else -> return emptyList()
    }
    return GUIDE.filter { it.university == key }
}
