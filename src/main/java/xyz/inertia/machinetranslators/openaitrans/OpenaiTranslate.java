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
import org.omegat.gui.exttrans.MTConfigDialog;
import org.omegat.util.CredentialsManager;
import org.omegat.util.Language;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.StringUtil;
import org.omegat.util.gui.StaticUIUtils;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import java.awt.Color;

import cn.hutool.core.map.MapUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openide.awt.Mnemonics;

public class OpenaiTranslate extends BaseCachedTranslate implements IMachineTranslation {

    protected static final String PROPERTY_API_KEY = "openai.api.Key";

    protected static final String PROPERTY_API_URL = "openai.api.url";
    protected static final String PROPERTY_API_MODEL = "openai.api.model";
    protected static final String PROPERTY_API_PROVIDER = "openai.api.format";
    protected static final String PROPERTY_API_PROMPT = "openai.api.prompt";
    protected static final String PROPERTY_API_TEMPERATURE = "openai.api.temperature";
    protected static final String PROPERTY_API_CACHE = "openai.api.enable.cache";
    protected static final String PROPERTY_API_GLOSSARY = "openai.api.enable.glossary";
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("OpenaiTranslateBundle");

    protected static final String[] openaiModels = {
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
        "claude-3-opus-20240229",
        "claude-3-5-sonnet-20240620",
        "claude-3-5-sonnet-20241022",
        "claude-3-sonnet-20240229",
        "claude-3-5-haiku-20241022",
        "claude-3-haiku-20240307"
    };
    protected static final String[] Providers = {"default", "OpenAI", "Claude", "DeepSeek"};

    protected static final Logger LOGGER = LoggerFactory.getLogger(OpenaiTranslate.class);

    static String getString(String key) {
        return BUNDLE.getString(key);
    }

    /**
     * 在软件启动时会自动调用该函数来注册插件.
     */
    @SuppressWarnings("unused")
    public static void loadPlugins() {
        LOGGER.debug("加载 OpenaiTranslate Plugin");
        Core.registerMachineTranslationClass(OpenaiTranslate.class);
    }

    /**
     * 卸载插件，可以留空，示例代码就留空.
     */
    @SuppressWarnings("unused")
    public static void unloadPlugins() {}

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
        String url = Preferences.getPreference(PROPERTY_API_URL);
        String model = Preferences.getPreference(PROPERTY_API_MODEL);
        String temperature_str = Preferences.getPreference(PROPERTY_API_TEMPERATURE);
        String provider = Preferences.getPreference(PROPERTY_API_PROVIDER);
        String prompt = Preferences.getPreference(PROPERTY_API_PROMPT);
        String enableCache = Preferences.getPreference(PROPERTY_API_CACHE);
        String enableGlossary = Preferences.getPreference(PROPERTY_API_GLOSSARY);

        BigDecimal fullAccuracy = new BigDecimal(Double.parseDouble(temperature_str));
        fullAccuracy = fullAccuracy.setScale(3, RoundingMode.DOWN); // 截断到三位小数
        double temperature = fullAccuracy.doubleValue();

        if (apiKey.isEmpty()) {
            return getString("MT_ENGINE_OPENAI_API_KEY_NOTFOUND");
        }

        if (url.isEmpty()) {
            return getString("MT_ENGINE_OPENAI_API_URL_NOTFOUND");
        }


        List<String> openaiModelsList = Arrays.asList(openaiModels);
        List<String> claudeModelsList = Arrays.asList(claudeModels);

        if (openaiModelsList.contains(model)) {
            provider = ("default".equals(provider) || "DeepSeek".equals(provider)) ? "OpenAI" : provider;
            // url += "/v1/chat/completions";
        }
        // 检查 model 是在 claudeModels 中
        else if (claudeModelsList.contains(model)) {
            provider = ("default".equals(provider)) ? "Claude" : provider;
            // url += "/v1/messages";
        }
        // 如果 model 不在任何一个列表中
        else {
            return "An unsupported model was used!";
        }

