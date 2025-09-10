/*
 * Copyright (C) 2024 Inertia
 *
 * 这是一个基于 LLM API 的 OmegaT 机器翻译插件
 * This is an OmegaT machine translation plugin based on LLM API
 *
 * 本项目参考和借鉴了以下开源项目：
 * This project references and learns from the following open source projects:
 * - omegat-org/azure-translate-plugin
 * - yoyicue/omegat-tencent-plugin
 * - omegat-org/omegat (YandexTranslate.java)
 * - GoogleTranslateWithoutApiKey
 * - omegat-niutrans-plugin
 *
 * 本程序遵循 GNU 通用公共许可证第3版（GPL v3）
 * This program is licensed under GNU General Public License v3
 */
package xyz.inertia.machinetranslators.openaitrans;

import org.omegat.core.Core;
import org.omegat.core.machinetranslators.BaseCachedTranslate;
import org.omegat.gui.exttrans.IMachineTranslation;
import org.omegat.util.CredentialsManager;
import org.omegat.util.Language;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.StaticUIUtils;

import java.awt.*;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.*;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.openide.awt.Mnemonics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;

// 抽象基础翻译器类
abstract class BaseOpenaiTranslate extends BaseCachedTranslate implements IMachineTranslation {
    protected String configId;
    protected String displayName;
    protected boolean showConfigUI;
    
    protected static final Logger LOGGER = LoggerFactory.getLogger(BaseOpenaiTranslate.class);
    
    protected final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    protected final ObjectMapper objectMapper = new ObjectMapper();
    
    // 共享的常量
    protected static final String[] openaiModels = {
        "gpt-5",
        "gpt-5-mini",
        "gpt-5-nano",
        "gpt-4.1",
        "gpt-4.1-mini",
        "gpt-4.1-nano",
        "gpt-4o",
        "gpt-4o-2024-11-20",
        "gpt-4o-2024-08-06",
        "gpt-4o-2024-05-13",
        "gpt-4o-mini",
        "gpt-4",
        "gpt-4-turbo",
        "gpt-3.5-turbo",
        "gpt-3.5",
        "deepseek-chat"
    };
    protected static final String[] claudeModels = {
        "claude-opus-4-1-20250805",
        "claude-opus-4-20250514",
        "claude-sonnet-4-20250514",
        "claude-3-7-sonnet-20250219",
        "claude-3-5-sonnet-20241022",
        "claude-3-5-sonnet-20240620",
        "claude-3-opus-20240229",
        "claude-3-sonnet-20240229",
        "claude-3-5-haiku-20241022",
        "claude-3-haiku-20240307"
    };
    protected static final String[] Providers = {"default", "OpenAI", "Claude", "DeepSeek", "VolcEngine"};

    // 基础配置常量
    protected static final String PROPERTY_API_KEY = "openai.api.Key";
    protected static final String PROPERTY_API_URL = "openai.api.url";
    protected static final String PROPERTY_API_MODEL = "openai.api.model";
    protected static final String PROPERTY_API_CUSTOM_MODEL = "openai.api.custom.model";
    protected static final String PROPERTY_USE_CUSTOM_MODEL = "openai.api.use.custom.model";
    protected static final String PROPERTY_API_PROVIDER = "openai.api.format";
    protected static final String PROPERTY_API_PROMPT = "openai.api.prompt";
    protected static final String PROPERTY_API_TEMPERATURE = "openai.api.temperature";
    protected static final String PROPERTY_API_CACHE = "openai.api.enable.cache";
    protected static final String PROPERTY_API_GLOSSARY = "openai.api.enable.glossary";
    
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("OpenaiTranslateBundle");

    static String getString(String key) {
        return BUNDLE.getString(key);
    }
    
    public BaseOpenaiTranslate(String configId, String displayName, boolean showConfigUI) {
        this.configId = configId;
        this.displayName = displayName;
        this.showConfigUI = showConfigUI;
    }
    
    @Override
    public String getName() {
        // This handles the case where the constructor is called by the superclass `BaseCachedTranslate`
        // before `displayName` has been initialized. In that case, we fall back to the class
        // name, which is a safe, non-null value for the cache key.
        // For dynamically generated classes, Byte Buddy overrides this method entirely,
        // so this logic only applies to the base `OpenaiTranslate` class.
        if (displayName != null) {
            return displayName;
        }
        return getClass().getName();
    }
    
    @Override
    public boolean isConfigurable() {
        return showConfigUI;
    }
    
    @Override
    protected abstract String getPreferenceName();
    
    protected String getConfigKey(String baseKey) {
        if ("api".equals(configId)) {
            return baseKey;
        } else {
            return baseKey.replace("openai.", "openai." + configId + ".");
        }
    }
    
    protected String getCredential(String key) {
        String property = System.getProperty(key);
        if (property != null) {
            return property;
        }
        return CredentialsManager.getInstance().retrieve(key).orElse("");
    }
    
    protected void setCredential(String id, String value, boolean temporary) {
        System.setProperty(id, value);
        if (temporary) {
            CredentialsManager.getInstance().store(id, "");
        } else {
            CredentialsManager.getInstance().store(id, value);
        }
    }
    
    // 执行实际的HTTP翻译请求
    protected String executeTranslationRequest(String url, String apiKey, String model, double temperature, 
                                               String prompt, String text, String provider, 
                                               Language sLang, Language tLang) throws Exception {
        String result = "";
        
        try {
            HttpResponse<String> response;
            if ("OpenAI".equals(provider) || "VolcEngine".equals(provider)) {
                // 构建消息列表
                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                systemMessage.put("content", prompt);

                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", text);

                // 构建请求体
                Map<String, Object> bodyMap = new HashMap<>();
                bodyMap.put("model", model);
                bodyMap.put("messages", Arrays.asList(systemMessage, userMessage));
                bodyMap.put("temperature", temperature);

                LOGGER.info("bodyMap = {}", bodyMap);
                String bodyStr = objectMapper.writeValueAsString(bodyMap);
                LOGGER.debug("bodyStr = {}", bodyStr);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                        .build();

                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    LOGGER.error("HTTP请求失败: {}", e.getMessage());
                    if (e.getCause() instanceof javax.net.ssl.SSLHandshakeException) {
                        LOGGER.error("SSL握手失败: {}", e.getMessage());
                        return getString("MT_ENGINE_OPENAI_SSL_ERROR");
                    } else if (e.getCause() instanceof java.net.SocketException) {
                        LOGGER.error("网络连接错误: {}", e.getMessage());
                        return getString("MT_ENGINE_OPENAI_NETWORK_ERROR");
                    } else {
                        return String.format(getString("MT_ENGINE_OPENAI_REQUEST_ERROR"), e.getMessage());
                    }
                }

                final String responseBody = response.body();
                LOGGER.debug("response body = {}", responseBody);

                JsonNode jsonNode = objectMapper.readTree(responseBody);
                JsonNode errorNode = jsonNode.get("error");
                LOGGER.debug("response jsonobject error_code = {}", errorNode != null ? "has error" : "no error_code");
                if (errorNode == null) {
                    JsonNode choicesArray = jsonNode.get("choices");
                    if (choicesArray != null && choicesArray.isArray() && choicesArray.size() > 0) {
                        JsonNode contentNode = choicesArray.get(0).get("message").get("content");
                        result = contentNode != null ? contentNode.asText() : "[Cannot get output]";
                        putToCache(sLang, tLang, text, result);
                    } else {
                        result = "[Cannot get output]";
                    }
                } else {
                    String error = errorNode.asText();
                    if (error == null || error.isEmpty()) {
                        result = "error code null，no description";
                    } else {
                        result = "error: " + error;
                    }
                }
            } else if ("Claude".equals(provider)) {
                // 构建消息列表
                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", text);

                // 构建请求体
                Map<String, Object> bodyMap = new HashMap<>();
                bodyMap.put("model", model);
                bodyMap.put("max_tokens", 4096);
                bodyMap.put("system", prompt);
                bodyMap.put("messages", Arrays.asList(userMessage));
                bodyMap.put("temperature", temperature);

                LOGGER.info("bodyMap = {}", bodyMap);
                String bodyStr = objectMapper.writeValueAsString(bodyMap);
                LOGGER.debug("bodyStr = {}", bodyStr);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                        .build();

                try {
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    LOGGER.error("HTTP请求失败: {}", e.getMessage());
                    if (e.getCause() instanceof javax.net.ssl.SSLHandshakeException) {
                        LOGGER.error("SSL握手失败: {}", e.getMessage());
                        return getString("MT_ENGINE_OPENAI_SSL_ERROR");
                    } else if (e.getCause() instanceof java.net.SocketException) {
                        LOGGER.error("网络连接错误: {}", e.getMessage());
                        return getString("MT_ENGINE_OPENAI_NETWORK_ERROR");
                    } else {
                        return String.format(getString("MT_ENGINE_OPENAI_REQUEST_ERROR"), e.getMessage());
                    }
                }

                final String responseBody = response.body();
                LOGGER.debug("response body = {}", responseBody);

                JsonNode jsonNode = objectMapper.readTree(responseBody);
                JsonNode errorNode = jsonNode.get("error");
                LOGGER.debug("response jsonobject error_code = {}", errorNode != null ? "has error" : "no error_code");
                if (errorNode == null) {
                    JsonNode contentArray = jsonNode.get("content");
                    if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                        JsonNode textNode = contentArray.get(0).get("text");
                        result = textNode != null ? textNode.asText() : "[cannot get output]";
                        putToCache(sLang, tLang, text, result);
                    } else {
                        result = "[cannot get output]";
                    }
                } else {
                    String error = errorNode.asText();
                    if (error == null || error.isEmpty()) {
                        result = "error code null，no description";
                    } else {
                        result = "error: " + error;
                    }
                }
            }

