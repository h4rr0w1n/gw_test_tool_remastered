package com.amhs.swim.test.gui;

import com.amhs.swim.test.testcase.BaseTestCase;
import com.amhs.swim.test.testcase.BaseTestCase.TestMessage;
import com.amhs.swim.test.util.*;
import com.amhs.swim.test.config.CaseConfigManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Dynamic execution environment for individual AMHS/SWIM test cases.
 * 
 * Implements the 3-column Master-Detail UI Model with Top Action Bar.
 */
public class TestCasePanel extends JPanel {

    private Color bgPanel, bgHeader, bgLog, bgRowEven, clrAccent, clrSuccess, clrWarn, clrFg, clrFgDim, clrSeparator, clrBtnText, clrBtnBg;

    // References
    private BaseTestCase currentCase;
    private Runnable onCancel;
    private TestMessage currentMsg;
    private byte[] attachedFileData = null;
    private String attachedFileName = null;

    // UI Components
    private JPanel topBar;
    
    // Config Panel (Left)
    private JPanel configFormPanel;
    private Map<String, JTextField> configFields = new HashMap<>();
    private JComboBox<String> priorityCombo;
    private JComboBox<String> contentTypeCombo;
    private JLabel attachmentLabel;
    private JTextArea descriptionArea;
    private JButton btnSend;
    private JButton btnRunCase;
    private JButton btnViewFull;
    
    // Log Panel (Right)
    private JTextArea logArea;

    // Top Bar Comps
    private JToggleButton btnRecordingTime;
    private JLabel lblRecordingTime;
    private JCheckBox chkCasePass;
    private JCheckBox chkCaseFail;
    private JTextField txtCaseNote;
    private JCheckBox chkMsgPass;
    private JCheckBox chkMsgFail;
    private JTextField txtMsgNote;
    private JButton btnSaveMsgResult;
    private JButton btnDisplayResult;
    private JButton btnSettings;

    private Map<Integer, AtomicInteger> attempts = new HashMap<>();

    public TestCasePanel() {
        setupTheme();
        setLayout(new BorderLayout());
        setBackground(bgPanel);

        initComponents();
        showPlaceholder();
    }

