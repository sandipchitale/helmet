package sandipchitale.helmet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
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
import java.util.Arrays;
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
        builder.setTitle("Select Helm Release.Revision [ Namespace ] for Diff");
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


        EditorWindow currentWindow = fileEditorManager.getCurrentWindow();
        if (currentWindow != null) {
            fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);
            currentWindow = fileEditorManager.getCurrentWindow();
        }

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

            // Chart Info
            JsonNode chartInfoNode1 = jsonNode1.get("chart").get("values");
            LightVirtualFile charInfoLightVirtualFile1 = new LightVirtualFile("Chart Info" + title1,
                    PlainTextFileType.INSTANCE,
                    String.format("Chart: %s\nStatus: %s\n",
                            jsonNode1.get("name").asText(),
                            jsonNode1.get("info").get("status").asText()));
            charInfoLightVirtualFile1.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            charInfoLightVirtualFile1.setLanguage(PlainTextLanguage.INSTANCE);
            if (currentWindow == null) {
                fileEditorManager.openFile(charInfoLightVirtualFile1, true, true);
            } else {
                fileEditorManager.openFileWithProviders(charInfoLightVirtualFile1, true, currentWindow);
            }

            // Values
            JsonNode valuesNode1 = jsonNode1.get("chart").get("values");
            LightVirtualFile valuesLightVirtualFile1 = new LightVirtualFile("Values" + title1,
                    PlainTextFileType.INSTANCE,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(valuesNode1));
            valuesLightVirtualFile1.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            valuesLightVirtualFile1.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(valuesLightVirtualFile1, true, true);

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
            LightVirtualFile templatesvaluesLightVirtualFile1 = new LightVirtualFile("Templates" + title1,
                    PlainTextFileType.INSTANCE,
                    templatesStringBuilder1.toString());
            templatesvaluesLightVirtualFile1.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            templatesvaluesLightVirtualFile1.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(templatesvaluesLightVirtualFile1, true, true);

            // Manifest
            LightVirtualFile manifestLightVirtualFile1 = new LightVirtualFile("Manifest" + title1,
                    PlainTextFileType.INSTANCE,
                    jsonNode1.get("manifest").asText().replace("\\n", "\n"));
            manifestLightVirtualFile1.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            manifestLightVirtualFile1.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(manifestLightVirtualFile1, true, true);

            // Hooks
            StringBuilder hooksStringBuilder1 = new StringBuilder();
            ArrayNode hooks1 = (ArrayNode) jsonNode1.get("hooks");
            hooks1.forEach(hook -> {
                hooksStringBuilder1.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                hooksStringBuilder1.append(hook.get("manifest").asText().replace("\\n", "\n"));
                hooksStringBuilder1.append("----\n");
            });
            LightVirtualFile hooksLightVirtualFile1 = new LightVirtualFile("Hooks" + title1,
                    PlainTextFileType.INSTANCE,
                    hooksStringBuilder1.toString());
            hooksLightVirtualFile1.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            hooksLightVirtualFile1.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(hooksLightVirtualFile1, true, true);

            // Notes
            LightVirtualFile notesLightVirtualFile1 = new LightVirtualFile("Notes" + title1,
                    PlainTextFileType.INSTANCE,
                    jsonNode1.get("info").get("notes").asText().replace("\\n", "\n"));
            notesLightVirtualFile1.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            notesLightVirtualFile1.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(notesLightVirtualFile1, true, true);

            LightVirtualFile sacrificeVirtualFile = new LightVirtualFile("_",
                    PlainTextFileType.INSTANCE,
                    "");
            sacrificeVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            sacrificeVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(sacrificeVirtualFile, true, true);

            currentWindow = fileEditorManager.getCurrentWindow();
            if (currentWindow != null) {
                fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);
                currentWindow = fileEditorManager.getCurrentWindow();
            }

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

            // Chart Info
            JsonNode chartInfoNode2 = jsonNode2.get("chart").get("values");
            LightVirtualFile charInfoLightVirtualFile2 = new LightVirtualFile("Chart Info" + title2,
                    PlainTextFileType.INSTANCE,
                    String.format("Chart: %s\nStatus: %s\n",
                            jsonNode2.get("name").asText(),
                            jsonNode2.get("info").get("status").asText()));
            charInfoLightVirtualFile2.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            charInfoLightVirtualFile2.setLanguage(PlainTextLanguage.INSTANCE);
            if (currentWindow == null) {
                fileEditorManager.openFile(charInfoLightVirtualFile2, true, true);
            } else {
                fileEditorManager.openFileWithProviders(charInfoLightVirtualFile2, true, currentWindow);
            }

            // Values
            JsonNode valuesNode2 = jsonNode2.get("chart").get("values");
            LightVirtualFile valuesLightVirtualFile2 = new LightVirtualFile("Values" + title2,
                    PlainTextFileType.INSTANCE,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(valuesNode2));
            valuesLightVirtualFile2.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            valuesLightVirtualFile2.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(valuesLightVirtualFile2, true, true);

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
            LightVirtualFile templatesvaluesLightVirtualFile2 = new LightVirtualFile("Templates" + title2,
                    PlainTextFileType.INSTANCE,
                    templatesStringBuilder2.toString());
            templatesvaluesLightVirtualFile2.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            templatesvaluesLightVirtualFile2.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(templatesvaluesLightVirtualFile2, true, true);

            // Manifest
            LightVirtualFile manifestLightVirtualFile2 = new LightVirtualFile("Manifest" + title2,
                    PlainTextFileType.INSTANCE,
                    jsonNode2.get("manifest").asText().replace("\\n", "\n"));
            manifestLightVirtualFile2.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            manifestLightVirtualFile2.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(manifestLightVirtualFile2, true, true);

            // Hooks
            StringBuilder hooksStringBuilder2 = new StringBuilder();
            ArrayNode hooks2 = (ArrayNode) jsonNode2.get("hooks");
            hooks2.forEach(hook -> {
                hooksStringBuilder2.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                hooksStringBuilder2.append(hook.get("manifest").asText().replace("\\n", "\n"));
                hooksStringBuilder2.append("----\n");
            });
            LightVirtualFile hooksLightVirtualFile2 = new LightVirtualFile("Hooks" + title2,
                    PlainTextFileType.INSTANCE,
                    hooksStringBuilder2.toString());
            hooksLightVirtualFile2.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            hooksLightVirtualFile2.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(hooksLightVirtualFile2, true, true);

            // Notes
            LightVirtualFile notesLightVirtualFile2 = new LightVirtualFile("Notes" + title2,
                    PlainTextFileType.INSTANCE,
                    jsonNode2.get("info").get("notes").asText().replace("\\n", "\n"));
            notesLightVirtualFile2.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            notesLightVirtualFile2.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(notesLightVirtualFile2, true, true);

            fileEditorManager.closeFile(sacrificeVirtualFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
