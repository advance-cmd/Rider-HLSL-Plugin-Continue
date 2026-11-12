package com.hlsl.validation;

import com.hlsl.completion.HlslIncludeIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Every .urtshader of a real Unity project resolves its includes and compiles without errors. The project is not
 * in the repository: the test reads the one HLSL_UNITY_PROJECT names, writes nothing into it, and does nothing
 * when the variable is not set or DXC is not installed.
 */
public class HlslUnityRealProjectTest extends BasePlatformTestCase {

    public void testEveryUrtShaderResolvesItsIncludesAndCompilesCleanly() {
        String path = System.getenv("HLSL_UNITY_PROJECT");
        if (path == null || path.isBlank() || HlslDxcSettings.autoDetectDxc() == null) return;
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), path);
        VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'));
        assertNotNull("no directory at " + path, root);
        VirtualFile assets = root.findChild("Assets");
        assertNotNull("not a Unity project: " + path, assets);

        List<VirtualFile> shaders = new ArrayList<>();
        VfsUtilCore.iterateChildrenRecursively(assets, file -> true, file -> {
            if (!file.isDirectory() && "urtshader".equalsIgnoreCase(file.getExtension())) shaders.add(file);
            return true;
        });
        assertFalse("no .urtshader under " + assets.getPath(), shaders.isEmpty());

        List<String> problems = new ArrayList<>();
        for (VirtualFile shader : shaders) {
            PsiFile psi = getPsiManager().findFile(shader);
            assertNotNull(psi);
            for (String include : HlslIncludeIndex.parseIncludes(psi.getText())) {
                if (HlslIncludeIndex.resolve(shader, include) == null) problems.add(shader.getName() + ": " + include + " not found");
            }
            HlslDxcAnnotator annotator = new HlslDxcAnnotator();
            HlslDxcAnnotator.ValidationResult result = annotator.doAnnotate(annotator.collectInformation(psi));
            if (result == null) continue;
            for (HlslDxcDiagnostics.Diagnostic diagnostic : result.diagnostics()) {
                if (!"info".equals(diagnostic.severity())) {
                    problems.add(shader.getName() + ":" + diagnostic.line() + " " + diagnostic.severity() + " " + diagnostic.message());
                }
            }
        }
        assertEquals(List.of(), problems);
    }
}
