package com.shutterly.app.data

/**
 * 账目类别（6 类，单选）。
 * keywords 用于截图自动分类：优先匹配商户名，其次整段识别文本。
 * 注意：多字关键词优先、避免单字“服/衣”误命中“客服/服务”等。
 */
enum class Category(val displayName: String, val keywords: List<String>) {
    FOOD(
        "餐饮",
        listOf(
            "饭", "面", "咖啡", "奶茶", "餐厅", "饭店", "外卖", "早餐", "午餐", "晚餐",
            "小吃", "快餐", "烧烤", "火锅", "肯德基", "麦当劳", "星巴克", "蛋糕", "面包",
            "米线", "饺子", "食堂", "美团", "饿了么", "零食", "餐饮"
        )
    ),
    TRANSPORT(
        "交通",
        listOf("地铁", "滴滴", "打车", "加油", "公交", "高铁", "火车", "飞机", "机票", "停车", "出租", "出行", "骑行", "高速", "车票", "ETC")
    ),
    TOBACCO_ALCOHOL(
        "烟酒",
        listOf("烟", "酒", "啤酒", "白酒", "香烟", "烟草", "烟酒")
    ),
    MEDICINE(
        "医药",
        listOf("药", "医院", "诊所", "挂号", "体检", "药店", "口腔", "牙科", "医保")
    ),
    TOPUP(
        "充值服务",
        listOf("话费", "充值", "会员", "流量", "电费", "水费", "燃气", "煤气", "宽带", "游戏", "视频", "Q币", "点卡", "礼包")
    ),
    CLOTHING(
        "服饰",
        listOf("服装", "衣服", "服饰", "女装", "男装", "衣饰", "连衣裙", "卫衣", "裤子", "裤", "鞋", "袜子", "袜", "帽子", "帽", "围巾", "T恤")
    );

    companion object {
        /** 从文本中按关键词归类，命中不了返回 null（由用户人工选择） */
        fun fromText(text: String?): Category? {
            if (text.isNullOrBlank()) return null
            for (c in entries) {
                if (c.keywords.any { text.contains(it) }) return c
            }
            return null
        }
    }
}
