package com.hlsl.completion;

import com.hlsl.HlslLanguage;
import com.hlsl.ShaderFixtures;
import com.hlsl.parameterinfo.HlslParameterInfoHandler;
import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * End-to-end checks with the plugin loaded in a real IDE fixture: contributor registration, VFS include
 * resolution, prefix matching and live template wiring are exercised, not just the analysis helper.
 */
public class HlslCompletionInIdeTest extends BasePlatformTestCase {

    /**
     * "struct" must be offered as the declaration live template, not as a second bare keyword next to it: the
     * platform orders two entries with an identical lookup string itself, so the keyword would come first.
     */
    public void testStructIsOfferedAsTheDeclarationTemplate() throws IOException {
        LiveTemplateCompletionContributor.setShowTemplatesInTests(true, getTestRootDisposable());
        myFixture.configureByText("Deferred.hlsl", "struct<caret>");
        myFixture.completeBasic();
        LookupElement[] items = myFixture.getLookupElements();
        assertNotNull("no completion list for 'struct'", items);
        StringBuilder order = new StringBuilder();
        int template = -1;
        int bareKeyword = -1;
        for (int i = 0; i < items.length; i++) {
            LookupElementPresentation presentation = LookupElementPresentation.renderElement(items[i]);
            order.append('[').append(items[i].getLookupString()).append('|').append(presentation.getTypeText()).append(']');
            if (!"struct".equals(items[i].getLookupString())) continue;
            if ("Struct declaration".equals(presentation.getTypeText())) template = i;
            else bareKeyword = i;
        }
        assertTrue("the struct declaration template is not offered: " + order, template >= 0);
        assertEquals("the bare keyword must not shadow the template: " + order, -1, bareKeyword);
    }

    /** The reported case: a struct reached through ConstantBuffer&lt;T&gt; in an include, typed in lowercase. */
    public void testIncludedStructMemberWithLowercasePrefix() throws IOException {
        addLightingUtil();
        assertOfferedOrInserted(patched("gPass.AmbientLight.rgb", "gPass.inv<caret>"),
                "InvViewProj", "gPass.InvViewProj");
    }

    public void testBareDotListsEveryIncludedField() throws IOException {
        addLightingUtil();
        myFixture.configureByText("Deferred.hlsl", patched("gPass.AmbientLight.rgb", "gPass.<caret>"));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull("no completion list for gPass.", lookup);
        assertTrue("expected the PassConstants fields, got " + lookup,
                lookup.containsAll(List.of("InvViewProj", "EyePosW", "Lights", "AmbientLight")));
    }

    public void testTextureIntrinsicMembersAreListed() throws IOException {
        addLightingUtil();
        myFixture.configureByText("Deferred.hlsl",
                patched("gDepthMap.GetDimensions(width, height);", "gDepthMap.Get<caret>Dimensions(width, height);"));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertNotNull("no completion list for gDepthMap.Get", lookup);
        assertTrue("expected GetDimensions on Texture2D, got " + lookup, lookup.contains("GetDimensions"));
    }

    /** Texture intrinsics are language features, not declarations, and must match a lowercase prefix too. */
    public void testTextureIntrinsicWithLowercasePrefix() throws IOException {
        addLightingUtil();
        assertOfferedOrInserted(patched("gDepthMap.GetDimensions(width, height);",
                        "gDepthMap.getdim<caret>(width, height);"),
                "GetDimensions", "gDepthMap.GetDimensions");
    }

    public void testDeclaringAVariableDoesNotOpenCompletion() throws IOException {
        addLightingUtil();
        myFixture.configureByText("Deferred.hlsl",
                patched("int2(pin.PosH.xy)", "int2 pix<caret>el = int2(pin.PosH.xy)"));
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertTrue("declaring a variable must not offer unrelated symbols, got " + lookup,
                lookup == null || lookup.isEmpty());
    }

    public void testIncludeResolutionThroughProjectService() throws IOException {
        addLightingUtil();
        myFixture.configureByText("Deferred.hlsl", load("Deferred.hlsl"));
        List<String> includes = HlslIncludeIndex.parseIncludes(
                myFixture.getEditor().getDocument().getCharsSequence());
        assertTrue("raw include parsing failed: " + includes, includes.contains("LightingUtil.hlsl"));
        VirtualFile file = myFixture.getFile().getVirtualFile();
        assertNotNull(file);
        HlslIncludeIndex index = getProject().getService(HlslIncludeIndex.class);
        assertNotNull("the include index project service is missing", index);
        List<CharSequence> sources = index.collectTransitiveSources(file);
        assertEquals("the include chain did not resolve in the fixture: " + sources, 1, sources.size());
        assertTrue("the resolved include is not LightingUtil.hlsl",
                sources.get(0).toString().contains("struct PassConstants"));
    }

