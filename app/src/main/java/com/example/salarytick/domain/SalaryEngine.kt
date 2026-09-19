package com.example.salarytick.domain

import com.example.salarytick.core.money.divRate
import com.example.salarytick.core.money.toMoney
import java.math.BigDecimal

/**
 * 薪资引擎 —— **整个 App 唯一允许算钱的地方**。
 * UI 层不许自己乘除，一律从这里取值，否则以后对不上实际收入。
 *
 * ── 口径（用户已锁定）────────────────────────────────────
 *  日薪   = 月度应发 ÷ 21.75
 *  时薪   = 日薪 ÷ 每日工时(8)
 *  分薪   = 时薪 ÷ 60
 *  秒薪   = 时薪 ÷ 3600
 *  金额一律**税前**
 *
 *  加班工资与平时一样，所以不再有加班倍数、也不记录上班时段：
 *  全天任何时刻都按同一套单价走。
 * ────────────────────────────────────────────────────────
 */
object SalaryEngine {

    private val MINUTES_PER_HOUR: BigDecimal = BigDecimal("60")
    private val SECONDS_PER_HOUR: BigDecimal = BigDecimal("3600")

    /** 日薪 = 月度应发 ÷ 21.75 */
    fun dailyRate(config: SalaryConfig): BigDecimal =
        config.monthlyGross.divRate(config.legalPaidDays)

    /** 时薪 = 日薪 ÷ 每日工时 */
    fun hourlyRate(config: SalaryConfig): BigDecimal =
        dailyRate(config).divRate(config.workHoursPerDay)

    /** 由时薪展开出全套单价 */
    fun rates(config: SalaryConfig): RateSnapshot {
        val hourly = hourlyRate(config)
        return RateSnapshot(
            perDay = dailyRate(config),
            perHour = hourly,
            perMinute = hourly.divRate(MINUTES_PER_HOUR),
            perSecond = hourly.divRate(SECONDS_PER_HOUR),
        )
    }

    /**
     * 今日已赚 = 每秒单价 × 已流逝秒数。
     *
     * ⚠️ 这里是**每次重算**，而不是把上一秒的结果累加 ——
     *    逐秒累加会让误差不断累积，跑一天能漂出好几毛钱。
     */
    fun earned(perSecondRate: BigDecimal, elapsedSeconds: Long): BigDecimal =
        earned(perSecondRate, BigDecimal.valueOf(elapsedSeconds))

    /** 同上，但支持带小数的秒数 —— 让金额能按亚秒步长连续走 */
    fun earned(perSecondRate: BigDecimal, elapsedSeconds: BigDecimal): BigDecimal =
        perSecondRate
            .multiply(elapsedSeconds)
            .toMoney()

    /**
     * 「实时入账」用的每秒单价 —— 界面上**每一颗金币 = 一秒入账**。
     *
     * 口径就是普通工资口径（时薪 ÷ 3600，**每天按 8 小时算**），
     * 不因为要摊到 24 小时而改变，所以对得上「一天班」的实际收入。
     */
    fun tickPerSecond(config: SalaryConfig): BigDecimal =
        rates(config).perSecond

    /** 同上，换成「每分钟进账多少」—— 整分那一刻的烟花额度 */
    fun tickPerMinute(config: SalaryConfig): BigDecimal =
        rates(config).perMinute
}
