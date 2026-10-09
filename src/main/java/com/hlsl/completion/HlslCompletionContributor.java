package com.hlsl.completion;

import com.hlsl.HlslLanguage;
import com.hlsl.lexer.HlslLexer;
import com.hlsl.parameterinfo.HlslParameterInfoHandler;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HlslCompletionContributor extends CompletionContributor {

    public HlslCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(HlslLanguage.INSTANCE),
                new HlslCompletionProvider());
    }

    private static class HlslCompletionProvider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiFile file = parameters.getOriginalFile();
            if (file.getLanguage() != HlslLanguage.INSTANCE) return;

            // HLSL names are CamelCase (Texture2D, InvViewProj, GetDimensions), so a lowercase prefix such
            // as "gPass.inv" must still match them. Relying on the platform's matcher silently dropped every
            // candidate under a case-sensitive setting, which looked like "no completion at all".
            CompletionResultSet lookup = caseInsensitive(result);

            CharSequence source = file.getViewProvider().getDocument() != null
                    ? file.getViewProvider().getDocument().getCharsSequence() : file.getViewProvider().getContents();
            int offset = parameters.getOffset();
            if (HlslCompletionAnalysis.isSuppressed(source, offset)) {
                stop(result, lookup);
                return;
            }

            HlslIncludeIndex index = file.getProject().getService(HlslIncludeIndex.class);
            List<CharSequence> includes = index != null && file.getVirtualFile() != null
                    ? index.collectTransitiveSources(file.getVirtualFile()) : List.of();
            HlslCompletionAnalysis.Result analysis = HlslCompletionAnalysis.analyze(source, offset, includes);
            if (analysis.context() == HlslCompletionAnalysis.Context.SUPPRESSED) {
                stop(result, lookup);
                return;
            }
            if (analysis.context() == HlslCompletionAnalysis.Context.SEMANTIC) {
                for (String semantic : HlslLexer.getSemantics()) {
                    lookup.addElement(LookupElementBuilder.create(semantic).withIcon(AllIcons.Nodes.Annotationtype));
                }
                stop(result, lookup);
                return;
            }
            if (analysis.context() == HlslCompletionAnalysis.Context.MEMBER) {
                for (HlslCompletionAnalysis.Candidate member : analysis.candidates()) {
                    LookupElementBuilder element = LookupElementBuilder.create(member.name())
                            .withTypeText(member.type(), true);
                    if (member.kind() == HlslCompletionAnalysis.Kind.FUNCTION) {
                        element = element.withIcon(AllIcons.Nodes.Method)
                                .withTailText("()", true)
                                .withInsertHandler(PAREN_INSERT);
                    } else {
                        element = element.withIcon(AllIcons.Nodes.Field);
                    }
                    lookup.addElement(element);
                }
                stop(result, lookup);
                return;
            }

            // Static vocabulary. A keyword that also has a live template is left out: two entries with an
            // identical lookup string are ordered by the platform without consulting this plugin, so the bare
            // keyword would shadow the `struct`/`cbuffer` templates, which expand to the whole declaration.
            Set<String> added = new HashSet<>();
            for (HlslCompletionAnalysis.Candidate symbol : analysis.candidates()) {
                if (!added.add(symbol.name())) continue;
                LookupElementBuilder element = LookupElementBuilder.create(symbol.name()).withTypeText(symbol.type(), true);
                if (symbol.kind() == HlslCompletionAnalysis.Kind.FUNCTION) {
                    element = element.withIcon(AllIcons.Nodes.Method).withTailText("()", true).withInsertHandler(PAREN_INSERT);
                } else if (symbol.kind() == HlslCompletionAnalysis.Kind.TYPE) {
                    element = element.withIcon(AllIcons.Nodes.Class);
                } else {
                    element = element.withIcon(AllIcons.Nodes.Variable);
                }
                lookup.addElement(element);
            }
            for (String kw : HlslLexer.getKeywords()) {
                if (!added.add(kw) || hasLiveTemplate(kw)) continue;
                lookup.addElement(LookupElementBuilder.create(kw)
                        .withBoldness(true)
                        .withIcon(AllIcons.Nodes.Favorite));
            }
            for (String t : HlslLexer.getTypeKeywords()) {
                if (!added.add(t)) continue;
                lookup.addElement(LookupElementBuilder.create(t)
                        .withIcon(AllIcons.Nodes.Class));
            }
            for (String fn : HlslLexer.getBuiltinFunctions()) {
                if (!added.add(fn)) continue;
                lookup.addElement(LookupElementBuilder.create(fn)
                        .withIcon(AllIcons.Nodes.Method)
                        .withTailText("()", true)
                        .withInsertHandler(PAREN_INSERT));
            }
        }
    }

    /** Abbreviations of the live templates this plugin ships, see resources/liveTemplates/HLSL.xml. */
    private static final Set<String> TEMPLATE_ABBREVIATIONS = Set.of("struct", "cbuffer");

    /**
     * True when a shipped live template with this abbreviation is registered, in which case offering the bare
     * keyword as well would put a worse entry first.
     */
    private static boolean hasLiveTemplate(String keyword) {
        if (!TEMPLATE_ABBREVIATIONS.contains(keyword)) return false;
        for (TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
            if (keyword.equals(template.getKey())) return true;
        }
        return false;
    }

    private static CompletionResultSet caseInsensitive(CompletionResultSet result) {
        String prefix = result.getPrefixMatcher().getPrefix();
        return prefix.isEmpty() ? result : result.withPrefixMatcher(new CaseInsensitivePrefixMatcher(prefix));
    }

    /**
     * The platform's matcher is case sensitive, which hides every CamelCase HLSL name (InvViewProj,
     * GetDimensions, Texture2D) as soon as the typed prefix uses different capitalisation.
     */
    private static final class CaseInsensitivePrefixMatcher extends PrefixMatcher {
        CaseInsensitivePrefixMatcher(String prefix) {
            super(prefix);
        }

        @Override
        public boolean prefixMatches(@NotNull String name) {
            return name.regionMatches(true, 0, myPrefix, 0, myPrefix.length());
        }

        @Override
        public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String newPrefix) {
            return new CaseInsensitivePrefixMatcher(newPrefix);
        }
    }

    /**
     * Marks the completion as finished on both the platform's result set and the re-wrapped one, because the
     * platform inspects its own instance to decide whether later contributors may still add variants.
     */
    private static void stop(CompletionResultSet original, CompletionResultSet target) {
        target.stopHere();
        if (target != original) original.stopHere();
    }

    private static final InsertHandler<LookupElement> PAREN_INSERT = (InsertionContext ctx, LookupElement item) -> {
        int offset = ctx.getTailOffset();
        CharSequence text = ctx.getDocument().getCharsSequence();
        int next = offset;
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
        if (ctx.getCompletionChar() == '(') ctx.setAddCompletionChar(false);
        if (next < text.length() && text.charAt(next) == '(') {
            ctx.getEditor().getCaretModel().moveToOffset(next + 1);
        } else {
            ctx.getDocument().insertString(offset, "()");
            ctx.getEditor().getCaretModel().moveToOffset(offset + 1);
        }
        showArgumentList(ctx);
    };

    /**
     * Completing a function name creates the argument list without typing a character, so the typed handler
     * never sees it: the parameter popup has to be requested here, exactly as completing a Java method does.
     */
    private static void showArgumentList(InsertionContext ctx) {
        Project project = ctx.getProject();
        Editor editor = ctx.getEditor();
        PsiFile file = ctx.getFile();
        // The document is still being modified while the lookup finishes, so ask for the hint afterwards.
        ApplicationManager.getApplication().invokeLater(
                () -> HlslParameterInfoHandler.showAt(project, editor, file), ModalityState.current());
    }
}
