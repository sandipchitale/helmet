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
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.components.BorderLayoutPanel;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class HelmDiffSelectedManifestAction extends AnAction {
    private final KubernetesClient kubernetesClient;

    private final BorderLayoutPanel sideBySidePanel = new BorderLayoutPanel();

    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList1 = new JBList<>();
    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList2 = new JBList<>();

    public HelmDiffSelectedManifestAction() {
        this.kubernetesClient = new KubernetesClientBuilder().build();

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
        List<NamespaceSecretReleaseRevision> namespaceStringStringNamespaceSecretReleaseRevisionSet = new ArrayList<>();
        kubernetesClient
                .namespaces()
                .list()
                .getItems()
                .forEach((Namespace namespace) -> {
                    kubernetesClient
                            .secrets()
                            .inNamespace(namespace.getMetadata().getName())
                            .list()
                            .getItems()
                            .stream()
                            .filter(secret -> {
                                Matcher matcher = Constants.helmSecretNamePattern.matcher(secret.getMetadata().getName());
                                return (matcher.matches());
                            })
                            .forEach(secret -> {
                                Matcher matcher = Constants.helmSecretNamePattern.matcher(secret.getMetadata().getName());
                                if (matcher.matches()) {
                                    String release = matcher.group(1);
                                    String revision = matcher.group(2);
                                    namespaceStringStringNamespaceSecretReleaseRevisionSet.add(new NamespaceSecretReleaseRevision(namespace, secret, release, revision));
                                }
                            });
                });

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
                    showSelectedManifestReleaseRevisionDiff(e.getProject(), namespaceSecretReleaseRevision1, namespaceSecretReleaseRevision2);
                }
            }
        } finally {
            // Remove listeners
            namespaceSecretReleaseRevisionList1.removeListSelectionListener(adjustOkActionState);
            namespaceSecretReleaseRevisionList2.removeListSelectionListener(adjustOkActionState);
        }
    }

    private static void showSelectedManifestReleaseRevisionDiff(Project project,
                                                                NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision1,
                                                                NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision2) {

        HelmReleaseRevisionAccessor helmReleaseRevisionAccessor1 = new HelmReleaseRevisionAccessor(namespaceSecretStringStringNamespaceSecretReleaseRevision1);
        String title1 = helmReleaseRevisionAccessor1.getTitle();

        Map<String, String> ManifestsMap1 = helmReleaseRevisionAccessor1.getManifestsMap();

        if (ManifestsMap1.isEmpty()) {
            return;
        }

        HelmReleaseRevisionAccessor helmReleaseRevisionAccessor2 = new HelmReleaseRevisionAccessor(namespaceSecretStringStringNamespaceSecretReleaseRevision2);
        String title2 = helmReleaseRevisionAccessor2.getTitle();

        Map<String, String> ManifestsMap2 = helmReleaseRevisionAccessor2.getManifestsMap();

        if (ManifestsMap2.isEmpty()) {
            return;
        }

        JBList<String> ManifestsList1 = new JBList<>();

        ManifestsList1.setModel(JBList.createDefaultListModel(ManifestsMap1.keySet()));
        ManifestsList1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JBList<String> ManifestsList2 = new JBList<>();

        ManifestsList2.setModel(JBList.createDefaultListModel(ManifestsMap2.keySet()));
        ManifestsList2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel splitPane = new JPanel(new GridLayout(1, 2, 5, 5));
        splitPane.add(new JScrollPane(ManifestsList1));
        splitPane.add(new JScrollPane(ManifestsList2));

        DialogBuilder builder = new DialogBuilder(project);
        builder.setCenterPanel(splitPane);
        builder.setDimensionServiceKey("SelectManifestsForDiff");
        builder.setTitle("Select Manifest from Each Release Revision for Diff");
        builder.removeAllActions();

        builder.addCancelAction();

        builder.addOkAction();
        builder.setOkActionEnabled(false);

        ListSelectionListener adjustOkActionState = (ListSelectionEvent listSelectionEvent) -> {
            builder.setOkActionEnabled(
                    ManifestsList1.getSelectedValue() != null
                            && ManifestsList2.getSelectedValue() != null);
        };

        ManifestsList1.addListSelectionListener(adjustOkActionState);
        ManifestsList2.addListSelectionListener(adjustOkActionState);

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

            String selectedManifest1 = ManifestsList1.getSelectedValue();
            String selectedManifest2 = ManifestsList2.getSelectedValue();

            DiffContent ManifestsContent1 = diffContentFactory.create(project,
                    ManifestsMap1.get(selectedManifest1));
            DiffContent ManifestsContent2 = diffContentFactory.create(project,
                    ManifestsMap2.get(selectedManifest2));
            ManifestsContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            ManifestsContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            SimpleDiffRequest ManifestsDiffRequest = new SimpleDiffRequest(selectedManifest1 + title1 + " vs " + selectedManifest2 + title2,
                    ManifestsContent1,
                    ManifestsContent2,
                    selectedManifest1 + title1 + ".yaml",
                    selectedManifest2 + title2 + ".yaml");
            diffManager.showDiff(project, ManifestsDiffRequest);

            fileEditorManager.closeFile(sacrificeVirtualFile);
        }
    }

}
