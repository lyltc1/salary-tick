package com.example.salarytick.domain

import java.math.BigDecimal

/**
 * 薪资配置。默认值取你实际的情况。
 *
 * ⚠️ 口径来自需求确认：
 *  税前月薪 = 10000（基础工资与绩效工资已合并，不再拆分；首次启动会引导重填）
 *  日薪     = 税前月薪 ÷ 21.75      ← 固定计薪日，**不用当月实际工作日数**
 *  时薪     = 日薪 ÷ 每日工时(8)   ← 固定 8 小时
 *
 * 计薪天数与每日工时都是法定/公司口径，**不开放设置**，
 * 所以这里只剩一个可改的字段：税前月薪。
 *
 * 加班不再另设倍数：**加班工资与平时一样**，所以这里只有一套单价。
 */
data class SalaryConfig(
    /** 税前月薪（基础工资 + 绩效工资合并后的总额） */
    val monthlyGross: BigDecimal = BigDecimal("10000"),
) {
    /** 法定月计薪天数 */
    val legalPaidDays: BigDecimal get() = LEGAL_PAID_DAYS

    /** 每日标准工时 */
    val workHoursPerDay: BigDecimal get() = WORK_HOURS_PER_DAY

    companion object {
        /** 法定月计薪天数：固定 21.75，不可设置 */
        val LEGAL_PAID_DAYS: BigDecimal = BigDecimal("21.75")

        /** 每日标准工时：固定 8 小时，不可设置 */
        val WORK_HOURS_PER_DAY: BigDecimal = BigDecimal("8")
    }
}

/**
 * 某一档的「单价快照」。
 */
data class RateSnapshot(
    val perDay: BigDecimal,
    val perHour: BigDecimal,
    val perMinute: BigDecimal,
    val perSecond: BigDecimal,
)

/**
 * 当天属于哪种日子 —— **决定这一天要不要上班**。
 * 工资口径不跟着它变（加班工资与平时一样），它只回答「上不上班」。
 */
enum class DayType(val label: String, val isWorkday: Boolean) {
    /** 普通工作日 */
    WORKDAY("工作日", isWorkday = true),

    /** 调休补班：本来是周末，但法定安排要上班 */
    MAKEUP_WORKDAY("调休上班", isWorkday = true),

    /** 公司加班日：每月最后一个星期六（若撞上法定节假日则仍算放假） */
    COMPANY_OVERTIME("加班日", isWorkday = true),

    /** 周末休息日 */
    WEEKEND_OFF("休息日", isWorkday = false),

    /** 法定节假日（含调休连起来的假期） */
    LEGAL_HOLIDAY("法定节假日", isWorkday = false),

    /** 请假（预留） */
    PERSONAL_LEAVE("请假", isWorkday = false),
}

/** 全 App 默认值集中在这里 */
object SalaryDefaults {
    val config: SalaryConfig = SalaryConfig()
}
