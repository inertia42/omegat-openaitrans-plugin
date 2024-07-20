# OmegaT OpenAI 翻译插件
简体中文|[English](https://github.com/inertia42/omegat-openaitrans-plugin/blob/master/README.md)

调用 OpenAI 及 Claude API 进行翻译，有如下功能
* 支持多个模型
* 支持修改 API URL
* 支持自定义 Prompt
* 支持修改`temperature`参数。

## 使用方法
> 使用 OpenAI 或 Claude API 需要拥有相应的 `API KEY`，本项目不提供 `API KEY`。
1. 下载 releases 里的 jar 文件，将其放进 OmegaT 插件目录。这个目录默认应该在 OmegaT 安装目录下的 plugins 文件夹。
2. （必须）打开 OmegaT，选项——首选项——机器翻译，选中 Openai Translator，点击配置，然后填入 apikey
3. （必须）在 `URL` 处填入对应的 URL。
4. （可选）在 `Prompt` 框内填入自定义的 prompt，如无需要可以不填。如果选择自定义，则不会自动识别语言，需要自行在 prompt 中指定目标语言，若留空则会自动设置目标语言。
5. （可选）修改 `temperature`，默认设置为 0。
6. 选择需要调用的模型。
7. 最后勾选启用，点确定并关闭首选项窗口。

## 目前支持的模型
目前支持以下模型：

| 模型名称              | 模型提供商  |
|-------------------|--------|
| gpt-4o            | OpenAI |
| gpt-4o-mini       | OpenAI |
| gpt-4             | OpenAI |
| gpt-4-turbo       | OpenAI |
| gpt-3.5-turbo     | OpenAI |
| gpt-3.5           | OpenAI |
| claude-3-opus     | Claude |
| claude-3-5-sonnet | Claude |
| claude-3-sonnet   | Claude |
| claude-3-haiku    | Claude |

其中部分模型可能未经测试，如果问题请通过 issue 反馈。如有需要添加的模型，也请通过 issue 反馈。

## 许可证
本项目采用GPL许可证。

感谢[omegat-niutrans-plugin项目](https://github.com/xflcx1991/omegat-niutrans-plugin)，本项目部分代码 fork 自该项目。