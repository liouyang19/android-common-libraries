package com.taisau.android.common.utils.jvm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date

object DateUtils {

    const val PATTERN_DATE = "yyyy-MM-dd"
    const val PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss"
    const val PATTERN_DATETIME_MS = "yyyy-MM-dd HH:mm:ss.SSS"
    const val PATTERN_TIME = "HH:mm:ss"
    const val PATTERN_ISO = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    private val defaultZone = ZoneId.systemDefault()

    fun now(): LocalDateTime = LocalDateTime.now()

    fun nowDate(): LocalDate = LocalDate.now()

    fun nowMillis(): Long = System.currentTimeMillis()

    fun format(date: LocalDateTime, pattern: String = PATTERN_DATETIME): String {
        return date.format(DateTimeFormatter.ofPattern(pattern))
    }

    fun format(date: LocalDate, pattern: String = PATTERN_DATE): String {
        return date.format(DateTimeFormatter.ofPattern(pattern))
    }

    fun format(millis: Long, pattern: String = PATTERN_DATETIME): String {
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), defaultZone)
        return format(date, pattern)
    }

    fun parseDate(str: String, pattern: String = PATTERN_DATE): LocalDate? {
        return try {
            LocalDate.parse(str, DateTimeFormatter.ofPattern(pattern))
        } catch (e: Exception) {
            null
        }
    }

    fun parseDateTime(str: String, pattern: String = PATTERN_DATETIME): LocalDateTime? {
        return try {
            LocalDateTime.parse(str, DateTimeFormatter.ofPattern(pattern))
        } catch (e: Exception) {
            null
        }
    }

    fun toMillis(dateTime: LocalDateTime): Long {
        return dateTime.atZone(defaultZone).toInstant().toEpochMilli()
    }

    fun toDate(dateTime: LocalDateTime): Date {
        return Date.from(dateTime.atZone(defaultZone).toInstant())
    }

    fun fromDate(date: Date): LocalDateTime {
        return LocalDateTime.ofInstant(date.toInstant(), defaultZone)
    }

    fun toZonedDateTime(millis: Long, zone: ZoneId = defaultZone): ZonedDateTime {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Long {
        return ChronoUnit.DAYS.between(from, to)
    }

    fun hoursBetween(from: LocalDateTime, to: LocalDateTime): Long {
        return ChronoUnit.HOURS.between(from, to)
    }

    fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long {
        return ChronoUnit.MINUTES.between(from, to)
    }

    fun secondsBetween(from: LocalDateTime, to: LocalDateTime): Long {
        return ChronoUnit.SECONDS.between(from, to)
    }

    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }

    fun getAge(birthDate: LocalDate): Int {
        val today = LocalDate.now()
        return ChronoUnit.YEARS.between(birthDate, today).toInt()
    }

    fun startOfDay(date: LocalDate): LocalDateTime {
        return date.atStartOfDay()
    }

    fun endOfDay(date: LocalDate): LocalDateTime {
        return date.atTime(23, 59, 59, 999_999_999)
    }

    fun firstDayOfMonth(date: LocalDate): LocalDate {
        return date.withDayOfMonth(1)
    }

    fun lastDayOfMonth(date: LocalDate): LocalDate {
        return date.withDayOfMonth(date.lengthOfMonth())
    }

    fun isToday(millis: Long): Boolean {
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), defaultZone).toLocalDate()
        return date == LocalDate.now()
    }

    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val date1 = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis1), defaultZone).toLocalDate()
        val date2 = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis2), defaultZone).toLocalDate()
        return date1 == date2
    }

    fun getCurrentQuarter(): Int {
        val month = LocalDate.now().monthValue
        return (month - 1) / 3 + 1
    }

    fun getWeekOfYear(date: LocalDate = LocalDate.now()): Int {
        return date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
    }
}
