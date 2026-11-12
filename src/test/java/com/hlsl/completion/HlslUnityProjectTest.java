package com.hlsl.completion;

import com.hlsl.HlslFileType;
import com.hlsl.UnityTestSources;
import com.hlsl.documentation.HlslDocumentationProvider;
import com.hlsl.parameterinfo.HlslParameterInfoHandler;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;

import java.util.List;
import java.util.Map;

/**
 * A .urtshader in a Unity project: its includes name package files by their Packages/ path, which Unity keeps
 * in Library/PackageCache under the package's name and a hash.
 */
public class HlslUnityProjectTest extends BasePlatformTestCase {

    private static final String SHADER_PATH = "Assets/Rendering/Visibility.urtshader";
    private static final String CARET = "<caret>";

    private VirtualFile addProject() {
        return addProject(UnityTestSources.SHADER);
    }

    /** Lays out the project, with {@code shaderText} as the text of the shader. */
    private VirtualFile addProject(String shaderText) {
        VirtualFile shader = null;
        for (Map.Entry<String, String> file : UnityTestSources.project(SHADER_PATH).entrySet()) {
            boolean isShader = file.getKey().equals(SHADER_PATH);
            PsiFile added = myFixture.addFileToProject(file.getKey(), isShader ? shaderText : file.getValue());
            if (isShader) shader = added.getVirtualFile();
        }
        return shader;
    }

    private void openShader(String text) {
        VirtualFile shader = addProject(text.replace(CARET, ""));
        myFixture.configureFromExistingVirtualFile(shader);
        int caret = text.indexOf(CARET);
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
    }

    public void testAUrtShaderIsAnHlslFile() {
        assertSame(HlslFileType.INSTANCE, addProject().getFileType());
    }

    /** The reported case: the package include was not found, because the package sits in the cache. */
    public void testAPackagePathFindsTheDownloadedPackageWhateverItsHash() {
        VirtualFile shader = addProject();
        VirtualFile trace = HlslIncludeIndex.resolve(shader, UnityTestSources.TRACE_INCLUDE);
        assertNotNull("the package file in Library/PackageCache is found", trace);
        assertTrue(trace.getPath(), trace.getPath().endsWith(
                UnityTestSources.CACHE_DIRECTORY + UnityTestSources.URT + "TraceRayAndQueryHit.hlsl"));
        HlslIncludeIndex index = getProject().getService(HlslIncludeIndex.class);
        assertEquals("a package file includes the others by their Packages/ paths too", "CommonStructs.hlsl",
                index.directIncludes(trace).get(0).getName());
    }

    public void testAnEmbeddedPackageComesBeforeTheCache() {
        VirtualFile shader = addProject();
        PsiFile embedded = myFixture.addFileToProject(UnityTestSources.TRACE_INCLUDE, "// embedded\n");
        assertEquals(embedded.getVirtualFile(), HlslIncludeIndex.resolve(shader, UnityTestSources.TRACE_INCLUDE));
    }

    public void testAnAssetsPathIsReadFromTheProjectRoot() {
        addProject();
        PsiFile common = myFixture.addFileToProject("Assets/Rendering/Common.hlsl", "float Two() { return 2; }\n");
        PsiFile user = myFixture.addFileToProject("Assets/Effects/Deep/Use.hlsl", "#include \"Assets/Rendering/Common.hlsl\"\n");
        assertEquals(common.getVirtualFile(), HlslIncludeIndex.resolve(user.getVirtualFile(), "Assets/Rendering/Common.hlsl"));
    }

    public void testOutsideAUnityProjectAPackagePathIsAnOrdinaryPath() {
        PsiFile shader = myFixture.addFileToProject("Shaders/Main.hlsl", "");
        myFixture.addFileToProject("Library/PackageCache/" + UnityTestSources.PACKAGE + "@abc/" + UnityTestSources.URT
                + "TraceRayAndQueryHit.hlsl", "");
        assertNull("without Packages/manifest.json there is no Unity project",
                HlslIncludeIndex.resolve(shader.getVirtualFile(), UnityTestSources.TRACE_INCLUDE));
    }

