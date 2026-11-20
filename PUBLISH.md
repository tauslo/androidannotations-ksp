# Maven Central 发布说明

本仓库是基于开源 AndroidAnnotations（https://github.com/androidannotations/androidannotations，Apache License 2.0）的 KSP 版本 fork：将原 kapt 代码生成器移植为 Kotlin Symbol Processing（KSP）处理器，并以 `io.github.tauslo` 坐标重新发布。

## 默认行为

- 构建始终可用：在 `AndroidAnnotations/` 目录下执行 `mvn package`。
- 未配置发布凭据时，`mvn deploy` 只构建、不发布：根 pom 默认 `maven.deploy.skip=true`。

## 启用发布

1. 创建本地配置文件 `~/.m2/central-portal-settings.xml`（Windows 即 `C:\Users\<用户名>\.m2\central-portal-settings.xml`）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <proxies>
    <!-- 如需代理（例如 http://127.0.0.1:7897），在此配置 proxy -->
  </proxies>
  <servers>
    <server>
      <id>central-portal</id>
      <username>Sonatype 用户名</username>
      <password>Portal token（在 central.sonatype.com 生成）</password>
    </server>
  </servers>
</settings>
```

2. 该文件存在时，根 pom 的 `central-publish` profile 自动激活，将 `maven.deploy.skip` 置为 `false`；文件不存在则构建正常进行但不会 publish。
3. **该文件包含凭据，严禁提交到本仓库或分享给他人。**

## 发布命令

```
mvn deploy -s C:\Users\<用户名>\.m2\central-portal-settings.xml -DperformRelease=true
```

在 `AndroidAnnotations/` 目录下执行。需要 GPG 签名（GnuPG 需在 PATH 中；ed25519 公钥见仓库根目录 `aa-pubkey-9DF2.asc`，指纹 `9DF286AC135E117F03389DA7A11E229CA8F505C0`）。

发布成功后到 https://central.sonatype.com/publishing 关闭（close）并 release 对应 staging 仓库。
