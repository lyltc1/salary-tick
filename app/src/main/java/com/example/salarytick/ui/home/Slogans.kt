package com.example.salarytick.ui.home

/**
 * 首页「每日一句」文案池：每次打开 App 随机挑一条。
 *
 * 句子长短差很多（最短 8 个字，最长 40 多个字），
 * 所以展示统一交给 [SloganText]：占满整宽、最多三行，
 * 免得长句挤在标题旁边把标题行撑变形。
 */
object Slogans {

    private val ALL: List<String> = listOf(
        "在工作中成长，于生活里安放自己",
        "工作压力大时，请常来看看",
        "奋斗自有回响。",
        "先照顾好自己，再应付世间琐碎。",
        "业精于勤而荒于嬉，行成于思而毁于随。",
        "我们在劳动过程中学习思考，劳动的结果，我们认识了世界的奥妙，于是我们就真正来改变生活了。",
        "世间没有一种具有真正价值的东西，可以不经艰苦辛勤劳动而得到。",
        "你的工作将会占据生活很大一部分，唯有相信自己所做的是伟大的工作，你才能获得真正的满足。",
    )

    /** 随机一条。调用方用 remember 存住，只在本次打开时选一次 */
    fun random(): String = ALL.random()

    /** 换一条：尽量不跟当前这条重复（池子就几条，纯随机很容易抽回同一句） */
    fun next(current: String): String =
        ALL.filter { it != current }.ifEmpty { ALL }.random()
}
