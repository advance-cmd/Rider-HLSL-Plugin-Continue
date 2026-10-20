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

    /** The reported case: Shadow.hlsl is included by LightingUtil.hlsl, which every pass shader includes. */
    public void testAHelperIsCompiledThroughTheShaderThatIncludesItsIncluder() {
        VirtualFile shadow = addReportedLayout();
        HlslCompileRoot.Chain chain = roots.find(shadow, null);
        assertNotNull("Shadow.hlsl has includers", chain);
        // Both roots are two includes away, so the first by path is taken, every time.
        assertEquals(List.of("Color.hlsl", "LightingUtil.hlsl", "Shadow.hlsl"), names(chain));
    }

    public void testAPragmaPicksTheRoot() {
        VirtualFile shadow = addReportedLayout();
        assertEquals(List.of("Triangle.hlsl", "LightingUtil.hlsl", "Shadow.hlsl"), names(roots.find(shadow, "Triangle.hlsl")));
        assertEquals("the name is matched without regard to case",
                "Triangle.hlsl", roots.find(shadow, "triangle.HLSL").root().getName());
        assertEquals("an unknown name falls back to the usual choice",
                "Color.hlsl", roots.find(shadow, "Missing.hlsl").root().getName());
    }

    public void testAShaderNothingIncludesIsCompiledOnItsOwn() {
        addReportedLayout();
        VirtualFile triangle = myFixture.findFileInTempDir("HLSL/Triangle.hlsl");
        assertNull(roots.find(triangle, null));
    }

    public void testTheRootClosestToTheFileWins() {
        VirtualFile shadow = addReportedLayout();
        myFixture.addFileToProject("HLSL/ShadowDebug.hlsl", "#include \"Shadow.hlsl\"\n");
        assertEquals(List.of("ShadowDebug.hlsl", "Shadow.hlsl"), names(roots.find(shadow, null)));
    }

    /** A shader in the directory above includes the helper by its subdirectory. */
    public void testAnIncluderInTheDirectoryAboveIsFound() {
        PsiFile common = myFixture.addFileToProject("Shaders/Common/Fog.hlsli", "float3 ApplyFog(float3 c) { return c; }\n");
        myFixture.addFileToProject("Shaders/Sky.hlsl", "#include \"Common/Fog.hlsli\"\n");
        assertEquals(List.of("Sky.hlsl", "Fog.hlsli"), names(roots.find(common.getVirtualFile(), null)));
    }

    public void testAnIncludeCycleHasNoRoot() {
        PsiFile a = myFixture.addFileToProject("Cycle/A.hlsl", "#include \"B.hlsl\"\n");
        myFixture.addFileToProject("Cycle/B.hlsl", "#include \"A.hlsl\"\n");
        assertNull(roots.find(a.getVirtualFile(), null));
    }

    private VirtualFile addReportedLayout() {
        myFixture.addFileToProject("HLSL/LightingUtil.hlsl",
                "struct PassConstants { float4x4 ViewProj; };\n#include \"Shadow.hlsl\"\n");
        PsiFile shadow = myFixture.addFileToProject("HLSL/Shadow.hlsl",
                "float ViewZ(PassConstants pass, float3 posW)\n{\n    return mul(float4(posW,1),pass.ViewProj).w;\n}\n");
        myFixture.addFileToProject("HLSL/Triangle.hlsl", "#include \"LightingUtil.hlsl\"\n");
        myFixture.addFileToProject("HLSL/Color.hlsl", "#include \"LightingUtil.hlsl\"\n");
        return shadow.getVirtualFile();
    }

    private static List<String> names(HlslCompileRoot.Chain chain) {
        assertNotNull(chain);
        return chain.files().stream().map(VirtualFile::getName).toList();
    }
}
