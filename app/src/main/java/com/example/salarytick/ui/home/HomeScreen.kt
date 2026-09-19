package com.example.salarytick.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.salarytick.core.money.formatCny
import com.example.salarytick.data.SalarySettings
import com.example.salarytick.data.SettingsRepository
import com.example.salarytick.domain.DayType
import com.example.salarytick.domain.HolidayCalendar
import com.example.salarytick.domain.SalaryConfig
import com.example.salarytick.domain.SalaryDefaults
import com.example.salarytick.domain.SalaryEngine
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 一小时的秒数 —— 主金额按「小时制」走，整点清零 */
private const val HOUR_SECONDS: Long = 3_600L

/**
 * 当前是否在前台（Activity 至少走到 STARTED）。
 *
 * 背景：App 按了 Home 之后，Choreographer 照样给帧、协程里的 delay 循环照样醒 ——
 * 于是粒子每帧推演、金额每 100ms 重算。实测放在后台一分钟 CPU 稳定吃 30%，
 * 手机白白发热掉电。凡是「一直在跑」的循环，都得先看这个开关。
 *
 * 回到前台会立刻变 true，时间类状态在下一拍就刷新，金额不会算错。
 */
@Composable
internal fun rememberIsForeground(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return foreground
}

// 金币卡的调色：金底 + 深棕字，接近「一堆金币」的观感
private val GoldTop = Color(0xFFFFE9A8)
private val GoldMid = Color(0xFFFFCC4D)
private val GoldBottom = Color(0xFFF0A020)
private val OnGold = Color(0xFF5D3A16)
private val OnGoldSoft = Color(0xFF8A6327)

/**
 * 主界面 —— 单页 MVP。
 *
 * 数据流很简单：
 *   心跳 -> 按「当前时刻」重算今天到此刻的入账金额 -> 显示
 *   **每次都是整体重算**，不做逐秒累加，所以中途丢几秒也不会错位。
 *
 * 「烟花金币」：**钱任何时候都在进账** —— 午休、下班、周末也不例外。
 * 主金额走 **小时制**：只累计本小时已过的秒数，**整点清零**重来。
 * 每秒迸一撮金币 + 飘一个秒薪数字；**整分**放几发烟花 + 飘一个分薪数字。
 * 秒薪口径就是普通工资口径（时薪 ÷ 3600，一天按 8 小时）。
 *
 * 加班工资与平时一样，也不再记录上班时段，所以全天任何时刻都是同一套单价。
 */
@Composable
fun SalaryTickApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    // 设置已落盘：改完保存，重启 App 不丢
    var settings by remember { mutableStateOf(repository.load()) }
    var showSettings by remember { mutableStateOf(false) }
    // 没走过引导就弹一次；填完或跳过都会落盘，之后不再打扰
    var showOnboarding by remember { mutableStateOf(!settings.onboarded) }

    val foreground = rememberIsForeground()
    val foregroundNow by rememberUpdatedState(foreground)

    // 日期、当天类型、班段状态，1 秒刷一次就够
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            // 后台不刷：Composition 暂停时写了也没人看，白烧 CPU
            if (foregroundNow) now = LocalDateTime.now()
            delay(1_000L)
        }
    }

    // 每次打开 App 随机一句；靠 remember 存住，页面重绘不会换来换去
    // 点一下这句就换一条 —— 不另设按钮，整块文字就是热区
    var slogan by remember { mutableStateOf(Slogans.random()) }

    val config = settings.config
    val date: LocalDate = now.toLocalDate()
    val time: LocalTime = now.toLocalTime()

    // 法定节假日 / 调休 / 加班日都由日历判定；工资口径不跟着变（加班与平时一样）
    val dayType: DayType = HolidayCalendar.dayTypeOf(
        date = date,
        monthEndSaturdayOvertime = settings.monthEndSaturdayOvertime,
    )
    // 每秒 / 每分钟入账的单价（8 小时口径）
    val perSecond: BigDecimal = SalaryEngine.tickPerSecond(config)
    val perMinute: BigDecimal = SalaryEngine.tickPerMinute(config)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 开了 edge-to-edge，得自己躲开状态栏
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TitleRow(onSettingsClick = { showSettings = true })

            // 每次打开随机换一句；轻点再换一条
            SloganText(slogan = slogan, onRefresh = { slogan = Slogans.next(slogan) })

            HeroCard(
                perSecond = perSecond,
                perMinute = perMinute,
                dayType = dayType,
                date = date,
                secondOfHour = (time.minute * 60 + time.second).toLong(),
                secondTick = time.toSecondOfDay().toLong(),
                minuteTick = (time.hour * 60 + time.minute).toLong(),
                hourTick = date.toEpochDay() * 24 + time.hour,
            )

            RateRows(
                monthly = config.monthlyGross,
                daily = SalaryEngine.dailyRate(config),
                hourly = SalaryEngine.hourlyRate(config),
            )

            WorkCalendarCard(
                today = date,
                monthEndSaturdayOvertime = settings.monthEndSaturdayOvertime,
            )

            VersionFooter()
        }
    }

    if (showOnboarding) {
        OnboardingDialog(
            defaultMonthly = SalaryDefaults.config.monthlyGross,
            onConfirm = { monthly ->
                val next = settings.copy(
                    config = SalaryConfig(monthlyGross = monthly),
                    onboarded = true,
                )
                settings = next
                repository.save(next)
                showOnboarding = false
            },
            onSkip = {
                val next = settings.copy(onboarded = true)
                settings = next
                repository.save(next)
                showOnboarding = false
            },
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onConfirm = { newSettings ->
                settings = newSettings
                repository.save(newSettings)
                showSettings = false
            },
        )
    }
}