    private void initComponents() {
        // --- TOP BAR ---
        topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topBar.setBackground(bgHeader);
        topBar.setBorder(new MatteBorder(0, 0, 1, 0, clrSeparator));

        btnRecordingTime = new JToggleButton("Start Recording");
        btnRecordingTime.setBackground(bgHeader); 
        btnRecordingTime.setForeground(clrFg);
        btnRecordingTime.addActionListener(e -> {
            if (btnRecordingTime.isSelected()) {
                btnRecordingTime.setText("Stop Recording");
                btnRecordingTime.setForeground(clrSuccess != null ? clrSuccess : Color.GREEN.darker());
            } else {
                btnRecordingTime.setText("Start Recording");
                btnRecordingTime.setForeground(clrFg);
            }
        });

        // Case manual states
        chkCasePass = new JCheckBox("Pass (Case)"); chkCasePass.setBackground(bgHeader); chkCasePass.setForeground(clrFg);
        chkCaseFail = new JCheckBox("Fail (Case)"); chkCaseFail.setBackground(bgHeader); chkCaseFail.setForeground(clrFg);
        ButtonGroup bgCase = new ButtonGroup(); bgCase.add(chkCasePass); bgCase.add(chkCaseFail);
        txtCaseNote = new JTextField(10); txtCaseNote.setToolTipText("Note for Case");
        
        // Message manual states
        chkMsgPass = new JCheckBox("Pass (Msg)"); chkMsgPass.setBackground(bgHeader); chkMsgPass.setForeground(clrFg);
        chkMsgFail = new JCheckBox("Fail (Msg)"); chkMsgFail.setBackground(bgHeader); chkMsgFail.setForeground(clrFg);
        ButtonGroup bgMsg = new ButtonGroup(); bgMsg.add(chkMsgPass); bgMsg.add(chkMsgFail);
        txtMsgNote = new JTextField(10); txtMsgNote.setToolTipText("Note for Message");
        
        btnSaveMsgResult = new JButton("Save Msg");
        btnSaveMsgResult.addActionListener(e -> {
            saveMsgState();
            if (currentCase != null && currentMsg != null) {
                ResultManager.getInstance().getState(currentCase.getTestCaseId())
                    .msgLockedMap.put(currentMsg.getIndex(), true);
                updateUIFlags();
            }
        });

        btnDisplayResult = new JButton("Display Result");
        
        JButton btnExport = new JButton("Export XLSX");
        btnExport.addActionListener(e -> doExport());

        btnSettings = new JButton("Tool Settings");

        topBar.add(btnRecordingTime);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(chkCasePass); topBar.add(chkCaseFail); topBar.add(txtCaseNote);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(chkMsgPass); topBar.add(chkMsgFail); topBar.add(txtMsgNote); topBar.add(btnSaveMsgResult);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(btnDisplayResult); topBar.add(btnExport);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(btnSettings);

        // Listeners for manual state changes
        java.awt.event.ActionListener caseStateListener = e -> saveCaseState();
        chkCasePass.addActionListener(caseStateListener);
        chkCaseFail.addActionListener(caseStateListener);
        txtCaseNote.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { saveCaseState(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { saveCaseState(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { saveCaseState(); }
        });

        java.awt.event.ActionListener msgStateListener = e -> saveMsgState();
        chkMsgPass.addActionListener(msgStateListener);
        chkMsgFail.addActionListener(msgStateListener);
        txtMsgNote.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { saveMsgState(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { saveMsgState(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { saveMsgState(); }
        });

        // Top bar will be exported via getTopBar() instead of added internally

        // --- LEFT COLUMN: CONFIG & DESCRIPTION ---
        JPanel midPanel = new JPanel(new BorderLayout());
        midPanel.setBackground(bgPanel);

        // Config section - Modern Form UI
        JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.setBackground(bgPanel);
        JLabel lblConfig = new JLabel("  Message Payload Configuration");
        lblConfig.setForeground(clrAccent);
        lblConfig.setBorder(new EmptyBorder(5,0,5,0));
        
        // Create form panel with GridBagLayout
        configFormPanel = new JPanel();
        configFormPanel.setBackground(bgPanel);
        configFormPanel.setLayout(new GridBagLayout());
        configFormPanel.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, clrSeparator),
            new EmptyBorder(10, 10, 10, 10)
        ));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        
        // AMQP Priority Row
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        configFormPanel.add(new JLabel("AMQP PRIORITY:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        priorityCombo = new JComboBox<>(new String[]{"0", "1", "2", "3", "4", "5", "6", "7"});
        priorityCombo.setEditable(true);
        configFormPanel.add(priorityCombo, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton btnUploadPriority = new JButton("Upload");
        btnUploadPriority.setToolTipText("Upload priority from file");
        configFormPanel.add(btnUploadPriority, gbc);
        row++;
        
        // Content Type Row
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        configFormPanel.add(new JLabel("CONTENT TYPE:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        contentTypeCombo = new JComboBox<>(new String[]{
            "text/plain; charset=\"utf-8\"",
            "text/plain; charset=\"utf-16\"",
            "application/octet-stream",
            "application/xml",
            "application/json"
        });
        contentTypeCombo.setEditable(true);
        configFormPanel.add(contentTypeCombo, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton btnUploadContentType = new JButton("Upload");
        btnUploadContentType.setToolTipText("Upload content type from file");
        configFormPanel.add(btnUploadContentType, gbc);
        row++;
        
        // Dynamic fields will be added here based on case/message
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3; gbc.weightx = 1.0;
        JLabel dynamicFieldsLabel = new JLabel("Additional fields will appear based on selected message...");
        dynamicFieldsLabel.setForeground(clrFgDim);
        configFormPanel.add(dynamicFieldsLabel, gbc);
        row++;
        
        // Attachment Section
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        JPanel attachmentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        attachmentPanel.setBackground(bgRowEven);
        attachmentPanel.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, clrSeparator),
            new EmptyBorder(10, 5, 10, 5)
        ));
        JLabel lblAttachments = new JLabel("ATTACHMENTS:");
        lblAttachments.setFont(lblAttachments.getFont().deriveFont(Font.BOLD));
        attachmentPanel.add(lblAttachments);
        JButton btnAttachFile = new JButton("Upload File");
        btnAttachFile.addActionListener(e -> doAttachFile());
        attachmentPanel.add(btnAttachFile);
        attachmentLabel = new JLabel("No file attached");
        attachmentLabel.setForeground(clrFgDim);
        attachmentPanel.add(attachmentLabel);
        configFormPanel.add(attachmentPanel, gbc);
        
        JScrollPane configScroll = new JScrollPane(configFormPanel);
        configScroll.setBorder(new MatteBorder(1, 1, 1, 1, clrSeparator));
        
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        actionPanel.setBackground(bgPanel);
        btnViewFull = new JButton("View Full Payload");
        btnSend = new JButton("Send");
        JButton btnRevertDefault = new JButton("Revert To Default");
        
        btnRevertDefault.addActionListener(e -> doRevertToDefault());
        btnSend.addActionListener(e -> doSendSingle());
        btnViewFull.addActionListener(e -> doViewFullPayload());
        actionPanel.add(btnViewFull);
        actionPanel.add(btnRevertDefault);
        actionPanel.add(btnSend);

        configPanel.add(lblConfig, BorderLayout.NORTH);
        configPanel.add(configScroll, BorderLayout.CENTER);
        configPanel.add(actionPanel, BorderLayout.SOUTH);

        // Description section
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBackground(bgPanel);
        JLabel lblDesc = new JLabel("  Description (synced up with each message and case from ICAO testbook)");
        lblDesc.setForeground(clrAccent);
        lblDesc.setBorder(new EmptyBorder(5,0,5,0));
        descriptionArea = new JTextArea();
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setEditable(false);
        descriptionArea.setBackground(bgRowEven);
        descriptionArea.setForeground(clrFg);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        
        descPanel.add(lblDesc, BorderLayout.NORTH);
        descPanel.add(descScroll, BorderLayout.CENTER);

        JSplitPane midSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, configPanel, descPanel);
        midSplit.setResizeWeight(0.6);
        midSplit.setBorder(null);
        midPanel.add(midSplit, BorderLayout.CENTER);

        // --- RIGHT COLUMN: LOGS ---
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBackground(bgLog);
        JLabel lblLog = new JLabel("  Log (as and more detailed than the current logs)");
        lblLog.setForeground(clrAccent);
        lblLog.setBorder(new EmptyBorder(5,0,5,0));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(bgLog);
        logArea.setForeground(new Color(0xE2, 0xE8, 0xF0));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(null);

        logPanel.add(lblLog, BorderLayout.NORTH);
        logPanel.add(logScroll, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, midPanel, logPanel);
        mainSplit.setResizeWeight(0.5);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);
    }

