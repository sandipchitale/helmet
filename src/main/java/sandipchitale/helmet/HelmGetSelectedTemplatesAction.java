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
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HelmGetSelectedTemplatesAction extends AnAction {
    private final BorderLayoutPanel whatPanel = new BorderLayoutPanel();

    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList = new JBList<>();


    public HelmGetSelectedTemplatesAction() {
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

        ListSelectionListener adjustOkActionState = (ListSelectionEvent listSelectionEvent) -> builder.setOkActionEnabled(namespaceSecretReleaseRevisionList.getSelectedValue() != null);

        try {
            namespaceSecretReleaseRevisionList.addListSelectionListener(adjustOkActionState);
            boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
            if (isOk) {
                NamespaceSecretReleaseRevision selectedValue = namespaceSecretReleaseRevisionList.getSelectedValue();
                if (selectedValue != null) {
                    showSelectedTemplatesOfReleaseRevision(e.getProject(), selectedValue);
                }
            }
        } finally {
            // Remove listener
            namespaceSecretReleaseRevisionList.removeListSelectionListener(adjustOkActionState);
        }

    }

    private static void showSelectedTemplatesOfReleaseRevision(Project project,
                                                               NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision) {
        HelmReleaseRevisionAccessor helmReleaseRevisionAccessor = new HelmReleaseRevisionAccessor(namespaceSecretStringStringNamespaceSecretReleaseRevision);
        String title = helmReleaseRevisionAccessor.getTitle();

        Map<String, String> templatesMap = helmReleaseRevisionAccessor.getTemplatesMap();

        if (templatesMap.isEmpty()) {
            return;
        }

        JBList<String> templatesList = new JBList<>();
        templatesList.setModel(JBList.createDefaultListModel(templatesMap.keySet()));
        templatesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        DialogBuilder builder = new DialogBuilder(project);
        builder.setCenterPanel(new JScrollPane(templatesList));
        builder.setDimensionServiceKey("SelectTemplate");
        builder.setTitle("Select Template");
        builder.removeAllActions();

        builder.addCancelAction();

        builder.addOkAction();
        builder.setOkActionEnabled(false);

        templatesList.addListSelectionListener((ListSelectionEvent listSelectionEvent) ->
                builder.setOkActionEnabled(templatesList.getSelectedValue() != null));

        boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
        if (isOk) {
            List<String> selectedTemplates = templatesList.getSelectedValuesList();
            if (selectedTemplates != null && !selectedTemplates.isEmpty()) {
                FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

                EditorWindow currentWindow = fileEditorManager.getCurrentWindow();
                if (currentWindow != null) {
                    fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);
                }

                selectedTemplates.forEach((String selectedTemplate) -> {
                    // Templates
                    // Figure out a way to set language for syntax highlighting based on file extension
                    FileType fileType = PlainTextFileType.INSTANCE;
                    if (selectedTemplate.endsWith(".txt")) {
                        fileType = FileTypeUtils.getFileType("Helm TEXT template");
                    } else if (selectedTemplate.endsWith(".tpl")) {
                        fileType = FileTypeUtils.getFileType("Go Template", "YAML");
                    } else {
                        fileType = FileTypeUtils.getFileType("Helm YAML template", "YAML");
                    }
                    LightVirtualFile templatesvaluesLightVirtualFile = new LightVirtualFile("Template: " + selectedTemplate + " of" + title,
                            fileType,
                            templatesMap.get(selectedTemplate));
                    templatesvaluesLightVirtualFile.setWritable(false);
                    templatesvaluesLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
                    fileEditorManager.openFile(templatesvaluesLightVirtualFile, true, true);
                });
            }
        }
    }
}
