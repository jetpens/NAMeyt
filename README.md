# mirai-login-solver-sakura

--------------------------

这是另外的一整套验证处理工具，主要是为了优化和方便处理各种验证码

> 本项目前身: [TxCaptchaHelper](https://github.com/mzdluo123/TxCaptchaHelper)



## 系统要求

| mirai-login-solver-sakura | mirai-core | Android             |
|:--------------------------|:-----------|:--------------------|
| 0.0.1 - dev               | 2.13.0+    | Android 9+ (API 28) |

--------------

### 已完成 / 计划

- [X] GUI (Graphics User Interface)
- [ ] CLI (Command Line Interface)

#### GUI

- [X] SMS
- [X] 设备锁验证
- [X] 滑块验证
- [X] Pic 4code 验证

###### 预览

<details>
<summary>点击展开</summary>

![](.images/main-snapshot/img.webp)

![](.images/main-snapshot/img_1.webp)

![](.images/main-snapshot/img_2.webp)

![](.images/main-snapshot/img_3.webp)

</details>

### 下载

Way 1:
从 [Releases](https://github.com/KasukuSakura/mirai-login-solver-sakura/releases)
下载

- `mirai-login-solver-sakura-XXX.mirai2.jar` -> mirai-console 插件
- `apk-release.apk` 安卓应用程序

Way 2: 从最新构建下载

<details>
<summary>点击展开</summary>

![](.images/download-from-ci/img.webp)

![](.images/download-from-ci/img_1.webp)

![](.images/download-from-ci/img_2.webp)

下载的压缩包里有全部的最新构建成果

</details>


## 配置

mirai-login-solver-sakura 配置通过 jvm 参数指定

| property                  | default | accepts    | desc                             |
|:--------------------------|:--------|:-----------|:---------------------------------|
| mlss.no-tunnel            | false   | true/false | 是否禁用 tunnel                      |
| mlss.port                 | 0       | 0-65536    | mlss 后端端口号                       |
| mlss.tunnel.limited       | true    | true/false | 是否启动安全策略限制 tunnel 链接             |

## Q & A

> Q: 扫码后崩溃 <br/>
> A: 更新 `Android WebView`, 具体方法请百度 `Android 更新 WebView`

> Q: 怎么在服务器上验证 <br/>
> A: 使用命令行模式 (添加 jvm 参数 `-Dmirai.no-desktop=t