    public JPanel getTopBar() { return topBar; }
    
    public void setOnCancel(Runnable r) { this.onCancel = r; }

    public void setOnDisplayResult(Runnable r) {
        btnDisplayResult.addActionListener(e -> r.run());
    }

    public void setOnSettings(Runnable r) {
        btnSettings.addActionListener(e -> r.run());
    }

    public void loadTestCase(BaseTestCase tc) {
        currentCase = tc;
        currentMsg = null;
        attachedFileData = null;
        attachedFileName = null;
        attachmentLabel.setText("No file attached");
        logArea.setText("");
        descriptionArea.setText(TestbookLoader.getDescription(tc.getTestCaseId()));

        // Load Case state
        CaseSessionState state = ResultManager.getInstance().getState(tc.getTestCaseId());
        if (state.casePass != null) {
            if (state.casePass) chkCasePass.setSelected(true);
            else chkCaseFail.setSelected(true);
        } else {
            chkCasePass.setSelected(false);
            chkCaseFail.setSelected(false);
        }
        txtCaseNote.setText(state.caseNote);

        // Reset message state view
        chkMsgPass.setSelected(false); chkMsgFail.setSelected(false); txtMsgNote.setText("");

        // Clear config form
        clearConfigForm();
        priorityCombo.setSelectedItem("4");
        contentTypeCombo.setSelectedItem("text/plain; charset=\"utf-8\"");

        // Hook logger
        Logger.setCaseLogListener(tc.getTestCaseId(), message -> SwingUtilities.invokeLater(() -> appendLog(message)));
        appendLogBanner(tc);
        SwingUtilities.invokeLater(() -> {
            descriptionArea.setCaretPosition(0);
            updateUIFlags();
        });
    }

