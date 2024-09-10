package sandipchitale.helmet;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
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
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Map;
import java.util.Set;

public class HelmDiffSelectedTemplateAction extends AnAction {
    private final BorderLayoutPanel sideBySidePanel = new BorderLayoutPanel();

    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList1 = new JBList<>();
    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList2 = new JBList<>();

    public HelmDiffSelectedTemplateAction() {

        JPanel splitPane = new JPanel(new GridLayout(1, 2, 5, 5));

        namespaceSecretReleaseRevisionList1.setCellRenderer(ReleaseRevisionNamespaceDefaultListCellRenderer.INSTANCE);
        namespaceSecretReleaseRevisionList1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        splitPane.add(new JScrollPane(namespaceSecretReleaseRevisionList1));

        namespaceSecretReleaseRevisionList2.setCellRenderer(ReleaseRevisionNamespaceDefaultListCellRenderer.INSTANCE);
        namespaceSecretReleaseRevisionList2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        splitPane.add(new JScrollPane(namespaceSecretReleaseRevisionList2));

        sideBySidePanel.add(splitPane, BorderLayout.CENTER);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Set<NamespaceSecretReleaseRevision> namespaceStringStringNamespaceSecretReleaseRevisionSet =
                HelmReleaseRevisionSecretsAccessor.getNamespaceSecretReleaseRevisionSetAllNamespaces();

        namespaceSecretReleaseRevisionList1.setListData(namespaceStringStringNamespaceSecretReleaseRevisionSet.toArray(new NamespaceSecretReleaseRevision[0]));
        namespaceSecretReleaseRevisionList2.setListData(namespaceStringStringNamespaceSecretReleaseRevisionSet.toArray(new NamespaceSecretReleaseRevision[0]));

        DialogBuilder builder = new DialogBuilder(e.getProject());

        builder.setCenterPanel(sideBySidePanel);
        builder.setDimensionServiceKey("SelectNamespaceHelmReleaseRevisionForDiff");
        builder.setTitle("Select Helm Release.Revisions [ Namespaces ] for Diff");
        builder.removeAllActions();

        builder.addCancelAction();

        builder.addOkAction();
        builder.setOkActionEnabled(false);
        builder.setOkOperation(() -> {
            NamespaceSecretReleaseRevision selectedValue1 = namespaceSecretReleaseRevisionList1.getSelectedValue();
            NamespaceSecretReleaseRevision selectedValue2 = namespaceSecretReleaseRevisionList2.getSelectedValue();
            if (selectedValue1.equals(selectedValue2)) {
                Messages.showMessageDialog(
                        e.getProject(),
                        "Please select different Release.Revision for diff",
                        "Select Different Release.Revisions for Diff",
                        Messages.getInformationIcon());
                return;
            }
            builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        });

        ListSelectionListener adjustOkActionState = (ListSelectionEvent listSelectionEvent) -> {
            builder.setOkActionEnabled(
                    namespaceSecretReleaseRevisionList1.getSelectedValue() != null
                    && namespaceSecretReleaseRevisionList2.getSelectedValue() != null);
        };

        try {
            namespaceSecretReleaseRevisionList1.addListSelectionListener(adjustOkActionState);
            namespaceSecretReleaseRevisionList2.addListSelectionListener(adjustOkActionState);

            boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
            if (isOk) {
                NamespaceSecretReleaseRevision namespaceSecretReleaseRevision1 = namespaceSecretReleaseRevisionList1.getSelectedValue();
                NamespaceSecretReleaseRevision namespaceSecretReleaseRevision2 = namespaceSecretReleaseRevisionList2.getSelectedValue();
                if (namespaceSecretReleaseRevision1 != null && namespaceSecretReleaseRevision2 != null) {
                    showSelectedTemplateReleaseRevisionDiff(e.getProject(), namespaceSecretReleaseRevision1, namespaceSecretReleaseRevision2);
                }
            }
        } finally {
            // Remove listeners
            namespaceSecretReleaseRevisionList1.removeListSelectionListener(adjustOkActionState);
            namespaceSecretReleaseRevisionList2.removeListSelectionListener(adjustOkActionState);
        }
    }

