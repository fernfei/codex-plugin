package com.codex;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * 将项目内文件路径复制到剪贴板，选中时附带行号
 * 支持跨平台快捷键：Mac(Cmd+Option+K) 和 Windows/Linux(Ctrl+Alt+K)
 * 实现 DumbAware 接口允许在索引构建期间使用此功能
 */
public class SendSelectionToTerminalAction extends AnAction implements DumbAware {

    private static final Logger LOG = Logger.getInstance(SendSelectionToTerminalAction.class);
    private static final String ACTION_TEXT = "Copy Project Path / Line Numbers";
    private static final String ACTION_DESCRIPTION = "Copy project-relative file path; add line numbers when selected";

    /**
     * 构造函数 - 设置本地化的Action文本和描述
     */
    public SendSelectionToTerminalAction() {
        super(
            ACTION_TEXT,
            ACTION_DESCRIPTION,
            null
        );
    }

    /**
     * 执行Action的主要逻辑
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        try {
            // 使用 ReadAction.nonBlocking() 在后台线程中安全地获取文件信息
            ReadAction
                .nonBlocking(() -> {
                    // 在后台线程中获取选中代码和文件信息
                    return getSelectionInfo(e);
                })
                .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState(), selectionInfo -> {
                    if (selectionInfo == null) {
                        return; // 异常情况已记录日志
                    }

                    // 复制到剪贴板（在 UI 线程执行）
                    copyToClipboard(selectionInfo);
                    LOG.info("已复制到剪贴板: " + selectionInfo);
                })
                .submit(AppExecutorUtil.getAppExecutorService());

        } catch (Exception ex) {
            showError("复制失败: " + ex.getMessage());
            LOG.error("Error: " + ex.getMessage(), ex);
        }
    }

    /**
     * 更新Action的可用性状态
     * 只有在有编辑器、有文件打开时才启用
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
            if (selectedFiles.length > 0) {
                file = selectedFiles[0];
            }
        }

        // 只要有文件就启用，是否选中由执行逻辑决定
        e.getPresentation().setEnabledAndVisible(file != null);
    }

    /**
     * 获取要复制的文件信息并格式化为指定格式
     */
    private @Nullable String getSelectionInfo(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        if (project == null || editor == null) {
            showError("无法获取编辑器信息");
            return null;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();

        // 获取当前文件
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile == null) {
            VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
            if (selectedFiles.length > 0) {
                virtualFile = selectedFiles[0];
            }
        }
        if (virtualFile == null) {
            showError("无法获取当前文件");
            return null;
        }

        // 获取文件路径
        String filePath = getProjectRelativePath(project, virtualFile);
        if (filePath == null) {
            showError("无法确定文件路径");
            return null;
        }

        if (selectedText == null || selectedText.trim().isEmpty()) {
            return "@" + filePath;
        }

        // 获取选中范围的行号
        int startOffset = selectionModel.getSelectionStart();
        int endOffset = selectionModel.getSelectionEnd();

        int startLine = editor.getDocument().getLineNumber(startOffset) + 1; // +1 因为行号从1开始
        int endLine = editor.getDocument().getLineNumber(endOffset) + 1;

        // 格式化输出：@path#Lstart-Lend
        // 如果是单行，只显示一个行号
        String formattedPath;
        if (startLine == endLine) {
            formattedPath = "@" + filePath + "#L" + startLine;
        } else {
            formattedPath = "@" + filePath + "#L" + startLine + "-" + endLine;
        }

        return formattedPath;
    }

    /**
     * 获取项目内文件路径（以项目名为前缀）
     */
    private @Nullable String getProjectRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
        try {
            VirtualFile baseDir = project.getBaseDir();
            String relativePath = baseDir != null ? VfsUtilCore.getRelativePath(file, baseDir, '/') : null;
            if (relativePath == null || relativePath.isEmpty()) {
                relativePath = file.getName();
            }
            String projectName = project.getName();
            return projectName + "/" + relativePath;
        } catch (Exception ex) {
            LOG.error("获取文件路径异常: " + ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * 复制选中信息到剪贴板
     */
    private void copyToClipboard(@NotNull String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
        } catch (Exception ex) {
            showError("复制到剪贴板失败: " + ex.getMessage());
            LOG.error("Error occurred", ex);
        }
    }

    /**
     * 显示错误信息
     */
    private void showError(@NotNull String message) {
        LOG.warn(message);
    }
}
