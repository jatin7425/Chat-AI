package com.example.util

/**
 * Age-from-DOB math against a Space's in-story `simDate` (not real-world today). Hand-rolled
 * "yyyy-MM-dd" integer parsing rather than java.time -- LocalDate/Period need API 26+, and
 * minSdk is 24 here, so this avoids pulling in coreLibraryDesugaring for one calculation.
 */
object AgeUtil {

    private data class YmdDate(val year: Int, val month: Int, val day: Int)

    private fun parse(iso: String): YmdDate? {
        val parts = iso.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return YmdDate(year, month, day)
    }

    /** Returns null if either date is blank/unparseable, or if dob is after simDate. */
    fun computeAge(dobIso: String, simDateIso: String): Int? {
        if (dobIso.isBlank() || simDateIso.isBlank()) return null
        val dob = parse(dobIso) ?: return null
        val simDate = parse(simDateIso) ?: return null

        var age = simDate.year - dob.year
        val birthdayNotYetReachedThisYear =
            simDate.month < dob.month || (simDate.month == dob.month && simDate.day < dob.day)
        if (birthdayNotYetReachedThisYear) age--

        return if (age >= 0) age else null
    }
}
