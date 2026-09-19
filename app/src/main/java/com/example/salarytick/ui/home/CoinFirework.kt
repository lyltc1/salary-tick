package com.example.salarytick.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * 「烟花金币」—— 真·烟花：
 *   整分：连发几枚火箭窜上天，到顶炸成一圈彩色火星 + 一把金币，配一记爆闪；
 *   整点：再追加一场金币雨，把「这一小时赚到了」这件事做成庆祝；
 *   每秒：底部窜一小撮金币，节奏轻一点，负责连续感。
 *
 * 做法参考 Jaewoong Eum 的 Confetti Burst 一文（doveletter.dev）：
 *   · 粒子用**普通 var 字段**，不进 Compose 状态，省掉每颗粒子的状态开销
 *   · 一个 withFrameNanos 循环推演全部粒子，每帧只写 **一个** 时间戳 state
 *   · Canvas 单遍绘制，不产生任何每粒子重组
 * 于是挂一整天也只是「每帧重绘」，不会拖慢界面。
 *
 * 坐标一律用 **0~1 的比例**（x 相对宽度、y 相对高度），速度是「比例/秒」，
 * 这样粒子系统完全不依赖画布像素尺寸，转屏 / 不同屏幕自适应。
 *
 * ⚠️ 配色要点：卡片底本来就是金色，金币必须**亮面 + 深棕描边**才看得出来；
 *    纯金粒子贴金底会直接糊掉（踩过这个坑）。彩色火星则走高饱和，才能在金底上跳出来。
 */

/** 金币重力：单位 = 画布高度比例 / 秒² */
private const val COIN_GRAVITY = 1.25f

/** 金币空气阻力：速度按 exp(-k·dt) 衰减，与帧率无关 */
private const val COIN_DRAG = 0.85f

/** 火星更轻、更「飘」，衰减也更快 —— 这样才像烟花余烬 */
private const val SPARK_GRAVITY = 0.55f
private const val SPARK_DRAG = 1.55f

/** 火箭上升段的重力，比金币小，弧线更挺拔 */
private const val ROCKET_GRAVITY = 0.32f

/** 整分连发几枚火箭 */
private const val ROCKETS_PER_MINUTE = 3

/** 整点额外追加的火箭 */
private const val ROCKETS_PER_HOUR = 2

/** 每一发爆开的彩色火星数 */
private const val BURST_SPARKS = 46

/** 每一发炸出来的金币数 */
private const val BURST_COINS = 14

/** 整点金币雨的颗数 */
private const val RAIN_COINS = 22

/** 每秒那一小撮：金币 + 零星火星 */
private const val SECOND_COINS = 4
private const val SECOND_SPARKS = 3

/** 粒子上限，超出就不再新增，避免长时间挂着越积越多 */
private const val MAX_PARTICLES = 460

private enum class Kind { COIN, SPARK, FLASH }

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    /** 半径，单位 = min(宽, 高) 的比例 */
    val radius: Float,
    var spin: Float,
    val spinSpeed: Float,
    val life: Float,
    val kind: Kind,
    val color: Color,
    val gravity: Float,
    val drag: Float,
    /** 闪烁相位，让每颗火星的明灭不同步 */
    val twinkleSeed: Float,
    var prevX: Float = x,
    var prevY: Float = y,
    var age: Float = 0f,
)

/** 升空中的火箭：冲到 targetY 就炸 */
private class Rocket(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val targetY: Float,
    val hue: Float,
    var prevX: Float = x,
    var prevY: Float = y,
    var age: Float = 0f,
)

private class LabelParticle(
    var x: Float,
    var y: Float,
    val vy: Float,
    val layout: TextLayoutResult,
    val ink: Color,
    /** 分薪用大号描边，秒薪用小号 */
    val strong: Boolean,
    val life: Float,
    var age: Float = 0f,
)

/**
 * 金币烟花层。
 *
 * @param secondTick 每秒变化的计数：变化一次 -> 底部窜一小撮金币 + 一个秒薪飘字
 * @param minuteTick 每分变化的计数：变化一次 -> 连发火箭，炸成烟花 + 一个分薪飘字
 * @param hourTick 每小时变化的计数：变化一次 -> 追加一场金币雨
 * @param secondLabel 秒薪文本，如 "+¥0.07"
 * @param minuteLabel 分薪文本，如 "+¥4.26"
 */
