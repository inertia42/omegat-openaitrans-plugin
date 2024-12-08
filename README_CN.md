# OmegaT OpenAI 翻译插件
 简体中文 | [English](https://github.com/inertia42/omegat-openaitrans-plugin/blob/master/README.md)

调用 OpenAI 及 Claude API 进行翻译，有如下功能
* 支持多种模型。
* 支持修改 API URL。
* 支持自定义 prompt。
* 支持修改`temperature`参数。
* 支持传入术语表。

## 使用方法
> 使用 OpenAI 或 Claude API 需要拥有相应的 `API KEY`，本项目不提供 `API KEY`。
1. 下载 releases 里的 jar 文件，将其放进 OmegaT 插件目录。这个目录默认应该在 OmegaT 安装目录下的 plugins 文件夹。
2. （必须）打开 OmegaT，选项——首选项——机器翻译，选中 Openai Translator，点击配置，然后填入 apikey
3. （必须）在 `URL` 处填入对应的 URL。
4. （可选）在 `Prompt` 框内填入自定义的 prompt，如无需要可以不填。如果选择自定义，则不会自动识别语言，需要自行在 prompt 中指定目标语言，若留空则会使用默认 prompt，并自动设置目标语言。
5. （可选）修改 `temperature`，默认设置为 0。
6. （可选）修改 `API format`，有些服务提供商会使用 OpenAI 的 API 格式为 Claude 模型提供服务，如果不理解这个选项的含义，请不要修改默认设置，此时会自动根据选择的模型决定 API 格式，默认设置为 default。
7. 选择需要调用的模型。
8. （可选）启用术语表功能，启用后会将术语表窗口中显示的与该段待翻译文本匹配的术语表一并发送给 API。
9. （可选）启用缓存功能，启用后会优先搜索之前翻译结果的缓存。缓存在关闭项目后清空。
10. 最后勾选启用，点确定并关闭首选项窗口。

## 目前支持的模型
目前支持以下模型：

| 模型名称              | 模型提供商  |
|-------------------|--------|
| gpt-4o            | OpenAI |
| gpt-4o-2024-11-20 | OpenAI |
| gpt-4o-2024-08-06 | OpenAI |
| gpt-4o-2024-05-13 | OpenAI |
| gpt-4o-mini       | OpenAI |
| gpt-4             | OpenAI |
| gpt-4-turbo       | OpenAI |
| gpt-3.5-turbo     | OpenAI |
| gpt-3.5           | OpenAI |
| claude-3-opus-20240229     | Claude   |
| claude-3-5-sonnet-20240620 | Claude   |
| claude-3-5-sonnet-20241022 | Claude   |
| claude-3-sonnet-20240229   | Claude   |
| claude-3-5-haiku-20241022  | Claude   |
| claude-3-haiku-20240307    | Claude   |
|  deepseek-chat             | DeepSeek |

其中部分模型可能未经测试，如果问题请通过 issue 反馈。如有需要添加的模型，也请通过 issue 反馈。

## 关于 prompt 的说明
### 翻译部分 prompt
默认的 prompt 为 `Please translate the following text from {source language} to {target language}`，为了不影响自定义 prompt 的灵活性，并未使用和硬编码一些特殊的 prompt 技巧，如有需要，可以自行设置 prompt。例如若碰到返回与翻译内容无关的结果，可以自行添加翻译相关的 prompt，并在末尾强调不要返回与翻译结果无关的内容。

### 术语表相关的 prompt
术语表部分直接附在翻译 prompt 后，格式为 `You can refer to the following glossary when translating: {glossary}`。若该段对应术语表为空，则不会附加相关 prompt。

## 许可证
本项目采用GPL 3.0许可证。

感谢[omegat-niutrans-plugin项目](https://github.com/xflcx1991/omegat-niutrans-plugin)和[azure-translate-plugin项目](https://github.com/omegat-org/azure-translate-plugin)，本项目部分代码 fork 自这两个项目。