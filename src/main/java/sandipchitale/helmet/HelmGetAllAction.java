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

public class HelmGetAllAction extends AnAction {

    private final Pattern helmSecretNamePattern = Pattern.compile("^\\Qsh.helm.release.v1.\\E([^.]+)\\Q.v\\E(\\d+)");

    private final KubernetesClient kubernetesClient;

    public HelmGetAllAction() {
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
        JBList<Tuple4<Namespace, Secret, String, String>> namespaceSecretReleaseRevisionist = new JBList<>(namespaceStringStringTuple4Set.toArray(new Tuple4[0]));
        namespaceSecretReleaseRevisionist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        namespaceSecretReleaseRevisionist.setCellRenderer(new DefaultListCellRenderer() {
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
        });

        panel.add(new JScrollPane(namespaceSecretReleaseRevisionist), BorderLayout.CENTER);
        DialogBuilder builder = new DialogBuilder(e.getProject());
        builder.setCenterPanel(panel);
        builder.setDimensionServiceKey("SelectNamespaceHelmReleaseRevision");
        builder.setTitle("Select Helm Release.Revision [ Namespace ]");
        builder.removeAllActions();
        builder.addOkAction();
        builder.addCancelAction();
        boolean isOk = builder.show() == DialogWrapper.OK_EXIT_CODE;
        if (isOk) {
            Tuple4<Namespace, Secret, String, String> selectedValue = namespaceSecretReleaseRevisionist.getSelectedValue();
            if (selectedValue != null) {
                showReleaseRevision(e.getProject(), selectedValue);
            }
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static void showReleaseRevision(Project project,
                                            Tuple4<Namespace, Secret, String, String> namespaceSecretStringStringTuple4) {
        String title = String.format(" ( %s.%s ) [ %s ]",
                namespaceSecretStringStringTuple4.getV3(),
                namespaceSecretStringStringTuple4.getV4(),
                namespaceSecretStringStringTuple4.getV1().getMetadata().getName()
        );

        FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

        EditorWindow currentWindow = fileEditorManager.getCurrentWindow();
        if (currentWindow != null) {
            fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);
            currentWindow = fileEditorManager.getCurrentWindow();
        }

        Secret secret = namespaceSecretStringStringTuple4.getV2();
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
            JsonNode chartInfoNode = jsonNode.get("chart").get("values");
            LightVirtualFile charInfoLightVirtualFile = new LightVirtualFile("Chart Info" + title,
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

            // Values
            JsonNode valuesNode = jsonNode.get("chart").get("values");
            LightVirtualFile valuesLightVirtualFile = new LightVirtualFile("Values" + title,
                    PlainTextFileType.INSTANCE,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(valuesNode));
            valuesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            valuesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(valuesLightVirtualFile, true, true);

            // Templates
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
            LightVirtualFile templatesvaluesLightVirtualFile = new LightVirtualFile("Templates" + title,
                    PlainTextFileType.INSTANCE,
                    templatesStringBuilder.toString());
            templatesvaluesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            templatesvaluesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(templatesvaluesLightVirtualFile, true, true);

            // Manifest
            LightVirtualFile manifestLightVirtualFile = new LightVirtualFile("Manifest" + title,
                    PlainTextFileType.INSTANCE,
                    jsonNode.get("manifest").asText().replace("\\n", "\n"));
            manifestLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            manifestLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(manifestLightVirtualFile, true, true);

            // Hooks
            StringBuilder hooksStringBuilder = new StringBuilder();
            ArrayNode hooks = (ArrayNode) jsonNode.get("hooks");
            hooks.forEach(hook -> {
                hooksStringBuilder.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                hooksStringBuilder.append(hook.get("manifest").asText().replace("\\n", "\n"));
                hooksStringBuilder.append("----\n");
            });

            LightVirtualFile hooksLightVirtualFile = new LightVirtualFile("Hooks" + title,
                    PlainTextFileType.INSTANCE,
                    hooksStringBuilder.toString());
            hooksLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            hooksLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(hooksLightVirtualFile, true, true);

            // Notes
            LightVirtualFile notesvaluesLightVirtualFile = new LightVirtualFile("Notes" + title,
                    PlainTextFileType.INSTANCE,
                    jsonNode.get("info").get("notes").asText().replace("\\n", "\n"));
            notesvaluesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            notesvaluesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(notesvaluesLightVirtualFile, true, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
