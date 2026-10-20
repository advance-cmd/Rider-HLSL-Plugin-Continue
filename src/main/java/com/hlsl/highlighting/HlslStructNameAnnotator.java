package com.hlsl.highlighting;

import com.hlsl.HlslLanguage;
import com.hlsl.completion.HlslIncludeIndex;
import com.hlsl.psi.HlslTokenTypes;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highlights struct/cbuffer/tbuffer/class/interface/enum names and typedef aliases
 * at every usage site, including names declared in transitively #included files and, in a helper,
 * names the shaders including it declare before their #include of it.
 */
public class HlslStructNameAnnotator implements Annotator {

    private static final Pattern DECLARATION = Pattern.compile(
            "\\b(?:struct|cbuffer|tbuffer|class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b"
    );

    private static final Pattern TYPEDEF = Pattern.compile(
            "\\btypedef\\s+[A-Za-z_][\\w<>,\\s]*?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^\\]]*\\])?\\s*;"
    );

    private static final Key<CachedValue<Set<String>>> STRUCT_NAMES_KEY =
            Key.create("HLSL_STRUCT_NAMES");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!element.getLanguage().is(HlslLanguage.INSTANCE)) return;

        IElementType type = element.getNode().getElementType();
        if (type != HlslTokenTypes.IDENTIFIER
                && type != HlslTokenTypes.FUNCTION_CALL) {
            return;
        }

        Set<String> structNames = getStructNames(element.getContainingFile());
        if (structNames.contains(element.getText())) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .textAttributes(HlslSyntaxHighlighter.STRUCT_NAME)
                    .create();
        }
    }

    private static Set<String> getStructNames(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, STRUCT_NAMES_KEY, () -> {
            String text = file.getText();
            Set<String> names = new HashSet<>();
            collectFromText(text, names);
            // The sources completion reads as well: what the file includes and, for a helper that other shaders
            // include, what those shaders declare above their #include of it.
            VirtualFile vf = file.getVirtualFile();
            HlslIncludeIndex index = file.getProject().getService(HlslIncludeIndex.class);
            if (vf != null && index != null) {
                for (CharSequence source : index.collectVisibleSources(vf, text)) collectFromText(source, names);
            }
            // Those other files are covered by the PSI modification count too: an edit to any of them advances it
            // once committed, saved or not, and so does a change on disk to one that was never opened.
            return CachedValueProvider.Result.create(names, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    private static void collectFromText(CharSequence content, Set<String> names) {
        Matcher m = DECLARATION.matcher(content);
        while (m.find()) names.add(m.group(1));
        Matcher t = TYPEDEF.matcher(content);
        while (t.find()) names.add(t.group(1));
    }
}
