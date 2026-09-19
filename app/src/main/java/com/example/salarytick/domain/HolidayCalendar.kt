package com.example.salarytick.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 法定节假日 / 调休 / 公司加班日 —— **只回答「这一天要不要上班」**。
 *
 * ── 数据来源 ──────────────────────────────────────────────
 * 《国务院办公厅关于 2025 年部分节假日安排的通知》（国办发明电〔2024〕5 号）：
 *   元旦   1月1日(周三) 放假 1 天
 *   春节   1月28日(周二, 除夕)–2月4日(周二) 放假 8 天；1月26日(周日)、2月8日(周六) 上班
 *   清明   4月4日(周五)–4月6日(周日) 放假 3 天
 *   劳动节 5月1日(周四)–5月5日(周一) 放假 5 天；4月27日(周日) 上班
 *   端午   5月31日(周六)–6月2日(周一) 放假 3 天
 *   国庆+中秋 10月1日(周三)–10月8日(周三) 放假 8 天；9月28日(周日)、10月11日(周六) 上班
 *
 * 《国务院办公厅关于 2026 年部分节假日安排的通知》（新华社 2025-11-04 受权发布）：
 *   元旦   1月1日(周四)–1月3日(周六) 放假 3 天；1月4日(周日) 上班
 *   春节   2月15日(周日)–2月23日(周一) 放假 9 天；2月14日(周六)、2月28日(周六) 上班
 *   清明   4月4日(周六)–4月6日(周一) 放假 3 天
 *   劳动节 5月1日(周五)–5月5日(周二) 放假 5 天；5月9日(周六) 上班
 *   端午   6月19日(周五)–6月21日(周日) 放假 3 天
 *   中秋   9月25日(周五)–9月27日(周日) 放假 3 天
 *   国庆   10月1日(周四)–10月7日(周三) 放假 7 天；9月20日(周日)、10月10日(周六) 上班
 *
 * ── 判定优先级 ────────────────────────────────────────────
 *   1. 法定节假日（含调休连休）-> 放假
 *   2. 调休补班（周末但要上班）-> 上班
 *   3. 每月最后一个星期六，且不是法定节假日 -> 公司加班日
 *   4. 周六 / 周日 -> 休息
 *   5. 其余 -> 工作日
 *
 * ⚠️ 官方通知每年 11 月左右才发布次年安排，所以只内置了 2025、2026 两年。
 *    翻到没有数据的年份时，[hasPlan] 会返回 false，界面上要给出提示，
 *    别让人误以为「没标红 = 不放假」。
 */
object HolidayCalendar {

    /** 一段假期：闭区间 [start, end]，带一个名字用来在日历上标注 */
    private class HolidayRange(
        val start: LocalDate,
        val end: LocalDate,
        val name: String,
    ) {
        operator fun contains(date: LocalDate): Boolean =
            !date.isBefore(start) && !date.isAfter(end)
    }

    /** 一年的完整安排 */
    private class YearPlan(
        val holidays: List<HolidayRange>,
        /** 调休补班：落在周末但要上班的那几天 */
        val makeupWorkdays: Set<LocalDate>,
    ) {
        fun holidayNameOf(date: LocalDate): String? =
            holidays.firstOrNull { date in it }?.name
    }

    private val YEAR_2025 = YearPlan(
        holidays = listOf(
            HolidayRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), "元旦"),
            HolidayRange(LocalDate.of(2025, 1, 28), LocalDate.of(2025, 2, 4), "春节"),
            HolidayRange(LocalDate.of(2025, 4, 4), LocalDate.of(2025, 4, 6), "清明"),
            HolidayRange(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 5), "劳动节"),
            HolidayRange(LocalDate.of(2025, 5, 31), LocalDate.of(2025, 6, 2), "端午"),
            HolidayRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 8), "国庆"),
        ),
        makeupWorkdays = setOf(
            LocalDate.of(2025, 1, 26),
            LocalDate.of(2025, 2, 8),
            LocalDate.of(2025, 4, 27),
            LocalDate.of(2025, 9, 28),
            LocalDate.of(2025, 10, 11),
        ),
    )

    private val YEAR_2026 = YearPlan(
        holidays = listOf(
            HolidayRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), "元旦"),
            HolidayRange(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23), "春节"),
            HolidayRange(LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6), "清明"),
            HolidayRange(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), "劳动节"),
            HolidayRange(LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21), "端午"),
            HolidayRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27), "中秋"),
            HolidayRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), "国庆"),
        ),
        makeupWorkdays = setOf(
            LocalDate.of(2026, 1, 4),
            LocalDate.of(2026, 2, 14),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 5, 9),
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 10, 10),
        ),
    )

    private val PLANS: Map<Int, YearPlan> = mapOf(
        2025 to YEAR_2025,
        2026 to YEAR_2026,
    )

    /** 已经内置官方数据的年份 */
    val dataYears: List<Int> = PLANS.keys.sorted()

    /** 这一年有没有官方节假日数据 */
    fun hasPlan(year: Int): Boolean = PLANS.containsKey(year)

    /** 落在这天头上的假期名（春节 / 国庆 …），没有则 null */
    fun holidayNameOf(date: LocalDate): String? = PLANS[date.year]?.holidayNameOf(date)

    /** 每月最后一个星期六 —— 公司加班日的候选日 */
    fun lastSaturdayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.lastInMonth(DayOfWeek.SATURDAY))

    /** 这一天属于哪种日子 */
    fun dayTypeOf(date: LocalDate): DayType {
        val plan = PLANS[date.year]
        if (plan != null) {
            if (plan.holidays.any { date in it }) return DayType.LEGAL_HOLIDAY
            if (date in plan.makeupWorkdays) return DayType.MAKEUP_WORKDAY
        }

        // 每月最后一个星期六：不是法定节假日，就按公司加班日算
        if (date == lastSaturdayOf(date)) return DayType.COMPANY_OVERTIME

        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        return if (weekend) DayType.WEEKEND_OFF else DayType.WORKDAY
    }
}
