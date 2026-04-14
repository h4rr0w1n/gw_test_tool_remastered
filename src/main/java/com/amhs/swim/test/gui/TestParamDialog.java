package com.amhs.swim.test.gui;

import com.amhs.swim.test.testcase.BaseTestCase.TestParameter;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A modal dialog that dynamically generates a form for capturing test-specific parameters.
 * 
 * Used primarily for test cases that require manual input of AMHS O/R addresses, 
 * delivery reports, or custom message content. The form is built based on a 
 * list of {@link TestParameter} definitions.
 */
public class TestParamDialog extends JDialog {

    private Map<String, String> resultValues = null;
    private final Map<String, JComponent> inputFields = new HashMap<>();

    public TestParamDialog(JFrame parent, String title, List<TestParameter> parameters, Consumer<Map<String, String>> onExecute) {
        super(parent, title, true);
        
        setLayout(new BorderLayout());
        
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        int row = 0;
        for (TestParameter param : parameters) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.0;
            formPanel.add(new JLabel(param.getLabel()), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            JComponent field;
            if (param.isLargeText()) {
                JTextArea textArea = new JTextArea(param.getDefaultValue(), 4, 30);
                textArea.setLineWrap(true);
                JScrollPane scrollPane = new JScrollPane(textArea);
                formPanel.add(scrollPane, gbc);
                field = textArea;
            } else {
                JTextField textField = new JTextField(param.getDefaultValue(), 30);
                formPanel.add(textField, gbc);
                field = textField;
            }
            inputFields.put(param.getKey(), field);
            row++;
        }

        JScrollPane mainScroll = new JScrollPane(formPanel);
        add(mainScroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnExecute = new JButton("Execute");
        JButton btnCancel = new JButton("Cancel");

        btnExecute.addActionListener(e -> {
            resultValues = new HashMap<>();
            for (Map.Entry<String, JComponent> entry : inputFields.entrySet()) {
                String val = "";
                if (entry.getValue() instanceof JTextField) {
                    val = ((JTextField) entry.getValue()).getText();
                } else if (entry.getValue() instanceof JTextArea) {
                    val = ((JTextArea) entry.getValue()).getText();
                }
                resultValues.put(entry.getKey(), val);
            }
            dispose();
            onExecute.accept(resultValues);
        });

        btnCancel.addActionListener(e -> {
            resultValues = null;
            dispose();
        });

        buttonPanel.add(btnExecute);
        buttonPanel.add(btnCancel);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
    }
}