    private void clearConfigForm() {
        configFields.clear();
        // Remove all components except the first rows (priority, content-type) and attachment panel
        // Keep: priority row (3 comps), content-type row (3 comps), dynamic label (1 comp), attachment panel (1 comp) = 8
        while (configFormPanel.getComponentCount() > 8) {
            configFormPanel.remove(configFormPanel.getComponentCount() - 1);
        }
    }

    public void onMessageSelected(TestMessage msg) {
        currentMsg = msg;
        attachedFileData = null;
        attachedFileName = null;
        attachmentLabel.setText("No file attached");
        
        // Contextually parse the requirement text to approximate AMQP properties
        String txt = msg.getMinText().toLowerCase();
        
        // Set priority from parsed text or default
        String prio = "4";
        if (txt.contains("priority=")) {
            int idx = txt.indexOf("priority=") + 9;
            int end = idx;
            while(end < txt.length() && Character.isDigit(txt.charAt(end))) end++;
            if (end > idx) {
                prio = txt.substring(idx, end);
            }
        }
        priorityCombo.setSelectedItem(prio);
        
        // Set content type from parsed text or default
        String ctype = "text/plain; charset=\"utf-8\"";
        String btype = "AMQP_VALUE";
        if (txt.contains("binary") || txt.contains("application/octet-stream")) {
            ctype = "application/octet-stream";
            btype = "DATA";
        }
        if (txt.contains("charset=\"utf-16\"")) ctype = "text/plain; charset=\"utf-16\"";
        contentTypeCombo.setSelectedItem(ctype);
        
        // Clear and rebuild dynamic fields
        clearConfigForm();
        
        // Add dynamic fields based on case/message
        String ddata = msg.getDefaultData();
        String[] parts = ddata.split("\\|");
        String caseId = currentCase.getTestCaseId();
        int mIdx = msg.getIndex();
        
        String[] labels = {"payload / file_path"};
        if (caseId.equals("CTSW106")) {
            labels = new String[]{"amhs_ats_ohi", "payload / file_path"};
        } else if (caseId.equals("CTSW107")) {
            if (mIdx == 3) labels = new String[]{"amhs_subject", "payload / file_path"};
            else if (mIdx == 4) labels = new String[]{"subject", "amhs_subject", "payload / file_path"};
            else labels = new String[]{"subject", "payload / file_path"};
        } else if (caseId.equals("CTSW108") || caseId.equals("CTSW109")) {
            labels = new String[]{"amhs_originator", "payload / file_path"};
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        int row = 3; // Start after priority, content-type, and placeholder label
        
        // Remove placeholder label
        configFormPanel.remove(configFormPanel.getComponentCount() - 2);
        
        for (int i = 0; i < parts.length; i++) {
            String label = (i < labels.length) ? labels[i] : "extra_param_" + i;
            String value = parts[i].trim();
            
            gbc.gridy = row;
            gbc.weightx = 0;
            configFormPanel.add(new JLabel(label.toUpperCase() + ":"), gbc);
            
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            JTextField tf = new JTextField(value);
            tf.setEditable(true);
            configFormPanel.add(tf, gbc);
            configFields.put(label, tf);
            
            gbc.gridx = 2;
            gbc.weightx = 0;
            JButton btnUpload = new JButton("Upload");
            final String fieldLabel = label;
            btnUpload.setToolTipText("Upload " + label + " from file");
            btnUpload.addActionListener(e -> doUploadField(fieldLabel));
            configFormPanel.add(btnUpload, gbc);
            
            row++;
        }
        
        configFormPanel.revalidate();
        configFormPanel.repaint();

        // Sync Description box with message-specific text first, then case text
        String desc = "[MESSAGE REQUIREMENT (ICAO Testbook)]\n" + msg.getMinText() + "\n\n" + 
                      "[SCENARIO DESCRIPTION]\n" + TestbookLoader.getDescription(currentCase.getTestCaseId());
        descriptionArea.setText(desc);
        SwingUtilities.invokeLater(() -> descriptionArea.setCaretPosition(0));

        // Update message state toggle
        CaseSessionState state = ResultManager.getInstance().getState(currentCase.getTestCaseId());
        Boolean pass = state.getMsgPass(msg.getIndex());
        if (pass != null) {
            if (pass) chkMsgPass.setSelected(true);
            else chkMsgFail.setSelected(true);
        } else {
            chkMsgPass.setSelected(false);
            chkMsgFail.setSelected(false);
        }
        txtMsgNote.setText(state.getMsgNote(msg.getIndex()));
        updateUIFlags();
    }
    
    private void doUploadField(String fieldName) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select File for " + fieldName);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        
        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fc.getSelectedFile();
            try {
                String content = Files.readString(selectedFile.toPath());
                JTextField tf = configFields.get(fieldName);
                if (tf != null) {
                    tf.setText(content);
                }
                Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI", 
                    "INFO", "Loaded " + fieldName + " from file: " + selectedFile.getName());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to read file: " + e.getMessage());
                Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI", 
                    "ERROR", "Failed to load " + fieldName + ": " + e.getMessage());
            }
        }
    }

    private void saveCaseState() {
        if (currentCase == null) return;
        CaseSessionState state = ResultManager.getInstance().getState(currentCase.getTestCaseId());
        if (chkCasePass.isSelected()) state.casePass = true;
        else if (chkCaseFail.isSelected()) state.casePass = false;
        else state.casePass = null;
        state.caseNote = txtCaseNote.getText();
    }

    private void saveMsgState() {
        if (currentCase == null || currentMsg == null) return;
        CaseSessionState state = ResultManager.getInstance().getState(currentCase.getTestCaseId());
        if (chkMsgPass.isSelected()) state.setMsgPass(currentMsg.getIndex(), true);
        else if (chkMsgFail.isSelected()) state.setMsgPass(currentMsg.getIndex(), false);
        else state.setMsgPass(currentMsg.getIndex(), null);
        state.setMsgNote(currentMsg.getIndex(), txtMsgNote.getText());
    }

    private void doSendSingle() {
        if (currentCase == null || currentMsg == null) {
            JOptionPane.showMessageDialog(this, "Select a message to send.");
            return;
        }
        int msgIndex = currentMsg.getIndex();
        AtomicInteger cnt = attempts.computeIfAbsent(msgIndex, k -> new AtomicInteger(0));
        int attempt = cnt.incrementAndGet();
        
        Map<String, String> inputs = new HashMap<>();
        
        // Get values from form fields
        String priority = (String) priorityCombo.getSelectedItem();
        String contentType = (String) contentTypeCombo.getSelectedItem();
        
        // Build payload from dynamic fields
        StringBuilder payloadBuilder = new StringBuilder();
        for (Map.Entry<String, JTextField> entry : configFields.entrySet()) {
            if (payloadBuilder.length() > 0) payloadBuilder.append(" | ");
            payloadBuilder.append(entry.getValue().getText());
        }
        String finalPayload = payloadBuilder.toString();
        
        // If a file is attached, use the binary data instead of text payload
        if (attachedFileData != null && currentMsg.isFile()) {
            inputs.put("file_path", attachedFileName);
            inputs.put(currentMsg.getCustomKey(), attachedFileName);
            Logger.logCase(currentCase.getTestCaseId(), "INFO", 
                "Sending binary payload from attached file: " + attachedFileName);
        } else {
            inputs.put("p" + msgIndex, finalPayload);
            inputs.put(currentMsg.getCustomKey(), finalPayload);
        }

        new Thread(() -> {
            try {
                boolean sent = currentCase.executeSingle(msgIndex, attempt, inputs);
                ResultManager.getInstance().addResult(new TestResult(
                    currentCase.getTestCaseId(),
                    attempt,
                    msgIndex,
                    inputs.getOrDefault(currentMsg.getCustomKey(), ""),
                    sent ? "SUCCESS" : "ERROR"
                ));
                
                CaseSessionState state = ResultManager.getInstance().getState(currentCase.getTestCaseId());
                state.msgLockedMap.put(msgIndex, false);
                state.setMsgPass(msgIndex, null);
                state.setMsgNote(msgIndex, "");
                
                SwingUtilities.invokeLater(() -> {
                    if (currentMsg != null && currentMsg.getIndex() == msgIndex) {
                        chkMsgPass.setSelected(false);
                        chkMsgFail.setSelected(false);
                        txtMsgNote.setText("");
                    }
                    updateUIFlags();
                });
                
            } catch (Exception ex) {
                Logger.logCase(currentCase.getTestCaseId(), "ERROR", "Exception: " + ex.getMessage());
            }
        }).start();
    }
    
    private void doExport() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("AMHS_Report.xlsx"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ExcelReportExporter.exportToExcel(fc.getSelectedFile().getAbsolutePath());
                JOptionPane.showMessageDialog(this, "Exported successfully.");
            } catch(Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to export: " + e.getMessage());
            }
        }
    }
    
    private void doAttachFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select File to Attach");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        
        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fc.getSelectedFile();
            try {
                attachedFileData = Files.readAllBytes(selectedFile.toPath());
                attachedFileName = selectedFile.getName();
                
                attachmentLabel.setText(attachedFileName + " (" + String.format("%.2f", attachedFileData.length / 1024.0) + " KB)");
                Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI", 
                    "INFO", "File attached: " + attachedFileName + " (" + attachedFileData.length + " bytes)");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to read file: " + e.getMessage());
                Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI", 
                    "ERROR", "Failed to attach file: " + e.getMessage());
            }
        }
    }
    
    private void doRevertToDefault() {
        if (currentCase == null || currentMsg == null) {
            JOptionPane.showMessageDialog(this, "Select a message to revert.");
            return;
        }
        
        CaseConfigManager configMgr = CaseConfigManager.getInstance();
        String caseId = currentCase.getTestCaseId();
        int msgIndex = currentMsg.getIndex();
        
        // Revert to default payload
        configMgr.revertToDefault(caseId, msgIndex);
        
        // Reload the message view with default payload
        onMessageSelected(currentMsg);
        
        // Clear attached file
        attachedFileData = null;
        attachedFileName = null;
        
        Logger.logCase(caseId, "INFO", "Reverted message " + msgIndex + " to default payload");
        JOptionPane.showMessageDialog(this, 
            "Message " + msgIndex + " has been reverted to its default payload.");
    }

    private void updateUIFlags() {
        if (currentCase == null) {
            chkCasePass.setEnabled(false); chkCaseFail.setEnabled(false); txtCaseNote.setEnabled(false);
            chkMsgPass.setEnabled(false); chkMsgFail.setEnabled(false); txtMsgNote.setEnabled(false);
            if (btnSaveMsgResult != null) btnSaveMsgResult.setEnabled(false);
            return;
        }

        // Case Flag
        boolean caseSent = false;
        for (TestResult r : ResultManager.getInstance().getResults()) {
            if (r.getCaseCode().equals(currentCase.getTestCaseId())) {
                caseSent = true; break;
            }
        }
        chkCasePass.setEnabled(caseSent);
        chkCaseFail.setEnabled(caseSent);
        txtCaseNote.setEnabled(caseSent);

        // Message Flag
        if (currentMsg != null) {
            int mIdx = currentMsg.getIndex();
            boolean msgSent = false;
            for (TestResult r : ResultManager.getInstance().getResults()) {
                if (r.getCaseCode().equals(currentCase.getTestCaseId()) && r.getMessageIndex() == mIdx) {
                    msgSent = true; break;
                }
            }
            CaseSessionState state = ResultManager.getInstance().getState(currentCase.getTestCaseId());
            boolean isLocked = state.msgLockedMap.getOrDefault(mIdx, false);
            
            boolean msgEnabled = msgSent && !isLocked;
            chkMsgPass.setEnabled(msgEnabled);
            chkMsgFail.setEnabled(msgEnabled);
            txtMsgNote.setEnabled(msgEnabled);
            if (btnSaveMsgResult != null) btnSaveMsgResult.setEnabled(msgEnabled);
        } else {
            chkMsgPass.setEnabled(false); chkMsgFail.setEnabled(false); txtMsgNote.setEnabled(false);
            if (btnSaveMsgResult != null) btnSaveMsgResult.setEnabled(false);
        }
    }

    public void showPlaceholder() {
        descriptionArea.setText("");
        logArea.setText("");
        clearConfigForm();
        attachmentLabel.setText("No file attached");
    }

    private void appendLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void appendLogBanner(BaseTestCase tc) {
        logArea.append("═".repeat(60) + "\n");
        logArea.append("CASE " + tc.getTestCaseId() + "\n");
        logArea.append("═".repeat(60) + "\n");
    }

    private void setupTheme() {
        Color base = UIManager.getColor("Panel.background");
        boolean isDark = false;
        if (base != null) {
            double l = (0.2126 * base.getRed() + 0.7152 * base.getGreen() + 0.0722 * base.getBlue()) / 255;
            isDark = l < 0.5;
        }

        clrAccent = new Color(0x3B, 0x82, 0xF6);

        if (isDark) {
            bgPanel = new Color(0x0F, 0x17, 0x2A);
            bgRowEven = new Color(0x1E, 0x29, 0x3B);
            bgHeader = new Color(0x11, 0x18, 0x27);
            bgLog = new Color(0x02, 0x06, 0x17);
            clrFg = new Color(0xF1, 0xF5, 0xF9);
            clrFgDim = new Color(0x94, 0xA3, 0xB8);
            clrSeparator = new Color(0x33, 0x41, 0x55);
            clrBtnText = Color.BLACK;
            clrBtnBg = new Color(0x94, 0xA3, 0xB8);
        } else {
            bgPanel = new Color(0xF8, 0xFA, 0xFC);
            bgRowEven = Color.WHITE;
            bgHeader = new Color(0xFF, 0xFF, 0xFF);
            bgLog = new Color(0x1E, 0x29, 0x3B);
            clrFg = new Color(0x33, 0x41, 0x55);
            clrFgDim = new Color(0x64, 0x74, 0x8B);
            clrSeparator = new Color(0xE2, 0xE8, 0xF0);
            clrBtnText = Color.WHITE;
            clrBtnBg = new Color(0x0F, 0x17, 0x2A);
        }
    }

    private void doViewFullPayload() {
        if (currentMsg == null) {
            JOptionPane.showMessageDialog(this, "No message selected.");
            return;
        }
        
        // Build full payload display from form fields
        StringBuilder sb = new StringBuilder();
        sb.append("=== FULL PAYLOAD VIEW ===\n\n");
        sb.append("AMQP PRIORITY: ").append(priorityCombo.getSelectedItem()).append("\n");
        sb.append("CONTENT TYPE: ").append(contentTypeCombo.getSelectedItem()).append("\n");
        sb.append("\n--- FIELD VALUES ---\n");
        
        for (Map.Entry<String, JTextField> entry : configFields.entrySet()) {
            sb.append(entry.getKey().toUpperCase()).append(": ").append(entry.getValue().getText()).append("\n");
        }
        
        if (attachedFileData != null) {
            sb.append("\n--- ATTACHED FILE ---\n");
            sb.append("Filename: ").append(attachedFileName).append("\n");
            sb.append("Size: ").append(attachedFileData.length).append(" bytes\n");
        }
        
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        
        JOptionPane.showMessageDialog(this, scrollPane, "Full Payload View", JOptionPane.INFORMATION_MESSAGE);
    }
}
