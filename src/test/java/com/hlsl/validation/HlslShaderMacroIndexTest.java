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

    /** Shaped after a real C++ build file, including a runtime-valued entry. */
    private static final String MAIN_CPP = """
            const std::string extraLights = std::to_string(mManyLights ? kExtraLights : 0);
            const D3D_SHADER_MACRO lightDefines[] = {
                {"SUN_COUNT", "1"},
                {"LAMP_COUNT", "1"},
                {"SPOT_COUNT", "1"},
                {"EXTRA_COUNT", extraLights.c_str()},
                {"FOG", "1"},
                {nullptr, nullptr}
            };
            """;

    private static final String SHADER_USING_MACROS =
            "static const uint k = SUN_COUNT + LAMP_COUNT + SPOT_COUNT;\n"
                    + "#ifdef FOG\n#endif\n";

    public void testMacrosAreDetectedFromTheProjectsBuildSources() {
        myFixture.addFileToProject("main.cpp", MAIN_CPP);
        VirtualFile shader = myFixture.addFileToProject("HLSL/Composite.hlsl",
                "#include \"Shared.hlsl\"\n").getVirtualFile();
        HlslShaderMacroIndex index = getProject().getService(HlslShaderMacroIndex.class);
        assertNotNull("the macro index project service is missing", index);
        index.invalidate();

        // The annotator collects information inside a read action, which the project file index requires.
        List<String> definitions = ReadAction.compute(() -> index.definitionsFor(shader, SHADER_USING_MACROS));
        assertTrue("SUN_COUNT=1 must be detected, got " + definitions, definitions.contains("SUN_COUNT=1"));
        assertTrue("SPOT_COUNT=1 must be detected, got " + definitions, definitions.contains("SPOT_COUNT=1"));
        assertTrue("a macro the build defines must be passed even when only #ifdef mentions it: " + definitions,
                definitions.contains("FOG=1"));
        assertFalse("a runtime value cannot become a -D argument: " + definitions,
                definitions.stream().anyMatch(definition -> definition.startsWith("EXTRA_COUNT")));

        assertTrue("macros the shader never mentions must not change the compile: "
                        + ReadAction.compute(() -> index.definitionsFor(shader, "float x = 1.0f;")),
                ReadAction.compute(() -> index.definitionsFor(shader, "float x = 1.0f;")).isEmpty());
        assertFalse("the settings page shows what was detected", index.cachedDefinitions().isEmpty());
    }
}