@Composable
fun CoinFirework(
    secondTick: Long,
    minuteTick: Long,
    hourTick: Long,
    secondLabel: String,
    minuteLabel: String,
    modifier: Modifier = Modifier,
) {
    val particles = remember { ArrayList<Particle>(320) }
    val rockets = remember { ArrayList<Rocket>(8) }
    val labels = remember { ArrayList<LabelParticle>(16) }
    val textMeasurer = rememberTextMeasurer()

    // 币面上的 ¥ 只有一种，建一次就够，别每帧 measure
    val yenStyle = remember {
        TextStyle(color = CoinInk, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
    val yenLayout = remember(textMeasurer) { textMeasurer.measure("¥", yenStyle) }

    // 每帧只写这一个值，Canvas 靠它失效重绘
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            // 掉帧 / 后台回来时 dt 会很大，夹住 50ms，免得重力把粒子甩飞
            val dt = ((now - last) / 1_000_000f).coerceAtMost(50f) / 1000f
            last = now
            advance(particles, rockets, labels, dt) { rocket ->
                // 到顶 -> 炸。这里就是「烟花本体」
                explode(particles, rocket.x, rocket.y, rocket.hue)
            }
            frameNanos = now
        }
    }

    LaunchedEffect(secondTick) {
        if (particles.size < MAX_PARTICLES) {
            spawnFizz(particles)
        }
        labels.add(newLabel(textMeasurer, secondLabel, strong = false))
    }

    LaunchedEffect(minuteTick) {
        // 连发，间隔 180ms，比同时炸开更像一场表演
        repeat(ROCKETS_PER_MINUTE) { index ->
            if (index > 0) kotlinx.coroutines.delay(180L)
            launchRocket(rockets)
        }
        labels.add(newLabel(textMeasurer, minuteLabel, strong = true))
    }

    LaunchedEffect(hourTick) {
        kotlinx.coroutines.delay(320L)
        repeat(ROCKETS_PER_HOUR) { index ->
            if (index > 0) kotlinx.coroutines.delay(200L)
            launchRocket(rockets)
        }
        if (particles.size < MAX_PARTICLES) {
            spawnRain(particles)
        }
    }

    // ⚠️ 必须在「组合期」把这个状态读出来（而不是在 draw 里读）：
    //    draw 阶段不观察状态，只有组合期读过，Canvas 才会跟着每帧失效重绘。
    val frame = frameNanos
    // 3 秒一个周期的呼吸，顺手让底光也有生命感
    val breath = sin(frame % 3_000_000_000L / 3_000_000_000f * 2f * PI.toFloat()) * 0.5f + 0.5f

    Canvas(modifier = modifier) {
        // 双保险：draw 阶段也读一次，走 Compose 的「绘制期状态观察」通道
        val frameInDraw = frameNanos
        if (frameInDraw == 0L) return@Canvas
        drawGlow(breath)

        val w = size.width
        val h = size.height
        val unit = min(w, h)

        rockets.forEach { rocket -> drawRocket(rocket, w, h, unit) }
        // 火星 / 爆闪垫在下面，金币压在上面，别被糊住
        particles.forEach { p -> if (p.kind != Kind.COIN) drawBurst(p, w, h, unit) }
        particles.forEach { p -> if (p.kind == Kind.COIN) drawCoin(p, unit, yenLayout) }
        labels.forEach { label -> drawLabel(label) }
    }
}

/** 发射一枚火箭：从卡片底部窜上去，落点随机、颜色随机 */
private fun launchRocket(sink: MutableList<Rocket>) {
    val x = 0.18f + Random.nextFloat() * 0.64f
    sink.add(
        Rocket(
            x = x,
            y = 1.06f,
            vx = (Random.nextFloat() * 2f - 1f) * 0.05f,
            // 升得越快，弧线越挺；这里给足初速，让它一口气冲到中上部
            vy = -(1.15f + Random.nextFloat() * 0.35f),
            targetY = 0.22f + Random.nextFloat() * 0.20f,
            hue = Random.nextFloat() * 360f,
        )
    )
}

