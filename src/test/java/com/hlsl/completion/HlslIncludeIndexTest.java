package com.hlsl.completion;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;
import java.util.Set;

public class HlslIncludeIndexTest extends BasePlatformTestCase {
    public void testOnlyActualDirectivesAreIncludes() {
        String source = """
                // #include "comment.hlsli"
                /* #include "block.hlsli" */
                #define FAKE #include "macro.hlsli"
                #define QUOTED "#include <string.hlsli>"
                float root; #include "inline.hlsli"
                #include "mismatched.hlsli>
                   # include "real.hlsli"
                #include<system.hlsli>
                /* leading comment */ #include "comment-prefix.hlsli"
                """;
        assertEquals(List.of("real.hlsli", "system.hlsli", "comment-prefix.hlsli"), HlslIncludeIndex.parseIncludes(source));
    }

    public void testContinuedMacroBodiesDoNotBecomeIncludes() {
        String source = "#define BODY \\\r\n#include \"macro.hlsli\"\r\n"
                + "#include \\\r\n\"real.hlsli\"\r\n";
        assertEquals(List.of("real.hlsli"), HlslIncludeIndex.parseIncludes(source));
    }

    public void testExportsDeclarationsWithoutCallSitesOrLocals() {
        myFixture.addFileToProject("library.hlsli", """
                #define LIBRARY_MACRO 1
                struct Surface { float3 normal; };
                float GlobalExposure;
                float DeclaredHelper(float inputValue) {
                    float LocalOnly = MissingFunction(inputValue);
                    return LocalOnly;
                }
                """);
        VirtualFile root = myFixture.addFileToProject("main.hlsl", """
                #include "library.hlsli"
                float RootOnly;
                """).getVirtualFile();

        Set<String> names = index().collectTransitiveIdentifiers(root);
        assertContainsElements(names, "LIBRARY_MACRO", "Surface", "GlobalExposure", "DeclaredHelper");
        assertFalse(names.contains("MissingFunction"));
        assertFalse(names.contains("LocalOnly"));
        assertFalse(names.contains("inputValue"));
        assertFalse(names.contains("normal"));
        assertFalse(names.contains("RootOnly"));
    }

    public void testUnsavedIncludedDocumentInvalidatesCache() {
        VirtualFile library = myFixture.addFileToProject("library.hlsli", "float OldValue;").getVirtualFile();
        VirtualFile root = myFixture.addFileToProject("main.hlsl", "#include \"library.hlsli\"").getVirtualFile();
        HlslIncludeIndex index = index();
        assertContainsElements(index.collectTransitiveIdentifiers(root), "OldValue");

        replaceDocument(library, "float UnsavedValue;");

        Set<String> updated = index.collectTransitiveIdentifiers(root);
        assertContainsElements(updated, "UnsavedValue");
        assertFalse(updated.contains("OldValue"));
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document(library)));
    }

    public void testVfsContentChangesInvalidateCacheWithoutOpeningADocument() throws Exception {
        VirtualFile library = myFixture.getTempDirFixture().createFile("library.hlsli", "float OldValue;");
        VirtualFile root = myFixture.addFileToProject("main.hlsl", "#include \"library.hlsli\"").getVirtualFile();
        assertNull(FileDocumentManager.getInstance().getCachedDocument(library));
        HlslIncludeIndex index = index();
        assertContainsElements(index.collectTransitiveIdentifiers(root), "OldValue");

        WriteAction.run(() -> VfsUtil.saveText(library, "float NewValue;"));

        Set<String> updated = index.collectTransitiveIdentifiers(root);
        assertContainsElements(updated, "NewValue");
        assertFalse(updated.contains("OldValue"));
        assertNull(FileDocumentManager.getInstance().getCachedDocument(library));
    }

    public void testUnsavedRootDocumentChangesIncludeGraph() {
        myFixture.addFileToProject("old.hlsli", "float OldInclude;");
        myFixture.addFileToProject("new.hlsli", "float NewInclude;");
        VirtualFile root = myFixture.addFileToProject("main.hlsl", "#include \"old.hlsli\"").getVirtualFile();
        HlslIncludeIndex index = index();
        assertContainsElements(index.collectTransitiveIdentifiers(root), "OldInclude");

        replaceDocument(root, "#include \"new.hlsli\"");

        Set<String> updated = index.collectTransitiveIdentifiers(root);
        assertContainsElements(updated, "NewInclude");
        assertFalse(updated.contains("OldInclude"));
    }

    public void testSourcesExposeImmutableUnsavedTypeSnapshots() {
        VirtualFile library = myFixture.addFileToProject("types.hlsli",
                "struct FrameConstants { float Gain; };").getVirtualFile();
        VirtualFile root = myFixture.addFileToProject("main.hlsl", "#include \"types.hlsli\"").getVirtualFile();
        HlslIncludeIndex index = index();
        List<CharSequence> before = index.collectTransitiveSources(root);
        assertEquals(1, before.size());

        replaceDocument(library, "struct FrameConstants { float4 LightList[16]; };");

        List<CharSequence> after = index.collectTransitiveSources(root);
        assertEquals(1, after.size());
        assertTrue(after.get(0).toString().contains("LightList[16]"));
        assertTrue(before.get(0).toString().contains("Gain"));
    }

    public void testCyclesAreVisitedOnceAndDoNotExportRootSymbols() {
        myFixture.addFileToProject("library.hlsli", "#include \"main.hlsl\"\nfloat IncludedValue;");
        VirtualFile root = myFixture.addFileToProject("main.hlsl", """
                #include "library.hlsli"
                #include "library.hlsli"
                #include "missing.hlsli"
                float RootValue;
                """).getVirtualFile();
        HlslIncludeIndex index = index();
        assertEquals(Set.of("IncludedValue"), index.collectTransitiveIdentifiers(root));
        assertEquals(1, index.collectTransitiveSources(root).size());
    }

    public void testNestedIncludesResolveRelativeToTheirOwnFile() {
        myFixture.addFileToProject("shared/types.hlsli", "struct NestedType { float value; };");
        myFixture.addFileToProject("lib/library.hlsli", "#include \"../shared/types.hlsli\"\nfloat IncludedValue;");
        VirtualFile root = myFixture.addFileToProject("main.hlsl", "#include \"lib/library.hlsli\"").getVirtualFile();

        HlslIncludeIndex index = index();
        assertEquals(Set.of("IncludedValue", "NestedType"), index.collectTransitiveIdentifiers(root));
        assertEquals(2, index.collectTransitiveSources(root).size());
    }

    private HlslIncludeIndex index() {
        return new HlslIncludeIndex(getProject());
    }

    private Document document(VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        return document;
    }

    private void replaceDocument(VirtualFile file, String source) {
        Document document = document(file);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(source));
    }
}
