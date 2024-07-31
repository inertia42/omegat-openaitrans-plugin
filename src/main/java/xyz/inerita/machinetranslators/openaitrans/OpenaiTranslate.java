/*
参考了
https://github.com/yoyicue/omegat-tencent-plugin
https://github.com/omegat-org/omegat/blob/854b6b5a66a0306e5c27e74c0b5d656ed80b2bd4/src/org/omegat/core/machinetranslators/YandexTranslate.java
GoogleTranslateWithoutApiKey
omegat-niutrans-plugin
的写法，感谢上述作者
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

import java.awt.*;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class OpenaiTranslate extends BaseCachedTranslate {

    /**
     * 设置存储 key 的名字，读取和设置值由 OmegaT 提供 API 来操作.
     */
    private static final String PROPERTY_API_KEY = "openai.api.Key";
    private static final String PROPERTY_API_URL = "openai.api.url";
    private static final String PROPERTY_API_MODEL = "openai.api.model";
    private static final String PROPERTY_API_PROVIDER = "openai.api.format";
    private static final String PROPERTY_API_PROMPT = "openai.api.prompt";
    private static final String PROPERTY_API_TEMPERATURE = "openai.api.temperature";
    private static final String PROPERTY_API_CACHE = "openai.api.enable.cache";

    private static final String[] openaiModels = {"gpt-4o", "gpt-4o-mini", "gpt-4", "gpt-4-turbo", "gpt-3.5-turbo", "gpt-3.5"};
    private static final String[] claudeModels = {"claude-3-opus", "claude-3-5-sonnet", "claude-3-sonnet", "claude-3-haiku", "claude-3-5-sonnet-20240620"};
    private static final String[] Providers = {"default", "OpenAI", "Claude"};


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
        String url = getCredential(PROPERTY_API_URL);
        String model = getCredential(PROPERTY_API_MODEL);
        String temperature_str = getCredential(PROPERTY_API_TEMPERATURE);
        String provider = getCredential(PROPERTY_API_PROVIDER);
        String prompt = getCredential(PROPERTY_API_PROMPT);
        String enableCache= getCredential(PROPERTY_API_CACHE);

        BigDecimal fullAccuracy = new BigDecimal(Double.parseDouble(temperature_str));
        fullAccuracy = fullAccuracy.setScale(3, RoundingMode.DOWN); // 截断到三位小数
        double temperature = fullAccuracy.doubleValue();

        if (apiKey.isEmpty()) {
            // return url + " " + model + " " + temperature_str;
            return "Please set API KEY!";
        }

        if (url.isEmpty()) {
            // return url + " " + model + " " + temperature_str;
            return "Please set API URL!";
        }

        // LOGGER.debug("apiKey = {}", apiKey);

        List<String> openaiModelsList = Arrays.asList(openaiModels);
        List<String> claudeModelsList = Arrays.asList(claudeModels);

        if (openaiModelsList.contains(model)) {
            provider = (provider == "default") ? "OpenAI" : provider;
            // url += "/v1/chat/completions";
        } 
        // 检查 model 是否在 claudeModels 中
        else if (claudeModelsList.contains(model)) {
            provider = (provider == "default") ? "Claude" : provider;
            // url += "/v1/messages";
        } 
        // 如果 model 不在任何一个列表中
        else {
            return "An unsupported model was used!";
        }

        if (provider == "OpenAI") {
            url += "/v1/chat/completions";
        }
        else if (provider == "Claude") {
            url += "/v1/messages";
        }

        //     -----------------转换语言代码-----------------
        String lvSourceLang = sLang.getLanguage();
        String lvTargetLang = tLang.getLanguage();

        String defaultPrompt = String.format("Please translate the following text from %s to %s.", lvSourceLang, lvTargetLang);
        if (prompt.isEmpty()) {
            prompt = defaultPrompt;
        }


        //判断翻译缓存里有没有
        // U+2026 HORIZONTAL ELLIPSIS 水平省略号 …
        String lvShortText = text.length() > 5000 ? text.substring(0, 4997) + "\u2026" : text;
        String prev = getFromCache(sLang, tLang, lvShortText);
        if (prev != null && enableCache == "true") {
            return prev;
        }

        String result = "";

        if (provider == "OpenAI") {
            Map<String, Object> bodyMap = MapUtil.<String, Object>builder()
                    .put("model", model)
                    .put("messages", Arrays.asList(
                        MapUtil.<String, String>builder()
                            .put("role", "system")
                            .put("content", prompt)
                            .build(), // system prompt
                        MapUtil.<String, String>builder()
                            .put("role", "user")
                            .put("content", lvShortText)
                            .build() // user input
                    ))
                    .put("temperature", temperature) // 可根据需要调整
                    .build();
            String bodyStr = JSONUtil.toJsonStr(bodyMap);

            LOGGER.debug("bodyStr = {}", bodyStr);

            HttpRequest post = HttpUtil.createPost(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(bodyStr);

            HttpResponse response = post.execute();

            final String responseBody = response.body();

            LOGGER.debug("response body = {}", responseBody);

            JSONObject jsonObject = JSONUtil.parseObj(responseBody);
            LOGGER.debug("response jsonobject error_code = {}", jsonObject.getStr("error_code", "no error_code"));
            if (!jsonObject.containsKey("error")) {
                // 如果没有错误码，获取回复结果
                JSONArray choices = jsonObject.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    result = choices.getJSONObject(0).getJSONObject("message").getStr("content", "[Cannot get output]");
                    putToCache(sLang, tLang, lvShortText, result);
                } else {
                    result = "[Cannot get output]";
                }
            } else {
                // 如果有错误码，处理错误信息
                final String error = jsonObject.getStr("error");
                if (error == null) {
                    result = "error code null，no description";
                } else {
                    result = "error: " + error;
                }
            }
        }
        else if (provider == "Claude") {
            Map<String, Object> bodyMap = MapUtil.<String, Object>builder()
                    .put("model", model)
                    .put("max_tokens", 4096)
                    .put("system", prompt)
                    .put("messages", Arrays.asList(
                        MapUtil.<String, String>builder()
                            .put("role", "user")
                            .put("content", lvShortText)
                            .build() // user input
                    ))
                    .put("temperature", temperature) // 可根据需要调整
                    .build();
            String bodyStr = JSONUtil.toJsonStr(bodyMap);

            LOGGER.debug("bodyStr = {}", bodyStr);

            HttpRequest post = HttpUtil.createPost(url)
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(bodyStr);

            HttpResponse response = post.execute();

            final String responseBody = response.body();

            LOGGER.debug("response body = {}", responseBody);

            JSONObject jsonObject = JSONUtil.parseObj(responseBody);
            LOGGER.debug("response jsonobject error_code = {}", jsonObject.getStr("error_code", "no error_code"));
            if (!jsonObject.containsKey("error")) {
                // 如果没有错误码，获取回复结果
                JSONArray content = jsonObject.getJSONArray("content");
                if (content != null && !content.isEmpty()) {
                    result = content.getJSONObject(0).getStr("text", "[cannot get output]");
                    putToCache(sLang, tLang, lvShortText, result);
                } else {
                    result = "[cannot get output]";
                }
            } else {
                // 如果有错误码，处理错误信息
                final String error = jsonObject.getStr("error");
                if (error == null) {
                    result = "error code null，no description";
                } else {
                    result = "error: " + error;
                }
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
        public JTextField valueField3; // For prompt
        public JTextField valueField4; // For temperature
        public JComboBox<String> providerComboBox; // For service provider
        public JComboBox<String> modelComboBox; // For model name
        public JComboBox<String> apiComboBox; // For Api format
        public JCheckBox temporaryCheckBox;
        public JCheckBox cacheCheckBox;

        public MTConfigDialog(Window parent, String title) {
            super(parent, title, ModalityType.APPLICATION_MODAL);
            panel = new JPanel(new GridBagLayout());
            valueField1 = new JTextField();
            valueField2 = new JTextField();
            valueField3 = new JTextField();
            valueField4 = new JTextField();

            int totalLength = openaiModels.length + claudeModels.length;
            // 创建新数组
            String[] combinedModels = new String[totalLength];
            // 复制 openaiModels 到新数组
            System.arraycopy(openaiModels, 0, combinedModels, 0, openaiModels.length);
            // 复制 claudeModels 到新数组
            System.arraycopy(claudeModels, 0, combinedModels, openaiModels.length, claudeModels.length);
            // providerComboBox = new JComboBox<>(new String[]{"OpenAI", "Claude"});
            modelComboBox = new JComboBox<>(combinedModels);
            apiComboBox = new JComboBox<>(Providers);
            temporaryCheckBox = new JCheckBox("Only for this session");
            cacheCheckBox = new JCheckBox("Enable caching", true);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5); // Adding some padding

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.EAST;
            panel.add(new JLabel("API key"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(valueField1, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("URL(like https://api.openai.com)"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(valueField2, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Prompt"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(valueField3, gbc);

            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Temperature(default to 0)"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 3;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(valueField4, gbc);

            gbc.gridx = 0;
            gbc.gridy = 4;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Model Name"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 4;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(modelComboBox, gbc);

            gbc.gridx = 0;
            gbc.gridy = 5;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("API format"), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 5;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(apiComboBox, gbc);

            gbc.gridx = 0;
            gbc.gridy = 6;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel(""), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 6;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(temporaryCheckBox, gbc);

            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel(""), gbc);
            
            gbc.gridx = 1;
            gbc.gridy = 7;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(cacheCheckBox, gbc);

            gbc.gridx = 1;
            gbc.gridy = 8;
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
                String prompt = valueField3.getText().trim();
                String temperature = valueField4.getText().trim();
                String model = (String) modelComboBox.getSelectedItem();
                String provider = (String) apiComboBox.getSelectedItem();
                boolean temporary = temporaryCheckBox.isSelected();
                String cache = Boolean.toString(cacheCheckBox.isSelected());

                setCredential(PROPERTY_API_KEY, key, temporary);
                setCredential(PROPERTY_API_URL, url, temporary);
                setCredential(PROPERTY_API_PROMPT, prompt, temporary);
                setCredential(PROPERTY_API_TEMPERATURE, temperature, temporary);
                setCredential(PROPERTY_API_MODEL, model, temporary);
                setCredential(PROPERTY_API_PROVIDER, provider, temporary);
                setCredential(PROPERTY_API_CACHE, cache, temporary);
            }
        };

        // Set initial values for the fields
        dialog.valueField1.setText(getCredential(PROPERTY_API_KEY));
        // String provider = getCredential(PROPERTY_API_PROVIDER);
        // String defaultUrl = getDefaultUrlForProvider(provider);
        String url = getCredential(PROPERTY_API_URL);
        // if (url == null || url.isEmpty()) {
        //     url = defaultUrl;
        // }
        dialog.valueField2.setText(url);
        dialog.valueField3.setText(getCredential(PROPERTY_API_PROMPT));
        String temperature = getCredential(PROPERTY_API_TEMPERATURE);
        if (temperature == null || temperature.isEmpty()) {
            temperature = "0";
        }
        dialog.valueField4.setText(temperature);
        // dialog.providerComboBox.setSelectedItem(provider);
        dialog.modelComboBox.setSelectedItem(getCredential(PROPERTY_API_MODEL));
        dialog.apiComboBox.setSelectedItem(getCredential(PROPERTY_API_PROVIDER));
        dialog.temporaryCheckBox.setSelected(isCredentialStoredTemporarily(PROPERTY_API_KEY));

        dialog.setVisible(true);
    }
}
