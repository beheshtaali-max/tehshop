package com.example.fulluikotlin.ui.utils

fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
    val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    var gy = gy - 1600
    var gm = gm - 1
    var gd = gd - 1

    var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
    for (i in 0 until gm) gDayNo += gDaysInMonth[i]
    if (gm > 1 && ((gy + 1600) % 4 == 0 && ((gy + 1600) % 100 != 0 || (gy + 1600) % 400 == 0))) gDayNo += 1
    gDayNo += gd

    var jDayNo = gDayNo - 79

    val jNp = jDayNo / 12053
    jDayNo %= 12053

    var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
    jDayNo %= 1461

    if (jDayNo >= 366) {
        jy += (jDayNo - 1) / 365
        jDayNo = (jDayNo - 1) % 365
    }

    var jm = 0
    var jd = 0
    for (i in 0 until 11) {
        if (jDayNo >= jDaysInMonth[i]) {
            jDayNo -= jDaysInMonth[i]
        } else {
            jm = i + 1
            jd = jDayNo + 1
            break
        }
    }
    if (jm == 0) {
        jm = 12
        jd = jDayNo + 1
    }

    return JalaliDate(jy, jm, jd)
}


data class JalaliDate(
    val year: Int,
    val month: Int,
    val day: Int
)