package com.example.salarytick.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarytick.core.money.formatCny
import com.example.salarytick.domain.SalaryConfig
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 首次启动引导：月薪 / 年薪 / 日薪 任选一种填，统一换算成税前月薪。
 *
 * 换算：年薪 ÷ 12；日薪 × 21.75。
 * 选「稍后再说」直接用默认月薪，之后可从右上角设置里改。
 */
@Composable
fun OnboardingDialog(
    defaultMonthly: BigDecimal,
    onConfirm: (BigDecimal) -> Unit,
    onSkip: () -> Unit,
) {
    var mode by remember { mutableStateOf(SalaryInputMode.MONTHLY) }
    var input by remember { mutableStateOf(defaultMonthly.toPlainString()) }

    val amount: BigDecimal = input.toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?: BigDecimal.ZERO
    val monthly: BigDecimal = mode.toMonthly(amount)

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("先告诉我你的工资") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalaryInputMode.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { mode = item },
                            label = { Text(item.label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.sanitizeAmount() },
                    label = { Text(mode.label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = mode.hint(monthly),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(monthly) },
                enabled = monthly > BigDecimal.ZERO,
            ) { Text("开始") }
        },
        dismissButton = { TextButton(onClick = onSkip) { Text("稍后再说") } },
    )
}

/** 引导里可选的三种填法 */
private enum class SalaryInputMode(val label: String) {
    MONTHLY("月薪"),
    YEARLY("年薪"),
    DAILY("日薪"),
}

private fun SalaryInputMode.toMonthly(amount: BigDecimal): BigDecimal = when (this) {
    SalaryInputMode.MONTHLY -> amount
    SalaryInputMode.YEARLY -> amount.divide(BigDecimal("12"), 2, RoundingMode.HALF_UP)
    SalaryInputMode.DAILY -> amount.multiply(SalaryConfig.LEGAL_PAID_DAYS)
}

private fun SalaryInputMode.hint(monthly: BigDecimal): String = when (this) {
    SalaryInputMode.MONTHLY -> "填税前月薪（基础 + 绩效），之后可在设置里改。"
    SalaryInputMode.YEARLY -> "年薪 ÷ 12 = 税前月薪 ${monthly.formatCny()}"
    SalaryInputMode.DAILY -> "日薪 × 21.75 = 税前月薪 ${monthly.formatCny()}"
}

/** 只留数字和一个小数点，避免 "1.2.3" 这种非法输入 */
private fun String.sanitizeAmount(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    return if (filtered.count { it == '.' } <= 1) filtered else this
}
