package com.shutterly.app.recognition

import com.shutterly.app.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 自测（验收标准）：模拟微信/支付宝/银行三张账单截图文本，
 * 验证 金额 / 日期 / 商户 / 类别 四项提取结果。
 */
class BillTextExtractorTest {

    // 微信：负号金额，商户收款凭证
    private val wechatBill = """
        微信支付
        商户收款凭证
        向商户付款
        星巴克咖啡店
        支付总额
        -¥36.00
        支付方式 零钱
        交易时间 2026-08-12 14:32:08
        商户单号 420000236500000
        收款方 星巴克咖啡店
    """.trimIndent()

    // 支付宝：¥ 无负号，收款方 + 实付
    private val alipayBill = """
        支付宝
        账单详情
        收款方：张记沙县小吃
        商品说明：香拌面 + 蒸饺
        实付
        ¥23.50
        交易时间 2026-08-13 12:41:05
        支付方式 余额
        订单号 20260813220014312345
    """.trimIndent()

    // 银行短信：人民币+元 格式，含干扰的可用额度
    private val bankSms = """
        【工商银行】您尾号6688的储蓄卡于08月14日10:20消费人民币36.50元（话费充值），
        可用额度8,900.00元。收款方：中国移动通信集团
    """.trimIndent()

    // ── 金额 ─────────────────────────────────────────────────────

    @Test
    fun `微信账单提取扣款金额`() {
        val amount = BillTextExtractor.extractAmount(wechatBill)
        assertEquals(3600L, amount?.amountFen)
        assertTrue(amount?.negative == true)
    }

    @Test
    fun `支付宝账单提取实付金额`() {
        val amount = BillTextExtractor.extractAmount(alipayBill)
        assertEquals(2350L, amount?.amountFen)
    }

    @Test
    fun `银行短信排除可用额度并取消费金额`() {
        val amount = BillTextExtractor.extractAmount(bankSms)
        assertEquals(3650L, amount?.amountFen)
    }

    @Test
    fun `余额与扣款并存时优先扣款`() {
        val text = "账户余额 ¥5000.00，本次消费 -¥36.50"
        assertEquals(3650L, BillTextExtractor.extractAmount(text)?.amountFen)
    }

    @Test
    fun `CNY格式可识别`() {
        assertEquals(1850L, BillTextExtractor.extractAmount("CNY 18.50")?.amountFen)
    }

    @Test
    fun `只有干扰金额时返回空`() {
        assertNull(BillTextExtractor.extractAmount("您的余额为 1000.00 元"))
    }

    // ── 日期 ─────────────────────────────────────────────────────

    @Test
    fun `完整日期提取`() {
        assertEquals(
            LocalDate.of(2026, 8, 12),
            BillTextExtractor.extractDate(wechatBill)
        )
        assertEquals(
            LocalDate.of(2026, 8, 13),
            BillTextExtractor.extractDate(alipayBill)
        )
    }

    @Test
    fun `月日格式补当前年份`() {
        val date = BillTextExtractor.extractDate(bankSms)!!
        assertEquals(8, date.monthValue)
        assertEquals(14, date.dayOfMonth)
        assertEquals(LocalDate.now().year, date.year)
    }

    @Test
    fun `无日期时返回空`() {
        assertNull(BillTextExtractor.extractDate("只是一段没有日期的文字"))
    }

    // ── 商户 ─────────────────────────────────────────────────────

    @Test
    fun `微信提取收款方商户`() {
        assertEquals("星巴克咖啡店", BillTextExtractor.extractMerchant(wechatBill))
    }

    @Test
    fun `支付宝提取收款方商户`() {
        assertEquals("张记沙县小吃", BillTextExtractor.extractMerchant(alipayBill))
    }

    @Test
    fun `银行短信提取收款方商户`() {
        assertEquals("中国移动通信集团", BillTextExtractor.extractMerchant(bankSms))
    }

    @Test
    fun `向商户付款不误判为商户`() {
        assertNull(BillTextExtractor.extractMerchant("向商户付款\n支付总额 -¥36.00"))
    }

    // ── 分类 ─────────────────────────────────────────────────────

    @Test
    fun `微信按商户归为餐饮`() {
        val bill = BillTextExtractor.extract(wechatBill)
        assertEquals(Category.FOOD.name, bill.category)
    }

    @Test
    fun `支付宝按商户归为餐饮`() {
        val bill = BillTextExtractor.extract(alipayBill)
        assertEquals(Category.FOOD.name, bill.category)
    }

    @Test
    fun `银行短信按全文归为充值服务`() {
        val bill = BillTextExtractor.extract(bankSms)
        assertEquals(Category.TOPUP.name, bill.category)
    }

    // ── 完整流程（四项联查）───────────────────────────────────────

    @Test
    fun `完整提取流程四项齐全`() {
        val bill = BillTextExtractor.extract(wechatBill)
        assertTrue(bill.hasAmount)
        assertEquals(3600L, bill.amountFen)
        assertEquals(LocalDate.of(2026, 8, 12), LocalDate.ofEpochDay(bill.epochDay))
        assertEquals("星巴克咖啡店", bill.merchant)
        assertEquals(Category.FOOD.name, bill.category)
    }
}
