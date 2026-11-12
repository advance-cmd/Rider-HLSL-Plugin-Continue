package com.hlsl.completion;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Completion in every .urtshader of a real Unity project that uses Unified Ray Tracing reads the real package:
 * UnifiedRT:: offers its constants, structures and functions, and a Hit its fields and methods. The project is
 * not in the repository: the test reads the one HLSL_UNITY_PROJECT names, writes nothing into it, and does
 * nothing when the variable is not set.
 */
public class HlslUnityRealProjectCompletionTest extends BasePlatformTestCase {

    public void testUnifiedRtNamesAreCompletedInEveryShaderThatIncludesThePackage() {
        String path = System.getenv("HLSL_UNITY_PROJECT");
        if (path == null || path.isBlank()) return;
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), path);
        VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'));
        assertNotNull("no directory at " + path, root);
        List<VirtualFile> shaders = new ArrayList<>();
        VfsUtilCore.iterateChildrenRecursively(root.findChild("Assets"), file -> true, file -> {
            if (!file.isDirectory() && "urtshader".equalsIgnoreCase(file.getExtension())) shaders.add(file);
            return true;
        });

        HlslIncludeIndex index = getProject().getService(HlslIncludeIndex.class);
        int checked = 0;
        for (VirtualFile shader : shaders) {
            PsiFile psi = getPsiManager().findFile(shader);
            assertNotNull(psi);
            String text = psi.getText();
            if (!text.contains("UnifiedRayTracing/")) continue;
            checked++;
            // The end of the shader, where a new function would be written.
            String probe = text + "\nvoid CompletionProbe(UnifiedRT::DispatchInfo info)\n{\n    UnifiedRT::Hit hit;\n    ";
            List<CharSequence> includes = index.collectVisibleSources(shader, probe);

            List<String> members = names(HlslCompletionAnalysis.analyze(probe + "uint f = UnifiedRT::", probe.length() + 20, includes));
            assertTrue(shader.getName() + ": " + members, members.containsAll(List.of(
                    "kRayFlagNone", "kRayFlagAcceptFirstHitAndEndSearch", "Hit", "Ray", "TraceRayClosestHit", "TraceRayAnyHit")));
            String hitProbe = probe + "float d = hit.";
            List<String> fields = names(HlslCompletionAnalysis.analyze(hitProbe, hitProbe.length(), includes));
            assertTrue(shader.getName() + ": " + fields, fields.containsAll(List.of("hitDistance", "instanceID", "IsValid")));
            String callProbe = probe + "UnifiedRT::TraceRayClosestHit(";
            HlslCompletionAnalysis.CallInfo call = HlslCompletionAnalysis.callAt(callProbe, callProbe.length(), includes);
            assertNotNull(shader.getName() + ": no parameter info for TraceRayClosestHit", call);
            assertEquals(5, call.signatures().get(0).parameters().size());
        }
        assertTrue("no .urtshader of " + path + " includes the Unified Ray Tracing package", checked > 0);
    }

    private static List<String> names(HlslCompletionAnalysis.Result result) {
        return result.candidates().stream().map(HlslCompletionAnalysis.Candidate::name).toList();
    }
}
