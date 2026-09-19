package com.example.salarytick.data

import android.content.Context
import com.example.salarytick.domain.SalaryConfig
import com.example.salarytick.domain.SalaryDefaults
import java.math.BigDecimal

/**
 * 一份完整的用户设置 —— 只有薪资口径本身，
 * 加班倍数、上班时段都不再记录（加班工资与平时一样）。
 */
data class SalarySettings(
    val config: SalaryConfig = SalaryDefaults.config,
    /** 是否已经走过首次启动引导（填过一次薪资） */
    val onboarded: Boolean = false,
)

/**
 * 设置的本地存取。
 *
 * 用 framework 自带的 SharedPreferences，**不引入 Room / DataStore**，
 * 免得再下一轮大依赖。这里只有几个字段，完全够用。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SalarySettings = SalarySettings(
        config = SalaryConfig(
            monthlyGross = loadAmount(KEY_MONTHLY, SalaryDefaults.config.monthlyGross),
        ),
        onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
    )

    fun save(settings: SalarySettings) {
        prefs.edit()
            .putString(KEY_MONTHLY, settings.config.monthlyGross.toPlainString())
            .putBoolean(KEY_ONBOARDED, settings.onboarded)
            .apply()
    }

    private fun loadAmount(key: String, fallback: BigDecimal): BigDecimal =
        prefs.getString(key, null)
            ?.toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
            ?: fallback

    companion object {
        private const val PREFS_NAME = "salary_tick_settings"
        private const val KEY_MONTHLY = "monthly_gross"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
