package sandipchitale.helmet;

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

public class HelmGetSelectedHooksAction extends AnAction {

    private final KubernetesClient kubernetesClient;

    private final BorderLayoutPanel whatPanel = new BorderLayoutPanel();

    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList = new JBList<>();


    public HelmGetSelectedHooksAction() {
        this.kubernetesClient = new KubernetesClientBuilder().build();

        namespaceSecretReleaseRevisionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        namespaceSecretReleaseRevisionList.setCellRenderer(ReleaseRevisionNamespaceDefaultListCellRenderer.INSTANCE);

        whatPanel.add(new JScrollPane(namespaceSecretReleaseRevisionList), BorderLayout.CENTER);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<NamespaceSecretReleaseRevision> namespaceStringStringNamespaceSecretReleaseRevision4Set = new ArrayList<>();
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
                                    namespaceStringStringNamespaceSecretReleaseRevision4Set.add(new NamespaceSecretReleaseRevision(namespace, secret, release, revision));
                                }
                            });
                });

        namespaceSecretReleaseRevisionList.setModel(JBList.createDefaultListModel(namespaceStringStringNamespaceSecretReleaseRevision4Set));

        DialogBuilder builder = new DialogBuilder(e.getProject());
        builder.setCenterPanel(whatPanel);
        builder.setDimensionServiceKey("SelectNamespaceHelmReleaseRevision");
        builder.setTitle("Select Helm Release.Revision [ Namespace ]");
        builder.removeAllActions();

        builder.addCancelAction();

        builder.addOkAction();
        builder.setOkActionEnabled(false);

        ListSelectionListener adjustOkActionState = (ListSelectionEvent listSelectionEvent) -> {
            builder.setOkActionEnabled(namespaceSecretReleaseRevisionList.getSelectedValue() != null);
        };

        try {
            namespaceSecretReleaseRevisionList.addListSelectionListener(adjustOkActionState);
            boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
            if (isOk) {
                NamespaceSecretReleaseRevision selectedValue = namespaceSecretReleaseRevisionList.getSelectedValue();
                if (selectedValue != null) {
                    showReleaseRevision(e.getProject(), selectedValue);
                }
            }
        } finally {
            // Remove listener
            namespaceSecretReleaseRevisionList.removeListSelectionListener(adjustOkActionState);
        }

    }

    private static void showReleaseRevision(Project project,
                                            NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision) {
        HelmReleaseRevisionAccessor helmReleaseRevisionAccessor = new HelmReleaseRevisionAccessor(namespaceSecretStringStringNamespaceSecretReleaseRevision);
        String title = helmReleaseRevisionAccessor.getTitle();

        Map<String, String> hooksMap = helmReleaseRevisionAccessor.getHooksMap();

        if (hooksMap.isEmpty()) {
            return;
        }

        JBList<String> hooksList = new JBList<>();
        hooksList.setModel(JBList.createDefaultListModel(hooksMap.keySet()));
        hooksList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        DialogBuilder builder = new DialogBuilder(project);
        builder.setCenterPanel(new JScrollPane(hooksList));
        builder.setDimensionServiceKey("SelectHook");
        builder.setTitle("Select Hook");
        builder.removeAllActions();

        builder.addCancelAction();

        builder.addOkAction();
        builder.setOkActionEnabled(false);

        hooksList.addListSelectionListener((ListSelectionEvent listSelectionEvent) -> {
            builder.setOkActionEnabled(hooksList.getSelectedValue() != null);
        });

        boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
        if (isOk) {
            List<String> selectedHooks = hooksList.getSelectedValuesList();
            if (selectedHooks != null && !selectedHooks.isEmpty()) {
                FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

                EditorWindow currentWindow = fileEditorManager.getCurrentWindow();
                if (currentWindow != null) {
                    fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);
                }

                selectedHooks.forEach((String selectedHook) -> {
                    // Hooks
                    LightVirtualFile hooksvaluesLightVirtualFile = new LightVirtualFile("Hook: " + selectedHook + " of" + title,
                            PlainTextFileType.INSTANCE,
                            hooksMap.get(selectedHook));
                    hooksvaluesLightVirtualFile.setWritable(false);
                    // Figure out a way to set language for syntax highlighting based on file extension
                    hooksvaluesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                    fileEditorManager.openFile(hooksvaluesLightVirtualFile, true, true);
                });
            }
        }


    }
}
