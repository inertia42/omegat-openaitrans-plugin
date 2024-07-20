# OmegaT OpenAI Translation Plugin
English|[简体中文](https://github.com/inertia42/omegat-openaitrans-plugin/blob/master/README_CN.md)

Utilize OpenAI and Claude APIs for translation with the following features:
* Supports multiple models
* Supports modifying the API URL
* Supports custom prompts
* Supports modifying the `temperature` parameter

## Usage Instructions
> Using OpenAI or Claude APIs requires a corresponding `API KEY`. This project does not provide an `API KEY`.
1. Download the jar file from the releases and place it in the OmegaT plugins directory. This directory should be in the plugins folder within the OmegaT installation directory by default.
2. (Required) Open OmegaT, go to Options -> Preferences -> Machine Translation, select Openai Translator, click Configure, and enter the API key.
3. (Required) Fill in the corresponding URL in the `URL` field.
4. (Optional) Enter a custom prompt in the `Prompt` box if needed. If you choose to customize, automatic language detection will be disabled, and you will need to specify the target language in the prompt. If left blank, the target language will be set automatically.
5. (Optional) Modify the `temperature`, which is set to 0 by default.
6. Select the model you wish to use.
7. Finally, check Enable, click OK, and close the Preferences window.

## Currently Supported Models
The following models are currently supported:

| Model Name        | Provider |
|-------------------|----------|
| gpt-4o            | OpenAI   |
| gpt-4o-mini       | OpenAI   |
| gpt-4             | OpenAI   |
| gpt-4-turbo       | OpenAI   |
| gpt-3.5-turbo     | OpenAI   |
| gpt-3.5           | OpenAI   |
| claude-3-opus     | Claude   |
| claude-3-5-sonnet | Claude   |
| claude-3-sonnet   | Claude   |
| claude-3-haiku    | Claude   |

Some models may not have been tested; if you encounter any issues, please provide feedback through the issues section. If you need additional models, please also provide feedback through the issues section.

## License
This project is licensed under the GPL.

Thanks to the [omegat-niutrans-plugin project](https://github.com/xflcx1991/omegat-niutrans-plugin), part of the code for this project was forked from that project.