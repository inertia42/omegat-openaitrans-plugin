package xyz.xffish.machinetranslators.niutrans.util

/**
 * 错误码友好说明转换
 *
 * 原始链接 [error-code](https://niutrans.com/documents/contents/trans_text#error)
 */
object ErrorCode2Desc {
    val ERROR_CODE_2_DESC_MAP = mapOf(
            "10000" to "输入为空",
            "10001" to "请求频繁，超出QPS限制",
            "10003" to "请求字符串长度超过限制",
            "10005" to "源语编码有问题，非UTF-8",
            "13001" to "字符流量不足或者没有访问权限",
            "13002" to "\"apikey\"参数不可以是空",
            "13003" to "内容过滤异常",
            "13007" to "语言不支持",
            "13008" to "请求处理超时",
            "14001" to "分句异常",
            "14002" to "分词异常",
            "14003" to "后处理异常",
            "14004" to "对齐失败，不能够返回正确的对应关系",
            "000000" to "请求参数有误，请检查参数",
            "000001" to "Content-Type不支持【multipart/form-data】"
    )

    @JvmStatic
    fun translateErrorCode2Desc(errorCode: String): String {
        return ERROR_CODE_2_DESC_MAP.getOrDefault(errorCode, "未知错误码")

    }
}