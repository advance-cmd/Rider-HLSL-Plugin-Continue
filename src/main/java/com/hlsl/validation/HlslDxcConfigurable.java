package com.hlsl.validation;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HlslDxcConfigurable implements Configurable {

    private TextFieldWithBrowseButton dxcPathField;
    private JTextField defaultProfileField;
    private JTextField defaultEntryPointField;
    private JTextField hlslVersionField;
    private JCheckBox enableValidationCheckBox;
    private JCheckBox validateIncludeFilesCheckBox;
    private JCheckBox treatWarningsAsErrorsCheckBox;
    private JTextField preprocessorDefinesField;
    private JCheckBox autoDetectBuildMacrosCheckBox;
    private JCheckBox extraClangWarningsCheckBox;
    private JTextField additionalArgsField;

    // Warning group checkboxes: flag name -> checkbox
    private final Map<String, JCheckBox> warningCheckBoxes = new LinkedHashMap<>();

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "HLSL / DXC";
    }

    @Override
    public @Nullable JComponent createComponent() {
        dxcPathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
        chooserDescriptor.setTitle("Select DXC Executable");
        chooserDescriptor.setDescription("Path to dxc or dxc.exe");
        dxcPathField.addBrowseFolderListener(new TextBrowseFolderListener(chooserDescriptor));

        defaultProfileField = new JTextField();
        defaultEntryPointField = new JTextField();
        hlslVersionField = new JTextField();
        enableValidationCheckBox = new JCheckBox("Enable DXC validation");
        validateIncludeFilesCheckBox = new JCheckBox("Compile include files independently (.hlsli, .fxh, .ush, .h, .inc)");
        validateIncludeFilesCheckBox.setToolTipText("Normally include files need a parent shader's macros and declarations. A file-local profile or entry pragma also opts a file in.");
        treatWarningsAsErrorsCheckBox = new JCheckBox("Treat warnings as errors (-WX)");
        preprocessorDefinesField = new JTextField();
        autoDetectBuildMacrosCheckBox = new JCheckBox("Detect build macros from the project (D3D_SHADER_MACRO tables)");
        autoDetectBuildMacrosCheckBox.setToolTipText("Shaders are compiled by the build system with macros the plugin cannot see; read them from the project's shader macro tables instead of reporting undeclared identifiers.");
        extraClangWarningsCheckBox = new JCheckBox("Enable clang's full warning set (-Weverything)");
        extraClangWarningsCheckBox.setToolTipText("Hundreds of clang warning groups, most of which fire on correct HLSL. Off by default.");
        additionalArgsField = new JTextField();

        // Warning group checkboxes
        warningCheckBoxes.put("unused-variable", new JCheckBox("Unused variables (-Wunused-variable)"));
        warningCheckBoxes.put("unused-parameter", new JCheckBox("Unused parameters (-Wunused-parameter)"));
        warningCheckBoxes.put("unreachable-code", new JCheckBox("Unreachable code (-Wunreachable-code)"));
        warningCheckBoxes.put("uninitialized", new JCheckBox("Uninitialized variables (-Wuninitialized)"));
        warningCheckBoxes.put("conversion", new JCheckBox("Implicit conversions and truncation (-Wconversion)"));
        warningCheckBoxes.put("effects-syntax", new JCheckBox("Effects syntax, deprecated (-Weffects-syntax)"));

        JPanel warningsPanel = new JPanel();
        warningsPanel.setLayout(new BoxLayout(warningsPanel, BoxLayout.Y_AXIS));
        warningsPanel.setBorder(BorderFactory.createTitledBorder("Extra warning groups (off by default)"));
        for (JCheckBox cb : warningCheckBoxes.values()) {
            warningsPanel.add(cb);
        }

        // Show auto-detected path as hint
        String autoDetected = HlslDxcSettings.autoDetectDxc();
        String hint = autoDetected != null
                ? "Leave empty to use auto-detected: " + autoDetected
                : "DXC not found in PATH, Windows SDK, or Vulkan SDK";

        return FormBuilder.createFormBuilder()
                .addLabeledComponent("DXC executable path:", dxcPathField)
                .addComponentToRightColumn(new JLabel("<html><small>" + hint + "</small></html>"))
                .addLabeledComponent("Default shader profile:", defaultProfileField)
                .addLabeledComponent("Default entry point:", defaultEntryPointField)
                .addLabeledComponent("HLSL version (-HV):", hlslVersionField)
                .addLabeledComponent("Preprocessor defines (-D):", preprocessorDefinesField)
                .addComponentToRightColumn(new JLabel("<html><small>Extra macros for this project, e.g. <code>NUM_DIR_LIGHTS=1;NUM_SPOT_LIGHTS=1</code>. These override detected ones.</small></html>"))
                .addComponent(autoDetectBuildMacrosCheckBox)
                .addComponentToRightColumn(new JLabel("<html><small>" + detectedMacrosHint() + "</small></html>"))
                .addLabeledComponent("Additional DXC arguments:", additionalArgsField)
                .addComponentToRightColumn(new JLabel("<html><small>Quote paths containing spaces, e.g. -I &quot;C:\\Shader Includes&quot;. Relative paths use the shader folder.</small></html>"))
                .addComponent(enableValidationCheckBox)
                .addComponent(validateIncludeFilesCheckBox)
                .addComponent(treatWarningsAsErrorsCheckBox)
                .addComponent(extraClangWarningsCheckBox)
                .addComponent(warningsPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    /**
     * Shows what the last detection pass found, because a missing macro silently changes which
     * {@code #ifdef} branches the compiler even looks at.
     */
    private static String detectedMacrosHint() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length == 0) return "Detected build macros: no project is open.";
        HlslShaderMacroIndex index = projects[0].getService(HlslShaderMacroIndex.class);
        List<String> detected = index == null ? List.of() : index.cachedDefinitions();
        if (detected.isEmpty()) {
            return "Detected build macros: nothing detected yet — open a shader first, or set them below.";
        }
        return "Detected build macros: " + String.join(", ", detected) + " (defines below win).";
    }

    @Override
    public boolean isModified() {
        HlslDxcSettings settings = HlslDxcSettings.getInstance();
        return !dxcPathField.getText().equals(settings.getDxcPath())
                || !defaultProfileField.getText().equals(settings.getDefaultProfile())
                || !defaultEntryPointField.getText().equals(settings.getDefaultEntryPoint())
                || !hlslVersionField.getText().equals(settings.getHlslVersion())
                || !preprocessorDefinesField.getText().equals(settings.getPreprocessorDefines())
                || !additionalArgsField.getText().equals(settings.getAdditionalArgs())
                || enableValidationCheckBox.isSelected() != settings.isEnableValidation()
                || validateIncludeFilesCheckBox.isSelected() != settings.isValidateIncludeFiles()
                || treatWarningsAsErrorsCheckBox.isSelected() != settings.isTreatWarningsAsErrors()
                || extraClangWarningsCheckBox.isSelected() != settings.isExtraClangWarnings()
                || autoDetectBuildMacrosCheckBox.isSelected() != settings.isAutoDetectBuildMacros()
                || warningCheckBoxes.get("unused-variable").isSelected() != settings.isWarnUnusedVariable()
                || warningCheckBoxes.get("unused-parameter").isSelected() != settings.isWarnUnusedParameter()
                || warningCheckBoxes.get("unreachable-code").isSelected() != settings.isWarnUnreachableCode()
                || warningCheckBoxes.get("uninitialized").isSelected() != settings.isWarnUninitializedVariable()
                || warningCheckBoxes.get("conversion").isSelected()
                        != (settings.isWarnConversion() || settings.isWarnImplicitTruncation())
                || warningCheckBoxes.get("effects-syntax").isSelected() != settings.isWarnEffectsSyntax();
    }

    @Override
    public void apply() {
        HlslDxcSettings settings = HlslDxcSettings.getInstance();
        settings.setDxcPath(dxcPathField.getText());
        settings.setDefaultProfile(defaultProfileField.getText());
        settings.setDefaultEntryPoint(defaultEntryPointField.getText());
        settings.setHlslVersion(hlslVersionField.getText());
        settings.setPreprocessorDefines(preprocessorDefinesField.getText());
        settings.setAdditionalArgs(additionalArgsField.getText());
        settings.setEnableValidation(enableValidationCheckBox.isSelected());
        settings.setValidateIncludeFiles(validateIncludeFilesCheckBox.isSelected());
        settings.setTreatWarningsAsErrors(treatWarningsAsErrorsCheckBox.isSelected());
        settings.setExtraClangWarnings(extraClangWarningsCheckBox.isSelected());
        settings.setAutoDetectBuildMacros(autoDetectBuildMacrosCheckBox.isSelected());
        // The defines just changed, so a cached macro scan must not keep describing the previous setup.
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            HlslShaderMacroIndex index = project.getService(HlslShaderMacroIndex.class);
            if (index != null) index.invalidate();
        }
        settings.setWarnUnusedVariable(warningCheckBoxes.get("unused-variable").isSelected());
        settings.setWarnUnusedParameter(warningCheckBoxes.get("unused-parameter").isSelected());
        settings.setWarnUnreachableCode(warningCheckBoxes.get("unreachable-code").isSelected());
        settings.setWarnUninitializedVariable(warningCheckBoxes.get("uninitialized").isSelected());
        settings.setWarnImplicitTruncation(false);
        settings.setWarnConversion(warningCheckBoxes.get("conversion").isSelected());
        settings.setWarnEffectsSyntax(warningCheckBoxes.get("effects-syntax").isSelected());
    }

    @Override
    public void reset() {
        HlslDxcSettings settings = HlslDxcSettings.getInstance();
        dxcPathField.setText(settings.getDxcPath());
        defaultProfileField.setText(settings.getDefaultProfile());
        defaultEntryPointField.setText(settings.getDefaultEntryPoint());
        hlslVersionField.setText(settings.getHlslVersion());
        preprocessorDefinesField.setText(settings.getPreprocessorDefines());
        additionalArgsField.setText(settings.getAdditionalArgs());
        enableValidationCheckBox.setSelected(settings.isEnableValidation());
        validateIncludeFilesCheckBox.setSelected(settings.isValidateIncludeFiles());
        treatWarningsAsErrorsCheckBox.setSelected(settings.isTreatWarningsAsErrors());
        extraClangWarningsCheckBox.setSelected(settings.isExtraClangWarnings());
        autoDetectBuildMacrosCheckBox.setSelected(settings.isAutoDetectBuildMacros());
        warningCheckBoxes.get("unused-variable").setSelected(settings.isWarnUnusedVariable());
        warningCheckBoxes.get("unused-parameter").setSelected(settings.isWarnUnusedParameter());
        warningCheckBoxes.get("unreachable-code").setSelected(settings.isWarnUnreachableCode());
        warningCheckBoxes.get("uninitialized").setSelected(settings.isWarnUninitializedVariable());
        warningCheckBoxes.get("conversion")
                .setSelected(settings.isWarnConversion() || settings.isWarnImplicitTruncation());
        warningCheckBoxes.get("effects-syntax").setSelected(settings.isWarnEffectsSyntax());
    }
}