            return result;

        } catch (Exception e) {
            LOGGER.error("翻译过程发生错误: {}", e.getMessage());
            return String.format(getString("MT_ENGINE_OPENAI_GENERAL_ERROR"), e.getMessage());
        }
    }
    
    // 翻译实现将在具体子类中完成
    @Override
    protected abstract String translate(Language sLang, Language tLang, String text) throws Exception;
}

// 通用的配置翻译器实例
class ConfigurableOpenaiTranslate extends BaseOpenaiTranslate {
    
    public ConfigurableOpenaiTranslate(String configId, String displayName, boolean showConfigUI) {
        super(configId, displayName, showConfigUI);
    }
    
    /**
     * 新增构造函数，用于服务定位器模式
     */
    protected ConfigurableOpenaiTranslate(OpenaiTranslate.ConfigInfo info) {
        super(info.configId, info.displayName, false);
    }
    
    /**
     * 为动态生成的子类提供无参构造函数.
     * OmegaT 框架需要一个无参构造函数来实例化插件.
     * 这个构造函数将通过静态 Map 查找其配置信息来完成初始化.
     */
    public ConfigurableOpenaiTranslate() {
        // 调用父类构造函数之前无法访问 'this', 因此无法使用 getClass()
        // 我们将初始化推迟到实际使用之前
        super(null, null, false); // 临时调用, 值将在之后被正确设置
    }
    
    private void lazyInit() {
        // 如果 configId 为 null，说明是首次初始化
        if (configId == null) {
            OpenaiTranslate.ConfigInfo info = OpenaiTranslate.translatorConfigs.get(this.getClass());
            if (info != null) {
                this.configId = info.configId;
                this.displayName = info.displayName;
                this.showConfigUI = false;
            } else {
                LOGGER.error("无法为动态翻译器 {} 找到配置信息", this.getClass().getName());
            }
        }
    }
    
    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {
        lazyInit(); // 确保在首次翻译时已初始化
        return performTranslation(sLang, tLang, text, configId);
    }
    
    // 实际的翻译逻辑，被主类和实例类共享
    private String performTranslation(Language sLang, Language tLang, String text, String configId) throws Exception {
        String apiKey = getCredential(getConfigKey(PROPERTY_API_KEY));
        String url = Preferences.getPreference(getConfigKey(PROPERTY_API_URL));
        String model = Preferences.getPreference(getConfigKey(PROPERTY_API_MODEL));
        String temperature_str = Preferences.getPreference(getConfigKey(PROPERTY_API_TEMPERATURE));
        if (temperature_str == null || temperature_str.trim().isEmpty()) {
            temperature_str = "0";
        }
        String provider = Preferences.getPreference(getConfigKey(PROPERTY_API_PROVIDER));
        String prompt = Preferences.getPreference(getConfigKey(PROPERTY_API_PROMPT));
        String enableCache = Preferences.getPreference(getConfigKey(PROPERTY_API_CACHE));
        String enableGlossary = Preferences.getPreference(getConfigKey(PROPERTY_API_GLOSSARY));

        BigDecimal fullAccuracy = new BigDecimal(Double.parseDouble(temperature_str));
        fullAccuracy = fullAccuracy.setScale(3, RoundingMode.DOWN);
        double temperature = fullAccuracy.doubleValue();

        if (apiKey.isEmpty()) {
            return getString("MT_ENGINE_OPENAI_API_KEY_NOTFOUND");
        }

        if (url.isEmpty()) {
            return getString("MT_ENGINE_OPENAI_API_URL_NOTFOUND");
        }

        // 处理自定义模型
        boolean useCustomModel = Boolean.parseBoolean(Preferences.getPreference(getConfigKey(PROPERTY_USE_CUSTOM_MODEL)));
        if (useCustomModel) {
            String customModel = Preferences.getPreference(getConfigKey(PROPERTY_API_CUSTOM_MODEL));
            if (customModel != null && !customModel.trim().isEmpty()) {
                model = customModel.trim();
            } else {
                return getString("MT_ENGINE_OPENAI_CUSTOM_MODEL_EMPTY");
            }
        } else {
            List<String> openaiModelsList = Arrays.asList(openaiModels);
            List<String> claudeModelsList = Arrays.asList(claudeModels);

            if (openaiModelsList.contains(model)) {
                provider = ("default".equals(provider) || "DeepSeek".equals(provider)) ? "OpenAI" : provider;
            } else if (claudeModelsList.contains(model)) {
                provider = ("default".equals(provider)) ? "Claude" : provider;
            } else {
                return "An unsupported model was used!";
            }
        }

        if ("OpenAI".equals(provider)) {
            url += "/v1/chat/completions";
        } else if ("Claude".equals(provider)) {
            url += "/v1/messages";
        } else if ("VolcEngine".equals(provider)) {
            url += "/api/v3/chat/completions";
        }

        String lvSourceLang = sLang.getLanguage();
        String lvTargetLang = tLang.getLanguage();

        String defaultPrompt = String.format("Please translate the following text from %s to %s.", lvSourceLang, lvTargetLang);
        if (prompt.isEmpty()) {
            prompt = defaultPrompt;
        }

        String lvShortText = text.length() > 5000 ? text.substring(0, 4997) + "\u2026" : text;
        String prev = getCachedTranslation(sLang, tLang, lvShortText);
        if (prev != null && "true".equals(enableCache)) {
            return prev;
        }

        StringBuilder glossaryTable = new StringBuilder();
        if ("true".equals(enableGlossary)) {
            List<org.omegat.gui.glossary.GlossaryEntry> entries = Core.getGlossaryManager()
                    .searchSourceMatches(Core.getEditor().getCurrentEntry());
            if (!entries.isEmpty()) {
                glossaryTable.append("\nYou can refer to the following glossary when translating:\n");
                for (org.omegat.gui.glossary.GlossaryEntry entry : entries) {
                    glossaryTable.append(String.format(
                            "src: %s, loc: %s, comment: %s\n",
                            entry.getSrcText(), entry.getLocText(), entry.getCommentText()));
                }
                prompt = prompt + glossaryTable.toString();
            }
        }
        
        return executeTranslationRequest(url, apiKey, model, temperature, prompt, lvShortText, provider, sLang, tLang);
    }
    
    @Override
    protected String getConfigKey(String baseKey) {
        lazyInit(); // 确保在获取配置键时已初始化
        if ("api".equals(configId)) {
            return baseKey;
        } else {
            return baseKey.replace("openai.", "openai." + configId + ".");
        }
    }

    @Override
    protected String getPreferenceName() {
        lazyInit(); // Ensure initialization before accessing configId
        return "allow_openai_translate_" + configId;
    }
}

public class OpenaiTranslate extends BaseOpenaiTranslate {
    
    // 默认插件构造函数
    public OpenaiTranslate() {
        super("api", "Openai Translate", true);
    }

    @Override
    public String getName() {
        return "Openai Translate";
    }

    @Override
    protected String getPreferenceName() {
        return "allow_openai_translate";
    }

    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {
        return performTranslation(sLang, tLang, text);
    }
    
    // 实际的翻译逻辑，使用默认配置
    private String performTranslation(Language sLang, Language tLang, String text) throws Exception {
        String apiKey = getCredential(getConfigKey(PROPERTY_API_KEY));
        String url = Preferences.getPreference(getConfigKey(PROPERTY_API_URL));
        String model = Preferences.getPreference(getConfigKey(PROPERTY_API_MODEL));
        String temperature_str = Preferences.getPreference(getConfigKey(PROPERTY_API_TEMPERATURE));
        if (temperature_str == null || temperature_str.trim().isEmpty()) {
            temperature_str = "0";
        }
        String provider = Preferences.getPreference(getConfigKey(PROPERTY_API_PROVIDER));
        String prompt = Preferences.getPreference(getConfigKey(PROPERTY_API_PROMPT));
        String enableCache = Preferences.getPreference(getConfigKey(PROPERTY_API_CACHE));
        String enableGlossary = Preferences.getPreference(getConfigKey(PROPERTY_API_GLOSSARY));

        BigDecimal fullAccuracy = new BigDecimal(Double.parseDouble(temperature_str));
        fullAccuracy = fullAccuracy.setScale(3, RoundingMode.DOWN);
        double temperature = fullAccuracy.doubleValue();

        if (apiKey.isEmpty()) {
            return getString("MT_ENGINE_OPENAI_API_KEY_NOTFOUND");
        }

        if (url.isEmpty()) {
            return getString("MT_ENGINE_OPENAI_API_URL_NOTFOUND");
        }

        // 处理自定义模型
        boolean useCustomModel = Boolean.parseBoolean(Preferences.getPreference(getConfigKey(PROPERTY_USE_CUSTOM_MODEL)));
        if (useCustomModel) {
            String customModel = Preferences.getPreference(getConfigKey(PROPERTY_API_CUSTOM_MODEL));
            if (customModel != null && !customModel.trim().isEmpty()) {
                model = customModel.trim();
            } else {
                return getString("MT_ENGINE_OPENAI_CUSTOM_MODEL_EMPTY");
            }
        } else {
            List<String> openaiModelsList = Arrays.asList(openaiModels);
            List<String> claudeModelsList = Arrays.asList(claudeModels);

            if (openaiModelsList.contains(model)) {
                provider = ("default".equals(provider) || "DeepSeek".equals(provider)) ? "OpenAI" : provider;
            } else if (claudeModelsList.contains(model)) {
                provider = ("default".equals(provider)) ? "Claude" : provider;
            } else {
                return "An unsupported model was used!";
            }
        }

        if ("OpenAI".equals(provider)) {
            url += "/v1/chat/completions";
        } else if ("Claude".equals(provider)) {
            url += "/v1/messages";
        } else if ("VolcEngine".equals(provider)) {
            url += "/api/v3/chat/completions";
        }

        String lvSourceLang = sLang.getLanguage();
        String lvTargetLang = tLang.getLanguage();

        String defaultPrompt = String.format("Please translate the following text from %s to %s.", lvSourceLang, lvTargetLang);
        if (prompt.isEmpty()) {
            prompt = defaultPrompt;
        }

        String lvShortText = text.length() > 5000 ? text.substring(0, 4997) + "\u2026" : text;
        String prev = getCachedTranslation(sLang, tLang, lvShortText);
        if (prev != null && "true".equals(enableCache)) {
            return prev;
        }

        StringBuilder glossaryTable = new StringBuilder();
        if ("true".equals(enableGlossary)) {
            List<org.omegat.gui.glossary.GlossaryEntry> entries = Core.getGlossaryManager()
                    .searchSourceMatches(Core.getEditor().getCurrentEntry());
            if (!entries.isEmpty()) {
                glossaryTable.append("\nYou can refer to the following glossary when translating:\n");
                for (org.omegat.gui.glossary.GlossaryEntry entry : entries) {
                    glossaryTable.append(String.format(
                            "src: %s, loc: %s, comment: %s\n",
                            entry.getSrcText(), entry.getLocText(), entry.getCommentText()));
                }
                prompt = prompt + glossaryTable.toString();
            }
        }
        
        return executeTranslationRequest(url, apiKey, model, temperature, prompt, lvShortText, provider, sLang, tLang);
    }
    
