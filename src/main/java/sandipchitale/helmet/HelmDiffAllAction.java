package sandipchitale.helmet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

public class HelmDiffAllAction extends AnAction {
    private final KubernetesClient kubernetesClient;

    private final WhatPanel whatPanel = WhatPanel.build();

    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList1 = new JBList<>();
    private final JBList<NamespaceSecretReleaseRevision> namespaceSecretReleaseRevisionList2 = new JBList<>();

    public HelmDiffAllAction() {
        this.kubernetesClient = new KubernetesClientBuilder().build();

        JPanel splitPane = new JPanel(new GridLayout(1, 2, 5, 5));

        namespaceSecretReleaseRevisionList1.setCellRenderer(ReleaseRevisionNamespaceDefaultListCellRenderer.INSTANCE);
        namespaceSecretReleaseRevisionList1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        splitPane.add(new JScrollPane(namespaceSecretReleaseRevisionList1));

        namespaceSecretReleaseRevisionList2.setCellRenderer(ReleaseRevisionNamespaceDefaultListCellRenderer.INSTANCE);
        namespaceSecretReleaseRevisionList2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        splitPane.add(new JScrollPane(namespaceSecretReleaseRevisionList2));

        whatPanel.add(splitPane, BorderLayout.CENTER);
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

        builder.setCenterPanel(whatPanel);
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
            if (whatPanel.isAny()) {
                builder.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
            } else {
                Messages.showMessageDialog(
                        e.getProject(),
                        "Please select at least one of chart info, values, templates, manifests, hooks, notes for diff",
                        "Select at Least One for Diff",
                        Messages.getInformationIcon());
            }
        });

        ListSelectionListener adjustOkActionState = e1 -> {
            builder.setOkActionEnabled(
                    namespaceSecretReleaseRevisionList1.getSelectedValue() != null
                    && namespaceSecretReleaseRevisionList2.getSelectedValue() != null);
        };

        try {
            namespaceSecretReleaseRevisionList1.addListSelectionListener(adjustOkActionState);
            namespaceSecretReleaseRevisionList2.addListSelectionListener(adjustOkActionState);

            boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
            if (isOk) {
                NamespaceSecretReleaseRevision selectedValue1 = namespaceSecretReleaseRevisionList1.getSelectedValue();
                NamespaceSecretReleaseRevision selectedValue2 = namespaceSecretReleaseRevisionList2.getSelectedValue();
                if (selectedValue1 != null && selectedValue2 != null) {
                    showReleaseRevisionDiff(e.getProject(), selectedValue1, selectedValue2, whatPanel);
                }
            }
        } finally {
            // Remove listeners
            namespaceSecretReleaseRevisionList1.removeListSelectionListener(adjustOkActionState);
            namespaceSecretReleaseRevisionList2.removeListSelectionListener(adjustOkActionState);
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static void showReleaseRevisionDiff(Project project,
                                                NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision1,
                                                NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision2,
                                                WhatPanel whatPanel) {

        FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

        try {
            String title1 = String.format(Constants.RELEASE_REVISION_NAMESPACE_FORMAT,
                    namespaceSecretStringStringNamespaceSecretReleaseRevision1.release(),
                    namespaceSecretStringStringNamespaceSecretReleaseRevision1.revision(),
                    namespaceSecretStringStringNamespaceSecretReleaseRevision1.namespace().getMetadata().getName()
            );

            Secret secret1 = namespaceSecretStringStringNamespaceSecretReleaseRevision1.secret();
            String release1 = secret1.getData().get("release");
            byte[] decodedRelease1 = Base64Coder.decode(release1);

            decodedRelease1 = Base64Coder.decode(new String(decodedRelease1, StandardCharsets.UTF_8));
            GZIPInputStream gzipInputStream1 = new GZIPInputStream(new ByteArrayInputStream(decodedRelease1));
            ByteArrayOutputStream byteArrayOutputStream1 = new ByteArrayOutputStream();
            IOUtils.copy(gzipInputStream1, byteArrayOutputStream1);
            String releaseJsonString1 = byteArrayOutputStream1.toString(StandardCharsets.UTF_8);

            JsonNode jsonNode1 = objectMapper.readTree(releaseJsonString1);

            // Templates
            StringBuilder templatesStringBuilder1 = new StringBuilder();
            if (whatPanel.isTemplates()) {
                ArrayNode templates1 = (ArrayNode) jsonNode1.get("chart").get("templates");
                templates1.forEach(template -> {
                    templatesStringBuilder1.append("Template: ");
                    templatesStringBuilder1.append(template.get("name").asText());
                    templatesStringBuilder1.append("\n");
                    templatesStringBuilder1.append(new String(Base64Coder.decode(template.get("data").asText()), StandardCharsets.UTF_8));
                    templatesStringBuilder1.append("\n");
                    templatesStringBuilder1.append("----\n");
                });
            }

            // Hooks
            StringBuilder hooksStringBuilder1 = new StringBuilder();
            if (whatPanel.isHooks()) {
                ArrayNode hooks1 = (ArrayNode) jsonNode1.get("hooks");
                hooks1.forEach(hook -> {
                    hooksStringBuilder1.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                    hooksStringBuilder1.append(hook.get("manifest").asText().replace("\\n", "\n"));
                    hooksStringBuilder1.append("----\n");
                });
            }

            String title2 = String.format(Constants.RELEASE_REVISION_NAMESPACE_FORMAT,
                    namespaceSecretStringStringNamespaceSecretReleaseRevision2.release(),
                    namespaceSecretStringStringNamespaceSecretReleaseRevision2.revision(),
                    namespaceSecretStringStringNamespaceSecretReleaseRevision2.namespace().getMetadata().getName()
            );

            Secret secret2 = namespaceSecretStringStringNamespaceSecretReleaseRevision2.secret();
            String release2 = secret2.getData().get("release");
            byte[] decodedRelease2 = Base64Coder.decode(release2);

            decodedRelease2 = Base64Coder.decode(new String(decodedRelease2, StandardCharsets.UTF_8));
            GZIPInputStream gzipInputStream2 = new GZIPInputStream(new ByteArrayInputStream(decodedRelease2));
            ByteArrayOutputStream byteArrayOutputStream2 = new ByteArrayOutputStream();
            IOUtils.copy(gzipInputStream2, byteArrayOutputStream2);
            String releaseJsonString2 = byteArrayOutputStream2.toString(StandardCharsets.UTF_8);

            JsonNode jsonNode2 = objectMapper.readTree(releaseJsonString2);

            // Templates
            StringBuilder templatesStringBuilder2 = new StringBuilder();
            if (whatPanel.isTemplates()) {
                ArrayNode templates2 = (ArrayNode) jsonNode2.get("chart").get("templates");
                templates2.forEach(template -> {
                    templatesStringBuilder2.append("Template: ");
                    templatesStringBuilder2.append(template.get("name").asText());
                    templatesStringBuilder2.append("\n");
                    templatesStringBuilder2.append(new String(Base64Coder.decode(template.get("data").asText()), StandardCharsets.UTF_8));
                    templatesStringBuilder2.append("\n");
                    templatesStringBuilder2.append("----\n");
                });
            }

            // Hooks
            StringBuilder hooksStringBuilder2 = new StringBuilder();
            if (whatPanel.isHooks()) {
                ArrayNode hooks2 = (ArrayNode) jsonNode2.get("hooks");
                hooks2.forEach(hook -> {
                    hooksStringBuilder2.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                    hooksStringBuilder2.append(hook.get("manifest").asText().replace("\\n", "\n"));
                    hooksStringBuilder2.append("----\n");
                });
            }

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

            // Chart Info diff
            if (whatPanel.isChartInfo()) {
                DiffContent chartInfoContent1 = diffContentFactory.create(project,
                        String.format("Chart: %s\nStatus: %s\n",
                                jsonNode1.get("name").asText(),
                                jsonNode1.get("info").get("status").asText()));
                DiffContent chartInfoContent2 = diffContentFactory.create(project, String.format("Chart: %s\nStatus: %s\n",
                        jsonNode2.get("name").asText(),
                        jsonNode2.get("info").get("status").asText()));
                chartInfoContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                chartInfoContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                SimpleDiffRequest chartInfoDiffRequest = new SimpleDiffRequest(Constants.CHART_INFO + title1 + " vs " + Constants.CHART_INFO + title2,
                        chartInfoContent1,
                        chartInfoContent2,
                        Constants.CHART_INFO + title1,
                        Constants.CHART_INFO + title2);
                diffManager.showDiff(project, chartInfoDiffRequest);
            }

            // Values diff
            if (whatPanel.isValues()) {
                DiffContent valuesContent1 = diffContentFactory.create(project,
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode1.get("chart").get("values")));
                DiffContent valuesContent2 = diffContentFactory.create(project,
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode2.get("chart").get("values")));
                valuesContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                valuesContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                SimpleDiffRequest valuesDiffRequest = new SimpleDiffRequest(Constants.VALUES + title1 + " vs " + Constants.VALUES + title2,
                        valuesContent1,
                        valuesContent2,
                        Constants.VALUES + title1 + ".json",
                        Constants.VALUES + title2 + ".json");
                diffManager.showDiff(project, valuesDiffRequest);
            }

            // Templates diff
            if (whatPanel.isTemplates()) {
                DiffContent templatesContent1 = diffContentFactory.create(project, templatesStringBuilder1.toString());
                DiffContent templatesContent2 = diffContentFactory.create(project, templatesStringBuilder2.toString());
                templatesContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                templatesContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                SimpleDiffRequest templatesDiffRequest = new SimpleDiffRequest(Constants.TEMPLATES + title1 + " vs " + Constants.TEMPLATES + title2,
                        templatesContent1,
                        templatesContent2,
                        Constants.TEMPLATES + title1 + ".yaml",
                        Constants.TEMPLATES + title2 + ".yaml");
                diffManager.showDiff(project, templatesDiffRequest);
            }

            // Manifests diff
            if (whatPanel.isManifests()) {
                DiffContent manifestsContent1 = diffContentFactory.create(project, jsonNode1.get("manifest").asText().replace("\\n", "\n"));
                DiffContent manifestsContent2 = diffContentFactory.create(project, jsonNode2.get("manifest").asText().replace("\\n", "\n"));
                manifestsContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                manifestsContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                SimpleDiffRequest manifestsDiffsRequest = new SimpleDiffRequest(Constants.MANIFESTS + title1 + " vs " + Constants.MANIFESTS + title2,
                        manifestsContent1,
                        manifestsContent2,
                        Constants.MANIFESTS + title1 + ".yaml",
                        Constants.MANIFESTS + title2 + ".yaml");
                diffManager.showDiff(project, manifestsDiffsRequest);
            }

            // Hooks diffs
            if (whatPanel.isHooks()) {
                DiffContent hooksContent1 = diffContentFactory.create(project, hooksStringBuilder1.toString());
                DiffContent hooksContent2 = diffContentFactory.create(project, hooksStringBuilder2.toString());
                hooksContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                hooksContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                SimpleDiffRequest hooksDiffRequest = new SimpleDiffRequest(Constants.HOOKS + title1 + " vs " + Constants.HOOKS + title2,
                        hooksContent1,
                        hooksContent2,
                        Constants.HOOKS + title1 + ".yaml",
                        Constants.HOOKS + title2 + ".yaml");
                diffManager.showDiff(project, hooksDiffRequest);
            }

            // Notes diffs
            if (whatPanel.isNotes()) {
                DiffContent notesContent1 = diffContentFactory.create(project, jsonNode1.get("info").get("notes").asText().replace("\\n", "\n"));
                DiffContent notesContent2 = diffContentFactory.create(project, jsonNode2.get("info").get("notes").asText().replace("\\n", "\n"));
                notesContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                notesContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
                SimpleDiffRequest notesDiffRequest = new SimpleDiffRequest(Constants.NOTES + title1 + " vs " + Constants.NOTES + title2,
                        notesContent1,
                        notesContent2,
                        Constants.NOTES + title1,
                        Constants.NOTES + title2);
                diffManager.showDiff(project, notesDiffRequest);
            }

            fileEditorManager.closeFile(sacrificeVirtualFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
