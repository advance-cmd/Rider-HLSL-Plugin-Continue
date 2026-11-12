package com.hlsl.completion;

import com.hlsl.UnityTestSources;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Names declared in a namespace, as Unity's Unified Ray Tracing API declares everything in
 * {@code namespace UnifiedRT}: reached through {@code UnifiedRT::}, never among the global names.
 */
public class HlslNamespaceTest {

    private static final String CARET = "<caret>";
    /** What the shader includes, in the order the include index reads it. */
    private static final List<CharSequence> INCLUDES =
            List.of(UnityTestSources.TRACE, UnityTestSources.COMMON_STRUCTS);

    private static HlslCompletionAnalysis.Result analyze(String body) {
        String text = "void RayGenExecute(UnifiedRT::DispatchInfo dispatchInfo)\n{\n    " + body + "\n}\n";
        int offset = text.indexOf(CARET);
        assertTrue("the snippet has no caret: " + body, offset >= 0);
        return HlslCompletionAnalysis.analyze(text.replace(CARET, ""), offset, INCLUDES);
    }

    private static Map<String, HlslCompletionAnalysis.Kind> kinds(HlslCompletionAnalysis.Result result) {
        return result.candidates().stream().collect(Collectors.toMap(
                HlslCompletionAnalysis.Candidate::name, HlslCompletionAnalysis.Candidate::kind, (a, b) -> a));
    }

    private static HlslCompletionAnalysis.CallInfo callAt(String body) {
        String text = "void RayGenExecute(UnifiedRT::DispatchInfo dispatchInfo)\n{\n    " + body + "\n}\n";
        int offset = text.indexOf(CARET);
        return HlslCompletionAnalysis.callAt(text.replace(CARET, ""), offset, INCLUDES);
    }

    /** The reported case: nothing was offered after UnifiedRT::. */
    @Test
    public void theNamespaceOffersWhatItDeclares() {
        HlslCompletionAnalysis.Result result = analyze("uint flags = UnifiedRT::" + CARET);
        assertEquals(HlslCompletionAnalysis.Context.MEMBER, result.context());
        Map<String, HlslCompletionAnalysis.Kind> members = kinds(result);
        assertEquals(HlslCompletionAnalysis.Kind.VARIABLE, members.get("kRayFlagNone"));
        assertEquals(HlslCompletionAnalysis.Kind.VARIABLE, members.get("kRayFlagAcceptFirstHitAndEndSearch"));
        assertEquals(HlslCompletionAnalysis.Kind.TYPE, members.get("Hit"));
        assertEquals(HlslCompletionAnalysis.Kind.TYPE, members.get("Ray"));
        assertEquals(HlslCompletionAnalysis.Kind.FUNCTION, members.get("TraceRayClosestHit"));
        assertFalse("a field is not a member of the namespace: " + members, members.containsKey("hitDistance"));
        assertEquals("a typed prefix keeps the list", members.keySet(),
                kinds(analyze("uint flags = UnifiedRT::kRay" + CARET)).keySet());
    }

    @Test
    public void namespaceMembersAreNotGlobalNames() {
        Map<String, HlslCompletionAnalysis.Kind> global = kinds(analyze("float x = " + CARET));
        assertEquals(HlslCompletionAnalysis.Kind.NAMESPACE, global.get("UnifiedRT"));
        assertFalse(global.toString(), global.containsKey("Hit"));
        assertFalse(global.toString(), global.containsKey("kRayFlagNone"));
        assertTrue("no qualified name is offered on its own: " + global,
                global.keySet().stream().noneMatch(name -> name.contains("::")));
        assertTrue("a macro of the package is still offered", global.containsKey("UNIFIED_RT_GET_ACCEL_STRUCT"));
    }

    /** UnifiedRT::Hit hit; hit. offers the fields and the methods of the structure. */
    @Test
    public void aVariableOfAQualifiedTypeHasItsMembers() {
        Map<String, HlslCompletionAnalysis.Kind> members =
                kinds(analyze("UnifiedRT::Hit hit;\n    float d = hit." + CARET));
        assertEquals(HlslCompletionAnalysis.Kind.FIELD, members.get("instanceID"));
        assertEquals(HlslCompletionAnalysis.Kind.FIELD, members.get("hitDistance"));
        assertEquals(HlslCompletionAnalysis.Kind.FUNCTION, members.get("IsValid"));
    }

