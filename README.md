# Codex

Simple IntelliJ IDEA plugin that copies the project-relative file path from the editor context menu, and appends line numbers when text is selected.

## Usage

- Select code and use `Ctrl+Alt+K` (Win/Linux) or `Cmd+Alt+K` (Mac)
- Copies `@ProjectName/path` and adds `#L` line range when selected

## Development

- JDK 17 required
- Run: `./gradlew runIde`
- Build: `./gradlew buildPlugin`
