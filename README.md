# OmegaT OpenAI Translation Plugin
[简体中文](https://github.com/inertia42/omegat-openaitrans-plugin/blob/master/README_CN.md) | English

A plugin that calls OpenAI and Claude APIs for translation with the following features:
* Support for multiple models
* Customizable API URL
* Customizable prompts
* Adjustable temperature parameter
* Glossary integration

## Usage
> Using OpenAI or Claude API requires an `API KEY`. This project does not provide API keys.
1. Download the jar file from releases and place it in the OmegaT plugins directory. This directory should be in the plugins folder under your OmegaT installation directory by default.
2. (Required) Open OmegaT, go to Options - Preferences - Machine Translation, select Openai Translator, click Configure, and enter your apikey.
3. (Required) Enter the corresponding URL in the `URL` field.
4. (Optional) Enter a custom prompt in the `Prompt` field. If not needed, leave it empty. If you choose to customize, language auto-detection will be disabled, and you'll need to specify the target language in your prompt. If left empty, the default prompt will be used with automatic target language setting.
5. (Optional) Modify the `temperature` parameter, default is 0.
6. (Optional) Modify the `API format`. Some service providers use OpenAI's API format for Claude models. If you don't understand this option, please don't change the default setting. It will automatically determine the API format based on the selected model. Default is set to 'default'.
7. Select the model you want to use.
8. (Optional) Enable the glossary feature. When enabled, matching glossary terms for the current segment will be sent to the API along with the text.
9. (Optional) Enable caching. When enabled, previously translated results will be searched first. Cache is cleared when the project is closed.
10. Finally, check Enable, click OK and close the preferences window.

## Currently Supported Models
The following models are currently supported:

| Model Name          | Provider |
|--------------------|----------|
| gpt-4o             | OpenAI   |
| gpt-4o-2024-11-20  | OpenAI   |
| gpt-4o-2024-08-06  | OpenAI   |
| gpt-4o-2024-05-13  | OpenAI   |
| gpt-4o-mini        | OpenAI   |
| gpt-4              | OpenAI   |
| gpt-4-turbo        | OpenAI   |
| gpt-3.5-turbo      | OpenAI   |
| gpt-3.5            | OpenAI   |
| claude-3-opus-20240229     | Claude   |
| claude-3-5-sonnet-20240620 | Claude   |
| claude-3-5-sonnet-20241022 | Claude   |
| claude-3-sonnet-20240229   | Claude   |
| claude-3-5-haiku-20241022  | Claude   |
| claude-3-haiku-20240307    | Claude   |
| deepseek-chat              | DeepSeek |

Some models may not have been tested. If you encounter any issues, please report them through issues. If you need additional models added, please also request through issues.

## About Prompts
### Translation Prompt
The default prompt is `Please translate the following text from {source language} to {target language}`. To maintain flexibility for custom prompts, special prompt techniques are not hardcoded. If needed, you can set your own prompt. For example, if you encounter responses unrelated to translation, you can add translation-specific prompts and emphasize at the end not to return content unrelated to the translation.

### Glossary-related Prompt
The glossary section is appended directly after the translation prompt in the format: `You can refer to the following glossary when translating: {glossary}`. If there are no matching glossary terms for the current segment, this prompt will not be added.

## License
This project is licensed under GPL 3.0.

Thanks to [omegat-niutrans-plugin project](https://github.com/xflcx1991/omegat-niutrans-plugin) and [azure-translate-plugin project](https://github.com/omegat-org/azure-translate-plugin). Some code in this project is forked from these two projects.