package com.hlsl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Says so when a file this plugin is meant to open is opened as another file type. Rider's Unity support, for
 * one, registers .urtshader for its own HLSL type, which highlights the file but gives it no completion, and
 * the IDE keeps whichever registration it settled on. The banner offers to hand the extension to this plugin.
 */
public final class HlslFileTypeConflictNotifier implements EditorNotificationProvider, DumbAware {

    /** The extensions plugin.xml registers for {@link HlslFileType}. */
    static final Set<String> EXTENSIONS = Set.of("hlsl", "hlsli", "urtshader");
    private static final String IGNORED = "hlsl.fileTypeConflict.ignored.";

    @Override
    public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
            @NotNull Project project, @NotNull VirtualFile file) {
        String extension = conflictingExtension(file);
        if (extension == null) return null;
        String current = file.getFileType().getName();
        return editor -> {
            EditorNotificationPanel panel = new EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning);
            panel.setText("*." + extension + " files are opened as \"" + current + "\", so HLSL completion, "
                    + "navigation and DXC checks are off for them.");
            panel.createActionLabel("Open *." + extension + " with HLSL File", () -> {
                useHlslFileFor(extension);
                EditorNotifications.getInstance(project).updateAllNotifications();
            });
            panel.createActionLabel("File Types settings",
                    () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, "File Types"));
            panel.createActionLabel("Don't show again", () -> {
                PropertiesComponent.getInstance().setValue(IGNORED + extension, true);
                EditorNotifications.getInstance(project).updateAllNotifications();
            });
            return panel;
        };
    }

    /**
     * The extension of {@code file} when it is one this plugin registers but another file type opens it, and the
     * user has not asked to leave it so; {@code null} otherwise.
     */
    static @Nullable String conflictingExtension(@NotNull VirtualFile file) {
        String extension = file.getExtension();
        if (extension == null) return null;
        extension = extension.toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension) || file.getFileType() instanceof HlslFileType) return null;
        if (PropertiesComponent.getInstance().getBoolean(IGNORED + extension)) return null;
        return extension;
    }

    /**
     * Moves the extension from the file type that has it to {@link HlslFileType}, as removing it from that type
     * and adding it to HLSL File under Settings → Editor → File Types does.
     */
    static void useHlslFileFor(@NotNull String extension) {
        ApplicationManager.getApplication().runWriteAction(() -> {
            FileTypeManager manager = FileTypeManager.getInstance();
            ExtensionFileNameMatcher matcher = new ExtensionFileNameMatcher(extension);
            FileType current = manager.getFileTypeByExtension(extension);
            if (!(current instanceof HlslFileType) && !(current instanceof UnknownFileType)) {
                manager.removeAssociation(current, matcher);
            }
            manager.associate(HlslFileType.INSTANCE, matcher);
        });
    }
}