        if ("OpenAI".equals(provider)) {
            url += "/v1/chat/completions";
        } else if ("Claude".equals(provider)) {
            url += "/v1/messages";
        }

        //     -----------------转换语言代码-----------------
        String lvSourceLang = sLang.getLanguage();
        String lvTargetLang = tLang.getLanguage();

        String defaultPrompt =
                String.format("Please translate the following text from %s to %s.", lvSourceLang, lvTargetLang);
        if (prompt.isEmpty()) {
            prompt = defaultPrompt;
        }

        
        // LOGGER.info("prompt = {}", prompt);

        // 判断翻译缓存里有没有
        // U+2026 HORIZONTAL ELLIPSIS 水平省略号 …
        String lvShortText = text.length() > 5000 ? text.substring(0, 4997) + "\u2026" : text;
        String prev = getCachedTranslation(sLang, tLang, lvShortText);
        if (prev != null && "true".equals(enableCache)) {
            return prev;
        }

        // 添加 glossary 信息
        StringBuilder glossaryTable = new StringBuilder();
        if ("true".equals(enableGlossary)) {
            List<org.omegat.gui.glossary.GlossaryEntry> entries = Core.getGlossaryManager().searchSourceMatches(Core.getEditor().getCurrentEntry());
            if (!entries.isEmpty()) {
                glossaryTable.append("\nYou can refer to the following glossary when translating:\n");
                
                for (org.omegat.gui.glossary.GlossaryEntry entry : entries) {
                    glossaryTable.append(String.format("src: %s, loc: %s, comment: %s\n",
                        entry.getSrcText(),
                        entry.getLocText(),
                        entry.getCommentText()
                    ));
                }
                prompt = prompt + glossaryTable.toString();
            }
        }
        String result = "";
        
