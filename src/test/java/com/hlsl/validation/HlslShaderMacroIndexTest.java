package com.hlsl.validation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

/**
 * Detection has to work inside a real IDE project, not only on a string. The project and its services are
 * shared by the tests of this class, so the cached scan is dropped first and the assertions only state what
 * must be present.
 */
public class HlslShaderMacroIndexTest extends BasePlatformTestCase {

    /** Shaped after the real LearnDX12 main.cpp, including a runtime-valued entry. */
    private static final String MAIN_CPP = """
            const std::string extraLights = std::to_string(mManyLights ? kExtraLights : 0);
            const D3D_SHADER_MACRO lightDefines[] = {
                {"NUM_DIR_LIGHTS", "1"},
                {"NUM_POINT_LIGHTS", "1"},
                {"NUM_SPOT_LIGHTS", "1"},
                {"NUM_EXTRA_LIGHTS", extraLights.c_str()},
                {"FOG", "1"},
                {nullptr, nullptr}
            };
            """;

    private static final String SHADER_USING_MACROS =
            "static const uint k = NUM_DIR_LIGHTS + NUM_POINT_LIGHTS + NUM_SPOT_LIGHTS;\n"
                    + "#ifdef FOG\n#endif\n";

    public void testMacrosAreDetectedFromTheProjectsBuildSources() {
        myFixture.addFileToProject("main.cpp", MAIN_CPP);
        VirtualFile shader = myFixture.addFileToProject("HLSL/Deferred.hlsl",
                "#include \"LightingUtil.hlsl\"\n").getVirtualFile();
        HlslShaderMacroIndex index = getProject().getService(HlslShaderMacroIndex.class);
        assertNotNull("the macro index project service is missing", index);
        index.invalidate();

        // The annotator collects information inside a read action, which the project file index requires.
        List<String> definitions = ReadAction.compute(() -> index.definitionsFor(shader, SHADER_USING_MACROS));
        assertTrue("NUM_DIR_LIGHTS=1 must be detected, got " + definitions, definitions.contains("NUM_DIR_LIGHTS=1"));
        assertTrue("NUM_SPOT_LIGHTS=1 must be detected, got " + definitions, definitions.contains("NUM_SPOT_LIGHTS=1"));
        assertTrue("a macro the build defines must be passed even when only #ifdef mentions it: " + definitions,
                definitions.contains("FOG=1"));
        assertFalse("a runtime value cannot become a -D argument: " + definitions,
                definitions.stream().anyMatch(definition -> definition.startsWith("NUM_EXTRA_LIGHTS")));

        assertTrue("macros the shader never mentions must not change the compile: "
                        + ReadAction.compute(() -> index.definitionsFor(shader, "float x = 1.0f;")),
                ReadAction.compute(() -> index.definitionsFor(shader, "float x = 1.0f;")).isEmpty());
        assertFalse("the settings page shows what was detected", index.cachedDefinitions().isEmpty());
    }
}