/**
 * 到顶爆炸：一记爆闪 + 一圈彩色火星 + 一把金币。
 * 火星走整圈 360°，金币走朝上的扇形，散得慢一点，让人看清「掉钱」。
 */
private fun explode(sink: MutableList<Particle>, x: Float, y: Float, hue: Float) {
    if (sink.size > MAX_PARTICLES) return

    // 爆闪：短命的大光斑，负责「砰」那一下
    sink.add(
        Particle(
            x = x,
            y = y,
            vx = 0f,
            vy = 0f,
            radius = 0.26f,
            spin = 0f,
            spinSpeed = 0f,
            life = 0.34f,
            kind = Kind.FLASH,
            color = Color.hsv(hue % 360f, 0.30f, 1f),
            gravity = 0f,
            drag = 0f,
            twinkleSeed = 0f,
        )
    )

    repeat(BURST_SPARKS) { index ->
        // 均匀铺满一圈，再加一点抖动，免得看起来像规整的钟表刻度
        val angle = index.toFloat() / BURST_SPARKS * 2f * PI.toFloat() +
            (Random.nextFloat() * 2f - 1f) * 0.12f
        val speed = 0.72f + Random.nextFloat() * 0.88f
        sink.add(
            Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed * 0.92f,
                radius = 0.010f + Random.nextFloat() * 0.010f,
                spin = 0f,
                spinSpeed = 0f,
                life = 1.1f + Random.nextFloat() * 1.0f,
                kind = Kind.SPARK,
                color = Color.hsv(
                    hue = (hue + Random.nextFloat() * 48f - 24f) % 360f,
                    saturation = 0.72f + Random.nextFloat() * 0.28f,
                    value = 1f,
                ),
                gravity = SPARK_GRAVITY,
                drag = SPARK_DRAG,
                twinkleSeed = Random.nextFloat() * 6.28f,
            )
        )
    }

    repeat(BURST_COINS) {
        val angle = -PI.toFloat() / 2f + (Random.nextFloat() * 2f - 1f) * PI.toFloat() * 0.85f
        val speed = 0.45f + Random.nextFloat() * 0.55f
        sink.add(
            Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed * 0.9f,
                vy = sin(angle) * speed,
                radius = 0.030f + Random.nextFloat() * 0.014f,
                spin = Random.nextFloat() * 360f,
                spinSpeed = (Random.nextFloat() * 2f - 1f) * 620f,
                life = 1.8f + Random.nextFloat() * 0.9f,
                kind = Kind.COIN,
                color = CoinBody,
                gravity = COIN_GRAVITY,
                drag = COIN_DRAG,
                twinkleSeed = 0f,
            )
        )
    }
}

/** 整点的金币雨：从画布上方洒下来，把「整点清零」做成庆祝 */
private fun spawnRain(sink: MutableList<Particle>) {
    repeat(RAIN_COINS) {
        sink.add(
            Particle(
                x = Random.nextFloat(),
                y = -0.08f - Random.nextFloat() * 0.45f,
                vx = (Random.nextFloat() * 2f - 1f) * 0.06f,
                vy = 0.22f + Random.nextFloat() * 0.26f,
                radius = 0.026f + Random.nextFloat() * 0.012f,
                spin = Random.nextFloat() * 360f,
                spinSpeed = (Random.nextFloat() * 2f - 1f) * 520f,
                life = 2.6f + Random.nextFloat() * 0.8f,
                kind = Kind.COIN,
                color = CoinBody,
                gravity = COIN_GRAVITY * 0.55f,
                drag = COIN_DRAG * 0.7f,
                twinkleSeed = 0f,
            )
        )
    }
}

