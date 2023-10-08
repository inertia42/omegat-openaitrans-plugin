/*
参考了
https://github.com/yoyicue/omegat-tencent-plugin
https://github.com/omegat-org/omegat/blob/854b6b5a66a0306e5c27e74c0b5d656ed80b2bd4/src/org/omegat/core/machinetranslators/YandexTranslate.java
GoogleTranslateWithoutApiKey
的写法，感谢上述作者
小牛翻译 API 文档：https://niutrans.com/documents/contents/question/1
 */
package xyz.xffish.machinetranslators.niutrans;

import cn.hutool.core.map.MapUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.omegat.core.Core;
import org.omegat.core.machinetranslators.BaseCachedTranslate;
import org.omegat.gui.exttrans.MTConfigDialog;
import org.omegat.util.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.xffish.machinetranslators.niutrans.util.ErrorCode2Desc;
import xyz.xffish.machinetranslators.niutrans.util.OLang2TLang;

import java.awt.*;
import java.util.Map;

public class XiaoniuTranslate extends BaseCachedTranslate {

    /**
     * 设置存储 key 的名字，读取和设置值由 OmegaT 提供 API 来操作.
     */
    private static final String PROPERTY_API_SECRET_KEY = "xiaoniu.api.secret.Key";
    /**
     * 官方API申请<br>
     * <a href="https://niutrans.com/documents/contents/question/1">...</a><br>
     * 用 protected 是因为 javadoc 默认不生成 private 字段的说明，下同
     */
    protected static final String PROPERTY_API_OFFICIAL_TEST_SECRET_KEY = "NOT-PROVIDED";
    /**
     * 小牛翻译请求 URL.
     */
    protected static final String URL = "https://api.niutrans.com/NiuTransServer/translation";

    private static final Logger LOGGER = LoggerFactory.getLogger(XiaoniuTranslate.class);

    /**
     * 在软件启动时会自动调用该函数来注册插件.
     */
    public static void loadPlugins() {
        LOGGER.debug("加载 XiaoniuTranslate Plugin");
        Core.registerMachineTranslationClass(XiaoniuTranslate.class);
    }

    /**
     * 卸载插件，可以留空，示例代码就留空.
     */
    public static void unloadPlugins() {
    }

    /**
     * 显示该插件介绍性的话.
     *
     * @return 介绍性话语
     */
    @Override
    protected String getPreferenceName() {
        return "allow_xiaoniu_translate";
    }

    /**
     * 在软件里显示该翻译插件的名字.
     *
     * @return 本翻译插件显示的名字
     */
    @Override
    public String getName() {
        return "Xiaoniu Translate";
    }

