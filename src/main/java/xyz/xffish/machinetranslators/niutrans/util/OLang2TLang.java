package xyz.xffish.machinetranslators.niutrans.util;

import cn.hutool.core.map.MapUtil;
import java.util.Map;

public final class OLang2TLang {

    /*
    OmegaT 用的其实是Java标准库Locale
  遵照 ISO-639-1 标准
  语言        OmegaT      小牛
  中文		    zh          zh
  英文		    en          en
  日文		    ja          ja


     */
  /**
   * OmegaT 语言代码和小牛语言代码对应表. 参见<br>
   * <a href="https://niutrans.com/documents/contents/trans_text#languageList">...</a>
   * <br>
   *
   * <br> 有道不区分具体那种英文，都是 en
   */
  private static final Map<String, String> OLANG_2_TLANG_MAP = MapUtil.<String, String>builder()
      .put("zh", "zh")
      .put("en", "en")
      .put("ja", "ja")
      .build();

  /**
   * 将 OmegaT 的语言代码转换成有道翻译识别的语言代码. 找不到就输出 auto
   *
   * @param sLang OmegaT的语言代码
   * @return 转换后的小牛翻译语言代码
   * @see "https://niutrans.com/documents/contents/trans_text#languageList"
   */
  public static String translateOLang2TLang(final String sLang) {
    String tLang = OLANG_2_TLANG_MAP.get(sLang);
    // 找不到就不转
    if (tLang == null) {
      tLang = sLang;
    }
    return tLang;
  }


  /**
   * Utility classes should not have a public or default constructor.
   */
  private OLang2TLang() {
  }
}