    @Override
    protected String getConfigKey(String baseKey) {
        if ("api".equals(configId)) {
            return baseKey;
        } else {
            return baseKey.replace("openai.", "openai." + configId + ".");
        }
    }
    
    // 多配置管理字段
    protected static final String PROPERTY_CONFIG_LIST = "openai.config.list";
    protected static final String PROPERTY_CONFIG_NAME_TEMPLATE = "openai.%s.config.name";
    protected static final String PROPERTY_CONFIG_ENABLED_TEMPLATE = "openai.%s.config.enabled";
    protected static final String PROPERTY_CONFIG_CREATED_TEMPLATE = "openai.%s.config.created";
    
    // API配置字段模板（用于多配置）
    protected static final String PROPERTY_API_KEY_TEMPLATE = "openai.%s.api.Key";
    protected static final String PROPERTY_API_URL_TEMPLATE = "openai.%s.api.url";
    protected static final String PROPERTY_API_MODEL_TEMPLATE = "openai.%s.api.model";
    protected static final String PROPERTY_API_CUSTOM_MODEL_TEMPLATE = "openai.%s.api.custom.model";
    protected static final String PROPERTY_USE_CUSTOM_MODEL_TEMPLATE = "openai.%s.api.use.custom.model";
    protected static final String PROPERTY_API_PROVIDER_TEMPLATE = "openai.%s.api.format";
    protected static final String PROPERTY_API_PROMPT_TEMPLATE = "openai.%s.api.prompt";
    protected static final String PROPERTY_API_TEMPERATURE_TEMPLATE = "openai.%s.api.temperature";
    protected static final String PROPERTY_API_CACHE_TEMPLATE = "openai.%s.api.enable.cache";
    protected static final String PROPERTY_API_GLOSSARY_TEMPLATE = "openai.%s.api.enable.glossary";
    
