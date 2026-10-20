package com.hlsl.validation;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/** A helper is compiled through the shader that includes it, found by reading the includes around it. */
public class HlslCompileRootTest extends BasePlatformTestCase {

    private HlslCompileRoot roots;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        roots = getProject().getService(HlslCompileRoot.class);
        roots.invalidate();
    }

    /** The reported case: Helper.hlsl is included by Shared.hlsl, which every pass shader includes. */
    public void testAHelperIsCompiledThroughTheShaderThatIncludesItsIncluder() {
        VirtualFile helper = addReportedLayout();
        HlslCompileRoot.Chain chain = roots.find(helper, null);
        assertNotNull("Helper.hlsl has includers", chain);
        // Both roots are two includes away, so the first by path is taken, every time.
        assertEquals(List.of("Alt.hlsl", "Shared.hlsl", "Helper.hlsl"), names(chain));
    }

    public void testAPragmaPicksTheRoot() {
        VirtualFile helper = addReportedLayout();
        assertEquals(List.of("Main.hlsl", "Shared.hlsl", "Helper.hlsl"), names(roots.find(helper, "Main.hlsl")));
        assertEquals("the name is matched without regard to case",
                "Main.hlsl", roots.find(helper, "main.HLSL").root().getName());
        assertEquals("an unknown name falls back to the usual choice",
                "Alt.hlsl", roots.find(helper, "Missing.hlsl").root().getName());
    }

    public void testAShaderNothingIncludesIsCompiledOnItsOwn() {
        addReportedLayout();
        VirtualFile main = myFixture.findFileInTempDir("HLSL/Main.hlsl");
        assertNull(roots.find(main, null));
    }

    public void testTheRootClosestToTheFileWins() {
        VirtualFile helper = addReportedLayout();
        myFixture.addFileToProject("HLSL/HelperDebug.hlsl", "#include \"Helper.hlsl\"\n");
        assertEquals(List.of("HelperDebug.hlsl", "Helper.hlsl"), names(roots.find(helper, null)));
    }

    /** A shader in the directory above includes the helper by its subdirectory. */
    public void testAnIncluderInTheDirectoryAboveIsFound() {
        PsiFile common = myFixture.addFileToProject("Shaders/Common/Fog.hlsli", "float3 AddHaze(float3 c) { return c; }\n");
        myFixture.addFileToProject("Shaders/Scene.hlsl", "#include \"Common/Fog.hlsli\"\n");
        assertEquals(List.of("Scene.hlsl", "Fog.hlsli"), names(roots.find(common.getVirtualFile(), null)));
    }

    public void testAnIncludeCycleHasNoRoot() {
        PsiFile a = myFixture.addFileToProject("Cycle/A.hlsl", "#include \"B.hlsl\"\n");
        myFixture.addFileToProject("Cycle/B.hlsl", "#include \"A.hlsl\"\n");
        assertNull(roots.find(a.getVirtualFile(), null));
    }

    private VirtualFile addReportedLayout() {
        myFixture.addFileToProject("HLSL/Shared.hlsl",
                "struct FrameConstants { float4x4 WorldToClip; };\n#include \"Helper.hlsl\"\n");
        PsiFile helper = myFixture.addFileToProject("HLSL/Helper.hlsl",
                "float DepthOf(FrameConstants pass, float3 worldPos)\n{\n    return mul(float4(worldPos,1),pass.WorldToClip).w;\n}\n");
        myFixture.addFileToProject("HLSL/Main.hlsl", "#include \"Shared.hlsl\"\n");
        myFixture.addFileToProject("HLSL/Alt.hlsl", "#include \"Shared.hlsl\"\n");
        return helper.getVirtualFile();
    }

    private static List<String> names(HlslCompileRoot.Chain chain) {
        assertNotNull(chain);
        return chain.files().stream().map(VirtualFile::getName).toList();
    }
}