        try {
            HttpResponse response;
            if ("OpenAI".equals(provider)) {
                Map<String, Object> bodyMap = MapUtil.<String, Object>builder()
                        .put("model", model)
                        .put(
                                "messages",
                                Arrays.asList(
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
                LOGGER.info("bodyMap = {}", bodyMap);
                String bodyStr = JSONUtil.toJsonStr(bodyMap);
                LOGGER.debug("bodyStr = {}", bodyStr);

                HttpRequest post = HttpUtil.createPost(url)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .body(bodyStr);

                try {
                    response = post.execute();
                } catch (Exception e) {
                    LOGGER.error("HTTP请求失败: {}", e.getMessage());
                    // 根据异常类型返回不同的错误信息
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
            } else if ("Claude".equals(provider)) {
                Map<String, Object> bodyMap = MapUtil.<String, Object>builder()
                        .put("model", model)
                        .put("max_tokens", 4096)
                        .put("system", prompt)
                        .put(
                                "messages",
                                Arrays.asList(
                                        MapUtil.<String, String>builder()
                                                .put("role", "user")
                                                .put("content", lvShortText)
                                                .build() // user input
                                        ))
                        .put("temperature", temperature) // 可根据需要调整
                        .build();
                LOGGER.info("bodyMap = {}", bodyMap);
                String bodyStr = JSONUtil.toJsonStr(bodyMap);
                LOGGER.debug("bodyStr = {}", bodyStr);

                HttpRequest post = HttpUtil.createPost(url)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .header("Content-Type", "application/json")
                        .body(bodyStr);

                try {
                    response = post.execute();
                } catch (Exception e) {
                    LOGGER.error("HTTP请求失败: {}", e.getMessage());
                    // 根据异常类型返回不同的错误信息
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
            
        } catch (Exception e) {
            LOGGER.error("翻译过程发生错误: {}", e.getMessage());
            return String.format(getString("MT_ENGINE_OPENAI_GENERAL_ERROR"), e.getMessage());
        }
    }

    /**
     * 是否在设置界面允许该插件的配置按钮可用，如果 false，配置按钮是灰色不可点的，也就没法配置 Token 了
     */
    @Override
    public boolean isConfigurable() {
        return true;
    }

    @SuppressWarnings("serial")
    public class OpenAIConfigPanel extends JPanel {
        private javax.swing.JPanel descriptionPanel;
        private javax.swing.JTextArea descriptionTextArea;
        private javax.swing.JPanel itemsPanel;
        private javax.swing.JPanel credentialsPanel;
        public javax.swing.JTextArea apiKeyField;
        public javax.swing.JTextArea promptField;
        public javax.swing.JTextField urlField;
        public javax.swing.JTextField temperatureField;
        public javax.swing.JComboBox<String> modelComboBox;
        public javax.swing.JComboBox<String> apiComboBox;
        public javax.swing.JCheckBox temporaryCheckBox;
        public javax.swing.JCheckBox cacheCheckBox;
        public javax.swing.JCheckBox glossaryCheckBox;
        public javax.swing.JButton okButton;
        public javax.swing.JButton cancelButton;

        public OpenAIConfigPanel() {
            initComponents();
        }

        private void initComponents() {
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            setLayout(new BorderLayout());

            // 初始化组件
            descriptionPanel = new JPanel(new BorderLayout());
            descriptionTextArea = new JTextArea();
            itemsPanel = new JPanel();
            credentialsPanel = new JPanel(new GridBagLayout());
            
            // 设置文本区域属性
            descriptionTextArea.setEditable(false);
            descriptionTextArea.setLineWrap(true);
            descriptionTextArea.setWrapStyleWord(true);
            descriptionTextArea.setOpaque(false);
            
            // 创建输入组件
            apiKeyField = createMultiLineTextArea();
            urlField = new JTextField();
            promptField = createMultiLineTextArea();
            temperatureField = new JTextField();
            modelComboBox = new JComboBox<>();
            apiComboBox = new JComboBox<>();
            temporaryCheckBox = new JCheckBox();
            cacheCheckBox = new JCheckBox(getString("MT_ENGINE_CACHE_BUTTON"), true);
            glossaryCheckBox = new JCheckBox(getString("MT_ENGINE_GLOSSARY_BUTTON"), true);

            // 使用 GridBagLayout 添加组件
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_KEY_LABEL", apiKeyField, gbc, 0);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_URL_LABEL", urlField, gbc, 1);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_PROMPT_LABEL", promptField, gbc, 2);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_TEMPERATURE_LABEL", temperatureField, gbc, 3);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_MODEL_NAME_LABEL", modelComboBox, gbc, 4);
            addLabelAndField(credentialsPanel, "MT_ENGINE_OPENAI_API_FORMAT_LABEL", apiComboBox, gbc, 5);

            // 添加复选框
            gbc.gridx = 1;
            gbc.gridy = 6;
            credentialsPanel.add(glossaryCheckBox, gbc);
            gbc.gridy = 7;
            credentialsPanel.add(temporaryCheckBox, gbc);
            gbc.gridy = 8;
            credentialsPanel.add(cacheCheckBox, gbc);

            // 添加按钮面板
            JPanel buttonPanel = new JPanel();
            Mnemonics.setLocalizedText(temporaryCheckBox, OStrings.getString("PREFS_CREDENTIAL_TEMPORARY_LABEL"));
            buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
            okButton = new JButton();
            Mnemonics.setLocalizedText(okButton, OStrings.getString("BUTTON_OK"));
            cancelButton = new JButton();
            Mnemonics.setLocalizedText(cancelButton, OStrings.getString("BUTTON_CANCEL"));
            buttonPanel.add(Box.createHorizontalGlue());
            buttonPanel.add(okButton);
            buttonPanel.add(Box.createRigidArea(new Dimension(5, 0)));
            buttonPanel.add(cancelButton);

            // 组装面板
            add(descriptionPanel, BorderLayout.NORTH);
            add(credentialsPanel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);
        }

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
                            int estimatedRows = Math.max(minRows, 
                                    Math.min(maxRows, 
                                        (int)Math.ceil((double)textWidth / availableWidth)));
                            
                            // 计算新的首选高度
                            int newHeight = lineHeight * estimatedRows + 2 * padding;
                            
                            // 设置新的首选大小
                            Dimension newSize = new Dimension(
                                    Math.min(maxWidth, Math.max(minWidth, viewRect.width)),
                                    newHeight);
                            
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
                public void insertUpdate(DocumentEvent e) { updateSize(); }
                @Override 
                public void removeUpdate(DocumentEvent e) { updateSize(); }
                @Override
                public void changedUpdate(DocumentEvent e) { updateSize(); }
            });

            // 设置边框和外观
            textArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(padding, padding, padding, padding)
            ));

            return textArea;
        }

        private void addLabelAndField(JPanel panel, String labelKey, JComponent field, GridBagConstraints gbc, int row) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.0;
            panel.add(new JLabel(getString(labelKey)), gbc);
            
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
    }

    public class OpenAIConfigDialog {
        private final JDialog dialog;
        private final OpenAIConfigPanel panel;

        public OpenAIConfigDialog(Window parent, String title) {
            dialog = new JDialog(parent, title, Dialog.ModalityType.APPLICATION_MODAL);
            panel = new OpenAIConfigPanel();

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
        OpenAIConfigDialog dialog = new OpenAIConfigDialog(parent, getName()) {
            @Override
            protected void onConfirm() {
                OpenAIConfigPanel panel = getPanel();
                boolean temporary = panel.temporaryCheckBox.isSelected();
                
                // 保存凭证信息
                setCredential(PROPERTY_API_KEY, panel.apiKeyField.getText().trim(), temporary);
                Preferences.setPreference(PROPERTY_API_URL, panel.urlField.getText().trim());
                
                // 保存其他设置到 Preferences
                Preferences.setPreference(PROPERTY_API_PROMPT, panel.promptField.getText().trim());
                Preferences.setPreference(PROPERTY_API_TEMPERATURE, panel.temperatureField.getText().trim());
                Preferences.setPreference(PROPERTY_API_MODEL, (String) panel.modelComboBox.getSelectedItem());
                Preferences.setPreference(PROPERTY_API_PROVIDER, (String) panel.apiComboBox.getSelectedItem());
                Preferences.setPreference(PROPERTY_API_CACHE, panel.cacheCheckBox.isSelected());
                Preferences.setPreference(PROPERTY_API_GLOSSARY, panel.glossaryCheckBox.isSelected());
            }
        };

        // 设置初始值
        OpenAIConfigPanel panel = dialog.getPanel();
        panel.apiKeyField.setText(getCredential(PROPERTY_API_KEY));
        panel.urlField.setText(Preferences.getPreference(PROPERTY_API_URL));
        panel.promptField.setText(Preferences.getPreference(PROPERTY_API_PROMPT));
        panel.temperatureField.setText(Preferences.getPreference(PROPERTY_API_TEMPERATURE));
        panel.modelComboBox.setModel(new DefaultComboBoxModel<>(getAllModels()));
        panel.modelComboBox.setSelectedItem(Preferences.getPreference(PROPERTY_API_MODEL));
        panel.apiComboBox.setModel(new DefaultComboBoxModel<>(Providers));
        panel.apiComboBox.setSelectedItem(Preferences.getPreference(PROPERTY_API_PROVIDER));
        panel.cacheCheckBox.setSelected(Boolean.parseBoolean(Preferences.getPreference(PROPERTY_API_CACHE)));
        panel.temporaryCheckBox.setSelected(false);
        panel.glossaryCheckBox.setSelected(Boolean.parseBoolean(Preferences.getPreference(PROPERTY_API_GLOSSARY)));

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