    /**
     * 生成配置ID
     */
    private static String generateConfigId() {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 获取配置列表
     */
    private static List<String> getConfigList() {
        String listStr = Preferences.getPreference(PROPERTY_CONFIG_LIST);
        if (listStr == null || listStr.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(listStr.split(","));
    }
    
    /**
     * 保存配置列表
     */
    private static void saveConfigList(List<String> configIds) {
        String listStr = String.join(",", configIds);
        Preferences.setPreference(PROPERTY_CONFIG_LIST, listStr);
    }
    
    /**
     * 从Preferences加载单个配置数据
     */
    private ConfigTabData loadConfigData(String configId) {
        ConfigTabData data;
        
        if ("api".equals(configId)) {
            // 第一个配置使用原有的key结构
            // 尝试从配置中读取名称，如果没有则使用默认值
            String configName = Preferences.getPreference(String.format(PROPERTY_CONFIG_NAME_TEMPLATE, "api"));
            if (configName == null || configName.trim().isEmpty()) {
                configName = getString("CONFIG_DEFAULT_NAME");
            }
            data = new ConfigTabData("api", configName);
            data.apiKey = getCredential(PROPERTY_API_KEY);
            data.apiUrl = Preferences.getPreference(PROPERTY_API_URL);
            data.prompt = Preferences.getPreference(PROPERTY_API_PROMPT);
            data.temperature = Preferences.getPreference(PROPERTY_API_TEMPERATURE);
            
            String savedModel = Preferences.getPreference(PROPERTY_API_MODEL);
            boolean useCustomModel = Boolean.parseBoolean(Preferences.getPreference(PROPERTY_USE_CUSTOM_MODEL));
            
            data.useCustomModel = useCustomModel;
            if (useCustomModel) {
                data.customModel = Preferences.getPreference(PROPERTY_API_CUSTOM_MODEL);
            } else {
                data.model = savedModel != null ? savedModel : "gpt-4o";
            }
            
            data.apiProvider = Preferences.getPreference(PROPERTY_API_PROVIDER);
            if (data.apiProvider == null) {
                data.apiProvider = "OpenAI";
            }
            
            data.enableCache = Boolean.parseBoolean(Preferences.getPreference(PROPERTY_API_CACHE));
            data.enableGlossary = Boolean.parseBoolean(Preferences.getPreference(PROPERTY_API_GLOSSARY));
            data.temporaryCredential = false;
            
            // 加载默认配置的新字段
            String enabledStr = Preferences.getPreference(String.format(PROPERTY_CONFIG_ENABLED_TEMPLATE, "api"));
            data.enabled = enabledStr == null || enabledStr.trim().isEmpty() ? true : Boolean.parseBoolean(enabledStr);
            
            data.createdTime = Preferences.getPreference(String.format(PROPERTY_CONFIG_CREATED_TEMPLATE, "api"));
            if (data.createdTime == null || data.createdTime.trim().isEmpty()) {
                data.createdTime = String.valueOf(System.currentTimeMillis());
            }
            
        } else {
            // 其他配置使用模板key结构
            String configName = Preferences.getPreference(String.format(PROPERTY_CONFIG_NAME_TEMPLATE, configId));
            if (configName == null || configName.trim().isEmpty()) {
                configName = getString("CONFIG_NAME_PREFIX") + configId;
            }
            
            data = new ConfigTabData(configId, configName);
            data.enabled = Boolean.parseBoolean(Preferences.getPreference(String.format(PROPERTY_CONFIG_ENABLED_TEMPLATE, configId)));
            data.createdTime = Preferences.getPreference(String.format(PROPERTY_CONFIG_CREATED_TEMPLATE, configId));
            
            data.apiKey = getCredential(String.format(PROPERTY_API_KEY_TEMPLATE, configId));
            data.apiUrl = Preferences.getPreference(String.format(PROPERTY_API_URL_TEMPLATE, configId));
            data.prompt = Preferences.getPreference(String.format(PROPERTY_API_PROMPT_TEMPLATE, configId));
            data.temperature = Preferences.getPreference(String.format(PROPERTY_API_TEMPERATURE_TEMPLATE, configId));
            
            String savedModel = Preferences.getPreference(String.format(PROPERTY_API_MODEL_TEMPLATE, configId));
            boolean useCustomModel = Boolean.parseBoolean(Preferences.getPreference(String.format(PROPERTY_USE_CUSTOM_MODEL_TEMPLATE, configId)));
            
            data.useCustomModel = useCustomModel;
            if (useCustomModel) {
                data.customModel = Preferences.getPreference(String.format(PROPERTY_API_CUSTOM_MODEL_TEMPLATE, configId));
            } else {
                data.model = savedModel != null ? savedModel : "gpt-4o";
            }
            
            data.apiProvider = Preferences.getPreference(String.format(PROPERTY_API_PROVIDER_TEMPLATE, configId));
            if (data.apiProvider == null) {
                data.apiProvider = "OpenAI";
            }
            
            data.enableCache = Boolean.parseBoolean(Preferences.getPreference(String.format(PROPERTY_API_CACHE_TEMPLATE, configId)));
            data.enableGlossary = Boolean.parseBoolean(Preferences.getPreference(String.format(PROPERTY_API_GLOSSARY_TEMPLATE, configId)));
            data.temporaryCredential = false;
        }
        
        return data;
    }
    
    /**
     * 加载所有配置数据
     */
    private List<ConfigTabData> loadAllConfigs() {
        List<ConfigTabData> configs = new ArrayList<>();
        
        // 首先加载默认配置（api）
        ConfigTabData defaultConfig = loadConfigData("api");
        configs.add(defaultConfig);
        
        // 然后加载其他配置
        List<String> configIds = getConfigList();
        for (String configId : configIds) {
            if (!"api".equals(configId)) { // 避免重复加载默认配置
                ConfigTabData config = loadConfigData(configId);
                configs.add(config);
            }
        }
        
        return configs;
    }
    
    /**
     * 保存所有配置数据
     */
    private void saveAllConfigurations(List<ConfigTabData> allConfigs) {
        // 收集所有配置ID（除了api）
        List<String> configIds = new ArrayList<>();
        
        // 保存每个配置
        for (ConfigTabData config : allConfigs) {
            saveConfigData(config);
            
            // 收集非默认配置的ID
            if (!"api".equals(config.configId)) {
                configIds.add(config.configId);
            }
        }
        
        // 更新配置列表
        saveConfigList(configIds);
        
        // 清理已删除的配置
        cleanupDeletedConfigs(allConfigs);
    }
    
    /**
     * 清理已删除的配置
     */
    private void cleanupDeletedConfigs(List<ConfigTabData> currentConfigs) {
        // 获取当前存储的配置列表
        List<String> storedConfigIds = getConfigList();
        
        // 获取当前配置的ID列表
        List<String> currentConfigIds = new ArrayList<>();
        for (ConfigTabData config : currentConfigs) {
            if (!"api".equals(config.configId)) {
                currentConfigIds.add(config.configId);
            }
        }
        
        // 找到需要删除的配置ID
        for (String storedId : storedConfigIds) {
            if (!currentConfigIds.contains(storedId)) {
                // 删除这个配置的所有相关设置
                deleteConfig(storedId);
            }
        }
    }
    
    /**
     * 删除指定配置的所有设置
     */
    private void deleteConfig(String configId) {
        // 注意：不删除 "api" 配置，因为那是默认配置
        if ("api".equals(configId)) {
            return;
        }
        
        // 删除所有相关的配置项
        Preferences.setPreference(String.format(PROPERTY_CONFIG_NAME_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_CONFIG_ENABLED_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_CONFIG_CREATED_TEMPLATE, configId), "");
        
        // 删除API相关配置
        Preferences.setPreference(String.format(PROPERTY_API_URL_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_API_MODEL_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_API_CUSTOM_MODEL_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_USE_CUSTOM_MODEL_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_API_PROVIDER_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_API_PROMPT_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_API_TEMPERATURE_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_API_CACHE_TEMPLATE, configId), "");
        Preferences.setPreference(String.format(PROPERTY_API_GLOSSARY_TEMPLATE, configId), "");
        
        // 删除凭证
        setCredential(String.format(PROPERTY_API_KEY_TEMPLATE, configId), "", false);
    }
    
    /**
     * 保存单个配置数据到Preferences
     */
    private void saveConfigData(ConfigTabData data) {
        if ("api".equals(data.configId)) {
            // 第一个配置使用原有的key结构
            // 保存配置名称（新增）
            Preferences.setPreference(String.format(PROPERTY_CONFIG_NAME_TEMPLATE, "api"), data.name);
            Preferences.setPreference(String.format(PROPERTY_CONFIG_ENABLED_TEMPLATE, "api"), String.valueOf(data.enabled));
            Preferences.setPreference(String.format(PROPERTY_CONFIG_CREATED_TEMPLATE, "api"), data.createdTime);
            
            boolean temporary = data.temporaryCredential;
            setCredential(PROPERTY_API_KEY, data.apiKey, temporary);
            Preferences.setPreference(PROPERTY_API_URL, data.apiUrl);
            Preferences.setPreference(PROPERTY_API_PROMPT, data.prompt);
            Preferences.setPreference(PROPERTY_API_TEMPERATURE, data.temperature);
            
            Preferences.setPreference(PROPERTY_USE_CUSTOM_MODEL, String.valueOf(data.useCustomModel));
            if (data.useCustomModel) {
                Preferences.setPreference(PROPERTY_API_CUSTOM_MODEL, data.customModel);
                Preferences.setPreference(PROPERTY_API_MODEL, "custom");
            } else {
                Preferences.setPreference(PROPERTY_API_MODEL, data.model);
            }
            
            Preferences.setPreference(PROPERTY_API_PROVIDER, data.apiProvider);
            Preferences.setPreference(PROPERTY_API_CACHE, String.valueOf(data.enableCache));
            Preferences.setPreference(PROPERTY_API_GLOSSARY, String.valueOf(data.enableGlossary));
            
        } else {
            // 其他配置使用模板key结构
            Preferences.setPreference(String.format(PROPERTY_CONFIG_NAME_TEMPLATE, data.configId), data.name);
            Preferences.setPreference(String.format(PROPERTY_CONFIG_ENABLED_TEMPLATE, data.configId), String.valueOf(data.enabled));
            Preferences.setPreference(String.format(PROPERTY_CONFIG_CREATED_TEMPLATE, data.configId), data.createdTime);
            
            boolean temporary = data.temporaryCredential;
            setCredential(String.format(PROPERTY_API_KEY_TEMPLATE, data.configId), data.apiKey, temporary);
            Preferences.setPreference(String.format(PROPERTY_API_URL_TEMPLATE, data.configId), data.apiUrl);
            Preferences.setPreference(String.format(PROPERTY_API_PROMPT_TEMPLATE, data.configId), data.prompt);
            Preferences.setPreference(String.format(PROPERTY_API_TEMPERATURE_TEMPLATE, data.configId), data.temperature);
            
            Preferences.setPreference(String.format(PROPERTY_USE_CUSTOM_MODEL_TEMPLATE, data.configId), String.valueOf(data.useCustomModel));
            if (data.useCustomModel) {
                Preferences.setPreference(String.format(PROPERTY_API_CUSTOM_MODEL_TEMPLATE, data.configId), data.customModel);
                Preferences.setPreference(String.format(PROPERTY_API_MODEL_TEMPLATE, data.configId), "custom");
            } else {
                Preferences.setPreference(String.format(PROPERTY_API_MODEL_TEMPLATE, data.configId), data.model);
            }
            
            Preferences.setPreference(String.format(PROPERTY_API_PROVIDER_TEMPLATE, data.configId), data.apiProvider);
            Preferences.setPreference(String.format(PROPERTY_API_CACHE_TEMPLATE, data.configId), String.valueOf(data.enableCache));
            Preferences.setPreference(String.format(PROPERTY_API_GLOSSARY_TEMPLATE, data.configId), String.valueOf(data.enableGlossary));
        }
    }

    /**
     * 在软件启动时会自动调用该函数来注册插件.
     */
    @SuppressWarnings("unused")
    public static void loadPlugins() {
        LOGGER.debug("加载 OpenaiTranslate Plugin");
        
        // 注册默认插件
        Core.registerMachineTranslationClass(OpenaiTranslate.class);
        
        // 注册额外的翻译插件实例
        registerAdditionalTranslators();
    }
    
    /**
     * 注册额外的翻译器实例
     */
    private static void registerAdditionalTranslators() {
        // 读取配置列表并注册额外插件
        List<String> configIds = getConfigList();
        for (String configId : configIds) {
            if (!"api".equals(configId)) { // 跳过默认配置
                // 检查配置是否启用
                String enabledStr = Preferences.getPreference(String.format(PROPERTY_CONFIG_ENABLED_TEMPLATE, configId));
                boolean enabled = enabledStr == null || enabledStr.trim().isEmpty() ? true : Boolean.parseBoolean(enabledStr);
                
                if (enabled) {
                    // 获取配置名称
                    String configName = Preferences.getPreference(String.format(PROPERTY_CONFIG_NAME_TEMPLATE, configId));
                    if (configName == null || configName.trim().isEmpty()) {
                        configName = getString("CONFIG_NAME_PREFIX") + configId;
                    }
                    
                    // 创建并注册配置特定的翻译器类
                    registerConfigSpecificTranslator(configId, configName);
                }
            }
        }
    }
    
    /**
     * 为特定配置注册翻译器
     */
    private static void registerConfigSpecificTranslator(String configId, String configName) {
        try {
            // 使用 Byte Buddy 动态生成继承自 ConfigurableOpenaiTranslate 的类
            Class<? extends ConfigurableOpenaiTranslate> dynamicTranslatorClass = new ByteBuddy()
                    .subclass(ConfigurableOpenaiTranslate.class)
                    .name("xyz.inertia.machinetranslators.openaitrans.DynamicTranslator_" + configId)
                    .method(ElementMatchers.named("getName"))
                    .intercept(FixedValue.value(configName))
                    .make()
                    .load(OpenaiTranslate.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();

            // 将配置信息存入中央注册表，以便无参构造函数可以进行懒加载初始化
            translatorConfigs.put(dynamicTranslatorClass, new ConfigInfo(configId, configName));

            // 注册这个动态生成的类
            Core.registerMachineTranslationClass(dynamicTranslatorClass);
            LOGGER.debug("动态注册插件实例: {} (配置ID: {})", configName, configId);
        } catch (Exception e) {
            LOGGER.error("注册插件实例失败: {} (配置ID: {}), 错误: {}", configName, configId, e.getMessage(), e);
        }
    }

    /**
     * 为特定配置创建翻译器类
     */
    private static Class<? extends BaseOpenaiTranslate> createConfigTranslatorClass(String configId, String configName) {
        // 使用工厂模式创建配置特定的翻译器类
        return ConfigSpecificTranslatorFactory.createTranslatorClass(configId, configName);
    }
    
    /**
     * 中央配置注册表，用于存储类与配置信息的映射
     */
    static final Map<Class<?>, ConfigInfo> translatorConfigs = new HashMap<>();

    /**
     * 配置信息的数据载体
     */
    static class ConfigInfo {
        final String configId;
        final String displayName;

        ConfigInfo(String configId, String displayName) {
            this.configId = configId;
            this.displayName = displayName;
        }
    }
    
    // 预定义的配置特定翻译器类
    public static class AdditionalTranslator1 extends ConfigurableOpenaiTranslate {
        public AdditionalTranslator1() { super(translatorConfigs.get(AdditionalTranslator1.class)); }
    }
    public static class AdditionalTranslator2 extends ConfigurableOpenaiTranslate {
        public AdditionalTranslator2() { super(translatorConfigs.get(AdditionalTranslator2.class)); }
    }
    public static class AdditionalTranslator3 extends ConfigurableOpenaiTranslate {
        public AdditionalTranslator3() { super(translatorConfigs.get(AdditionalTranslator3.class)); }
    }
    public static class AdditionalTranslator4 extends ConfigurableOpenaiTranslate {
        public AdditionalTranslator4() { super(translatorConfigs.get(AdditionalTranslator4.class)); }
    }
    public static class AdditionalTranslator5 extends ConfigurableOpenaiTranslate {
        public AdditionalTranslator5() { super(translatorConfigs.get(AdditionalTranslator5.class)); }
    }
    
    /**
     * 配置特定翻译器的工厂类
     */
    private static class ConfigSpecificTranslatorFactory {
        private static int translatorCounter = 0;
        private static final List<Class<? extends BaseOpenaiTranslate>> translatorClasses = Arrays.asList(
                AdditionalTranslator1.class, AdditionalTranslator2.class,
                AdditionalTranslator3.class, AdditionalTranslator4.class, AdditionalTranslator5.class
        );

        static Class<? extends BaseOpenaiTranslate> createTranslatorClass(String configId, String displayName) {
            // 循环使用预定义的类
            Class<? extends BaseOpenaiTranslate> translatorClass = translatorClasses.get(translatorCounter % translatorClasses.size());
            translatorCounter++;
            
            // 将配置信息存入中央注册表，以类作为键
            translatorConfigs.put(translatorClass, new ConfigInfo(configId, displayName));
            
            return translatorClass;
        }
    }

    /**
     * 卸载插件，可以留空，示例代码就留空.
     */
    @SuppressWarnings("unused")
    public static void unloadPlugins() {}

    // 基类方法已在OpenaiTranslate类中重写以确保兼容性

    /**
     * Store a credential. Credentials are stored in temporary system properties and, if
     * <code>temporary</code> is <code>false</code>, in the program's persistent preferences encoded in
     * Base64. Retrieve a credential with {@link #getCredential(String)}.
     *
     * @param id
     *            ID or key of the credential to store
     * @param value
     *            value of the credential to store
     * @param temporary
     *            if <code>false</code>, encode with Base64 and store in persistent preferences as well
     */
    protected void setCredential(String id, String value, boolean temporary) {
        System.setProperty(id, value);
        if (temporary) {
            CredentialsManager.getInstance().store(id, "");
        } else {
            CredentialsManager.getInstance().store(id, value);
        }
    }

    /**
     * Retrieve a credential with the given ID. First checks temporary system properties, then falls back to
     * the program's persistent preferences. Store a credential with
     * {@link #setCredential(String, String, boolean)}.
     *
     * @param id
     *            ID or key of the credential to retrieve
     * @return the credential value in plain text
     */
    protected String getCredential(String id) {
        String property = System.getProperty(id);
        if (property != null) {
            return property;
        }
        return CredentialsManager.getInstance().retrieve(id).orElse("");
    }

    protected void setKey(String key, boolean temporary) {
        setCredential(PROPERTY_API_KEY, key, temporary);
    }

    protected String getKey() throws Exception {
        String key = getCredential(PROPERTY_API_KEY);
        if (StringUtil.isEmpty(key)) {
            throw new Exception(getString("MT_ENGINE_OPENAI_API_KEY_NOTFOUND"));
        }
        return key;
    }

    // 旧的translate方法已删除，功能已移到基类中

    // isConfigurable方法已在基类中实现

    // 配置数据模型
    public static class ConfigTabData {
        public String configId;           // 配置唯一标识符
        public String name;               // 配置显示名称
        public boolean enabled;           // 是否启用此配置
        public String createdTime;        // 创建时间
        public String apiKey;
        public String apiUrl;
        public String prompt;
        public String temperature;
        public String model;
        public String customModel;
        public boolean useCustomModel;
        public String apiProvider;
        public boolean enableCache;
        public boolean enableGlossary;
        public boolean temporaryCredential;
        
        public ConfigTabData(String name) {
            this.configId = generateConfigId();
            this.name = name;
            this.enabled = true;
            this.createdTime = String.valueOf(System.currentTimeMillis());
            // 设置默认值
            this.apiKey = "";
            this.apiUrl = "";
            this.prompt = "";
            this.temperature = "0";
            this.model = "gpt-4o";
            this.customModel = "";
            this.useCustomModel = false;
            this.apiProvider = "OpenAI";
            this.enableCache = true;
            this.enableGlossary = true;
            this.temporaryCredential = false;
        }
        
        public ConfigTabData(String configId, String name) {
            this.configId = configId;
            this.name = name;
            this.enabled = true;
            this.createdTime = String.valueOf(System.currentTimeMillis());
            // 设置默认值
            this.apiKey = "";
            this.apiUrl = "";
            this.prompt = "";
            this.temperature = "0";
            this.model = "gpt-4o";
            this.customModel = "";
            this.useCustomModel = false;
            this.apiProvider = "OpenAI";
            this.enableCache = true;
            this.enableGlossary = true;
            this.temporaryCredential = false;
        }
        
        public ConfigTabData copy() {
            ConfigTabData copy = new ConfigTabData(this.name);
            copy.configId = generateConfigId(); // 生成新的ID
            copy.enabled = this.enabled;
            copy.createdTime = String.valueOf(System.currentTimeMillis());
            copy.apiKey = this.apiKey;
            copy.apiUrl = this.apiUrl;
            copy.prompt = this.prompt;
            copy.temperature = this.temperature;
            copy.model = this.model;
            copy.customModel = this.customModel;
            copy.useCustomModel = this.useCustomModel;
            copy.apiProvider = this.apiProvider;
            copy.enableCache = this.enableCache;
            copy.enableGlossary = this.enableGlossary;
            copy.temporaryCredential = this.temporaryCredential;
            return copy;
        }
    }

    // 动态标签页组件
    @SuppressWarnings("serial")
    public class DynamicTabbedPane extends JPanel {
        private JPanel tabBar;
        private JPanel contentArea;
        private CardLayout cardLayout;
        private List<ConfigTabInfo> tabs;
        private int selectedIndex = -1;
        private int tabCounter = 1;
        
        private class ConfigTabInfo {
            String id;
            String title;
            ConfigTabPanel panel;
            JButton tabButton;
            JButton closeButton;
            ConfigTabData data;
        }
        
        public DynamicTabbedPane() {
            tabs = new ArrayList<>();
            initComponents();
        }
        
        public DynamicTabbedPane(ConfigTabData initialData) {
            tabs = new ArrayList<>();
            initComponents(initialData);
        }
        
        public DynamicTabbedPane(List<ConfigTabData> allConfigs) {
            tabs = new ArrayList<>();
            initComponents(allConfigs);
        }
        
        private void initComponents() {
            initComponents((ConfigTabData) null);
        }
        
        private void initComponents(ConfigTabData initialData) {
            setLayout(new BorderLayout());
            
            // 创建标签页栏
            tabBar = new JPanel();
            tabBar.setLayout(new BoxLayout(tabBar, BoxLayout.X_AXIS));
            tabBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));
            
            // 创建内容区域
            cardLayout = new CardLayout();
            contentArea = new JPanel(cardLayout);
            
            // 添加按钮
            JButton addButton = new JButton("+");
            addButton.setToolTipText(getString("CONFIG_ADD_TAB_TOOLTIP"));
            addButton.setPreferredSize(new Dimension(30, 25));
            addButton.setMaximumSize(new Dimension(30, 25));
            addButton.addActionListener(e -> addNewTab());
            
            tabBar.add(Box.createHorizontalStrut(5));
            tabBar.add(addButton);
            tabBar.add(Box.createHorizontalGlue());
            
            add(tabBar, BorderLayout.NORTH);
            add(contentArea, BorderLayout.CENTER);
            
            // 添加默认标签页，如果有初始数据则使用初始数据
            if (initialData != null) {
                addDefaultTabWithData(initialData);
            } else {
                addDefaultTab();
            }
        }
        
        private void initComponents(List<ConfigTabData> allConfigs) {
            setLayout(new BorderLayout());
            
            // 创建标签页栏
            tabBar = new JPanel();
            tabBar.setLayout(new BoxLayout(tabBar, BoxLayout.X_AXIS));
            tabBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));
            
            // 创建内容区域
            cardLayout = new CardLayout();
            contentArea = new JPanel(cardLayout);
            
            // 添加按钮
            JButton addButton = new JButton("+");
            addButton.setToolTipText(getString("CONFIG_ADD_TAB_TOOLTIP"));
            addButton.setPreferredSize(new Dimension(30, 25));
            addButton.setMaximumSize(new Dimension(30, 25));
            addButton.addActionListener(e -> addNewTab());
            
            tabBar.add(Box.createHorizontalStrut(5));
            tabBar.add(addButton);
            tabBar.add(Box.createHorizontalGlue());
            
            add(tabBar, BorderLayout.NORTH);
            add(contentArea, BorderLayout.CENTER);
            
            // 添加所有配置标签页
            if (allConfigs != null && !allConfigs.isEmpty()) {
                for (int i = 0; i < allConfigs.size(); i++) {
                    ConfigTabData config = allConfigs.get(i);
                    addTab(config, false); // 不自动选择标签页
                }
                // 最后选择第一个标签页
                selectTab(0);
            } else {
                addDefaultTab();
            }
        }
        
