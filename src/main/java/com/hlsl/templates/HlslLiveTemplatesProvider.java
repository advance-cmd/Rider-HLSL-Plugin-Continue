package com.hlsl.templates;

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider;
import org.jetbrains.annotations.Nullable;

/** Ships the default HLSL live templates (see resources/liveTemplates/HLSL.xml). */
public final class HlslLiveTemplatesProvider implements DefaultLiveTemplatesProvider {

    @Override
    public String @Nullable [] getDefaultLiveTemplateFiles() {
        return new String[]{"/liveTemplates/HLSL"};
    }

    @Override
    public String @Nullable [] getHiddenLiveTemplateFiles() {
        return null;
    }
}
