package com.example.salarytick.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 金额工具 —— 全 App 只在这里定义「一分钱怎么舍入」。
 *
 * 口径（用户已锁定）：财务口径一律两位小数，HALF_UP（四舍五入）。
 * 策略：计算**中途**保留 10 位小数，只在**最后落到屏幕上**的时候才收成两位，
 *       否则每一步都四舍五入，误差会一路累积。
 */

/** 计算中途用的除法：不丢精度 */
fun BigDecimal.divRate(rate: BigDecimal): BigDecimal =
    this.divide(rate, 10, RoundingMode.HALF_UP)

/** 收口：转成真正的「金额」精度 */
fun BigDecimal.toMoney(): BigDecimal =
    this.setScale(2, RoundingMode.HALF_UP)

private fun formatter(pattern: String): DecimalFormat =
    DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.CHINA))

/** ¥1,234.56 —— 带千分位 */
fun BigDecimal.formatCny(): String =
    "¥" + formatter("#,##0.00").format(this.toMoney())

/**
 * 秒薪 / 分薪这种极小值用，保留指定位数。
 * 例如 0.071055 元/秒，收成两位就变成 0.07 看不出变化了。
 */
fun BigDecimal.formatRate(scale: Int): String {
    require(scale >= 0) { "scale 不能为负" }
    val pattern = if (scale == 0) {
        "#,##0"
    } else {
        "#,##0." + "0".repeat(scale)
    }
    return formatter(pattern).format(this.setScale(scale, RoundingMode.HALF_UP))
}

/** 秒 -> 「2 小时 30 分」这种人话 */
fun Long.formatDuration(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    return when {
        h > 0 -> "$h 小时 $m 分"
        m > 0 -> "$m 分"
        else -> "$this 秒"
    }
}
