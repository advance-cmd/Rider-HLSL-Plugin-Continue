package com.hlsl.documentation;

import com.hlsl.HlslFileType;
import com.hlsl.lexer.HlslLexer;
import com.hlsl.validation.HlslDxcAnnotator;
import com.hlsl.validation.HlslDxcSettings;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.regex.Pattern;

/**
 * Intrinsic functions and built-in types have no declaration in any source file, so hover and Ctrl+Click are
 * served by the built-ins file the plugin generates.
 */
public class HlslBuiltinsTest extends BasePlatformTestCase {

    /** Scalars, vectors and matrices are spelled out by their names and have nothing to explain. */
    private static final Pattern NUMERIC_TYPE = Pattern.compile(
            "(void|bool|int|uint|dword|half|float|double|min16float|min10float|min16int|min12int|min16uint|string)"
                    + "([1-4](x[1-4])?)?");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        HlslDxcSettings.getInstance().setHintLanguage("en");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            HlslDxcSettings.getInstance().setHintLanguage("auto");
        } finally {
            super.tearDown();
        }
    }

    public void testEveryIntrinsicFunctionTheLexerKnowsIsDeclared() {
        for (String name : HlslLexer.getBuiltinFunctions()) {
            HlslBuiltins.Entry entry = HlslBuiltins.entry(name);
            assertNotNull("no declaration for the intrinsic " + name, entry);
            assertEquals(name, HlslBuiltins.Kind.FUNCTION, entry.kind());
        }
    }

    public void testEveryResourceTypeTheLexerKnowsIsDeclared() {
        for (String name : HlslLexer.getTypeKeywords()) {
            if (NUMERIC_TYPE.matcher(name).matches()) continue;
            HlslBuiltins.Entry entry = HlslBuiltins.entry(name);
            assertNotNull("no declaration for the built-in type " + name, entry);
            assertEquals(name, HlslBuiltins.Kind.TYPE, entry.kind());
        }
    }

    /** Every declaration is described in both languages, and the Chinese text is a translation. */
    public void testEveryBuiltinIsDescribedInEnglishAndChinese() {
        for (String name : HlslBuiltins.names()) {
            HlslDxcSettings.getInstance().setHintLanguage("en");
            String english = HlslBuiltins.description(name);
            assertNotNull("no English description for " + name, english);
            HlslDxcSettings.getInstance().setHintLanguage("zh");
            String chinese = HlslBuiltins.description(name);
            assertNotNull("no Chinese description for " + name, chinese);
            assertFalse("the Chinese description of " + name + " is the English one", english.equals(chinese));
        }
    }

    /** The reported case: hovering RWTexture2D says what it is. */
    public void testHoveringRWTexture2DExplainsIt() {
        String text = "RWTexture2D<float4> gTarget : register(u0);\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        int offset = text.indexOf("RWTexture2D") + 4;
        HlslBuiltinDocumentationProvider provider = new HlslBuiltinDocumentationProvider();
        PsiElement claimed = provider.getCustomDocumentationElement(myFixture.getEditor(), myFixture.getFile(), null, offset);
        assertNotNull("the built-ins provider must claim the hovered type", claimed);
        assertEquals("RWTexture2D", claimed.getText());
        String doc = provider.generateHoverDoc(claimed, claimed);
        assertNotNull(doc);
        assertTrue(doc, doc.contains("class RWTexture2D"));
        assertTrue("the description is missing: " + doc, doc.contains("bound to a u register"));
    }

    /** Hover in current IDEs goes through documentation targets, which must reach the same text. */
    public void testTheDocumentationTargetDescribesTheBuiltin() {
        String text = "uint Flip(uint v)\n{\n    return reversebits(v);\n}\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("reversebits") + 2);
        DocumentationTarget target = new HlslDocumentationTargetProvider().documentationTarget(element, element);
        assertTrue("expected the built-in target, got " + target, target instanceof HlslBuiltinTarget);
        String hint = target.computeDocumentationHint();
        assertNotNull(hint);
        assertTrue(hint, hint.startsWith("uint reversebits(uint value)"));
        assertTrue(hint, hint.contains("Reverses the order of the bits"));
        DocumentationResult documentation = target.computeDocumentation();
        assertNotNull(documentation);
    }

    public void testTheExplanationFollowsTheHintLanguage() {
        HlslDxcSettings.getInstance().setHintLanguage("zh");
        String text = "RWTexture2D<float4> gTarget : register(u0);\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("RWTexture2D") + 1);
        String doc = new HlslBuiltinDocumentationProvider().generateDoc(element, element);
        assertNotNull(doc);
        assertTrue(doc, doc.contains("可读写二维纹理"));
        assertTrue(doc, doc.contains("内建类型"));
    }

    /** The reported case: Ctrl+Click on reversebits went nowhere and nothing was underlined. */
    public void testCtrlClickOnAnIntrinsicGoesToItsDeclaration() {
        myFixture.configureByText(HlslFileType.INSTANCE,
                "uint Flip(uint v)\n{\n    return rever<caret>sebits(v);\n}\n");
        PsiElement target = singleTarget();
        assertEquals("reversebits", target.getText());
        PsiFile file = target.getContainingFile();
        assertTrue("the target is not in the built-ins file: " + file, HlslBuiltins.isBuiltinsFile(file.getVirtualFile()));
        String builtins = file.getText();
        int line = builtins.lastIndexOf('\n', target.getTextRange().getStartOffset() - 1);
        int previousLine = builtins.lastIndexOf('\n', line - 1);
        assertTrue("the declaration must carry its description above it",
                builtins.substring(previousLine + 1, line).startsWith("// Reverses the order of the bits"));

        // Ctrl+hover shows the hint of the target, asked for with the name in the shader as the original.
        PsiElement source = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        DocumentationTarget hint = new HlslDocumentationTargetProvider().documentationTarget(target, source);
        assertNotNull(hint);
        assertTrue(hint.computeDocumentationHint(), hint.computeDocumentationHint().contains("reversebits"));
    }

    public void testCtrlClickOnAResourceTypeGoesToItsClass() {
        myFixture.configureByText(HlslFileType.INSTANCE, "RWTexture<caret>2D<float4> gTarget : register(u0);\n");
        PsiElement target = singleTarget();
        assertEquals("RWTexture2D", target.getText());
        PsiFile file = target.getContainingFile();
        assertTrue(HlslBuiltins.isBuiltinsFile(file.getVirtualFile()));
        int start = target.getTextRange().getStartOffset();
        assertEquals("class RWTexture2D", file.getText().substring(start - "class ".length(), start + "RWTexture2D".length()));
    }

    /** A function of the shader itself is still resolved in the shader. */
    public void testCtrlClickOnAUserFunctionStillGoesToTheShader() {
        myFixture.configureByText(HlslFileType.INSTANCE,
                "float Helper(float x)\n{\n    return x;\n}\nfloat4 main() : SV_Target\n{\n    return Hel<caret>per(1);\n}\n");
        PsiElement target = singleTarget();
        assertEquals("Helper", target.getText());
        assertSame(myFixture.getFile(), target.getContainingFile());
        assertEquals(myFixture.getFile().getText().indexOf("Helper"), target.getTextRange().getStartOffset());
    }

    /** The reported case: [numthreads(8,8,1)] had no explanation on hover. */
    public void testHoveringNumthreadsExplainsIt() {
        String text = "[numthreads(8,8,1)]\nvoid CS_Main(uint3 id: SV_DispatchThreadID){\n}\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        int offset = text.indexOf("numthreads") + 3;
        HlslBuiltinDocumentationProvider provider = new HlslBuiltinDocumentationProvider();
        PsiElement claimed = provider.getCustomDocumentationElement(myFixture.getEditor(), myFixture.getFile(), null, offset);
        assertNotNull("the built-ins provider must claim the attribute", claimed);
        String doc = provider.generateHoverDoc(claimed, claimed);
        assertNotNull(doc);
        assertTrue(doc, doc.contains("[numthreads(uint x, uint y, uint z)]"));
        assertTrue(doc, doc.contains("Number of threads in one thread group"));
        assertTrue(doc, doc.contains("attribute"));

        DocumentationTarget target = new HlslDocumentationTargetProvider().documentationTarget(claimed, claimed);
        assertTrue("expected the built-in target, got " + target, target instanceof HlslBuiltinTarget);
        assertTrue(target.computeDocumentationHint(), target.computeDocumentationHint().startsWith("[numthreads("));
    }

    public void testAnAttributeWithoutArgumentsIsExplainedInChinese() {
        HlslDxcSettings.getInstance().setHintLanguage("zh");
        String text = "void f()\n{\n    [unroll]\n    for (int i = 0; i < 4; i++) {}\n}\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        PsiElement element = myFixture.getFile().findElementAt(text.indexOf("unroll") + 1);
        String doc = new HlslBuiltinDocumentationProvider().generateDoc(element, element);
        assertNotNull(doc);
        assertTrue(doc, doc.contains("把循环一直展开"));
        assertTrue(doc, doc.contains("属性"));
    }

    /** loop, call and branch are attributes only inside their brackets. */
    public void testTheSameWordOutsideAnAttributeIsNotExplained() {
        String text = "float call(float loop)\n{\n    float branch = loop;\n    return branch;\n}\n";
        myFixture.configureByText(HlslFileType.INSTANCE, text);
        for (String word : new String[]{"call", "loop", "branch"}) {
            PsiElement element = myFixture.getFile().findElementAt(text.indexOf(word) + 1);
            assertNull(word + " is not an attribute here", HlslBuiltins.nameOf(element));
        }
    }

    public void testCtrlClickOnAnAttributeGoesToItsDeclaration() {
        myFixture.configureByText(HlslFileType.INSTANCE,
                "[numth<caret>reads(8,8,1)]\nvoid CS_Main(uint3 id: SV_DispatchThreadID){\n}\n");
        PsiElement target = singleTarget();
        assertEquals("numthreads", target.getText());
        assertTrue(HlslBuiltins.isBuiltinsFile(target.getContainingFile().getVirtualFile()));
    }

    public void testTheBuiltinsFileIsReadOnlyAndNeverCompiled() {
        assertFalse(HlslBuiltins.file().isWritable());
        PsiFile builtins = PsiManager.getInstance(getProject()).findFile(HlslBuiltins.file());
        assertNotNull(builtins);
        assertNull("the built-ins file must not be compiled", new HlslDxcAnnotator().collectInformation(builtins));
    }

    private PsiElement singleTarget() {
        PsiElement[] targets = GotoDeclarationAction.findAllTargetElements(
                getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
        assertEquals("expected exactly one target", 1, targets.length);
        return targets[0];
    }
}
