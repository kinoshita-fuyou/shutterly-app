package com.shutterly.app.recognition

import com.shutterly.app.data.Category
import java.math.BigDecimal
import java.time.LocalDate

/** 金额提取结果 */
data class AmountResult(val amountFen: Long, val negative: Boolean)

/**
 * 账单文本提取器：从 OCR 文本中提取 金额 / 日期 / 商户，并自动归类。
 * 纯 Kotlin，不依赖 Android 环境，可在 JVM 上单元测试。
 *
 * 金额提取策略（正确率关键）：
 *  1. 用多种模式抓取候选金额：-¥18.50（微信）、¥18.50（支付宝）、18.50元、CNY 18.50、人民币18.50
 *  2. 以候选金额为中心取前后各 30 字作为上下文打分：
 *     - 上下文含「扣款/支出/付款/消费/实付/支付金额…」→ 加分
 *     - 含「余额/可用额度/还款日/应还/待还/剩余…」→ 大幅减分（排除干扰金额）
 *     - 金额自带负号 → 加分（支出特征）
 *  3. 得分最高者为扣款金额；识别不准时确认卡片允许人工修改
 */
object BillTextExtractor {

    private val CURRENCY_AMOUNT = Regex("""[-－]?\s*[¥￥]\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val YUAN_AMOUNT = Regex("""[-－]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元""")
    private val CNY_AMOUNT = Regex("""(?i)(?:cny|rmb)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val RMB_AMOUNT = Regex("""人民币\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val AMOUNT_PATTERNS = listOf(CURRENCY_AMOUNT, YUAN_AMOUNT, CNY_AMOUNT, RMB_AMOUNT)

    /** 支出语义关键词：出现即显著加分 */
    private val SPEND_WORDS = listOf(
        "扣款", "支出", "付款", "消费", "实付", "支付金额", "交易金额", "付款金额",
        "支付成功", "交易成功", "向商户付款", "转账给", "扣费", "支付"
    )

    /** 强支出语义：额外加分（“实付”“支付金额”等几乎必然是本次扣款） */
    private val STRONG_SPEND_WORDS = listOf("实付", "支付金额", "交易金额", "付款金额", "扣款")

    /** 干扰金额排除词：出现即大幅减分 */
    private val EXCLUDE_WORDS = listOf(
        "余额", "可用额度", "还款日", "应还", "待还", "剩余", "总额度", "可用",
        "分期", "利息", "优惠", "红包", "退款", "转入", "存入", "收入", "到账",
        "理财", "积分", "立减", "折扣"
    )

    private const val CONTEXT_RADIUS = 30
    private const val SPEND_SCORE = 60
    private const val STRONG_SPEND_SCORE = 60
    private const val NEGATIVE_SCORE = 40
    private const val EXCLUDE_PENALTY = -300
    /** 排除词须紧邻金额（其前方 ≤8 字符），只排除“余额/额度/待还”等词自己的金额 */
    private const val EXCLUDE_ADJACENCY = 8

    private data class Candidate(
        val fen: Long,
        val negative: Boolean,
        val context: String,
        val amountStart: Int
    )

    /** 提取扣款金额；无任何可靠候选返回 null */
    fun extractAmount(text: String): AmountResult? {
        val candidates = mutableListOf<Candidate>()
        for (pattern in AMOUNT_PATTERNS) {
            for (m in pattern.findAll(text)) {
                val raw = m.groupValues[1].replace(",", "")
                val fen = try {
                    (BigDecimal(raw) * BigDecimal(100)).toLong()
                } catch (_: NumberFormatException) {
                    continue
                }
                val start = (m.range.first - CONTEXT_RADIUS).coerceAtLeast(0)
                val end = (m.range.last + CONTEXT_RADIUS).coerceAtMost(text.length)
                val trimmed = m.value.trimStart()
                val negative = trimmed.startsWith("-") || trimmed.startsWith("－")
                candidates.add(Candidate(fen, negative, text.substring(start, end), m.range.first))
            }
        }

        // 同一金额+同一上下文被多个模式命中时去重
        val distinct = candidates.filterIndexed { i, c ->
            candidates.indexOfFirst { it.fen == c.fen && it.context == c.context } == i
        }

        var best: Candidate? = null
        var bestScore = Int.MIN_VALUE
        for (c in distinct) {
            var score = 0
            if (c.negative) score += NEGATIVE_SCORE
            if (SPEND_WORDS.any { c.context.contains(it) }) score += SPEND_SCORE
            if (STRONG_SPEND_WORDS.any { c.context.contains(it) }) score += STRONG_SPEND_SCORE
            if (exclusionAdjacent(text, c.amountStart)) score += EXCLUDE_PENALTY
            if (score > bestScore ||
                (score == bestScore && best != null && c.negative && !best!!.negative)
            ) {
                best = c
                bestScore = score
            }
        }
        // 最佳候选得分为负 = 仅剩被排除词紧邻的金额（余额/额度等），不可能是本次扣款，
        // 返回 null 由确认卡片人工填写，禁止静默录错
        return if (bestScore < 0) null
        else best?.let { AmountResult(it.fen, it.negative) }
    }

    /** 排除词（余额/可用额度/应还…）是否紧邻该金额之前 —— 该金额是余额/额度而非扣款 */
    private fun exclusionAdjacent(text: String, amountStart: Int): Boolean {
        for (kw in EXCLUDE_WORDS) {
            var idx = text.indexOf(kw)
            while (idx >= 0) {
                val kwEnd = idx + kw.length
                if (kwEnd <= amountStart && amountStart - kwEnd <= EXCLUDE_ADJACENCY) {
                    return true
                }
                idx = text.indexOf(kw, idx + 1)
            }
        }
        return false
    }

    /** 提取交易日期；识别不到返回 null（调用方回退为当前时间） */
    fun extractDate(text: String): LocalDate? {
        val full = Regex("""(20\d{2})\s*[年./-]\s*(\d{1,2})\s*[月./-]\s*(\d{1,2})\s*日?""").find(text)
        if (full != null) {
            toDate(full.groupValues[1].toInt(), full.groupValues[2].toInt(), full.groupValues[3].toInt())
                ?.let { return it }
        }
        val md = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日""").find(text)
        if (md != null) {
            val now = LocalDate.now()
            toDate(now.year, md.groupValues[1].toInt(), md.groupValues[2].toInt())
                ?.let { return it }
        }
        // MM-DD（仅当无完整日期时兜底，如“08-14”）；避开 HH:mm 时间格式
        val mmdd = Regex("""(?<![\d:])([01]?\d)[-/.]([0-3]?\d)(?![\d:])""").find(text)
        if (mmdd != null) {
            val now = LocalDate.now()
            toDate(now.year, mmdd.groupValues[1].toInt(), mmdd.groupValues[2].toInt())
                ?.let { return it }
        }
        return null
    }

