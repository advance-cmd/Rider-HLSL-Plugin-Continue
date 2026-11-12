package com.hlsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Rider's Unity support registers .urtshader for its own HLSL type. The banner says so and hands the extension
 * to this plugin, as moving it under Settings → Editor → File Types does.
 */
public class HlslFileTypeConflictNotifierTest extends BasePlatformTestCase {

    public void testAUrtShaderOpenedAsHlslFileNeedsNoBanner() {
        VirtualFile file = myFixture.addFileToProject("Assets/A.urtshader", "").getVirtualFile();
        assertNull(HlslFileTypeConflictNotifier.conflictingExtension(file));
        assertNotNull("a provider for the editor is registered", new HlslFileTypeConflictNotifier());
    }

    public void testAnotherFileTypeIsReportedAndCanBeReplaced() {
        ExtensionFileNameMatcher matcher = new ExtensionFileNameMatcher("urtshader");
        try {
            // What Rider's Unity support does to the extension.
            ApplicationManager.getApplication().runWriteAction(() -> {
                FileTypeManager.getInstance().removeAssociation(HlslFileType.INSTANCE, matcher);
                FileTypeManager.getInstance().associate(PlainTextFileType.INSTANCE, matcher);
            });
            VirtualFile file = myFixture.addFileToProject("Assets/B.urtshader", "").getVirtualFile();
            assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
            assertEquals("urtshader", HlslFileTypeConflictNotifier.conflictingExtension(file));

            HlslFileTypeConflictNotifier.useHlslFileFor("urtshader");
            assertEquals(HlslFileType.INSTANCE, FileTypeManager.getInstance().getFileTypeByExtension("urtshader"));
            assertNull(HlslFileTypeConflictNotifier.conflictingExtension(file));
        } finally {
            HlslFileTypeConflictNotifier.useHlslFileFor("urtshader");
        }
    }

    public void testFilesOfOtherExtensionsAreLeftAlone() {
        VirtualFile file = myFixture.addFileToProject("notes.txt", "").getVirtualFile();
        assertNull(HlslFileTypeConflictNotifier.conflictingExtension(file));
    }
}
