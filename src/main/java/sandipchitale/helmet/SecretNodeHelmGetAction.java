package sandipchitale.helmet;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1Secret;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

public class SecretNodeHelmGetAction extends AnAction {
    private final KubernetesClient kubernetesClient = new KubernetesClientBuilder().build();

    private final WhatPanel whatPanel = WhatPanel.build();
    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList = new JBList<>();

    public SecretNodeHelmGetAction() {
        namespaceSecretReleaseRevisionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        namespaceSecretReleaseRevisionList.setCellRenderer(ReleaseRevisionNamespaceDefaultListCellRenderer.INSTANCE);

        whatPanel.add(new JScrollPane(namespaceSecretReleaseRevisionList), BorderLayout.CENTER);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent actionEvent) {
        Project project = actionEvent.getProject();
        ToolWindow toolWindow = ToolWindowManager.getInstance(Objects.requireNonNull(project)).getToolWindow("Kubernetes Dashboard");
        KubernetesObject kubernetesObject = Utils.getKubernetesObject(actionEvent.getDataContext().getData(PlatformCoreDataKeys.SELECTED_ITEM));
        if (kubernetesObject instanceof V1Secret v1Secret) {
            Matcher matcher = Constants.helmSecretNamePattern.matcher(Objects.requireNonNull(Objects.requireNonNull(v1Secret.getMetadata()).getName()));
            if (matcher.matches()) {
                String release = matcher.group(1);
                String revision = matcher.group(2);
                Set<NamespaceSecretReleaseRevision> namespaceStringStringNamespaceSecretReleaseRevisionSet =
                        new HashSet<>();

                namespaceStringStringNamespaceSecretReleaseRevisionSet.add(new NamespaceSecretReleaseRevision(
                        v1Secret.getMetadata().getNamespace(),
                        null,
                        v1Secret,
                        release,
                        revision
                ));

                namespaceSecretReleaseRevisionList.setModel(JBList.createDefaultListModel(namespaceStringStringNamespaceSecretReleaseRevisionSet));
                namespaceSecretReleaseRevisionList.setSelectedIndex(0);

                DialogBuilder builder = new DialogBuilder(actionEvent.getProject());
                builder.setCenterPanel(whatPanel);
                builder.setDimensionServiceKey("SelectNamespaceHelmReleaseRevision");
                builder.setTitle("Select Helm Release.Revision [ Namespace ]");
                builder.removeAllActions();

                builder.addCancelAction();

                builder.addOkAction();
                builder.setOkActionEnabled(namespaceSecretReleaseRevisionList.getSelectedIndex() != -1);

                builder.setOkOperation(() -> {
                    if (whatPanel.isAny()) {
                        builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
                    } else {
                        Messages.showMessageDialog(
                                actionEvent.getProject(),
                                "Please select at least one of chart info, values, templates, manifests, hooks, notes for get",
                                "Select at Least One for Get",
                                Messages.getInformationIcon());
                    }
                });

                ListSelectionListener adjustOkActionState = (ListSelectionEvent listSelectionEvent) ->
                        builder.setOkActionEnabled(namespaceSecretReleaseRevisionList.getSelectedIndex() != -1);

                try {
                    namespaceSecretReleaseRevisionList.addListSelectionListener(adjustOkActionState);
                    boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
                    if (isOk) {
                        if (whatPanel.isAny()) {
                            NamespaceSecretReleaseRevision selectedValue = namespaceSecretReleaseRevisionList.getSelectedValue();
                            if (selectedValue != null) {
                                Utils.showReleaseRevision(actionEvent.getProject(), selectedValue, whatPanel);
                            }
                        }
                    }
                } finally {
                    // Remove listener
                    namespaceSecretReleaseRevisionList.removeListSelectionListener(adjustOkActionState);
                }
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent actionEvent) {
        boolean visible = false;
        KubernetesObject kubernetesObject = Utils.getKubernetesObject(actionEvent.getDataContext().getData(PlatformCoreDataKeys.SELECTED_ITEM));
        if (kubernetesObject instanceof V1Secret secret) {
            if (Constants.helmSecretNamePattern.matcher(Objects.requireNonNull(Objects.requireNonNull(secret.getMetadata()).getName())).matches()) {
                visible = true;
            }
        }
        actionEvent.getPresentation().setVisible(visible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