    public void testParameterInfoHandlerIsRegisteredForHlsl() {
        ParameterInfoHandler[] handlers = ShowParameterInfoHandler.getHandlers(getProject(), HlslLanguage.INSTANCE);
        assertTrue("the HLSL parameter info handler is not registered",
                Arrays.stream(handlers).anyMatch(handler -> handler instanceof HlslParameterInfoHandler));
    }

    /** Drives the registered handler through the platform, which fails loudly if it is broken. */
    public void testParameterInfoRunsOnARealCall() throws IOException {
        addLightingUtil();
        myFixture.configureByText("Deferred.hlsl", "float3 c = ApplyFog(<caret>);");
        ShowParameterInfoHandler.invoke(getProject(), myFixture.getEditor(), myFixture.getFile(),
                myFixture.getCaretOffset(), null, false);
    }

    /**
     * The reported gap: completing a function name inserts the parentheses without typing a character, so the
     * typed handler never fires. The caret must end up inside the list with something to show there.
     */
    public void testCompletingAFunctionLeavesTheCaretInTheArgumentList() throws IOException {
        addLightingUtil();
        myFixture.configureByText("Deferred.hlsl",
                "#include \"LightingUtil.hlsl\"\nfloat3 c = Apply<caret>;\n");
        myFixture.completeBasic();
        if (myFixture.getLookup() != null) myFixture.finishLookup('\n');
        myFixture.checkResult("#include \"LightingUtil.hlsl\"\nfloat3 c = ApplyFog(<caret>);\n");

        HlslIncludeIndex index = getProject().getService(HlslIncludeIndex.class);
        List<CharSequence> includes = index.collectTransitiveSources(myFixture.getFile().getVirtualFile());
        HlslCompletionAnalysis.CallInfo call = HlslCompletionAnalysis.callAt(
                myFixture.getEditor().getDocument().getCharsSequence(), myFixture.getCaretOffset(), includes);
        assertNotNull("the caret must end up inside the argument list", call);
        assertEquals("ApplyFog", call.name());
        assertEquals(5, call.signatures().get(0).parameters().size());
    }

    public void testParameterInfoIsRequestedOnlyForHlslFiles() throws IOException {
        addLightingUtil();
        myFixture.configureByText("Deferred.hlsl", "float3 c = ApplyFog(<caret>);");
        assertTrue("inside an HLSL argument list the popup must be requested",
                HlslParameterInfoHandler.showAt(getProject(), myFixture.getEditor(), myFixture.getFile()));
        assertFalse("a missing file must be handled quietly",
                HlslParameterInfoHandler.showAt(getProject(), myFixture.getEditor(), null));
    }

    public void testLiveTemplateWiringIsDeclaredAndPackaged() throws IOException {
        String descriptor = readResource("/META-INF/plugin.xml");
        assertTrue("plugin.xml must register the HLSL template context type",
                descriptor.contains("HlslTemplateContextType"));
        assertTrue("plugin.xml must register the default live templates provider",
                descriptor.contains("HlslLiveTemplatesProvider"));
        String templates = readResource("/liveTemplates/HLSL.xml");
        assertTrue("the struct template is missing: " + templates, templates.contains("name=\"struct\""));
        assertTrue("the struct template is not scoped to HLSL: " + templates,
                templates.contains("name=\"HLSL\""));
    }

    /**
     * A single matching item is auto-inserted, which closes the lookup, so both outcomes are success.
     */
    private void assertOfferedOrInserted(String text, String lookupName, String insertedText) {
        myFixture.configureByText("Deferred.hlsl", text);
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        boolean offered = lookup != null && lookup.contains(lookupName);
        boolean inserted = myFixture.getEditor().getDocument().getText().contains(insertedText);
        assertTrue("expected " + lookupName + " to be offered or inserted, lookup=" + lookup,
                offered || inserted);
    }

    private void addLightingUtil() throws IOException {
        myFixture.addFileToProject("LightingUtil.hlsl", load("LightingUtil.hlsl"));
    }

    private static String patched(String needle, String replacement) throws IOException {
        String source = load("Deferred.hlsl");
        int at = source.indexOf(needle);
        assertTrue("needle not found: " + needle, at >= 0);
        return source.substring(0, at) + replacement + source.substring(at + needle.length());
    }

    private static String load(String name) throws IOException {
        return ShaderFixtures.require(name);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = HlslCompletionInIdeTest.class.getResourceAsStream(path)) {
            assertNotNull("missing resource: " + path, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
