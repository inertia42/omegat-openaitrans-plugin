/*
参考了
https://github.com/yoyicue/omegat-tencent-plugin
https://github.com/omegat-org/omegat/blob/854b6b5a66a0306e5c27e74c0b5d656ed80b2bd4/src/org/omegat/core/machinetranslators/YandexTranslate.java
GoogleTranslateWithoutApiKey
的写法，感谢上述作者
小牛翻译 API 文档：https://niutrans.com/documents/contents/question/1
 */
package xyz.inertia.machinetranslators.openaitrans;

import cn.hutool.core.map.MapUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONArray;
import org.omegat.core.Core;
import org.omegat.core.machinetranslators.BaseCachedTranslate;
import org.omegat.gui.exttrans.MTConfigDialog;
import org.omegat.util.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.inertia.machinetranslators.openaitrans.util.ErrorCode2Desc;
import xyz.inertia.machinetranslators.openaitrans.util.OLang2TLang;

import java.awt.*;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.*;

public class OpenaiTranslate extends BaseCachedTranslate {

    /**
     * 设置存储 key 的名字，读取和设置值由 OmegaT 提供 API 来操作.
     */
    private static final String PROPERTY_API_KEY = "openai.api.Key";
    private static final String PROPERTY_API_URL = "openai.api.url";
    private static final String PROPERTY_API_MODEL = "openai.api.model";
    private static final String PROPERTY_API_PROVIDER = "openai";
    /**
     * 官方API申请<br>
     * <a href="https://niutrans.com/documents/contents/question/1">...</a><br>
     * 用 protected 是因为 javadoc 默认不生成 private 字段的说明，下同
     */
    protected static final String PROPERTY_API_OFFICIAL_TEST_SECRET_KEY = "NOT-PROVIDED";
    /**
     * 小牛翻译请求 URL.
     */
    // protected static final String URL = "https://api.niutrans.com/NiuTransServer/translation";

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenaiTranslate.class);

    /**
     * 在软件启动时会自动调用该函数来注册插件.
     */
    public static void loadPlugins() {
        LOGGER.debug("加载 OpenaiTranslate Plugin");
        Core.registerMachineTranslationClass(OpenaiTranslate.class);
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
        return "allow_openai_translate";
    }

    /**
     * 在软件里显示该翻译插件的名字.
     *
     * @return 本翻译插件显示的名字
     */
    @Override
    public String getName() {
        return "Openai Translate";
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
        String apiKey = getCredential(PROPERTY_API_KEY);
        String endpoint = getCredential(PROPERTY_API_URL);
        String model = getCredential(PROPERTY_API_MODEL);
        String systemPrompt = "请将下面的与等离子体物理和核聚变相关的英语文本翻译成中文，要求用语专业准确，语言流畅通顺不拗口，符合中文的表达习惯又不偏离原意，符合信达雅的要求，在不改变原意的前提下可以对文本进行少量的润色，若句子过长可以适当将其分成短句。要求用语专业准确，语言流畅通顺不拗口，符合中文的表达习惯又不偏离原意，符合信达雅的要求。";
        if (apiKey.isEmpty()) {
            // key 是空的那就用官方测试key
            return "Please set APIKEY!";
        }

        LOGGER.debug("apiKey = {}", apiKey);

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
//         Map<String, String> bodyMap = MapUtil.<String, String>builder()
//                 .put("from", lvSourceLang)
//                 .put("to", lvTargetLang)
//                 .put("apikey", secretKey)
// //        .put("src_text", URLEncoder.encode(text, StandardCharsets.UTF_8))
//                 .put("src_text", text)
//                 .build();

        Map<String, Object> bodyMap = MapUtil.<String, Object>builder()
                .put("model", model)
                .put("messages", Arrays.asList(
                    MapUtil.<String, String>builder()
                        .put("role", "system")
                        .put("content", systemPrompt)
                        .build(), // system prompt
                    MapUtil.<String, String>builder()
                        .put("role", "user")
                        .put("content", lvShortText)
                        .build() // user input
                ))
                .put("temperature", 0.7) // 可根据需要调整
                .build();


//        JSONUtil.parse(bodyMap);
        String bodyStr = JSONUtil.toJsonStr(bodyMap);

        LOGGER.debug("bodyStr = {}", bodyStr);

        // HttpRequest post = HttpUtil.createPost(URL)

        HttpRequest post = HttpUtil.createPost(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(bodyStr);

        HttpResponse response = post.execute();
        LOGGER.debug("response status = {}", response.getStatus());
        LOGGER.debug("response isGzip = {}", response.isGzip());
        String headerContentEncoding = response.header(Header.CONTENT_ENCODING);
        LOGGER.debug("response headerContentEncoding = {}", headerContentEncoding);

        String result = "";
        final String responseBody = response.body();

        LOGGER.debug("response body = {}", responseBody);

        JSONObject jsonObject = JSONUtil.parseObj(responseBody);
        LOGGER.debug("response jsonobject error_code = {}", jsonObject.getStr("error_code", "没有error_code"));
        if (!jsonObject.containsKey("error")) {
            // 如果没有错误码，获取回复结果
            JSONArray choices = jsonObject.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                result = choices.getJSONObject(0).getJSONObject("message").getStr("content", "[未能获取到输出]");
            } else {
                result = "[未能获取到输出]";
            }
        } else {
            // 如果有错误码，处理错误信息
            final String error = jsonObject.getStr("error");
            if (error == null) {
                result = "错误码null，没有错误描述信息";
            } else {
                result = "错误: " + error;
            }
        }
        return result;
    }


    /**
     * 是否在设置界面允许该插件的配置按钮可用，如果 false，配置按钮是灰色不可点的，也就没法配置 Token 了
     */
    @Override
    public boolean isConfigurable() {
        return true;
    }

    public class MTConfigDialog extends JDialog {
        public JPanel panel;
        public JTextField valueField1; // For apikey
        public JTextField valueField2; // For url
        public JComboBox<String> providerComboBox; // For service provider
        public JComboBox<String> modelComboBox; // For model name
        public JCheckBox temporaryCheckBox;

        public MTConfigDialog(Window parent, String title) {
            super(parent, title, ModalityType.APPLICATION_MODAL);
            panel = new JPanel(new GridBagLayout());
            valueField1 = new JTextField();
            valueField2 = new JTextField();
            providerComboBox = new JComboBox<>(new String[]{"OpenAI", "Claude"});
            modelComboBox = new JComboBox<>(new String[]{"gpt-4o", "gpt-4", "gpt-4o-mini"});
            temporaryCheckBox = new JCheckBox("Only for this session");

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5); // Adding some padding

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.EAST;
            panel.add(new JLabel("apikey"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(valueField1, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("url"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(valueField2, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Service Provider"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(providerComboBox, gbc);

            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Model Name"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 3;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(modelComboBox, gbc);

            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel(""), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 4;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(temporaryCheckBox, gbc);

            gbc.gridx = 1;
            gbc.gridy = 5;
            gbc.fill = GridBagConstraints.NONE;
            JButton confirmButton = new JButton("Confirm");
            confirmButton.addActionListener(e -> onConfirm());
            panel.add(confirmButton, gbc);

            add(panel);
            pack();
            setLocationRelativeTo(parent);
        }

        protected void onConfirm() {
        // Placeholder for child class to implement
        }
    }

    /**
     * 设置里面该插件的配置按钮被按下后弹出的界面、控制逻辑、获取数据，存储数据
     */
    @Override
    public void showConfigurationUI(Window parent) {
        MTConfigDialog dialog = new MTConfigDialog(parent, getName()) {
            @Override
            protected void onConfirm() {
                String key = valueField1.getText().trim();
                String url = valueField2.getText().trim();
                String provider = (String) providerComboBox.getSelectedItem();
                String model = (String) modelComboBox.getSelectedItem();
                boolean temporary = temporaryCheckBox.isSelected();

                setCredential(PROPERTY_API_KEY, key, temporary);
                setCredential(PROPERTY_API_URL, url, temporary);
                setCredential(PROPERTY_API_PROVIDER, provider, temporary);
                setCredential(PROPERTY_API_MODEL, model, temporary);
            }
        };

        // Set initial values for the fields
        dialog.valueField1.setText(getCredential(PROPERTY_API_KEY));
        String provider = getCredential(PROPERTY_API_PROVIDER);
        String defaultUrl = getDefaultUrlForProvider(provider);
        String url = getCredential(PROPERTY_API_URL);
        if (url == null || url.isEmpty()) {
            url = defaultUrl;
        }
        dialog.valueField2.setText(url);
        dialog.providerComboBox.setSelectedItem(provider);
        dialog.modelComboBox.setSelectedItem(getCredential(PROPERTY_API_MODEL));
        dialog.temporaryCheckBox.setSelected(isCredentialStoredTemporarily(PROPERTY_API_KEY));

        dialog.setVisible(true);
    }

    private String getDefaultUrlForProvider(String provider) {
        switch (provider) {
            case "OpenAI":
                return "https://api.openai.com";
            case "Claude":
                return "https://api.provider2.com";
            default:
                return "";
        }
    }
}