/** 每秒那一小撮：底边窜上来的金币 + 零星火星，负责「一直在进账」的连续感 */
private fun spawnFizz(sink: MutableList<Particle>) {
    repeat(SECOND_COINS) {
        val baseX = 0.18f + Random.nextFloat() * 0.64f
        val angle = -PI.toFloat() / 2f + (Random.nextFloat() * 2f - 1f) * 0.5f
        val speed = 0.80f + Random.nextFloat() * 0.30f
        sink.add(
            Particle(
                x = baseX,
                y = 1.02f,
                vx = cos(angle) * speed * 0.9f,
                vy = sin(angle) * speed,
                radius = 0.026f + Random.nextFloat() * 0.010f,
                spin = Random.nextFloat() * 360f,
                spinSpeed = (Random.nextFloat() * 2f - 1f) * 420f,
                life = 1.5f + Random.nextFloat() * 0.6f,
                kind = Kind.COIN,
                color = CoinBody,
                gravity = COIN_GRAVITY,
                drag = COIN_DRAG,
                twinkleSeed = 0f,
            )
        )
    }
    repeat(SECOND_SPARKS) {
        val angle = -PI.toFloat() / 2f + (Random.nextFloat() * 2f - 1f) * 0.7f
        val speed = 0.55f + Random.nextFloat() * 0.45f
        sink.add(
            Particle(
                x = 0.5f + (Random.nextFloat() * 2f - 1f) * 0.30f,
                y = 1.00f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                radius = 0.008f + Random.nextFloat() * 0.007f,
                spin = 0f,
                spinSpeed = 0f,
                life = 0.9f + Random.nextFloat() * 0.6f,
                kind = Kind.SPARK,
                color = Color.hsv(38f + Random.nextFloat() * 22f, 0.85f, 1f),
                gravity = SPARK_GRAVITY,
                drag = SPARK_DRAG,
                twinkleSeed = Random.nextFloat() * 6.28f,
            )
        )
    }
}

private fun advance(
    particles: MutableList<Particle>,
    rockets: MutableList<Rocket>,
    labels: MutableList<LabelParticle>,
    dt: Float,
    onBoom: (Rocket) -> Unit,
) {
    // 火箭：冲到目标高度、开始下坠、或者飞太久，就炸
    val rocketIt = rockets.iterator()
    while (rocketIt.hasNext()) {
        val rocket = rocketIt.next()
        rocket.prevX = rocket.x
        rocket.prevY = rocket.y
        rocket.vy += ROCKET_GRAVITY * dt
        rocket.x += rocket.vx * dt
        rocket.y += rocket.vy * dt
        rocket.age += dt
        if (rocket.y <= rocket.targetY || rocket.vy >= 0f || rocket.age > 3f) {
            rocketIt.remove()
            onBoom(rocket)
        }
    }

    val pIt = particles.iterator()
    while (pIt.hasNext()) {
        val p = pIt.next()
        p.prevX = p.x
        p.prevY = p.y
        p.vy += p.gravity * dt
        val damping = exp(-p.drag * dt)
        p.vx *= damping
        p.vy *= damping
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.spin += p.spinSpeed * dt
        p.age += dt
        if (p.age >= p.life) pIt.remove()
    }

    val lIt = labels.iterator()
    while (lIt.hasNext()) {
        val label = lIt.next()
        label.y += label.vy * dt
        label.age += dt
        if (label.age >= label.life) lIt.remove()
    }
}

private fun DrawScope.drawGlow(breath: Float) {
    val radius = min(size.width, size.height) * (0.92f + 0.06f * breath)
    val center = Offset(size.width * 0.5f, size.height * 0.34f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f + 0.08f * breath), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** 上升中的火箭：一条拖尾 + 一颗亮头 */
private fun DrawScope.drawRocket(rocket: Rocket, w: Float, h: Float, unit: Float) {
    val head = Offset(rocket.x * w, rocket.y * h)
    drawLine(
        color = Color.hsv(rocket.hue % 360f, 0.75f, 1f),
        start = Offset(rocket.prevX * w, rocket.prevY * h),
        end = head,
        strokeWidth = unit * 0.010f,
        alpha = 0.65f,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = Color.hsv(rocket.hue % 360f, 0.45f, 1f),
        radius = unit * 0.028f,
        center = head,
    )
    drawCircle(color = Color.White, radius = unit * 0.012f, center = head, alpha = 0.95f)
}

/** 火星：拖尾 + 柔光 + 白心，外加一点明灭；爆闪则是快速摊开的光斑 */
private fun DrawScope.drawBurst(p: Particle, w: Float, h: Float, unit: Float) {
    val t = (p.age / p.life).coerceIn(0f, 1f)
    val center = Offset(p.x * w, p.y * h)

    if (p.kind == Kind.FLASH) {
        val fadeOut = 1f - t
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(p.color.copy(alpha = 0.85f), Color.Transparent),
                center = center,
                radius = p.radius * unit * (0.7f + 1.1f * t),
            ),
            radius = p.radius * unit * (0.7f + 1.1f * t),
            center = center,
            alpha = fadeOut * fadeOut * 0.7f,
        )
        return
    }

    val twinkle = 0.62f + 0.38f * sin(p.age * 32f + p.twinkleSeed)
    val alpha = fade(t) * twinkle

    // 拖尾：从上一帧位置拉一条线，比画一串圆便宜，也更像余烬
    drawLine(
        color = p.color,
        start = Offset(p.prevX * w, p.prevY * h),
        end = center,
        strokeWidth = p.radius * unit * 1.1f,
        alpha = alpha * 0.55f,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = p.color,
        radius = p.radius * unit * 2.0f,
        center = center,
        alpha = alpha * 0.35f,
    )
    drawCircle(
        color = Color.White,
        radius = p.radius * unit * 0.62f,
        center = center,
        alpha = alpha * 0.9f,
    )
}

