package sandipchitale.helmet;

import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Objects;
import java.util.Set;

public class HelmGetAction extends AnAction {

    private final WhatPanel whatPanel = WhatPanel.build();

    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList = new JBList<>();


    public HelmGetAction() {
        namespaceSecretReleaseRevisionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        namespaceSecretReleaseRevisionList.setCellRenderer(ReleaseRevisionNamespaceDefaultListCellRenderer.INSTANCE);

        whatPanel.add(new JScrollPane(namespaceSecretReleaseRevisionList), BorderLayout.CENTER);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Set<NamespaceSecretReleaseRevision> namespaceStringStringNamespaceSecretReleaseRevisionSet =
                HelmReleaseRevisionSecretsAccessor.getNamespaceSecretReleaseRevisionSetAllNamespaces();

        namespaceSecretReleaseRevisionList.setModel(JBList.createDefaultListModel(namespaceStringStringNamespaceSecretReleaseRevisionSet));

        DialogBuilder builder = new DialogBuilder(e.getProject());
        builder.setCenterPanel(whatPanel);
        builder.setDimensionServiceKey("SelectNamespaceHelmReleaseRevision");
        builder.setTitle("Select Helm Release.Revision [ Namespace ]");
        builder.removeAllActions();

        builder.addCancelAction();

        builder.addOkAction();
        builder.setOkActionEnabled(false);
        builder.setOkOperation(() -> {
            if (whatPanel.isAny()) {
                builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
            } else {
                Messages.showMessageDialog(
                        e.getProject(),
                        "Please select at least one of chart info, values, templates, manifests, hooks, notes for get",
                        "Select at Least One for Get",
                        Messages.getInformationIcon());
            }
        });

        ListSelectionListener adjustOkActionState = (ListSelectionEvent listSelectionEvent) ->
                builder.setOkActionEnabled(namespaceSecretReleaseRevisionList.getSelectedValue() != null);

        try {
            namespaceSecretReleaseRevisionList.addListSelectionListener(adjustOkActionState);
            boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
            if (isOk) {
                if (whatPanel.isAny()) {
                    NamespaceSecretReleaseRevision selectedValue = namespaceSecretReleaseRevisionList.getSelectedValue();
                    if (selectedValue != null) {
                        showReleaseRevision(e.getProject(), selectedValue, whatPanel);
                    }
                }
            }
        } finally {
            // Remove listener
            namespaceSecretReleaseRevisionList.removeListSelectionListener(adjustOkActionState);
        }

    }

    private static void showReleaseRevision(Project project,
                                            NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision,
                                            WhatPanel whatPanel) {
        FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

        EditorWindow currentWindow = fileEditorManager.getCurrentWindow();
        if (currentWindow != null) {
            fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);
            currentWindow = fileEditorManager.getCurrentWindow();
        }

        HelmReleaseRevisionAccessor helmReleaseRevisionAccessor = new HelmReleaseRevisionAccessor(namespaceSecretStringStringNamespaceSecretReleaseRevision);
        String title = helmReleaseRevisionAccessor.getTitle();

        // Chart Info
        if (whatPanel.isChartInfo()) {
            FileType fileType = FileTypeUtils.getFileType("YAML");
            LightVirtualFile charInfoLightVirtualFile = new LightVirtualFile(Constants.CHART_INFO + title,
                    fileType,
                    helmReleaseRevisionAccessor.getChartInfo());
            charInfoLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            charInfoLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(charInfoLightVirtualFile, true, true);
        }

        // Values
        if (whatPanel.isValues()) {
            FileType fileType = FileTypeUtils.getFileType("JSON");
            LightVirtualFile valuesLightVirtualFile = new LightVirtualFile(Constants.VALUES + title,
                    fileType,
                    helmReleaseRevisionAccessor.getValues());
            valuesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            valuesLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(valuesLightVirtualFile, true, true);
        }

        // Templates
        if (whatPanel.isTemplates()) {
            FileType fileType = FileTypeUtils.getFileType("Helm YAML template", "YAML");
            LightVirtualFile templatesvaluesLightVirtualFile = new LightVirtualFile(Constants.TEMPLATES + title,
                    fileType,
                    helmReleaseRevisionAccessor.getTemplates());
            templatesvaluesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            templatesvaluesLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(templatesvaluesLightVirtualFile, true, true);
        }

        // Manifest
        if (whatPanel.isManifests()) {
            FileType fileType = FileTypeUtils.getFileType("YAML");
            LightVirtualFile manifestLightVirtualFile = new LightVirtualFile(Constants.MANIFESTS + title,
                    fileType,
                    helmReleaseRevisionAccessor.getManifests());
            manifestLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            manifestLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(manifestLightVirtualFile, true, true);
        }

        // Hooks
        if (whatPanel.isHooks()) {
            FileType fileType = FileTypeUtils.getFileType("YAML");
            LightVirtualFile hooksLightVirtualFile = new LightVirtualFile(Constants.HOOKS + title,
                    fileType,
                    helmReleaseRevisionAccessor.getHooks());
            hooksLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            hooksLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(hooksLightVirtualFile, true, true);
        }

        // Notes
        if (whatPanel.isNotes()) {
            LightVirtualFile notesvaluesLightVirtualFile = new LightVirtualFile(Constants.NOTES + title,
                    PlainTextFileType.INSTANCE,
                    helmReleaseRevisionAccessor.getNotes());
            notesvaluesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            notesvaluesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(notesvaluesLightVirtualFile, true, true);
        }
    }
}
