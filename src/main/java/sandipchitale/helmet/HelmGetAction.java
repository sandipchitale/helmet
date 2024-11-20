package sandipchitale.helmet;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
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

        DefaultListModel<NamespaceSecretReleaseRevision> defaultListModel = JBList.createDefaultListModel(namespaceStringStringNamespaceSecretReleaseRevisionSet);
        namespaceSecretReleaseRevisionList.setModel(defaultListModel);
        if (defaultListModel.getSize() == 1) {
            namespaceSecretReleaseRevisionList.setSelectedIndex(0);
        }

        DialogBuilder builder = new DialogBuilder(e.getProject());
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
                        e.getProject(),
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
                        Utils.showReleaseRevision(e.getProject(), selectedValue, whatPanel);
                    }
                }
            }
        } finally {
            // Remove listener
            namespaceSecretReleaseRevisionList.removeListSelectionListener(adjustOkActionState);
        }

    }

}