private fun DrawScope.drawCoin(
    coin: Particle,
    unit: Float,
    yen: TextLayoutResult,
) {
    val base = unit * 0.030f
    val scaleFactor = coin.radius * unit / base
    // 横向压扁 = 翻面；永远留 0.15 的厚度，别压成一条线
    val flip = abs(cos(coin.spin * PI.toFloat() / 180f)) * 0.85f + 0.15f
    val alpha = fade(coin.age / coin.life)
    val center = Offset(coin.x * size.width, coin.y * size.height)

    scale(scaleX = flip * scaleFactor, scaleY = scaleFactor, pivot = center) {
        drawCircle(color = CoinEdge, radius = base, center = center, alpha = alpha)
        drawCircle(color = CoinBody, radius = base * 0.80f, center = center, alpha = alpha)
        drawCircle(color = CoinShine, radius = base * 0.55f, center = center, alpha = alpha)
        drawText(
            textLayoutResult = yen,
            topLeft = Offset(
                x = center.x - yen.size.width / 2f,
                y = center.y - yen.size.height / 2f,
            ),
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawLabel(label: LabelParticle) {
    val alpha = fade(label.age / label.life)
    val topLeft = Offset(
        x = label.x * size.width - label.layout.size.width / 2f,
        y = label.y * size.height,
    )
    if (label.strong) {
        // 描边靠「先画一层偏移的亮色」糊出来，比真描边便宜，也更容易从金底里跳出来
        drawText(
            textLayoutResult = label.layout,
            color = LabelHalo,
            topLeft = topLeft + Offset(2f, 2f),
            alpha = alpha * 0.6f,
        )
    }
    drawText(
        textLayoutResult = label.layout,
        color = label.ink,
        topLeft = topLeft,
        alpha = alpha,
    )
}

/** 出生淡入 + 末尾淡出，中段保持全亮 */
private fun fade(t: Float): Float {
    val progress = t.coerceIn(0f, 1f)
    return min(progress / 0.05f, (1f - progress) / 0.30f).coerceIn(0f, 1f)
}

private fun newLabel(
    measurer: TextMeasurer,
    text: String,
    strong: Boolean,
): LabelParticle {
    val ink = if (strong) LabelStrongInk else LabelSoftInk
    val style = TextStyle(
        color = ink,
        fontSize = if (strong) 24.sp else 14.sp,
        fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Bold,
    )
    // 飘在金额左右两侧，别糊住中间的大数字
    val side = if (Random.nextBoolean()) 1f else -1f
    return LabelParticle(
        x = 0.5f + side * (0.22f + Random.nextFloat() * 0.06f),
        y = if (strong) 0.70f else 0.80f,
        vy = if (strong) -0.26f else -0.22f,
        layout = measurer.measure(text, style),
        ink = ink,
        strong = strong,
        life = if (strong) 2.6f else 1.6f,
    )
}

// 币面：亮金为主 + 深棕描边，才压得住金色的卡片底
private val CoinEdge = Color(0xFFB26A00)
private val CoinBody = Color(0xFFFFE082)
private val CoinShine = Color(0xFFFFFDF2)
private val CoinInk = Color(0xFF7A3B00)
private val LabelStrongInk = Color(0xFF7A3B00)
private val LabelSoftInk = Color(0xFF96631F)
private val LabelHalo = Color(0xFFFFFDF2)
