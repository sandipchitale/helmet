package sandipchitale.helmet;

import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import io.kubernetes.client.common.KubernetesObject;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.Objects;

public class Utils {
    static KubernetesObject getKubernetesObject(Object selectedItem) {
        if (selectedItem != null) {
            Class<?> clazz = selectedItem.getClass();
            while ((clazz != null) && !clazz.getName().equals(Object.class.getName())) {
                try {
                    Field resourceField = clazz.getDeclaredField("resource");
                    // Yay!
                    resourceField.setAccessible(true);
                    return (KubernetesObject) resourceField.get(selectedItem);
                } catch (NoSuchFieldException | IllegalAccessException ignore) {
                }
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    static void showReleaseRevision(Project project,
                                            NamespaceSecretReleaseRevision namespaceSecretStringStringNamespaceSecretReleaseRevision,
                                            WhatPanel whatPanel) {
        FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(project);

        EditorWindow currentWindow = fileEditorManager.getCurrentWindow();
        if (currentWindow != null) {
            fileEditorManager.createSplitter(JSplitPane.VERTICAL_SPLIT, currentWindow);
            currentWindow = fileEditorManager.getCurrentWindow();
        }

        HelmReleaseRevisionAccessor helmReleaseRevisionAccessor = new HelmReleaseRevisionAccessor(namespaceSecretStringStringNamespaceSecretReleaseRevision);
        String title = helmReleaseRevisionAccessor.getTitle();

        // Chart Info
        if (whatPanel.isChartInfo()) {
            FileType fileType = FileTypeUtils.getFileType("YAML");
            LightVirtualFile charInfoLightVirtualFile = new LightVirtualFile(Constants.CHART_INFO + title,
                    fileType,
                    helmReleaseRevisionAccessor.getChartInfo());
            charInfoLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            charInfoLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(charInfoLightVirtualFile, true, true);
        }

        // Values
        if (whatPanel.isValues()) {
            FileType fileType = FileTypeUtils.getFileType("JSON");
            LightVirtualFile valuesLightVirtualFile = new LightVirtualFile(Constants.VALUES + title,
                    fileType,
                    helmReleaseRevisionAccessor.getValues());
            valuesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            valuesLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(valuesLightVirtualFile, true, true);
        }

        // Templates
        if (whatPanel.isTemplates()) {
            FileType fileType = FileTypeUtils.getFileType("Helm YAML template", "YAML");
            LightVirtualFile templatesvaluesLightVirtualFile = new LightVirtualFile(Constants.TEMPLATES + title,
                    fileType,
                    helmReleaseRevisionAccessor.getTemplates());
            templatesvaluesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            templatesvaluesLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(templatesvaluesLightVirtualFile, true, true);
        }

        // Manifest
        if (whatPanel.isManifests()) {
            FileType fileType = FileTypeUtils.getFileType("YAML");
            LightVirtualFile manifestLightVirtualFile = new LightVirtualFile(Constants.MANIFESTS + title,
                    fileType,
                    helmReleaseRevisionAccessor.getManifests());
            manifestLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            manifestLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(manifestLightVirtualFile, true, true);
        }

        // Hooks
        if (whatPanel.isHooks()) {
            FileType fileType = FileTypeUtils.getFileType("YAML");
            LightVirtualFile hooksLightVirtualFile = new LightVirtualFile(Constants.HOOKS + title,
                    fileType,
                    helmReleaseRevisionAccessor.getHooks());
            hooksLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            hooksLightVirtualFile.setLanguage(Objects.requireNonNull(LanguageUtil.getFileTypeLanguage(fileType)));
            fileEditorManager.openFile(hooksLightVirtualFile, true, true);
        }

        // Notes
        if (whatPanel.isNotes()) {
            LightVirtualFile notesvaluesLightVirtualFile = new LightVirtualFile(Constants.NOTES + title,
                    PlainTextFileType.INSTANCE,
                    helmReleaseRevisionAccessor.getNotes());
            notesvaluesLightVirtualFile.setWritable(false);
            // Figure out a way to set language for syntax highlighting based on file extension
            notesvaluesLightVirtualFile.setLanguage(PlainTextLanguage.INSTANCE);
            fileEditorManager.openFile(notesvaluesLightVirtualFile, true, true);
        }
    }
}