    private fun toDate(year: Int, month: Int, day: Int): LocalDate? =
        try { LocalDate.of(year, month, day) } catch (_: Exception) { null }

    /** 提取商户名；识别不到返回 null（由用户填写） */
    fun extractMerchant(text: String): String? {
        val patterns = listOf(
            Regex("""商户名称[:：]?\s*([^\s\n\r]{1,30})"""),
            Regex("""收款方[:：]?\s*([^\s\n\r]{1,30})"""),
            Regex("""交易对象[:：]?\s*([^\s\n\r]{1,30})"""),
            Regex("""商户[:：]?\s*([^\s\n\r]{1,30})"""),
            Regex("""向([^\s\n\r]{1,30}?)付款"""),
            Regex("""支付给([^\s\n\r]{1,30}?)""")
        )
        val bad = listOf("商户", "付款", "收款凭证", "凭证", "微信支付", "支付宝", "零钱", "银行卡", "余额")
        for (pattern in patterns) {
            val m = pattern.find(text) ?: continue
            var name = m.groupValues[1].trim()
            name = name.trimEnd('。', '，', ',', '.', '！', '!', '；', ';')
            if (name.isBlank()) continue
            if (bad.any { name.contains(it) }) continue
            name = name.replace(Regex("""(有限公司|有限责任公司|股份有限公司|分公司|支行|营业厅)$"""), "")
            return name
        }
        return null
    }

    /** 完整提取流程 → 确认卡片数据 */
    fun extract(text: String): ExtractedBill {
        val amount = extractAmount(text)
        val date = extractDate(text) ?: LocalDate.now()
        val merchant = extractMerchant(text)
        // 分类优先看商户名，其次整段文本
        val category = merchant?.let { Category.fromText(it) } ?: Category.fromText(text)
        return ExtractedBill(
            amountFen = amount?.amountFen ?: 0L,
            hasAmount = amount != null,
            epochDay = date.toEpochDay(),
            merchant = merchant,
            category = category?.name,
            sourceTextPreview = text.take(200)
        )
    }
}
