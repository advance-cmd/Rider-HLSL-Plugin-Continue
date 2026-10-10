package com.hlsl.editor;

import com.hlsl.HlslFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/** Copy and cut have to keep working: the paste processor takes part in copy as well. */
public class HlslClipboardTest extends BasePlatformTestCase {

    public void testCopyPutsTheSelectionOnTheClipboard() {
        myFixture.configureByText(HlslFileType.INSTANCE,
                "float3 <selection>p = WorldPosAt(pixel);</selection>\n");
        myFixture.performEditorAction(IdeActions.ACTION_COPY);
        String copied = clipboardText();
        assertTrue("the selection must be on the clipboard, got: " + copied,
                copied != null && copied.contains("WorldPosAt(pixel)"));
        assertEquals("the document must not change on copy",
                "float3 p = WorldPosAt(pixel);\n", myFixture.getEditor().getDocument().getText());
    }

    public void testCutRemovesTheSelectionAndPutsItOnTheClipboard() {
        myFixture.configureByText(HlslFileType.INSTANCE,
                "float3 <selection>p = WorldPosAt(pixel);</selection>\n");
        myFixture.performEditorAction(IdeActions.ACTION_CUT);
        String copied = clipboardText();
        assertTrue("the selection must be on the clipboard, got: " + copied,
                copied != null && copied.contains("WorldPosAt(pixel)"));
        assertEquals("the cut text must be gone from the document",
                "float3 \n", myFixture.getEditor().getDocument().getText());
    }

    /** Copying has to survive being asked for a whole line, which is what Ctrl+C does with no selection. */
    public void testCopyingAWholeLine() {
        myFixture.configureByText(HlslFileType.INSTANCE,
                "float3 p = WorldPosAt(pixel);\nfloat3 n = normalize(p);\n");
        myFixture.getEditor().getSelectionModel().setSelection(0, 31);
        myFixture.performEditorAction(IdeActions.ACTION_COPY);
        String copied = clipboardText();
        assertNotNull("copy must put something on the clipboard", copied);
        assertTrue("got: " + copied, copied.contains("WorldPosAt"));
    }

    /** Copying with an empty selection must not throw: it used to before the flavour was fixed. */
    public void testCopyingWithAnEmptySelection() {
        myFixture.configureByText(HlslFileType.INSTANCE, "float3 p = WorldPosAt(pixel);\n");
        myFixture.getEditor().getSelectionModel().setSelection(0, 0);
        myFixture.performEditorAction(IdeActions.ACTION_COPY);
        assertEquals("the document must not change", "float3 p = WorldPosAt(pixel);\n",
                myFixture.getEditor().getDocument().getText());
    }

    /**
     * The flavour of the processor's marker must not look like plain text: the clipboard manager reads the
     * clipboard as a string and casts whatever comes back, which blew up copy and cut in the IDE.
     */
    public void testTheMarkerFlavourIsNotTheTextFlavour() {
        HlslPasteIndentProcessor.Marker marker = new HlslPasteIndentProcessor.Marker();
        assertFalse("the marker must not claim to be the clipboard's text",
                DataFlavor.stringFlavor.equals(marker.getFlavor()));
        assertFalse(DataFlavor.stringFlavor.isMimeTypeEqual(marker.getFlavor()));
        assertTrue("the flavour has to name the marker class, got: " + marker.getFlavor().getMimeType(),
                marker.getFlavor().getMimeType().startsWith(DataFlavor.javaJVMLocalObjectMimeType));
    }

    private String clipboardText() {
        try {
            Transferable contents = CopyPasteManager.getInstance().getContents();
            return contents == null ? null : (String) contents.getTransferData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            throw new AssertionError("clipboard unreadable: " + e, e);
        }
    }
}
