package com.example.salarytick.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salarytick.domain.DayType
import com.example.salarytick.domain.HolidayCalendar
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 工作日日历 —— 一眼看出「哪天要上班」。
 *
 * 每一格按当天类型上色：
 *   工作日 = 主色块 / 调休上班 = 第三色 / 加班日 = 副色 / 休息日 = 灰 / 法定节假日 = 红
 * 点一下某天，下面会说明这一天到底是什么日子。
 *
 * 月份可以左右翻，最早只能翻到 2025 年 1 月（再往前没有节假日数据，也没意义）。
 * 官方节假日数据目前内置 2025、2026 两年，翻到没数据的年份会给出提示。
 */
@Composable
fun WorkCalendarCard(
    today: LocalDate,
    /** 月末周六（每月最后一个星期六）算不算加班日 —— 设置里的开关 */
    monthEndSaturdayOvertime: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 只存「当月 1 号」，翻月就是加减一个月；往前不允许越过 2025 年 1 月
    var monthStart by remember {
        mutableStateOf(today.withDayOfMonth(1).coerceAtLeast(EARLIEST_MONTH))
    }
    var selected by remember { mutableStateOf(today) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            MonthHeader(
                monthStart = monthStart,
                today = today,
                canGoBack = monthStart > EARLIEST_MONTH,
                onJumpToday = {
                    monthStart = today.withDayOfMonth(1).coerceAtLeast(EARLIEST_MONTH)
                    selected = today
                },
                onShift = { months ->
                    monthStart = monthStart
                        .plusMonths(months.toLong())
                        .coerceAtLeast(EARLIEST_MONTH)
                },
            )

            Spacer(modifier = Modifier.height(10.dp))
            WeekdayHeader()

            val cells = remember(monthStart) { monthCells(monthStart) }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(modifier = Modifier.weight(1f).height(38.dp))
                        } else {
                            DayCell(
                                date = date,
                                type = HolidayCalendar.dayTypeOf(date, monthEndSaturdayOvertime),
                                selected = date == selected,
                                isToday = date == today,
                                onClick = { selected = date },
                            )
                        }
                    }
                }
            }

            if (!HolidayCalendar.hasPlan(monthStart.year)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠ ${monthStart.year} 年还没有官方节假日数据（目前内置 2025–2026，国务院一般在前一年 11 月发布），" +
                        "这里只按周末和每月最后一个星期六判断。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Legend()

            Spacer(modifier = Modifier.height(12.dp))
            SelectedDetail(
                selected = selected,
                monthEndSaturdayOvertime = monthEndSaturdayOvertime,
            )
        }
    }
}

/** 月份标题 + 左右翻月 + 回到今天 */
@Composable
private fun MonthHeader(
    monthStart: LocalDate,
    today: LocalDate,
    canGoBack: Boolean,
    onJumpToday: () -> Unit,
    onShift: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${monthStart.year}年${monthStart.monthValue}月",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (monthStart.year != today.year || monthStart.monthValue != today.monthValue) {
            TextButton(onClick = onJumpToday) { Text("今天") }
        }
        TextButton(onClick = { onShift(-1) }, enabled = canGoBack) { Text("‹") }
        TextButton(onClick = { onShift(1) }) { Text("›") }
    }
}

/** 周一开头的表头（国内习惯） */
@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEach { label ->
            Box(
                modifier = Modifier.weight(1f).height(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** ⚠️ 必须是 RowScope 扩展：格子里的 Modifier.weight 只有在 Row 作用域里才解析得到 */
@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    type: DayType,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    val (bg, ink) = dayColors(type)
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.onSurface
        isToday -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .padding(2.dp)
            .clip(shape)
            .background(bg)
            .border(width = if (borderColor == Color.Transparent) 0.dp else 2.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = ink,
        )
    }
}

/** 图例：分两行排，免得挤成一坨 */
@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(DayType.WORKDAY)
            LegendDot(DayType.MAKEUP_WORKDAY)
            LegendDot(DayType.COMPANY_OVERTIME)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(DayType.WEEKEND_OFF)
            LegendDot(DayType.LEGAL_HOLIDAY)
        }
    }
}

@Composable
private fun LegendDot(type: DayType) {
    val (bg, ink) = dayColors(type)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bg),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = type.label,
            fontSize = 12.sp,
            color = ink,
        )
    }
}

/** 选中那一天到底是怎么回事 */
@Composable
private fun SelectedDetail(
    selected: LocalDate,
    monthEndSaturdayOvertime: Boolean,
) {
    val type = HolidayCalendar.dayTypeOf(selected, monthEndSaturdayOvertime)
    val holiday = HolidayCalendar.holidayNameOf(selected)
    val detail = buildString {
        append("${selected.monthValue}月${selected.dayOfMonth}日")
        append(" · 周${WEEKDAY_LABELS[(selected.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7]}")
        append(" · ${type.label}")
        append(if (type.isWorkday) " · 要上班" else " · 不上班")
        if (holiday != null) append("（$holiday）")
        if (type == DayType.COMPANY_OVERTIME) append(" · 每月最后一个星期六")
    }
    Text(
        text = detail,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 每档颜色：底色 + 文字色 */
@Composable
private fun dayColors(type: DayType): Pair<Color, Color> = when (type) {
    DayType.WORKDAY ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

    DayType.MAKEUP_WORKDAY ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

    DayType.COMPANY_OVERTIME ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

    DayType.LEGAL_HOLIDAY ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

    DayType.WEEKEND_OFF,
    DayType.PERSONAL_LEAVE,
    ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

/** 把一个月铺成 7 列网格，前后补 null，周一开头 */
private fun monthCells(monthStart: LocalDate): List<LocalDate?> {
    val leading = (monthStart.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val length = monthStart.lengthOfMonth()
    val cells = ArrayList<LocalDate?>(42)
    repeat(leading) { cells.add(null) }
    for (day in 1..length) {
        cells.add(LocalDate.of(monthStart.year, monthStart.monthValue, day))
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 能翻到的最早月份：再往前既没有节假日数据，也过了算工资有意义的区间 */
private val EARLIEST_MONTH: LocalDate = LocalDate.of(2025, 1, 1)
