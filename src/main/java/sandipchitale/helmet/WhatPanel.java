package sandipchitale.helmet;

import com.intellij.util.ui.components.BorderLayoutPanel;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;

public class WhatPanel extends BorderLayoutPanel {

    private static final Border LEFT_BORDER = BorderFactory.createEmptyBorder(0, 20, 0 , 0);

    private JCheckBox allCheckBox;
    private JCheckBox chartInfoCheckBox;
    private JCheckBox valuesCheckBox;
    private JCheckBox templatesCheckBox;
    private JCheckBox manifestsCheckBox;
    private JCheckBox hooksCheckBox;
    private JCheckBox notesCheckBox;

    private WhatPanel() {
        super(5, 5);
    }

    static WhatPanel build() {
        WhatPanel whatPanel = new WhatPanel();

        JPanel whatCheckBoxesPanel = new JPanel(new GridLayout(0, 1, 5, 5));

        whatPanel.add(whatCheckBoxesPanel, BorderLayout.EAST);

        whatPanel.allCheckBox = new JCheckBox("All", true);
        whatPanel.chartInfoCheckBox = new JCheckBox("Chart Info", true);
        whatPanel.valuesCheckBox = new JCheckBox("Values", true);
        whatPanel.templatesCheckBox = new JCheckBox("Templates", true);
        whatPanel.manifestsCheckBox = new JCheckBox("Manifests", true);
        whatPanel.hooksCheckBox = new JCheckBox("Hooks", true);
        whatPanel.notesCheckBox = new JCheckBox("Notes", true);

        whatPanel.chartInfoCheckBox.setBorder(LEFT_BORDER);
        whatPanel.valuesCheckBox.setBorder(LEFT_BORDER);
        whatPanel.templatesCheckBox.setBorder(LEFT_BORDER);
        whatPanel.manifestsCheckBox.setBorder(LEFT_BORDER);
        whatPanel.hooksCheckBox.setBorder(LEFT_BORDER);
        whatPanel.notesCheckBox.setBorder(LEFT_BORDER);

        whatCheckBoxesPanel.add(whatPanel.allCheckBox);
        whatPanel.add(new JSeparator());
        whatCheckBoxesPanel.add(whatPanel.chartInfoCheckBox);
        whatCheckBoxesPanel.add(whatPanel.valuesCheckBox);
        whatCheckBoxesPanel.add(whatPanel.templatesCheckBox);
        whatCheckBoxesPanel.add(whatPanel.manifestsCheckBox);
        whatCheckBoxesPanel.add(whatPanel.hooksCheckBox);
        whatCheckBoxesPanel.add(whatPanel.notesCheckBox);

        whatPanel.allCheckBox.addActionListener(e -> {
            boolean selected = whatPanel.allCheckBox.isSelected();
                whatPanel.chartInfoCheckBox.setSelected(selected);
                whatPanel.valuesCheckBox.setSelected(selected);
                whatPanel.templatesCheckBox.setSelected(selected);
                whatPanel.manifestsCheckBox.setSelected(selected);
                whatPanel.hooksCheckBox.setSelected(selected);
                whatPanel.notesCheckBox.setSelected(selected);
        });

        ActionListener deselectAll = e -> whatPanel.allCheckBox.setSelected(false);
        whatPanel.chartInfoCheckBox.addActionListener(deselectAll);
        whatPanel.valuesCheckBox.addActionListener(deselectAll);
        whatPanel.templatesCheckBox.addActionListener(deselectAll);
        whatPanel.manifestsCheckBox.addActionListener(deselectAll);
        whatPanel.hooksCheckBox.addActionListener(deselectAll);
        whatPanel.notesCheckBox.addActionListener(deselectAll);

        return whatPanel;
    }

    boolean isAny() { return
            chartInfoCheckBox.isSelected()
            || valuesCheckBox.isSelected()
            || templatesCheckBox.isSelected()
            || manifestsCheckBox.isSelected()
            || hooksCheckBox.isSelected()
            || notesCheckBox.isSelected();
    }

    boolean isAll() { return allCheckBox.isSelected(); }
    boolean isChartInfo() { return allCheckBox.isSelected() || chartInfoCheckBox.isSelected(); }
    boolean isValues() { return allCheckBox.isSelected() || valuesCheckBox.isSelected(); }
    boolean isTemplates() { return allCheckBox.isSelected() || templatesCheckBox.isSelected(); }
    boolean isManifests() { return allCheckBox.isSelected() || manifestsCheckBox.isSelected(); }
    boolean isHooks() { return allCheckBox.isSelected() || hooksCheckBox.isSelected(); }
    boolean isNotes() { return allCheckBox.isSelected() || notesCheckBox.isSelected(); }
}