@Composable
private fun TitleRow(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SalaryTick",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        TextButton(onClick = onSettingsClick) { Text("设置") }
    }
}

/**
 * 随机一句的展示位。
 *
 * 文案长短差很多，所以这里占满整宽、独立成行（不再挤在标题旁边），
 * 最多三行、超出才省略 —— 最长那句也就两行半，正常不会被截断。
 *
 * 句子本身自带标点就够（名人名言的署名用破折号跟在后面），
 * 这里不再额外套引号 —— 引号一多，反而像在喊口号。
 *
 * 整块文字都可点，点一下换一句：不留按钮，观感上它只是一句文案。
 */
@Composable
private fun SloganText(slogan: String, onRefresh: () -> Unit) {
    Text(
        text = slogan,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRefresh() },
        fontSize = 13.sp,
        lineHeight = 20.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HeroCard(
    perSecond: BigDecimal,
    perMinute: BigDecimal,
    dayType: DayType,
    date: LocalDate,
    /** 本小时已经过去的秒数（0~3599），整点归零 */
    secondOfHour: Long,
    /** 秒 / 分节拍：变一次就触发一次金币与飘字 */
    secondTick: Long,
    minuteTick: Long,
    /** 整点节拍：变一次就来一场金币雨 */
    hourTick: Long,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(listOf(GoldTop, GoldMid, GoldBottom)),
                    shape = RoundedCornerShape(24.dp),
                ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 撞上法定节假日就直接报假期名（春节 / 国庆 …），比「法定节假日」好认
                    Chip(text = HolidayCalendar.holidayNameOf(date)?.let { "${it}假期" } ?: dayType.label)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${date.monthValue}月${date.dayOfMonth}日",
                        fontSize = 13.sp,
                        color = OnGoldSoft,
                    )
                }

                // 金额 + 金币烟花：整个区域的「爽点」都在这
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CoinFirework(
                        modifier = Modifier.fillMaxSize(),
                        secondTick = secondTick,
                        minuteTick = minuteTick,
                        hourTick = hourTick,
                        secondLabel = "+${perSecond.formatCny()}",
                        minuteLabel = "+${perMinute.formatCny()}",
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TickingAmount(perSecond = perSecond)
                        Text(
                            text = "本小时已入账 · 整点清零",
                            fontSize = 12.sp,
                            color = OnGoldSoft,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 每秒 / 每分的额度不再写成文字，交给上面的金币飘字去「演」，
                // 这里只用一条连续爬动的进度条表示本小时走了多少。
                AnimatedHourProgress(progress = secondOfHour.toFloat() / HOUR_SECONDS)
            }
        }
    }
}

/**
 * 金额本体 —— 自带 100ms 心跳。
 *
 * 单独切成一块，是为了让高频刷新只重组这几个数字，不连带整页重排。
 */
@Composable
private fun TickingAmount(perSecond: BigDecimal, modifier: Modifier = Modifier) {
    val foreground by rememberUpdatedState(rememberIsForeground())
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            // 100ms 的心跳只在前台有意义；后台让它睡死，省电
            if (foreground) now = LocalDateTime.now()
            delay(100L)
        }
    }

    // 小时制：只算「本小时已经过去的秒数」，一到整点自然清零重来
    val time = now.toLocalTime()
    // 秒 + 纳秒：钱按亚秒步长连续走，而不是一秒才跳一格
    val elapsedSeconds = BigDecimal.valueOf(time.minute.toLong() * 60 + time.second.toLong())
        .add(BigDecimal.valueOf(now.nano.toLong(), 9))
    // 每次整体重算（整点起 × 秒薪），跑到整点也不会有累积误差
    val amount = SalaryEngine.earned(perSecond, elapsedSeconds)

    // 每秒一次心跳放大，和「一秒入账一次」的节拍对齐
    val pulse by rememberInfiniteTransition(label = "amountPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Text(
        text = amount.formatCny(),
        fontSize = 44.sp,
        fontWeight = FontWeight.ExtraBold,
        color = OnGold,
        modifier = modifier.graphicsLayer {
            scaleX = 1f + 0.05f * pulse
            scaleY = 1f + 0.05f * pulse
        },
    )
}

/**
 * 本小时进度 —— 用「持续爬动」的进度条代替原来那行文字说明。
 * 上游每秒给一个新值，这里用线性补间摊平，看上去是一条一直在走的线。
 */
@Composable
private fun AnimatedHourProgress(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
        label = "hourProgress",
    )
    LinearProgressBar(
        progress = animated,
        trackColor = OnGold.copy(alpha = 0.15f),
        barColor = OnGold,
        modifier = modifier,
    )
}

/** 自己画进度条，避免依赖不同版本 Compose 进度组件的 API 差异 */
@Composable
private fun LinearProgressBar(
    progress: Float,
    trackColor: Color,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor),
        )
    }
}

@Composable
private fun Chip(text: String, highlighted: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (highlighted) OnGold
                else OnGold.copy(alpha = 0.14f)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (highlighted) GoldTop else OnGold,
        )
    }
}

/** 月薪 / 日薪 / 时薪 三件套，恒按劳动合同口径 */
@Composable
private fun RateRows(monthly: BigDecimal, daily: BigDecimal, hourly: BigDecimal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "薪资构成",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RateRow(label = "税前月薪", value = monthly.formatCny())
            ThinDivider()
            RateRow(label = "日薪", value = daily.formatCny())
            ThinDivider()
            RateRow(label = "时薪", value = hourly.formatCny())
        }
    }
}

/** 页面最底一行版本，弱化到「扫一眼能看到，但不抢戏」 */
@Composable
private fun VersionFooter() {
    val version = rememberAppVersion()
    if (version.isEmpty()) return
    Text(
        text = "SalaryTick $version",
        modifier = Modifier.fillMaxWidth(),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun RateRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
