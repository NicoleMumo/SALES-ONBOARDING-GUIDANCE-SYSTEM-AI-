package com.example.salesonboarding

import java.util.Locale

/** Kenyan phone numbers typed in any common way, written one way. */
object PhoneNumberFormat {
    private val separators = Regex("[\\s\\-().]")

    /**
     * Drops spaces, dashes, dots and brackets, and writes +254 7…, 254 7… or 7… as 07…
     * (likewise for 01…), so "0712 345 678" and "+254 712 345 678" both become 0712345678.
     * Anything that is not a Kenyan mobile number is returned as typed, for the check to explain.
     */
    fun tidy(raw: String): String {
        val compact = raw.trim().replace(separators, "")
        val local = when {
            compact.matches(Regex("\\+254[17]\\d{8}")) -> "0" + compact.removePrefix("+254")
            compact.matches(Regex("254[17]\\d{8}")) -> "0" + compact.removePrefix("254")
            compact.matches(Regex("[17]\\d{8}")) -> "0$compact"
            else -> compact
        }
        return if (local.matches(Regex("0[17]\\d{8}"))) local else raw.trim()
    }
}

/** What could be read from the front of an identity card. Each part is null when not found. */
data class IdCardDetails(
    val fullName: String? = null,
    val idNumber: String? = null,
    /** As the form expects it: YYYY-MM-DD. */
    val dateOfBirth: String? = null
)

/**
 * Picks the name, ID number and date of birth out of the text ML Kit read from an ID card.
 * Labels such as "FULL NAMES", "ID NUMBER" and "DATE OF BIRTH" are used when present;
 * otherwise the most likely line is taken. The sales agent checks every value before using it.
 */
object IdCardText {
    private val dateRegex = Regex("""\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{4})\b|\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val notNameWords = setOf(
        "REPUBLIC", "KENYA", "JAMHURI", "YA", "OF", "SERIAL", "NUMBER", "NO", "ID", "DATE", "BIRTH",
        "SEX", "MALE", "FEMALE", "DISTRICT", "PLACE", "ISSUE", "HOLDER", "SIGN", "SIGNATURE",
        "NATIONAL", "IDENTITY", "CARD", "FULL", "NAMES", "NAME"
    )

    fun read(text: String): IdCardDetails {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return IdCardDetails(
            fullName = fullName(lines),
            idNumber = idNumber(lines),
            dateOfBirth = dateOfBirth(lines)
        )
    }

    private fun afterLabel(lines: List<String>, label: Regex): String? {
        val index = lines.indexOfFirst { label.containsMatchIn(it.uppercase(Locale.ROOT)) }
        if (index < 0) return null
        val rest = lines[index].uppercase(Locale.ROOT).replace(label, "").trim(' ', ':', '.')
        return rest.ifBlank { lines.getOrNull(index + 1) }
    }

    private fun idNumber(lines: List<String>): String? {
        afterLabel(lines, Regex("""ID\s*(NUMBER|NO)\.?"""))
            ?.let { Regex("""\b\d{6,9}\b""").find(it)?.value }
            ?.let { return it }
        // Without a label: a 7- or 8-digit number (a card's serial number is longer).
        return lines.firstNotNullOfOrNull { Regex("""\b\d{7,8}\b""").find(it)?.value }
    }

    private fun dateOfBirth(lines: List<String>): String? {
        afterLabel(lines, Regex("""DATE\s*OF\s*BIRTH"""))?.let(::firstDate)?.let { return it }
        // Without a label, the earliest date on the card is the birth date (issue comes later).
        return lines.mapNotNull(::firstDate).minOrNull()
    }

    private fun firstDate(line: String): String? {
        val match = dateRegex.find(line) ?: return null
        val (day, month, year) = if (match.groupValues[4].isNotEmpty()) {
            Triple(match.groupValues[6], match.groupValues[5], match.groupValues[4])
        } else {
            Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
        val d = day.toInt()
        val m = month.toInt()
        if (d !in 1..31 || m !in 1..12) return null
        return String.format(Locale.US, "%s-%02d-%02d", year, m, d)
    }

    private fun fullName(lines: List<String>): String? {
        val labelled = afterLabel(lines, Regex("""FULL\s*NAMES?|\bNAMES?\b"""))?.takeIf(::looksLikeName)
        val name = labelled ?: lines.filter(::looksLikeName).maxByOrNull { it.length } ?: return null
        return name.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    private fun looksLikeName(line: String): Boolean {
        val words = line.split(Regex("\\s+"))
        return words.size in 2..4 &&
            words.all { it.length >= 2 && it.all { ch -> ch.isLetter() || ch == '\'' || ch == '-' } } &&
            words.none { it.uppercase(Locale.ROOT) in notNameWords }
    }
}

/**
 * Dates for the calendar picker, which counts days as UTC midnight in milliseconds. Answers
 * stay in YYYY-MM-DD, the format the date check expects.
 */
object CalendarDates {
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private val UTC: java.util.TimeZone = java.util.TimeZone.getTimeZone("UTC")
    private val ISO_SHAPE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

    /** What the calendar may offer: [startMillis] is the month it opens on when nothing is picked. */
    data class Limits(val earliestMillis: Long?, val latestMillis: Long?, val startMillis: Long, val years: IntRange) {
        fun allows(millis: Long): Boolean =
            (earliestMillis == null || millis >= earliestMillis) && (latestMillis == null || millis <= latestMillis)
    }

    private fun isoFormat() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
        timeZone = UTC
        isLenient = false
    }

    fun toIso(millis: Long): String = isoFormat().format(java.util.Date(millis))

    fun fromIso(value: String): Long? {
        val trimmed = value.trim()
        if (!ISO_SHAPE.matches(trimmed)) return null
        return runCatching { isoFormat().parse(trimmed)?.time }.getOrNull()
    }

    /** "12 March 1990", or null when the answer is not a full date. */
    fun readable(value: String): String? = fromIso(value)?.let {
        java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.UK).apply { timeZone = UTC }.format(java.util.Date(it))
    }

    /**
     * A date of birth may only be picked if the age check would accept it, and the calendar
     * opens 30 years back. Any other date question allows every date and opens on today.
     */
    fun limitsFor(field: FormField, today: java.util.Calendar): Limits {
        val todayMillis = java.util.Calendar.getInstance(UTC).apply {
            clear()
            set(today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH), today.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val rule = field.validationRules.filterIsInstance<DateFormatAndMinimumAge>().firstOrNull()
            ?: return Limits(null, null, todayMillis, 1900..today.get(java.util.Calendar.YEAR) + 30)
        val latest = yearsBefore(todayMillis, rule.minimumAge)
        val earliest = yearsBefore(todayMillis, rule.maximumAge + 1) + DAY_MILLIS
        val start = yearsBefore(todayMillis, 30).coerceIn(earliest, latest)
        return Limits(earliest, latest, start, yearOf(earliest)..yearOf(latest))
    }

    private fun yearsBefore(millis: Long, years: Int): Long =
        java.util.Calendar.getInstance(UTC).apply { timeInMillis = millis; add(java.util.Calendar.YEAR, -years) }.timeInMillis

    private fun yearOf(millis: Long): Int =
        java.util.Calendar.getInstance(UTC).apply { timeInMillis = millis }.get(java.util.Calendar.YEAR)
}
