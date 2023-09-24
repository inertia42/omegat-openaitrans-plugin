# OmegaT 小牛插件
一个能让 OmegaT 从小牛获取机器翻译的插件。内置了官方测试 Key，不用配置也可用，但不保证可靠性（经常会触发使用上限），所以还是建议申请一个彩云小译的 Token。

已在 JDK11,OmegaT 6.0.0,gradle 8.3 环境下测试通过。

参考了以下项目的代码，把我从黑箱带入 OmegaT 插件开发，感谢各位作者。
> https://github.com/yoyicue/omegat-tencent-plugin
> https://github.com/omegat-org/omegat/blob/854b6b5a66a0306e5c27e74c0b5d656ed80b2bd4/src/org/omegat/core/machinetranslators/YandexTranslate.java
> GoogleTranslateWithoutApiKey

特别感谢 [@Ninty](https://github.com/c19354837 "Ninty") 介绍的 Gson 和 OkHttp 用来处理数据。不过后来发现只需要一个很小子集的功能，也为了减小生成 jar 包的大小，工具包改为了 Hutool。

鉴于 OmegaT 插件教程太少和零散，我将在 readme 的[最下面](#introduction)讲以下入门环境搭建和参考资料。

## 使用方法
1. 申请一个小牛翻译apikey

    登录进[小牛翻译文档](https://niutrans.com/documents/contents/question/1)，申请开通小牛翻译 apikey（每日赠送 20 万字），也许需要等待一到两个工作日。
2. 克隆本项目，进入项目根目录，然后运行`./gradlew build`。    
3. 下载 releases 里的 zip，解压得到 jar 文件和别的插件一样放进 OmegaT 插件目录。这个目录默认应该在 OmegaT 安装目录下的`plugins`文件夹
3. 打开 OmegaT，选项——首选项——机器翻译，选中`Xiaoniu Translate`，点击配置，然后填入第一步得到 apikey，最后勾选启用，点确定关闭首选项窗口。
4. 没有第 4 步，做完上面 3 步，打开你的翻译项目，你应该能在机器翻译面板看到来自彩云小译的结果了。Enjoy it!

## 许可证
本项目采用[木兰宽松许可证, 第2版](https://license.coscl.org.cn/MulanPSL2/)
本项目采用了以下第三方组件：
* [Hutool](https://hutool.cn/) 5.4.0（木兰宽松许可证, 第2版）

---

<h1 id="introduction">OmegaT 插件开发起步</h1>
官方给的资料有两处，分别是
* https://github.com/omegat-org/omegat

    在`docs_devel`目录及其子目录下有两篇介绍入门的文章，分别是《OmegaT developer's guide》和《Creating an OmegaT Plugin》，格式是 odt 需要 LibreOffice 打开（当然也可以用 OpenOffice）。示例代码是 Google 翻译插件，可以看出和本项目的彩云小译的插件代码结构是相似的。
* https://github.com/omegat-org/plugin-skeleton

    这个官方示例是用来提供一个快速搭建开发环境的。用了 Gradle 而不是上一处示例项目的 Ant,目录结构也有变化。现在应该用这个项目，上一处仅仅用来当资料参考。参考本项目的 readme 里的步骤，应该很快可以把一个合法的插件项目创建出来。

除了官方代码，应该多看看别的项目写的代码，比如上文提到过的
> https://github.com/yoyicue/omegat-tencent-plugin
> https://github.com/omegat-org/omegat/blob/854b6b5a66a0306e5c27e74c0b5d656ed80b2bd4/src/org/omegat/core/machinetranslators/YandexTranslate.java
> GoogleTranslateWithoutApiKey

另外本项目代码也做了注释可以参考。

> 吐槽，升级到Gradle 8.3真是要吐了，一个构建工具升级一次版本就废弃一堆API，Gradle丝毫不考虑兼容性，开发是爽了，用户呢？