    /** The Hit TraceRayClosestHit returns is the namespace's own, written there without UnifiedRT::. */
    @Test
    public void theResultOfANamespacedFunctionHasItsMembers() {
        Map<String, HlslCompletionAnalysis.Kind> members = kinds(analyze(
                "UnifiedRT::Ray ray;\n    float d = UnifiedRT::TraceRayClosestHit(dispatchInfo, accel, 0xFF, ray, 0)." + CARET));
        assertTrue(members.toString(), members.containsKey("hitDistance"));
        assertTrue(members.toString(), members.containsKey("IsValid"));
    }

    @Test
    public void aNamespacedFunctionShowsItsParameters() {
        HlslCompletionAnalysis.CallInfo call = callAt("UnifiedRT::Hit hit = UnifiedRT::TraceRayClosestHit(dispatchInfo, " + CARET + ");");
        assertNotNull("UnifiedRT::TraceRayClosestHit has parameter info", call);
        assertEquals(List.of("TraceRayClosestHit(DispatchInfo dispatchInfo, RayTracingAccelStruct accelStruct, "
                + "uint instanceMask, Ray ray, uint rayFlags)"),
                call.signatures().stream().map(HlslCompletionAnalysis.Signature::label).toList());
        assertEquals(1, call.parameterIndex());
        assertNull("the function is not a global one", callAt("TraceRayClosestHit(" + CARET + ");"));
    }

    @Test
    public void methodsOfAStructureShowTheirParameters() {
        HlslCompletionAnalysis.CallInfo valid = callAt("UnifiedRT::Hit hit;\n    bool b = hit.IsValid(" + CARET + ");");
        assertNotNull(valid);
        assertEquals("IsValid()", valid.signatures().get(0).label());
        HlslCompletionAnalysis.CallInfo invalid = callAt("UnifiedRT::Hit hit = UnifiedRT::Hit::Invalid(" + CARET + ");");
        assertNotNull("a static method is named through its structure", invalid);
        assertEquals("Invalid()", invalid.signatures().get(0).label());
    }

    /** A function with a qualified return type used to be no function at all, as UnifiedRT::Ray Forward(...). */
    @Test
    public void aFunctionWithAQualifiedReturnTypeIsAFunction() {
        String declared = "UnifiedRT::Ray Forward(float3 origin) { UnifiedRT::Ray ray = (UnifiedRT::Ray)0; return ray; }\n";
        String text = declared + "void Use()\n{\n    float3 o = Forward(0)." + CARET + "\n}\n";
        HlslCompletionAnalysis.Result result = HlslCompletionAnalysis.analyze(
                text.replace(CARET, ""), text.indexOf(CARET), INCLUDES);
        assertTrue(kinds(result).toString(), kinds(result).containsKey("origin"));
        String call = declared + "void Use()\n{\n    Forward(" + CARET + ");\n}\n";
        HlslCompletionAnalysis.CallInfo info = HlslCompletionAnalysis.callAt(
                call.replace(CARET, ""), call.indexOf(CARET), INCLUDES);
        assertNotNull(info);
        assertEquals("Forward(float3 origin)", info.signatures().get(0).label());
    }

    @Test
    public void typingAVariableNameAfterAQualifiedTypeSuggestsNothing() {
        assertEquals(HlslCompletionAnalysis.Context.SUPPRESSED, analyze("UnifiedRT::Hit h" + CARET).context());
    }

    @Test
    public void hoverDescribesANamespacedDeclaration() {
        HlslCompletionAnalysis.Declaration trace = HlslCompletionAnalysis.declarationOf(
                "TraceRayClosestHit", "", 0, INCLUDES);
        assertNotNull(trace);
        assertEquals("// Traces a ray and returns the closest hit", trace.comment());
        assertTrue(trace.text(), trace.text().startsWith("Hit TraceRayClosestHit(DispatchInfo dispatchInfo"));
        HlslCompletionAnalysis.Declaration valid = HlslCompletionAnalysis.declarationOf("IsValid", "", 0, INCLUDES);
        assertNotNull("a method is described too", valid);
        assertTrue(valid.text(), valid.text().startsWith("bool IsValid()"));
    }
}
