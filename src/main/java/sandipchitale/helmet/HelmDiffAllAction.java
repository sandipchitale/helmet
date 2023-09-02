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
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.components.BorderLayoutPanel;
import groovy.lang.Tuple4;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class HelmDiffAllAction extends AnAction {

    private final Pattern helmSecretNamePattern = Pattern.compile("^\\Qsh.helm.release.v1.\\E([^.]+)\\Q.v\\E(\\d+)");

    private final KubernetesClient kubernetesClient;

    public HelmDiffAllAction() {
        this.kubernetesClient = new KubernetesClientBuilder().build();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        List<Tuple4<Namespace, Secret, String, String>> namespaceStringStringTuple4Set = new ArrayList<>();
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
                                Matcher matcher = helmSecretNamePattern.matcher(secret.getMetadata().getName());
                                return (matcher.matches());
                            })
                            .forEach(secret -> {
                                Matcher matcher = helmSecretNamePattern.matcher(secret.getMetadata().getName());
                                if (matcher.matches()) {
                                    String release = matcher.group(1);
                                    String revision = matcher.group(2);
                                    namespaceStringStringTuple4Set.add(new Tuple4<>(namespace, secret, release, revision));
                                }
                            });
                });

        BorderLayoutPanel panel = new BorderLayoutPanel();

        DefaultListCellRenderer listCellrenderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component listCellRendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (listCellRendererComponent instanceof JLabel listCellRendererComponentLabel) {
                    Tuple4<Namespace, Secret, String, String> valueTuple4 = (Tuple4<Namespace, Secret, String, String>) value;
                    listCellRendererComponentLabel.setText(
                            String.format("%-64s [ %s ]",
                                    valueTuple4.getV3() + "." + valueTuple4.getV4(),
                                    valueTuple4.getV1().getMetadata().getName()));
                }
                return listCellRendererComponent;
            }
        };

        JPanel splitPane = new JPanel(new GridLayout(1, 2, 5, 5));

        JBList<Tuple4<Namespace, Secret, String, String>> namespaceSecretReleaseRevisionist1 = new JBList<>(namespaceStringStringTuple4Set.toArray(new Tuple4[0]));
        namespaceSecretReleaseRevisionist1.setCellRenderer(listCellrenderer);
        namespaceSecretReleaseRevisionist1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        splitPane.add(new JScrollPane(namespaceSecretReleaseRevisionist1));

        JBList<Tuple4<Namespace, Secret, String, String>> namespaceSecretReleaseRevisionist2 = new JBList<>(namespaceStringStringTuple4Set.toArray(new Tuple4[0]));
        namespaceSecretReleaseRevisionist2.setCellRenderer(listCellrenderer);
        namespaceSecretReleaseRevisionist2.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        splitPane.add(new JScrollPane(namespaceSecretReleaseRevisionist2));

        panel.add(splitPane, BorderLayout.CENTER);

        DialogBuilder builder = new DialogBuilder(e.getProject());
        builder.setCenterPanel(panel);
        builder.setDimensionServiceKey("SelectNamespaceHelmReleaseRevisionForDiff");
        builder.setTitle("Select Helm Release.Revisions [ Namespaces ] for Diff");
        builder.removeAllActions();
        builder.addOkAction();
        builder.addCancelAction();
        boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
        if (isOk) {
            Tuple4<Namespace, Secret, String, String> selectedValue1 = namespaceSecretReleaseRevisionist1.getSelectedValue();
            Tuple4<Namespace, Secret, String, String> selectedValue2 = namespaceSecretReleaseRevisionist2.getSelectedValue();
            if (selectedValue1 != null && selectedValue2 != null) {
                showReleaseRevisionDiff(e.getProject(), selectedValue1, selectedValue2);
            }
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static void showReleaseRevisionDiff(Project project,
                                                Tuple4<Namespace, Secret, String, String> namespaceSecretStringStringTuple41,
                                                Tuple4<Namespace, Secret, String, String> namespaceSecretStringStringTuple42) {

        FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

        try {
            String title1 = String.format(" ( %s.%s ) [ %s ]",
                    namespaceSecretStringStringTuple41.getV3(),
                    namespaceSecretStringStringTuple41.getV4(),
                    namespaceSecretStringStringTuple41.getV1().getMetadata().getName()
            );

            Secret secret1 = namespaceSecretStringStringTuple41.getV2();
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
            ArrayNode templates1 = (ArrayNode) jsonNode1.get("chart").get("templates");
            templates1.forEach(template -> {
                templatesStringBuilder1.append("Template: ");
                templatesStringBuilder1.append(template.get("name").asText());
                templatesStringBuilder1.append("\n");
                templatesStringBuilder1.append(new String(Base64Coder.decode(template.get("data").asText()), StandardCharsets.UTF_8));
                templatesStringBuilder1.append("\n");
                templatesStringBuilder1.append("----\n");
            });

            // Hooks
            StringBuilder hooksStringBuilder1 = new StringBuilder();
            ArrayNode hooks1 = (ArrayNode) jsonNode1.get("hooks");
            hooks1.forEach(hook -> {
                hooksStringBuilder1.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                hooksStringBuilder1.append(hook.get("manifest").asText().replace("\\n", "\n"));
                hooksStringBuilder1.append("----\n");
            });

            String title2 = String.format(" ( %s.%s ) [ %s ]",
                    namespaceSecretStringStringTuple42.getV3(),
                    namespaceSecretStringStringTuple42.getV4(),
                    namespaceSecretStringStringTuple42.getV1().getMetadata().getName()
            );

            Secret secret2 = namespaceSecretStringStringTuple42.getV2();
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
            ArrayNode templates2 = (ArrayNode) jsonNode2.get("chart").get("templates");
            templates2.forEach(template -> {
                templatesStringBuilder2.append("Template: ");
                templatesStringBuilder2.append(template.get("name").asText());
                templatesStringBuilder2.append("\n");
                templatesStringBuilder2.append(new String(Base64Coder.decode(template.get("data").asText()), StandardCharsets.UTF_8));
                templatesStringBuilder2.append("\n");
                templatesStringBuilder2.append("----\n");
            });

            // Hooks
            StringBuilder hooksStringBuilder2 = new StringBuilder();
            ArrayNode hooks2 = (ArrayNode) jsonNode2.get("hooks");
            hooks2.forEach(hook -> {
                hooksStringBuilder2.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                hooksStringBuilder2.append(hook.get("manifest").asText().replace("\\n", "\n"));
                hooksStringBuilder2.append("----\n");
            });

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
            DiffContent chartInfoContent1 = diffContentFactory.create(project,
                    String.format("Chart: %s\nStatus: %s\n",
                            jsonNode1.get("name").asText(),
                            jsonNode1.get("info").get("status").asText()));
            DiffContent chartInfoContent2 = diffContentFactory.create(project, String.format("Chart: %s\nStatus: %s\n",
                    jsonNode2.get("name").asText(),
                    jsonNode2.get("info").get("status").asText()));
            chartInfoContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            chartInfoContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            SimpleDiffRequest chartInfoDiffRequest = new SimpleDiffRequest("Chart Info" + title1 + " vs " + "Chart Info" + title2,
                    chartInfoContent1,
                    chartInfoContent2,
                    "Chart Info" + title1,
                    "Chart Info" + title2);
            diffManager.showDiff(project, chartInfoDiffRequest);

            // Values diff
            DiffContent valuesContent1 = diffContentFactory.create(project,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode1.get("chart").get("values")));
            DiffContent valuesContent2 = diffContentFactory.create(project,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode2.get("chart").get("values")));
            valuesContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            valuesContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            SimpleDiffRequest valuesDiffRequest = new SimpleDiffRequest("Values" + title1 + " vs " + "Values" + title2,
                    valuesContent1,
                    valuesContent2,
                    "Values" + title1 + ".json",
                    "Values" + title2 + ".json");
            diffManager.showDiff(project, valuesDiffRequest);

            // Templates diff
            DiffContent templatesContent1 = diffContentFactory.create(project, templatesStringBuilder1.toString());
            DiffContent templatesContent2 = diffContentFactory.create(project, templatesStringBuilder2.toString());
            templatesContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            templatesContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            SimpleDiffRequest templatesDiffRequest = new SimpleDiffRequest("Templates" + title1 + " vs " + "Templates" + title2,
                    templatesContent1,
                    templatesContent2,
                    "Templates" + title1 + ".yaml",
                    "Templates" + title2 + ".yaml");
            diffManager.showDiff(project, templatesDiffRequest);

            // Manifests diff
            DiffContent manifestsContent1 = diffContentFactory.create(project, jsonNode1.get("manifest").asText().replace("\\n", "\n"));
            DiffContent manifestsContent2 = diffContentFactory.create(project, jsonNode2.get("manifest").asText().replace("\\n", "\n"));
            manifestsContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            manifestsContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            SimpleDiffRequest manifestsDiffsRequest = new SimpleDiffRequest("Manifests" + title1 + " vs " + "Manifests" + title2,
                    manifestsContent1,
                    manifestsContent2,
                    "Manifests" + title1 + ".yaml",
                    "Manifests" + title2 + ".yaml");
            diffManager.showDiff(project, manifestsDiffsRequest);

            // Hooks diffs
            DiffContent hooksContent1 = diffContentFactory.create(project, hooksStringBuilder1.toString());
            DiffContent hooksContent2 = diffContentFactory.create(project, hooksStringBuilder2.toString());
            hooksContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            hooksContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            SimpleDiffRequest hooksDiffRequest = new SimpleDiffRequest("Hooks" + title1 + " vs " + "Hooks" + title2,
                    hooksContent1,
                    hooksContent2,
                    "Hooks" + title1 + ".yaml",
                    "Hooks" + title2 + ".yaml");
            diffManager.showDiff(project, hooksDiffRequest);

            // Notes diffs
            DiffContent notesContent1 = diffContentFactory.create(project, jsonNode1.get("info").get("notes").asText().replace("\\n", "\n"));
            DiffContent notesContent2 = diffContentFactory.create(project, jsonNode2.get("info").get("notes").asText().replace("\\n", "\n"));
            notesContent1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            notesContent2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
            SimpleDiffRequest notesDiffRequest = new SimpleDiffRequest("Notes" + title1 + " vs " + "Notes" + title2,
                    notesContent1,
                    notesContent2,
                    "Notes" + title1,
                    "Notes" + title2);
            diffManager.showDiff(project, notesDiffRequest);

            fileEditorManager.closeFile(sacrificeVirtualFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