        private void addDefaultTab() {
            ConfigTabData defaultData = new ConfigTabData(getString("CONFIG_DEFAULT_NAME"));
            addTab(defaultData);
        }
        
        // 添加新方法：使用预设数据创建默认标签页
        private void addDefaultTabWithData(ConfigTabData data) {
            addTab(data);
        }
        
        private void addNewTab() {
            String newTabName = getString("CONFIG_NAME_PREFIX") + tabCounter++;
            ConfigTabData newData = new ConfigTabData(newTabName);
            
            // 如果有选中的标签页，复制其配置作为模板
            if (selectedIndex >= 0 && selectedIndex < tabs.size()) {
                ConfigTabData currentData = tabs.get(selectedIndex).data;
                newData = currentData.copy();
                newData.name = newTabName;
            }
            
            addTab(newData);
        }
        
        public void addTab(ConfigTabData data) {
            addTab(data, true);
        }
        
        public void addTab(ConfigTabData data, boolean autoSelect) {
            ConfigTabInfo tabInfo = new ConfigTabInfo();
            tabInfo.id = "tab_" + System.currentTimeMillis();
            tabInfo.title = data.name;
            tabInfo.data = data;
            tabInfo.panel = new ConfigTabPanel(data);
            
            // 创建标签页按钮
            tabInfo.tabButton = new JButton(data.name);
            tabInfo.tabButton.setPreferredSize(new Dimension(100, 25));
            tabInfo.tabButton.setMaximumSize(new Dimension(150, 25));
            tabInfo.tabButton.addActionListener(e -> selectTab(tabs.indexOf(tabInfo)));
            
            // 创建关闭按钮
            tabInfo.closeButton = new JButton("×");
            tabInfo.closeButton.setPreferredSize(new Dimension(20, 25));
            tabInfo.closeButton.setMaximumSize(new Dimension(20, 25));
            tabInfo.closeButton.setMargin(new Insets(0, 0, 0, 0));
            tabInfo.closeButton.setBorderPainted(false);
            tabInfo.closeButton.setContentAreaFilled(false);
            tabInfo.closeButton.setFocusable(false);
            tabInfo.closeButton.setToolTipText(getString("CONFIG_CLOSE_TAB_TOOLTIP"));
            tabInfo.closeButton.addActionListener(e -> removeTab(tabs.indexOf(tabInfo)));
            
            // 创建标签页面板
            JPanel tabPanel = new JPanel();
            tabPanel.setLayout(new BoxLayout(tabPanel, BoxLayout.X_AXIS));
            tabPanel.add(tabInfo.tabButton);
            
            // 只有多于一个标签页时才显示关闭按钮
            if (tabs.size() > 0) {
                tabPanel.add(tabInfo.closeButton);
                // 为现有标签页添加关闭按钮
                updateCloseButtonsVisibility();
            }
            
            // 添加到标签页栏（在加号按钮前）
            tabBar.add(tabPanel, tabBar.getComponentCount() - 2);
            
            // 添加到内容区域
            contentArea.add(tabInfo.panel, tabInfo.id);
            
            tabs.add(tabInfo);
            
            if (autoSelect) {
                selectTab(tabs.size() - 1);
            }
            
            revalidate();
            repaint();
        }
        
