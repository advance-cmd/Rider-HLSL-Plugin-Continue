package com.hlsl.completion;

import com.hlsl.documentation.HlslBuiltins;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/** Completing attributes and semantics inserts the rest of the name and what has to follow it. */
public class HlslAttributeCompletionTest extends BasePlatformTestCase {

    /** The reported case. */
    public void testNumthreadsIsCompletedWithItsParentheses() {
        myFixture.configureByText("IBL.hlsl", "[numth<caret>]\nvoid CS_Irradiance(uint3 id : SV_DispatchThreadID)\n{\n}\n");
        myFixture.completeBasic();
        myFixture.checkResult("[numthreads(<caret>)]\nvoid CS_Irradiance(uint3 id : SV_DispatchThreadID)\n{\n}\n");
    }

    /** Tab accepts the selected attribute the same way Enter does. */
    public void testTabAcceptsTheSelectedAttribute() {
        myFixture.configureByText("IBL.hlsl", "[<caret>]\nvoid CS_Irradiance(uint3 id : SV_DispatchThreadID)\n{\n}\n");
        myFixture.completeBasic();
        LookupImpl lookup = (LookupImpl) myFixture.getLookup();
        assertNotNull("no attributes offered inside the bracket", lookup);
        LookupElement numthreads = null;
        for (LookupElement element : myFixture.getLookupElements()) {
            if ("numthreads".equals(element.getLookupString())) numthreads = element;
        }
        assertNotNull("numthreads is not offered: " + myFixture.getLookupElementStrings(), numthreads);
        lookup.setCurrentItem(numthreads);
        myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
        myFixture.checkResult("[numthreads(<caret>)]\nvoid CS_Irradiance(uint3 id : SV_DispatchThreadID)\n{\n}\n");
    }

    public void testTheClosingBracketIsAddedWhenMissing() {
        myFixture.configureByText("IBL.hlsl", "[numth<caret>\nvoid CS_Irradiance(uint3 id : SV_DispatchThreadID)\n{\n}\n");
        myFixture.completeBasic();
        myFixture.checkResult("[numthreads(<caret>)]\nvoid CS_Irradiance(uint3 id : SV_DispatchThreadID)\n{\n}\n");
    }

    public void testAStringArgumentGetsItsQuotes() {
        myFixture.configureByText("Tess.hlsl", "[domai<caret>]\nvoid HS()\n{\n}\n");
        myFixture.completeBasic();
        myFixture.checkResult("[domain(\"<caret>\")]\nvoid HS()\n{\n}\n");
    }

    public void testAnAttributeWithoutArgumentsLeavesTheCaretAfterItsBracket() {
        myFixture.configureByText("Blur.hlsl", "void f()\n{\n    [unr<caret>]\n    for (int i = 0; i < 4; i++) {}\n}\n");
        myFixture.completeBasic();
        myFixture.checkResult("void f()\n{\n    [unroll]<caret>\n    for (int i = 0; i < 4; i++) {}\n}\n");
    }

    /** What completion offers, hover explains and Ctrl+Click finds. */
    public void testEveryCompletedAttributeIsDeclaredInTheBuiltins() {
        for (HlslAttributes.Attribute attribute : HlslAttributes.ALL) {
            HlslBuiltins.Entry entry = HlslBuiltins.entry(attribute.name());
            assertNotNull("the built-ins file does not declare " + attribute.name(), entry);
            assertEquals(attribute.name(), HlslBuiltins.Kind.ATTRIBUTE, entry.kind());
        }
    }

    public void testAnIndexDoesNotOfferAttributes() {
        myFixture.configureByText("Blur.hlsl", "float gValues[4];\nfloat f(int i)\n{\n    return gValues[<caret>];\n}\n");
        myFixture.completeBasic();
        List<String> lookup = myFixture.getLookupElementStrings();
        assertTrue("an index must not offer attributes: " + lookup, lookup == null || !lookup.contains("numthreads"));
    }

    /** The reported case: a parameter semantic typed before the function has a body. */
    public void testAParameterSemanticIsCompleted() {
        myFixture.configureByText("IBL.hlsl", "[numthreads(8, 8, 1)]\nvoid CS_Irradiance(uint3 id : SV_Disp<caret>)\n");
        myFixture.completeBasic();
        myFixture.checkResult("[numthreads(8, 8, 1)]\nvoid CS_Irradiance(uint3 id : SV_DispatchThreadID<caret>)\n");
    }

    public void testRegisterIsCompletedWithItsParentheses() {
        myFixture.configureByText("IBL.hlsl", "TextureCube gSky : regi<caret>;\n");
        myFixture.completeBasic();
        myFixture.checkResult("TextureCube gSky : register(<caret>);\n");
    }
}