    /**
     * 插件主体功能函数，接收原文，获得译文并返回.
     *
     * @param sLang 原文的语言
     * @param tLang 译文的语言
     * @param text  原文内容
     * @return 译文内容
     * @throws Exception 根据示例代码也抛出异常
     */
    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {
        String secretKey = getCredential(PROPERTY_API_SECRET_KEY);
        if (secretKey.isEmpty()) {
            // key 是空的那就用官方测试key
            secretKey = PROPERTY_API_OFFICIAL_TEST_SECRET_KEY;
        }

        LOGGER.debug("secretKey = {}", secretKey);

        //     -----------------转换语言代码-----------------
        String lvSourceLang = sLang.getLanguageCode().substring(0, 2).toLowerCase();
        lvSourceLang = OLang2TLang.translateOLang2TLang(lvSourceLang);

        String lvTargetLang = tLang.getLanguageCode().substring(0, 2).toLowerCase();
        lvTargetLang = OLang2TLang.translateOLang2TLang(lvTargetLang);


        //判断翻译缓存里有没有
        // U+2026 HORIZONTAL ELLIPSIS 水平省略号 …
        String lvShortText = text.length() > 5000 ? text.substring(0, 4997) + "\u2026" : text;
        String prev = getFromCache(sLang, tLang, lvShortText);
        LOGGER.debug("判断的缓存结果是prev={}", prev);
        if (prev != null) {
            // 啊，有缓存，那就直接返回不用请求了
            LOGGER.debug("啊，有缓存，太美妙了：{}", prev);
            return prev;
        }

        //----------------------------------------------------------------------
        // Omegat 包含了一个 org.omegat.util.JsonParser
        // 它是对 jdk8 Nashorn JavaScript engine 薄薄的封装
        // 但是 JEP 335 宣布弃用 Nashorn，为了以后可用性还是用第三方 json 库

//            x-authorization: token 3975l6lr5pcbvidl6jl2
//
//            Accept-Encoding: gzip
//            User-Agent: okhttp/4.8.1
        // 有错误的话是{"message": "API rate limit exceeded"}
        //构造 json 格式body
        Map<String, String> bodyMap = MapUtil.<String, String>builder()
                .put("from", lvSourceLang)
                .put("to", lvTargetLang)
                .put("apikey", secretKey)
//        .put("src_text", URLEncoder.encode(text, StandardCharsets.UTF_8))
                .put("src_text", text)
                .build();


//        JSONUtil.parse(bodyMap);
        String bodyStr = JSONUtil.toJsonStr(bodyMap);

        LOGGER.debug("bodyStr = {}", bodyStr);

        HttpRequest post = HttpUtil.createPost(URL)

//                .header("content-type", "application/json") //body会自动设置contentType
                .body(bodyStr);

        HttpResponse response = post.execute();
        LOGGER.debug("response status = {}", response.getStatus());
        LOGGER.debug("response isGzip = {}", response.isGzip());
        String headerContentEncoding = response.header(Header.CONTENT_ENCODING);
        LOGGER.debug("response headerContentEncoding = {}", headerContentEncoding);

        String translation = "";
        final String responseBody = response.body();

        LOGGER.debug("response body = {}", responseBody);

        JSONObject jsonObject = JSONUtil.parseObj(responseBody);
        LOGGER.debug("response jsonobject error_code = {}", jsonObject.getStr("error_code", "没有error_code"));
        if (!jsonObject.containsKey("error_code")) {
            translation = jsonObject.getStr("tgt_text", "[未能获取到输出]");
            // 把这次结果添加进缓存
            putToCache(sLang, tLang, lvShortText, translation);
        } else {
            // 出错描述直接写在 message 里，就不用专门的类转换错误码及其描述了
//      translation = (String) jsonObject.getObj("error_msg", "");
            final String errorCode = jsonObject.getStr("error_code");

            if (errorCode == null) {
                translation = "错误码null，没有错误描述信息";
            } else {
                final String errorCodeDesc = ErrorCode2Desc.translateErrorCode2Desc(errorCode);
                // 下面写法也可
//        final String errorCodeDesc = ErrorCode2Desc.INSTANCE.translateErrorCode2Desc(errorCode);
                translation = errorCode + "-" + errorCodeDesc;
            }

        }
//    translation = URLUtil.decode(translation);
        return translation;
    }


    /**
     * 是否在设置界面允许该插件的配置按钮可用，如果 false，配置按钮是灰色不可点的，也就没法配置 Token 了
     */
    @Override
    public boolean isConfigurable() {
        return true;
    }

    /**
     * 设置里面该插件的配置按钮被按下后弹出的界面、控制逻辑、获取数据，存储数据
     */
    @Override
    public void showConfigurationUI(Window parent) {
        MTConfigDialog dialog = new MTConfigDialog(parent, getName()) {
            @Override
            protected void onConfirm() {
                String key = panel.valueField1.getText().trim();
                boolean temporary = panel.temporaryCheckBox.isSelected();
                // 利用 OmegaT 提供的 API 来设置 PROPERTY_API_SECRET_KEY 变量代表的名字的值
                // 可以想象 OmegaT 提供了一个类似 HashMap 的结构，
                // setCredential 就是用指定 key 存储值，getCredential 就是用指定 key 取值
                // 第三个参数是是否启用“仅为本次会话保存”
                setCredential(PROPERTY_API_SECRET_KEY, key, temporary);
            }
        };
        // 弹出 Token 设置窗口的 label 显示的文字
        dialog.panel.valueLabel1.setText("apikey");
        // 利用 OmegaT 提供的 API 来获取 PROPERTY_API_SECRET_KEY 变量代表的名字的值
        dialog.panel.valueField1.setText(getCredential(PROPERTY_API_SECRET_KEY));
        // 只有一个内容要填，所以把第二个 label 和 输入框禁用。如果有个两个内容要填，比如腾讯翻译插件：secretId 和 scretKey，
        // 那下面的代码就不能设置 setVisible(false) 了，具体写法可参考 https://github.com/yoyicue/omegat-tencent-plugin
        dialog.panel.valueLabel2.setVisible(false);
        dialog.panel.valueField2.setVisible(false);

        // 设置是否勾选“仅为本次会话保存”。
        dialog.panel.temporaryCheckBox.setSelected(
                isCredentialStoredTemporarily(PROPERTY_API_SECRET_KEY));
        dialog.show();
    }
}
