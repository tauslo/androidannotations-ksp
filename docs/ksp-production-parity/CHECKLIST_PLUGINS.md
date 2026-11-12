# Plugins Checklist — Optional AA add-ons

> **默认不做** RoboGuice / Otto。  
> ORMLite / REST-Spring **仅当**目标线上工程依赖对应 `androidannotations-*` artifact 时开启。  
> 打勾后同步更新 [`EXECUTION_LOG.md`](EXECUTION_LOG.md)。

插件在 APT 中经 `ServiceLoader` 加载 `AndroidAnnotationsPlugin`。KSP 侧建议：

- 独立 Maven 模块（如 `androidannotations-rest-spring-ksp`），或
- 同 jar 内可开关的 handler 包 + 文档说明

先在业务仓库确认：

```text
org.androidannotations:androidannotations-ormlite
org.androidannotations:androidannotations-rest-spring
org.androidannotations:androidannotations-otto
org.androidannotations:androidannotations-roboguice
```

---

## RoboGuice（默认跳过）

- [ ] N/A — 库已停维护；若极老工程需要再开
- [ ] `@RoboGuice` → `RoboGuiceHandler` 行为移植（仅当强制要求）

## Otto（默认跳过）

- [ ] N/A — 已归档
- [ ] `@Subscribe` / `@Produce`（Square Otto）handler（仅当强制要求）

---

## ORMLite（按需）

- [ ] 确认业务仍用 ORMLite（而非 Room）
- [ ] KSP 模块或包：`OrmLiteDaoHandler`（`@OrmLiteDao`）
- [ ] 依赖 `ormlite-api` / helper 注入点对齐 APT
- [ ] Demo + 卸 kapt 验证
- [ ] 文档：迁移注意点

**粗估：** 3–5 人日

---

## REST-Spring（按需，大期）

- [ ] 确认业务仍用 `@Rest` 而非 Retrofit
- [ ] **生成** `@Rest` → `RestHandler` / client 实现类
- [ ] HTTP verbs：`@Get` `@Post` `@Put` `@Patch` `@Delete` `@Head` `@Options`
- [ ] Params：`@Body` `@Field` `@Part` `@Path` `@Header` `@Headers`
- [ ] `@RestService` 注入
- [ ] 错误处理 / converter 钩子对齐 APT（抽查线上接口）
- [ ] 独立验收工程（可基于 `androidannotations-rest-spring` tests）
- [ ] 文档与卸 kapt 步骤

**粗估：** 15–25 人日

---

## Acceptance（任一插件）

- [ ] 线上等价调用点零改动或仅文档化差异
- [ ] `EXECUTION_LOG` 记录开启原因与覆盖边界
- [ ] Push `ksp/main`（若需要）
