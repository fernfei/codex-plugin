# Codex

一个简单的 IntelliJ IDEA 插件：在编辑器右键菜单中复制项目内文件路径，选中时自动附加行号。

## 使用方法

- 选中代码后使用 `Ctrl+Alt+K`（Win/Linux）或 `Cmd+Alt+K`（Mac）
- 复制 `@项目名/路径`，选中时追加 `#L` 行号范围

## 开发

- 需要 JDK 17
- 运行：`./gradlew runIde`
- 构建：`./gradlew buildPlugin`
