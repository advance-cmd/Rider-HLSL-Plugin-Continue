package com.hlsl.documentation;

import com.hlsl.HlslLanguage;
import com.hlsl.validation.HlslDxcSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The system-value semantics and interpolation modifiers HLSL accepts after a colon, with a description for
 * each. The text comes from a resource bundle, so it is available in English and in Chinese and the two can
 * be switched from the settings page.
 */
public final class HlslSemantics {

    private static final HlslHintBundle TEXT = new HlslHintBundle("hlsl.semantics.semantics");

    /** Names HLSL defines as shader semantics or as interpolation modifiers. */
    private static final Map<String, Kind> NAMES = new LinkedHashMap<>();

    private enum Kind {
        /** A value the pipeline supplies or consumes, such as SV_Target. */
        SYSTEM_VALUE,
        /** A value passed between shader stages, such as TEXCOORD. */
        PARAMETER,
        /** How a value is interpolated between vertex and pixel stage. */
        INTERPOLATION,
        /** Where a resource or constant buffer is bound in the pipeline. */
        REGISTER
    }

    static {
        for (String name : new String[]{
                "SV_Target", "SV_Target0", "SV_Target1", "SV_Target2", "SV_Target3", "SV_Target4", "SV_Target5",
                "SV_Target6", "SV_Target7", "SV_Position", "SV_ClipDistance", "SV_CullDistance", "SV_Depth",
                "SV_DepthGreaterEqual", "SV_DepthLessEqual", "SV_Coverage", "SV_IsFrontFace",
                "SV_VertexID", "SV_InstanceID", "SV_PrimitiveID", "SV_RenderTargetArrayIndex", "SV_ViewportArrayIndex",
                "SV_ViewID", "SV_Barycentrics", "SV_StencilRef", "SV_InnerCoverage",
                "SV_DispatchThreadID", "SV_GroupID", "SV_GroupThreadID", "SV_GroupIndex",
                "SV_DomainLocation", "SV_OutputControlPointID", "SV_TessFactor", "SV_InsideTessFactor",
                "POSITION", "TEXCOORD", "TEXCOORD0", "TEXCOORD1", "TEXCOORD2", "TEXCOORD3",
                "COLOR", "COLOR0", "COLOR1", "NORMAL", "TANGENT", "BINORMAL", "BLENDINDICES", "BLENDWEIGHT",
                "PSIZE", "FOG", "VFACE", "VPOS", "DEPTH"
        }) {
            NAMES.put(name, name.startsWith("SV_") ? Kind.SYSTEM_VALUE : Kind.PARAMETER);
        }
        for (String name : new String[]{"linear", "centroid", "nointerpolation", "noperspective", "sample"}) {
            NAMES.put(name, Kind.INTERPOLATION);
        }
        NAMES.put("register", Kind.REGISTER);
    }

    private HlslSemantics() {
    }

    /** True when the name is a semantic this plugin can explain. */
    public static boolean isKnown(@NotNull String name) {
        return NAMES.containsKey(name);
    }

    /**
     * True when the element is a semantics word: a known name, inside a declaration, after a colon on its
     * own line. Everything else, including a variable that happens to be called TEXCOORD0, is not one.
     */
    public static boolean isSemanticsWord(@Nullable PsiElement element) {
        if (element == null) return false;
        PsiFile file = element.getContainingFile();
        if (file == null || file.getLanguage() != HlslLanguage.INSTANCE) return false;
        String name = element.getText();
        if (name.isEmpty() || !isKnown(name)) return false;
        return followsColon(file, element.getTextRange().getStartOffset());
    }

    /**
     * True when the identifier comes after a colon on its own line. A semicolon or a brace before it means
     * the colon belongs to an earlier statement, which is how a name used as a variable is rejected.
     */
    private static boolean followsColon(@NotNull PsiFile file, int offset) {
        CharSequence text = file.getViewProvider().getContents();
        int lineStart = offset;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        for (int i = offset - 1; i >= lineStart; i--) {
            if (text.charAt(i) == ':') return true;
            if (text.charAt(i) == ';' || text.charAt(i) == '{' || text.charAt(i) == '}') return false;
        }
        return false;
    }

    /**
     * The documentation of a semantic name in the configured language, or {@code null} when the name is not
     * a semantic or the hints are switched off.
     */
    public static @Nullable String documentation(@NotNull String name) {
        if (!HlslDxcSettings.getInstance().isSemanticsHintsEnabled()) return null;
        Kind kind = NAMES.get(name);
        if (kind == null) return null;
        String description = TEXT.text(name);
        if (description == null) return null;
        return "<html><b>" + StringUtil.escapeXmlEntities(name) + "</b> <i>"
                + StringUtil.escapeXmlEntities(kindName(kind)) + "</i><br>"
                + StringUtil.escapeXmlEntities(description) + "</html>";
    }

    /** The description alone, without markup, for the hover hint and for tests. */
    public static @Nullable String description(@NotNull String name) {
        return TEXT.text(name);
    }

    private static @NotNull String kindName(@NotNull Kind kind) {
        String key = "kind." + kind.name().toLowerCase(Locale.ROOT);
        String localized = TEXT.text(key);
        return localized != null ? localized : kind.name();
    }
}