    public void testAFileOfThePackageCacheIsKnownByItsPackagesPath() {
        VirtualFile shader = addProject();
        VirtualFile trace = HlslIncludeIndex.resolve(shader, UnityTestSources.TRACE_INCLUDE);
        VirtualFile root = HlslUnityPaths.projectRoot(shader);
        assertNotNull(root);
        assertEquals(UnityTestSources.TRACE_INCLUDE, HlslUnityPaths.projectPath(root, trace));
        assertEquals(SHADER_PATH, HlslUnityPaths.projectPath(root, shader));
        assertTrue(HlslUnityPaths.isInPackageCache(trace));
        assertFalse(HlslUnityPaths.isInPackageCache(shader));
    }

    /** Ctrl+Click on the include path opens the package file. */
    public void testCtrlClickOnAPackageIncludeOpensTheFile() {
        openShader(UnityTestSources.SHADER.replace("TraceRayAndQueryHit.hlsl\"", "TraceRay" + CARET + "AndQueryHit.hlsl\""));
        PsiElement target = singleTarget(myFixture.getCaretOffset());
        assertTrue(target instanceof PsiFile);
        assertTrue(((PsiFile) target).getVirtualFile().getPath().contains("Library/PackageCache/"));
    }

    /** Ctrl+Click on UnifiedRT::TraceRayClosestHit opens its declaration in the package. */
    public void testCtrlClickOnANamespacedFunctionOpensItsDeclaration() {
        openShader(UnityTestSources.SHADER);
        int offset = UnityTestSources.SHADER.indexOf("TraceRayClosestHit(") + 3;
        PsiElement target = singleTarget(offset);
        assertEquals("TraceRayAndQueryHit.hlsl", target.getContainingFile().getName());
        assertEquals("TraceRayClosestHit", target.getText());
    }

    public void testHoverDescribesANamespacedFunction() {
        openShader(UnityTestSources.SHADER);
        PsiElement element = myFixture.getFile().findElementAt(UnityTestSources.SHADER.indexOf("TraceRayClosestHit(") + 3);
        String hover = new HlslDocumentationProvider().generateHoverDoc(element, element);
        assertNotNull(hover);
        assertTrue(hover, hover.contains("Traces a ray and returns the closest hit"));
    }

    public void testCompletionAfterTheNamespaceListsItsMembers() {
        openShader(UnityTestSources.SHADER.replace("UnifiedRT::kRayFlagNone", "UnifiedRT::" + CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertTrue(lookup.toString(), lookup.containsAll(List.of(
                "kRayFlagNone", "kRayFlagAcceptFirstHitAndEndSearch", "Hit", "Ray", "TraceRayClosestHit")));
    }

    public void testCompletionOfAHitListsItsFieldsAndMethods() {
        openShader(UnityTestSources.SHADER.replace("hit.hitDistance", "hit." + CARET));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull(lookup);
        assertTrue(lookup.toString(), lookup.containsAll(List.of("hitDistance", "instanceID", "IsValid")));
    }

    public void testParameterInfoOfANamespacedFunction() {
        openShader(UnityTestSources.SHADER.replace("TraceRayClosestHit(dispatchInfo, ", "TraceRayClosestHit(dispatchInfo, " + CARET));
        MockCreateParameterInfoContext context =
                new MockCreateParameterInfoContext(myFixture.getEditor(), myFixture.getFile());
        assertNotNull(new HlslParameterInfoHandler().findElementForParameterInfo(context));
        HlslCompletionAnalysis.Signature signature = (HlslCompletionAnalysis.Signature) context.getItemsToShow()[0];
        assertEquals("TraceRayClosestHit", signature.name());
        assertEquals(5, signature.parameters().size());
    }

    /** Typing the second colon of UnifiedRT:: opens the list without a shortcut. */
    public void testTheSecondColonOpensTheList() {
        openShader("void f()\n{\n    UnifiedRT:" + CARET + "\n}\n");
        PsiElement position = myFixture.getFile().findElementAt(myFixture.getCaretOffset() - 1);
        assertNotNull(position);
        assertTrue(new HlslCompletionContributor().invokeAutoPopup(position, ':'));
        assertFalse(new HlslCompletionContributor().invokeAutoPopup(position, 'x'));
    }

    private PsiElement singleTarget(int offset) {
        PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(getProject(), myFixture.getEditor(), offset);
        assertEquals("expected exactly one target", 1, targets.length);
        return targets[0];
    }
}