    private static void showSelectedTemplateReleaseRevisionDiff(Project project,
                                                                NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision1,
                                                                NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision2) {

        HelmReleaseRevisionAccessor helmReleaseRevisionAccessor1 = new HelmReleaseRevisionAccessor(namespaceSecretStringStringNamespaceSecretReleaseRevision1);
        String title1 = helmReleaseRevisionAccessor1.getTitle();

        Map<String, String> templatesMap1 = helmReleaseRevisionAccessor1.getTemplatesMap();

        if (templatesMap1.isEmpty()) {
            return;
        }

        HelmReleaseRevisionAccessor helmReleaseRevisionAccessor2 = new HelmReleaseRevisionAccessor(namespaceSecretStringStringNamespaceSecretReleaseRevision2);
        String title2 = helmReleaseRevisionAccessor2.getTitle();

        Map<String, String> templatesMap2 = helmReleaseRevisionAccessor2.getTemplatesMap();

        if (templatesMap2.isEmpty()) {
            return;
        }

        JBList<String> templatesList1 = new JBList<>();

        templatesList1.setModel(JBList.createDefaultListModel(templatesMap1.keySet()));
        templatesList1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JBList<String> templatesList2 = new JBList<>();

        templatesList2.setModel(JBList.createDefaultListModel(templatesMap2.keySet()));
        templatesList2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel splitPane = new JPanel(new GridLayout(1, 2, 5, 5));
        splitPane.add(new JScrollPane(templatesList1));
        splitPane.add(new JScrollPane(templatesList2));

        DialogBuilder builder = new DialogBuilder(project);
        builder.setCenterPanel(splitPane);
        builder.setDimensionServiceKey("SelectTemplatesForDiff");
        builder.setTitle("Select Template from Each Release Revision for Diff");
        builder.removeAllActions();

        builder.addCancelAction();

        builder.addOkAction();
        builder.setOkActionEnabled(false);

        ListSelectionListener adjustOkActionState = (ListSelectionEvent listSelectionEvent) -> {
            builder.setOkActionEnabled(
                    templatesList1.getSelectedValue() != null
                            && templatesList2.getSelectedValue() != null);
        };

        templatesList1.addListSelectionListener(adjustOkActionState);
        templatesList2.addListSelectionListener(adjustOkActionState);

        boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
        if (isOk) {
            FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

            // Sacrificial file
            LightVirtualFile sacrificeVirtualFile = new LightVirtualFile("_",
                    PlainTextFileType.INSTANCE,
                    "");
            sacrificeVirtualFile.setWritable(false);
            sacrificeVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(sacrificeVirtualFile, true);

            EditorWindow currentWindow = fileEditorManager.getCurrentWindow();
            fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);

            DiffManager diffManager = DiffManager.getInstance();
            DiffContentFactory diffContentFactory = DiffContentFactory.getInstance();

            String selectedTemplate1 = templatesList1.getSelectedValue();
            FileType fileType1 = PlainTextFileType.INSTANCE;
            if (selectedTemplate1.endsWith(".txt")) {
                fileType1 = FileTypeUtils.getFileType("Helm TEXT template");
            } else if (selectedTemplate1.endsWith(".tpl")) {
                fileType1 = FileTypeUtils.getFileType("Go Template", "YAML");
            } else {
                fileType1 = FileTypeUtils.getFileType("Helm YAML template", "YAML");
            }

            String selectedTemplate2 = templatesList2.getSelectedValue();
            FileType fileType2 = PlainTextFileType.INSTANCE;
            if (selectedTemplate2.endsWith(".txt")) {
                fileType2 = FileTypeUtils.getFileType("Helm TEXT template");
            } else if (selectedTemplate2.endsWith(".tpl")) {
                fileType2 = FileTypeUtils.getFileType("Go Template", "YAML");
            } else {
                fileType2 = FileTypeUtils.getFileType("Helm YAML template", "YAML");
            }

            DiffContent templatesContent1 = DiffUtils.createDiffContent(diffContentFactory,
                    project,
                    selectedTemplate1 + title1,
                    templatesMap1.get(selectedTemplate1),
                    fileType1);
            DiffContent templatesContent2 = DiffUtils.createDiffContent(diffContentFactory,
                    project,
                    selectedTemplate2 + title2,
                    templatesMap2.get(selectedTemplate2),
                    fileType2);
            templatesContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            templatesContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            SimpleDiffRequest templatesDiffRequest = new SimpleDiffRequest(selectedTemplate1 + title1 + " vs " + selectedTemplate2 + title2,
                    templatesContent1,
                    templatesContent2,
                    selectedTemplate1 + title1,
                    selectedTemplate2 + title2);
            diffManager.showDiff(project, templatesDiffRequest);

            fileEditorManager.closeFile(sacrificeVirtualFile);
        }
    }

}