        private void updateCloseButtonsVisibility() {
            boolean showCloseButtons = tabs.size() > 1;
            for (ConfigTabInfo tabInfo : tabs) {
                tabInfo.closeButton.setVisible(showCloseButtons);
            }
        }
        
        private void selectTab(int index) {
            if (index >= 0 && index < tabs.size() && index != selectedIndex) {
                selectedIndex = index;
                ConfigTabInfo selectedTab = tabs.get(index);
                cardLayout.show(contentArea, selectedTab.id);
                
                // 更新标签页按钮状态
                for (int i = 0; i < tabs.size(); i++) {
                    ConfigTabInfo tab = tabs.get(i);
                    if (i == index) {
                        tab.tabButton.setBackground(Color.LIGHT_GRAY);
                        tab.tabButton.setOpaque(true);
                    } else {
                        tab.tabButton.setBackground(null);
                        tab.tabButton.setOpaque(false);
                    }
                }
            }
        }
        
        private void removeTab(int index) {
            if (index >= 0 && index < tabs.size() && tabs.size() > 1) {
                ConfigTabInfo tabInfo = tabs.get(index);
                
                // 从UI中移除标签页面板
                for (Component comp : tabBar.getComponents()) {
                    if (comp instanceof JPanel) {
                        JPanel panel = (JPanel) comp;
                        if (panel.getComponentCount() > 0 && 
                            panel.getComponent(0) == tabInfo.tabButton) {
                            tabBar.remove(panel);
                            break;
                        }
                    }
                }
                
                contentArea.remove(tabInfo.panel);
                tabs.remove(index);
                
                // 更新关闭按钮可见性
                updateCloseButtonsVisibility();
                
                // 选择相邻标签页
                if (selectedIndex >= tabs.size()) {
                    selectTab(tabs.size() - 1);
                } else if (selectedIndex == index) {
                    selectTab(Math.max(0, index - 1));
                } else if (selectedIndex > index) {
                    selectedIndex--;
                }
                
                revalidate();
                repaint();
            }
        }
        
        public List<ConfigTabData> getAllConfigData() {
            List<ConfigTabData> allData = new ArrayList<>();
            for (ConfigTabInfo tab : tabs) {
                // 从UI组件更新数据
                tab.panel.updateConfigData();
                allData.add(tab.data);
            }
            return allData;
        }
        
        public ConfigTabData getCurrentConfigData() {
            if (selectedIndex >= 0 && selectedIndex < tabs.size()) {
                ConfigTabInfo selectedTab = tabs.get(selectedIndex);
                selectedTab.panel.updateConfigData();
                return selectedTab.data;
            }
            return null;
        }
        
        public int getTabCount() {
            return tabs.size();
        }
        
