package sandipchitale.helmet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.zip.GZIPInputStream;

public class HelmGetAllAction extends AnAction {

    private final KubernetesClient kubernetesClient;

    private final WhatPanel whatPanel = WhatPanel.build();

    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList = new JBList<>();


    public HelmGetAllAction() {
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
        builder.addOkAction();
        builder.addCancelAction();

        builder.setOkActionEnabled(false);

        ListSelectionListener adjustOkActionState = e1 -> {
            builder.setOkActionEnabled(namespaceSecretReleaseRevisionList.getSelectedValue() != null);
        };

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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static void showReleaseRevision(Project project,
                                            NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision,
                                            WhatPanel whatPanel) {
        String title = String.format(Constants.RELEASE_REVISION_NAMESPACE_FORMAT,
                namespaceSecretStringStringNamespaceSecretReleaseRevision.release(),
                namespaceSecretStringStringNamespaceSecretReleaseRevision.revision(),
                namespaceSecretStringStringNamespaceSecretReleaseRevision.namespace().getMetadata().getName()
        );

        FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

        EditorWindow currentWindow = fileEditorManager.getCurrentWindow();
        if (currentWindow != null) {
            fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);
            currentWindow = fileEditorManager.getCurrentWindow();
        }

        Secret secret = namespaceSecretStringStringNamespaceSecretReleaseRevision.secret();
        String release = secret.getData().get("release");
        byte[] decodedRelease = Base64Coder.decode(release);

        decodedRelease = Base64Coder.decode(new String(decodedRelease, StandardCharsets.UTF_8));
        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(decodedRelease));
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            IOUtils.copy(gzipInputStream, byteArrayOutputStream);
            String releaseJsonString = byteArrayOutputStream.toString(StandardCharsets.UTF_8);

            JsonNode jsonNode = objectMapper.readTree(releaseJsonString);

            // Chart Info
            if (whatPanel.isChartInfo()) {
                LightVirtualFile charInfoLightVirtualFile = new LightVirtualFile(Constants.CHART_INFO + title,
                        PlainTextFileType.INSTANCE,
                        String.format("Chart: %s\nStatus: %s\n",
                        jsonNode.get("name").asText(),
                        jsonNode.get("info").get("status").asText()));
                charInfoLightVirtualFile.setWritable(false);
                // Figure out a way to set language for syntax highlighting based on file extension
                charInfoLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                if (currentWindow == null) {
                    fileEditorManager.openFile(charInfoLightVirtualFile, true, true);
                } else {
                    fileEditorManager.openFileWithProviders(charInfoLightVirtualFile, true, currentWindow);
                }
            }

            // Values
            if (whatPanel.isValues()) {
                JsonNode valuesNode = jsonNode.get("chart").get("values");
                LightVirtualFile valuesLightVirtualFile = new LightVirtualFile(Constants.VALUES + title,
                        PlainTextFileType.INSTANCE,
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(valuesNode));
                valuesLightVirtualFile.setWritable(false);
                // Figure out a way to set language for syntax highlighting based on file extension
                valuesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                fileEditorManager.openFile(valuesLightVirtualFile, true, true);
            }

            // Templates
            if (whatPanel.isTemplates()) {
                StringBuilder templatesStringBuilder = new StringBuilder();
                ArrayNode templates = (ArrayNode) jsonNode.get("chart").get("templates");
                templates.forEach(template -> {
                    templatesStringBuilder.append("Template: ");
                    templatesStringBuilder.append(template.get("name").asText());
                    templatesStringBuilder.append("\n");
                    templatesStringBuilder.append(new String(Base64Coder.decode(template.get("data").asText()), StandardCharsets.UTF_8));
                    templatesStringBuilder.append("\n");
                    templatesStringBuilder.append("----\n");
                });
                LightVirtualFile templatesvaluesLightVirtualFile = new LightVirtualFile(Constants.TEMPLATES + title,
                        PlainTextFileType.INSTANCE,
                        templatesStringBuilder.toString());
                templatesvaluesLightVirtualFile.setWritable(false);
                // Figure out a way to set language for syntax highlighting based on file extension
                templatesvaluesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                fileEditorManager.openFile(templatesvaluesLightVirtualFile, true, true);
            }

            // Manifest
            if (whatPanel.isManifests()) {
                LightVirtualFile manifestLightVirtualFile = new LightVirtualFile(Constants.MANIFESTS + title,
                        PlainTextFileType.INSTANCE,
                        jsonNode.get("manifest").asText().replace("\\n", "\n"));
                manifestLightVirtualFile.setWritable(false);
                // Figure out a way to set language for syntax highlighting based on file extension
                manifestLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                fileEditorManager.openFile(manifestLightVirtualFile, true, true);
            }

            // Hooks
            if (whatPanel.isHooks()) {
                StringBuilder hooksStringBuilder = new StringBuilder();
                ArrayNode hooks = (ArrayNode) jsonNode.get("hooks");
                hooks.forEach(hook -> {
                    hooksStringBuilder.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                    hooksStringBuilder.append(hook.get("manifest").asText().replace("\\n", "\n"));
                    hooksStringBuilder.append("----\n");
                });

                LightVirtualFile hooksLightVirtualFile = new LightVirtualFile(Constants.HOOKS + title,
                        PlainTextFileType.INSTANCE,
                        hooksStringBuilder.toString());
                hooksLightVirtualFile.setWritable(false);
                // Figure out a way to set language for syntax highlighting based on file extension
                hooksLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                fileEditorManager.openFile(hooksLightVirtualFile, true, true);
            }

            // Notes
            if (whatPanel.isNotes()) {
                LightVirtualFile notesvaluesLightVirtualFile = new LightVirtualFile(Constants.NOTES + title,
                        PlainTextFileType.INSTANCE,
                        jsonNode.get("info").get("notes").asText().replace("\\n", "\n"));
                notesvaluesLightVirtualFile.setWritable(false);
                // Figure out a way to set language for syntax highlighting based on file extension
                notesvaluesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
                fileEditorManager.openFile(notesvaluesLightVirtualFile, true, true);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
