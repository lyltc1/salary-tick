package com.example.salarytick.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarytick.core.money.formatCny
import com.example.salarytick.data.SalarySettings
import com.example.salarytick.domain.SalaryConfig
import com.example.salarytick.domain.SalaryEngine
import java.math.BigDecimal

/**
 * 薪资设置弹窗。改动会写进本地存储，重启 App 不丢。
 *
 * 可改的就两项：
 *   1. 税前月薪
 *   2. 月末周六算不算加班（每月最后一个星期六），默认打开
 * 每日工时固定 8 小时、月计薪天数固定 21.75，都是法定/公司口径，不再提供设置入口。
 */
@Composable
fun SettingsDialog(
    settings: SalarySettings,
    onDismiss: () -> Unit,
    onConfirm: (SalarySettings) -> Unit,
) {
    val config = settings.config

    // 口径说明区用的单价
    val daily = SalaryEngine.dailyRate(config)
    val hourly = SalaryEngine.hourlyRate(config)

    var monthly by remember { mutableStateOf(config.monthlyGross.toPlainString()) }
    var monthEndSaturday by remember { mutableStateOf(settings.monthEndSaturdayOvertime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionLabel("薪资")
                NumberField("税前月薪", monthly) { monthly = it }

                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("加班日")
                ToggleRow(
                    title = "月末周六算加班",
                    detail = "每月最后一个星期六记为公司加班日；关掉后按普通周末休息。",
                    checked = monthEndSaturday,
                    onCheckedChange = { monthEndSaturday = it },
                )

                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel("计算口径")
                ExplanationRow("日薪", "税前月薪 ${config.monthlyGross.formatCny()} ÷ ${config.legalPaidDays.toPlainString()}（法定计薪天数）= ${daily.formatCny()}/天")
                ExplanationRow("时薪", "日薪 ÷ ${config.workHoursPerDay.toPlainString()}h（每日标准工时）= ${hourly.formatCny()}/小时")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        settings.copy(
                            config = SalaryConfig(
                                monthlyGross = monthly.toAmountOr(config.monthlyGross),
                            ),
                            monthEndSaturdayOvertime = monthEndSaturday,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 一行开关：标题 + 说明 + Switch */
@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 口径说明的一行：名词 + 公式 */
@Composable
private fun ExplanationRow(label: String, detail: String) {
    Text(
        text = "$label  $detail",
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == '.' }
            // 只接受一个小数点，避免 "1.2.3" 这种非法输入
            if (filtered.count { it == '.' } <= 1) onValueChange(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 输入不合法时回退到原值，不让非法输入炸掉计算 */
private fun String.toAmountOr(fallback: BigDecimal): BigDecimal =
    this.toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?: fallback
