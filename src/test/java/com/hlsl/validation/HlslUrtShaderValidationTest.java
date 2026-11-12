package com.hlsl.validation;

import com.hlsl.UnityTestSources;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** What the DXC check of a Unity shader is put together from, read the way the plugin reads includes. */
public class HlslUrtShaderValidationTest extends BasePlatformTestCase {

    private static final String SHADER_PATH = "Assets/Rendering/Visibility.urtshader";
    private static final String PACKAGE_DIRECTORY = "Packages/" + UnityTestSources.PACKAGE + "/" + UnityTestSources.URT;

    private VirtualFile shader;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        for (Map.Entry<String, String> file : UnityTestSources.project(SHADER_PATH).entrySet()) {
            PsiFile added = myFixture.addFileToProject(file.getKey(), file.getValue());
            if (file.getKey().equals(SHADER_PATH)) shader = added.getVirtualFile();
        }
    }

    /** The package files are copied under the paths Unity includes them by, with the ray generation shader. */
    public void testThePackageFilesAreLaidOutByTheirPackagesPaths() {
        List<HlslDxcAnnotator.ContextFile> files =
                HlslDxcAnnotator.unityFiles(List.of(shader), List.of(UnityTestSources.SHADER), true);
        Map<String, String> byPath = files.stream().collect(Collectors.toMap(
                HlslDxcAnnotator.ContextFile::relativePath, HlslDxcAnnotator.ContextFile::text));
        assertEquals(Set.of(PACKAGE_DIRECTORY + "TraceRayAndQueryHit.hlsl", PACKAGE_DIRECTORY + "CommonStructs.hlsl",
                PACKAGE_DIRECTORY + "Hardware/HardwareRaygenShader.hlsl"), byPath.keySet());
        assertEquals(UnityTestSources.TRACE, byPath.get(PACKAGE_DIRECTORY + "TraceRayAndQueryHit.hlsl"));
        assertTrue("the files are read from the package cache", files.stream()
                .allMatch(file -> file.path().contains("Library/PackageCache/")));
    }

    public void testOnlyAUrtShaderGetsTheRayGenerationShader() {
        List<HlslDxcAnnotator.ContextFile> files =
                HlslDxcAnnotator.unityFiles(List.of(shader), List.of(UnityTestSources.SHADER), false);
        assertEquals(2, files.size());
    }

    public void testCollectingAUrtShaderGathersItsPackages() {
        HlslDxcAnnotator.CollectedInfo info = new HlslDxcAnnotator().collectInformation(psi(shader));
        assertNotNull(info);
        assertTrue(info.urtShader());
        assertEquals(3, info.unityFiles.size());
    }

    /** A package Unity downloaded is not the user's to fix, and compiled on its own it only reports noise. */
    public void testAFileOfThePackageCacheIsNotValidated() {
        VirtualFile trace = shader.getParent().getParent().getParent()
                .findFileByRelativePath(UnityTestSources.CACHE_DIRECTORY + UnityTestSources.URT + "TraceRayAndQueryHit.hlsl");
        assertNotNull(trace);
        assertNull(new HlslDxcAnnotator().collectInformation(psi(trace)));
    }

    public void testOutsideAUnityProjectNothingIsCopied() {
        PsiFile plain = myFixture.addFileToProject("Other/Main.hlsl", "#include \"Common.hlsl\"\n");
        myFixture.addFileToProject("Other/Common.hlsl", "");
        assertEquals(List.of(), HlslDxcAnnotator.unityFiles(List.of(plain.getVirtualFile()),
                List.of(plain.getText()), false));
    }

    private PsiFile psi(VirtualFile file) {
        PsiFile psi = getPsiManager().findFile(file);
        assertNotNull(psi);
        return psi;
    }
}