        /**
         * 更新指定面板的标签页标题
         */
        public void updateTabTitle(ConfigTabPanel panel, String newTitle) {
            for (ConfigTabInfo tab : tabs) {
                if (tab.panel == panel) {
                    tab.title = newTitle;
                    // 根据启用状态显示不同的标题样式
                    boolean enabled = tab.data.enabled;
                    String displayTitle = enabled ? newTitle : newTitle + getString("CONFIG_DISABLED_SUFFIX");
                    tab.tabButton.setText(displayTitle);
                    
                    // 设置不同的颜色来表示启用状态
                    if (enabled) {
                        tab.tabButton.setForeground(Color.BLACK);
                    } else {
                        tab.tabButton.setForeground(Color.GRAY);
                    }
                    break;
                }
            }
        }
    }

    // 单个配置标签页面板
    @SuppressWarnings("serial")
    public class ConfigTabPanel extends JPanel {
        private ConfigTabData configData;
        private JPanel credentialsPanel;
        
        // UI组件
        private JCheckBox enabledCheckBox;    // 新增：启用状态
        private JTextField configNameField;   // 新增：配置名称
        private JTextArea apiKeyField;
        private JTextArea promptField;
        private JTextField urlField;
        private JTextField temperatureField;
        private JComboBox<String> modelComboBox;
        private JComboBox<String> apiComboBox;
        private JTextField customModelField;
        private JCheckBox useCustomModelCheckBox;
        private JCheckBox temporaryCheckBox;
        private JCheckBox cacheCheckBox;
        private JCheckBox glossaryCheckBox;
        
        public ConfigTabPanel(ConfigTabData data) {
            this.configData = data;
            initComponents();
            loadConfigData();
        }
        
        private void initComponents() {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            credentialsPanel = new JPanel(new GridBagLayout());
            
            // 创建输入组件
            enabledCheckBox = new JCheckBox(getString("CONFIG_ENABLE_LABEL"), true);
            
            // 添加启用状态变化监听器
            enabledCheckBox.addActionListener(e -> {
                configData.enabled = enabledCheckBox.isSelected();
                // 更新标签页标题显示状态
                Container parent = ConfigTabPanel.this.getParent();
                while (parent != null && !(parent instanceof DynamicTabbedPane)) {
                    parent = parent.getParent();
                }
                if (parent instanceof DynamicTabbedPane) {
                    ((DynamicTabbedPane) parent).updateTabTitle(ConfigTabPanel.this, configData.name);
                }
            });
            
            configNameField = new JTextField();
            
            // 添加配置名称变化监听器
            configNameField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateTabTitle();
                }
                
                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateTabTitle();
                }
                
                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateTabTitle();
                }
                
                private void updateTabTitle() {
                    SwingUtilities.invokeLater(() -> {
                        String newName = configNameField.getText().trim();
                        if (!newName.isEmpty()) {
                            configData.name = newName;
                            // 通知父组件更新标签页标题
                            Container parent = ConfigTabPanel.this.getParent();
                            while (parent != null && !(parent instanceof DynamicTabbedPane)) {
                                parent = parent.getParent();
                            }
                            if (parent instanceof DynamicTabbedPane) {
                                ((DynamicTabbedPane) parent).updateTabTitle(ConfigTabPanel.this, newName);
                            }
                        }
                    });
                }
            });
            apiKeyField = createMultiLineTextArea();
            urlField = new JTextField();
            promptField = createMultiLineTextArea();
            temperatureField = new JTextField();
            modelComboBox = new JComboBox<>();
            apiComboBox = new JComboBox<>();
            customModelField = new JTextField();
            useCustomModelCheckBox = new JCheckBox(getString("MT_ENGINE_USE_CUSTOM_MODEL_LABEL"));
            customModelField.setVisible(false);
            
            // 添加复选框事件监听
            useCustomModelCheckBox.addActionListener(e -> {
                boolean useCustom = useCustomModelCheckBox.isSelected();
                handleCustomModelToggle(useCustom);
            });
            
            temporaryCheckBox = new JCheckBox();
            cacheCheckBox = new JCheckBox(getString("MT_ENGINE_CACHE_BUTTON"), true);
            glossaryCheckBox = new JCheckBox(getString("MT_ENGINE_GLOSSARY_BUTTON"), true);
            
            // 使用 GridBagLayout 添加组件
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            
            // 首先添加启用状态复选框（放在顶部）
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            credentialsPanel.add(enabledCheckBox, gbc);
            gbc.gridwidth = 1; // 重置
            
            // 添加配置名称字段
            addLabelAndField(credentialsPanel, getString("CONFIG_NAME_LABEL"), configNameField, gbc, 1);
            
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_KEY_LABEL", apiKeyField, gbc, 2);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_URL_LABEL", urlField, gbc, 3);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_PROMPT_LABEL", promptField, gbc, 4);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_TEMPERATURE_LABEL", temperatureField, gbc, 5);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_MODEL_NAME_LABEL", modelComboBox, gbc, 6);
            
            // 在同一位置添加自定义模型输入框，但初始隐藏
            gbc.gridx = 1;
            gbc.gridy = 6;
            gbc.weightx = 1.0;
            credentialsPanel.add(customModelField, gbc);
            
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_API_FORMAT_LABEL", apiComboBox, gbc, 7);
            
            // 添加复选框
            gbc.gridx = 1;
            gbc.gridy = 8;
            credentialsPanel.add(useCustomModelCheckBox, gbc);
            gbc.gridy = 9;
            credentialsPanel.add(glossaryCheckBox, gbc);
            gbc.gridy = 10;
            credentialsPanel.add(temporaryCheckBox, gbc);
            gbc.gridy = 11;
            credentialsPanel.add(cacheCheckBox, gbc);
            
            // 设置复选框文本
            Mnemonics.setLocalizedText(temporaryCheckBox, OStrings.getString("PREFS_CREDENTIAL_TEMPORARY_LABEL"));
            
            add(credentialsPanel, BorderLayout.CENTER);
        }
        
        private void loadConfigData() {
            enabledCheckBox.setSelected(configData.enabled);
            configNameField.setText(configData.name);
            apiKeyField.setText(configData.apiKey);
            urlField.setText(configData.apiUrl);
            promptField.setText(configData.prompt);
            temperatureField.setText(configData.temperature);
            
            // 设置模型下拉框
            modelComboBox.setModel(new DefaultComboBoxModel<>(getAllModels()));
            
            if (configData.useCustomModel) {
                customModelField.setText(configData.customModel);
                useCustomModelCheckBox.setSelected(true);
                handleCustomModelToggle(true);
            } else {
                modelComboBox.setSelectedItem(configData.model);
                useCustomModelCheckBox.setSelected(false);
            }
            
            apiComboBox.setModel(new DefaultComboBoxModel<>(Providers));
            apiComboBox.setSelectedItem(configData.apiProvider);
            
            cacheCheckBox.setSelected(configData.enableCache);
            glossaryCheckBox.setSelected(configData.enableGlossary);
            temporaryCheckBox.setSelected(configData.temporaryCredential);
        }
        
        public void updateConfigData() {
            configData.enabled = enabledCheckBox.isSelected();
            configData.name = configNameField.getText().trim();
            configData.apiKey = apiKeyField.getText().trim();
            configData.apiUrl = urlField.getText().trim();
            configData.prompt = promptField.getText().trim();
            configData.temperature = temperatureField.getText().trim();
            
            configData.useCustomModel = useCustomModelCheckBox.isSelected();
            if (configData.useCustomModel) {
                configData.customModel = customModelField.getText().trim();
            } else {
                configData.model = (String) modelComboBox.getSelectedItem();
            }
            
            configData.apiProvider = (String) apiComboBox.getSelectedItem();
            configData.enableCache = cacheCheckBox.isSelected();
            configData.enableGlossary = glossaryCheckBox.isSelected();
            configData.temporaryCredential = temporaryCheckBox.isSelected();
        }
        
        private void handleCustomModelToggle(boolean useCustom) {
            SwingUtilities.invokeLater(() -> {
                if (useCustom) {
                    // 隐藏下拉框，显示输入框
                    modelComboBox.setVisible(false);
                    customModelField.setVisible(true);
                    updateApiComboBoxForCustom();
                } else {
                    // 显示下拉框，隐藏输入框
                    customModelField.setVisible(false);
                    modelComboBox.setVisible(true);
                    restoreOriginalApiComboBox();
                }
                credentialsPanel.revalidate();
                credentialsPanel.repaint();
            });
        }
        
        private void updateApiComboBoxForCustom() {
            String[] providersWithoutDefault = {"OpenAI", "Claude", "DeepSeek", "VolcEngine"};
            String currentSelection = (String) apiComboBox.getSelectedItem();
            apiComboBox.setModel(new DefaultComboBoxModel<>(providersWithoutDefault));
            
            // 如果当前选择是 default，自动切换到 OpenAI
            if ("default".equals(currentSelection)) {
                apiComboBox.setSelectedItem("OpenAI");
            } else if (Arrays.asList(providersWithoutDefault).contains(currentSelection)) {
                apiComboBox.setSelectedItem(currentSelection);
            } else {
                apiComboBox.setSelectedItem("OpenAI");
            }
        }
        
        private void restoreOriginalApiComboBox() {
            String currentSelection = (String) apiComboBox.getSelectedItem();
            apiComboBox.setModel(new DefaultComboBoxModel<>(Providers));
            
            // 恢复之前的选择，如果仍然有效的话
            if (Arrays.asList(Providers).contains(currentSelection)) {
                apiComboBox.setSelectedItem(currentSelection);
            }
        }
        
        private void addLabelAndField(JPanel panel, String labelKey, JComponent field, GridBagConstraints gbc, int row) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.0;
            
            // 检查是否是资源键还是直接字符串
            String labelText;
            try {
                labelText = getString(labelKey);
            } catch (Exception e) {
                // 如果不是资源键，直接使用字符串
                labelText = labelKey;
            }
            
            panel.add(new JLabel(labelText), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            // 如果是 JTextArea，用 JScrollPane 包装
            if (field instanceof JTextArea) {
                JScrollPane scrollPane = new JScrollPane(field);
                scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                panel.add(scrollPane, gbc);
            } else {
                panel.add(field, gbc);
            }
            gbc.weightx = 0.0;
        }
        
        public ConfigTabData getConfigData() {
            updateConfigData();
            return configData;
        }
        
        // 创建多行文本区域的私有方法
        private JTextArea createMultiLineTextArea() {
            JTextArea textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

            // 设置基础参数
            int minRows = 1;
            int maxRows = 5;
            int minWidth = 200;
            int maxWidth = 400;
            int padding = 2; // 文本区域内边距

            // 获取字体度量以准确计算文本高度
            FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
            int lineHeight = fm.getHeight();

            // 设置初始大小
            Dimension minSize = new Dimension(minWidth, lineHeight * minRows + 2 * padding);
            Dimension maxSize = new Dimension(maxWidth, lineHeight * maxRows + 2 * padding);

            textArea.setMinimumSize(minSize);
            textArea.setMaximumSize(maxSize);
            textArea.setPreferredSize(minSize);

            textArea.getDocument().addDocumentListener(new DocumentListener() {
                private void updateSize() {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            // 获取文本区域的实际内容
                            String text = textArea.getText();
                            Rectangle viewRect = textArea.getVisibleRect();

                            // 计算需要的行数
                            int availableWidth = Math.max(minWidth, viewRect.width - 2 * padding);
                            int textWidth = fm.stringWidth(text);
                            int estimatedRows = Math.max(
                                    minRows, Math.min(maxRows, (int) Math.ceil((double) textWidth / availableWidth)));

                            // 计算新的首选高度
                            int newHeight = lineHeight * estimatedRows + 2 * padding;

                            // 设置新的首选大小
                            Dimension newSize =
                                    new Dimension(Math.min(maxWidth, Math.max(minWidth, viewRect.width)), newHeight);

                            if (!textArea.getPreferredSize().equals(newSize)) {
                                textArea.setPreferredSize(newSize);
                                textArea.revalidate();
                            }
                        } catch (Exception e) {
                            // 处理可能的异常
                            LOGGER.error("更新文本区域大小时出错", e);
                        }
                    });
                }

                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateSize();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateSize();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateSize();
                }
            });

            // 设置边框和外观
            textArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.GRAY),
                    BorderFactory.createEmptyBorder(padding, padding, padding, padding)));

            return textArea;
        }
    }

    @SuppressWarnings("serial")
    public class OpenAIConfigPanel extends JPanel {
        private DynamicTabbedPane tabbedPane;
        public javax.swing.JButton okButton;
        public javax.swing.JButton cancelButton;
        
        // 兼容性属性，用于向后兼容
        public javax.swing.JTextArea apiKeyField;
        public javax.swing.JTextArea promptField;
        public javax.swing.JTextField urlField;
        public javax.swing.JTextField temperatureField;
        public javax.swing.JComboBox<String> modelComboBox;
        public javax.swing.JComboBox<String> apiComboBox;
        public javax.swing.JTextField customModelField;
        public javax.swing.JCheckBox useCustomModelCheckBox;
        public javax.swing.JCheckBox temporaryCheckBox;
        public javax.swing.JCheckBox cacheCheckBox;
        public javax.swing.JCheckBox glossaryCheckBox;

        public OpenAIConfigPanel() {
            initComponents();
        }
        
        public OpenAIConfigPanel(ConfigTabData initialData) {
            initComponents(initialData);
        }
        
        public OpenAIConfigPanel(List<ConfigTabData> allConfigs) {
            initComponents(allConfigs);
        }

        private void initComponents() {
            initComponents((ConfigTabData) null);
        }
        
        private void initComponents(ConfigTabData initialData) {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // 创建动态标签页组件
            tabbedPane = new DynamicTabbedPane(initialData);
            
            // 创建按钮面板
            JPanel buttonPanel = createButtonPanel();
            
            // 组装面板
            add(tabbedPane, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);
            
            // 初始化兼容性属性（指向当前选中的标签页的组件）
            updateCompatibilityFields();
        }
        
        private void initComponents(List<ConfigTabData> allConfigs) {
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // 创建动态标签页组件
            tabbedPane = new DynamicTabbedPane(allConfigs);
            
            // 创建按钮面板
            JPanel buttonPanel = createButtonPanel();
            
            // 组装面板
            add(tabbedPane, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);
            
            // 初始化兼容性属性（指向当前选中的标签页的组件）
            updateCompatibilityFields();
        }
        
        private JPanel createButtonPanel() {
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
            
            okButton = new JButton();
            Mnemonics.setLocalizedText(okButton, OStrings.getString("BUTTON_OK"));
            
            cancelButton = new JButton();
            Mnemonics.setLocalizedText(cancelButton, OStrings.getString("BUTTON_CANCEL"));
            
            buttonPanel.add(Box.createHorizontalGlue());
            buttonPanel.add(okButton);
            buttonPanel.add(Box.createRigidArea(new Dimension(5, 0)));
            buttonPanel.add(cancelButton);
            
            return buttonPanel;
        }
        
        /**
         * 更新兼容性属性，使其指向当前选中的标签页的组件
         * 这样可以保持与现有代码的兼容性
         */
        private void updateCompatibilityFields() {
            if (tabbedPane.getTabCount() > 0) {
                DynamicTabbedPane.ConfigTabInfo currentTab = tabbedPane.tabs.get(tabbedPane.selectedIndex >= 0 ? tabbedPane.selectedIndex : 0);
                ConfigTabPanel currentPanel = currentTab.panel;
                
                // 更新兼容性属性引用
                this.apiKeyField = currentPanel.apiKeyField;
                this.promptField = currentPanel.promptField;
                this.urlField = currentPanel.urlField;
                this.temperatureField = currentPanel.temperatureField;
                this.modelComboBox = currentPanel.modelComboBox;
                this.apiComboBox = currentPanel.apiComboBox;
                this.customModelField = currentPanel.customModelField;
                this.useCustomModelCheckBox = currentPanel.useCustomModelCheckBox;
                this.temporaryCheckBox = currentPanel.temporaryCheckBox;
                this.cacheCheckBox = currentPanel.cacheCheckBox;
                this.glossaryCheckBox = currentPanel.glossaryCheckBox;
            }
        }
        
        /**
         * 获取当前选中的配置数据
         */
        public ConfigTabData getCurrentConfigData() {
            updateCompatibilityFields(); // 确保引用是最新的
            return tabbedPane.getCurrentConfigData();
        }
        
        /**
         * 获取所有标签页的配置数据
         */
        public List<ConfigTabData> getAllConfigData() {
            return tabbedPane.getAllConfigData();
        }
        
        /**
         * 加载配置数据到首个标签页（保持兼容性）
         */
        public void loadConfigToFirstTab(ConfigTabData data) {
            if (tabbedPane.getTabCount() > 0) {
                DynamicTabbedPane.ConfigTabInfo firstTab = tabbedPane.tabs.get(0);
                firstTab.data = data;
                firstTab.panel.loadConfigData();
                updateCompatibilityFields();
            }
        }
        
        /**
         * 获取动态标签页组件
         */
        public DynamicTabbedPane getTabbedPane() {
            return tabbedPane;
        }

        // 为 ConfigTabPanel 提供的公共方法
        private JTextArea createMultiLineTextArea() {
            JTextArea textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

            // 设置基础参数
            int minRows = 1;
            int maxRows = 5;
            int minWidth = 200;
            int maxWidth = 400;
            int padding = 2; // 文本区域内边距

            // 获取字体度量以准确计算文本高度
            FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
            int lineHeight = fm.getHeight();

            // 设置初始大小
            Dimension minSize = new Dimension(minWidth, lineHeight * minRows + 2 * padding);
            Dimension maxSize = new Dimension(maxWidth, lineHeight * maxRows + 2 * padding);

            textArea.setMinimumSize(minSize);
            textArea.setMaximumSize(maxSize);
            textArea.setPreferredSize(minSize);

            textArea.getDocument().addDocumentListener(new DocumentListener() {
                private void updateSize() {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            // 获取文本区域的实际内容
                            String text = textArea.getText();
                            Rectangle viewRect = textArea.getVisibleRect();

                            // 计算需要的行数
                            int availableWidth = Math.max(minWidth, viewRect.width - 2 * padding);
                            int textWidth = fm.stringWidth(text);
                            int estimatedRows = Math.max(
                                    minRows, Math.min(maxRows, (int) Math.ceil((double) textWidth / availableWidth)));

                            // 计算新的首选高度
                            int newHeight = lineHeight * estimatedRows + 2 * padding;

                            // 设置新的首选大小
                            Dimension newSize =
                                    new Dimension(Math.min(maxWidth, Math.max(minWidth, viewRect.width)), newHeight);

                            if (!textArea.getPreferredSize().equals(newSize)) {
                                textArea.setPreferredSize(newSize);
                                textArea.revalidate();
                            }
                        } catch (Exception e) {
                            // 处理可能的异常
                            LOGGER.error("更新文本区域大小时出错", e);
                        }
                    });
                }

                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateSize();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateSize();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateSize();
                }
            });

            // 设置边框和外观
            textArea.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.GRAY),
                    BorderFactory.createEmptyBorder(padding, padding, padding, padding)));

            return textArea;
        }
    }

    public class OpenAIConfigDialog {
        private final JDialog dialog;
        private final OpenAIConfigPanel panel;

        public OpenAIConfigDialog(Window parent, String title) {
            dialog = new JDialog(parent, title, Dialog.ModalityType.APPLICATION_MODAL);
            panel = new OpenAIConfigPanel();
            initDialog();
        }
        
        public OpenAIConfigDialog(Window parent, String title, ConfigTabData initialData) {
            dialog = new JDialog(parent, title, Dialog.ModalityType.APPLICATION_MODAL);
            panel = new OpenAIConfigPanel(initialData);
            initDialog();
        }
        
        public OpenAIConfigDialog(Window parent, String title, List<ConfigTabData> allConfigs) {
            dialog = new JDialog(parent, title, Dialog.ModalityType.APPLICATION_MODAL);
            panel = new OpenAIConfigPanel(allConfigs);
            initDialog();
        }
        
        private void initDialog() {
            JScrollPane scrollPane = new JScrollPane(panel);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

            dialog.getContentPane().add(scrollPane);

            panel.cancelButton.addActionListener(e -> dialog.dispose());
            panel.okButton.addActionListener(e -> {
                onConfirm();
                dialog.dispose();
            });

            StaticUIUtils.setEscapeClosable(dialog);
            dialog.getRootPane().setDefaultButton(panel.okButton);
        }

        protected void onConfirm() {
            // 将在 OpenaiTranslate 类中实现
        }

        public void show() {
            dialog.setPreferredSize(new Dimension(600, 500));
            dialog.pack();
            dialog.setLocationRelativeTo(dialog.getOwner());
            dialog.setVisible(true);
        }

        public OpenAIConfigPanel getPanel() {
            return panel;
        }
    }

    /**
     * 设置里面该插件的配置按钮被按下后弹出的界面、控制逻辑、获取数据，存储数据
     */
    @Override
    public void showConfigurationUI(Window parent) {
        // 加载所有配置数据
        List<ConfigTabData> allConfigs = loadAllConfigs();

        OpenAIConfigDialog dialog = new OpenAIConfigDialog(parent, getName(), allConfigs) {
            @Override
            protected void onConfirm() {
                OpenAIConfigPanel panel = getPanel();
                
                // 获取所有配置数据
                List<ConfigTabData> allConfigs = panel.getAllConfigData();
                if (allConfigs == null || allConfigs.isEmpty()) {
                    LOGGER.warn("无法获取配置数据");
                    return;
                }
                
                // 保存所有配置
                saveAllConfigurations(allConfigs);
            }
        };

        dialog.show();
    }

    private String[] getAllModels() {
        String[] combinedModels = new String[openaiModels.length + claudeModels.length];
        System.arraycopy(openaiModels, 0, combinedModels, 0, openaiModels.length);
        System.arraycopy(claudeModels, 0, combinedModels, openaiModels.length, claudeModels.length);
        return combinedModels;
    }
    

    private String truncateOrPad(String text, int length) {
        if (text == null) {
            text = "";
        }
        if (text.length() > length) {
            return text.substring(0, length - 2) + "..";
        }
        return String.format("%-" + length + "s", text);
    }
}
