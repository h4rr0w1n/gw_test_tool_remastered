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
    private JTextArea amqpConfigArea;
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

        // Config section
        JPanel configPanel = new JPanel(new BorderLayout());
        configPanel.setBackground(bgPanel);
        JLabel lblConfig = new JLabel("  Config (load as a complete AMQP message from the saved txt file)");
        lblConfig.setForeground(clrAccent);
        lblConfig.setBorder(new EmptyBorder(5,0,5,0));
        amqpConfigArea = new JTextArea();
        amqpConfigArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane configScroll = new JScrollPane(amqpConfigArea);
        
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        actionPanel.setBackground(bgPanel);
        btnViewFull = new JButton("View Full Popup");
        btnSend = new JButton("Send");
        JButton btnAttachFile = new JButton("Attach File");
        JButton btnRevertDefault = new JButton("Revert To Default");
        
        btnAttachFile.addActionListener(e -> doAttachFile());
        btnRevertDefault.addActionListener(e -> doRevertToDefault());
        btnSend.addActionListener(e -> doSendSingle());
        actionPanel.add(btnViewFull);
        actionPanel.add(btnAttachFile);
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

        // Show the default AMQP baseline message (0 alterations) as the starting reference
        // for every test case, so testers can use it as a cross-check before altering fields.
        String recipient = com.amhs.swim.test.config.TestConfig.getInstance()
                              .getProperty("gateway.test_recipient", "VVTSYMYX");
        String broker    = com.amhs.swim.test.config.TestConfig.getInstance()
                              .getProperty("amqp_broker_profile", "STANDARD");
        String topic     = com.amhs.swim.test.config.TestConfig.getInstance()
                              .getProperty("gateway.default_topic", "TEST.TOPIC");
        StringBuilder def = new StringBuilder();
        def.append("[DEFAULT AMQP MESSAGE — 0 alterations — cross-check baseline]\n");
        def.append("Uses all conformant default values. Select a message node to configure.\n");
        def.append("-------------------------------------------\n");
        def.append(String.format("%-25s : %s\n", "amqp_priority",        "4"));
        def.append(String.format("%-25s : %s\n", "content_type",         "text/plain; charset=\"utf-8\""));
        def.append(String.format("%-25s : %s\n", "amqp_broker_profile",  broker));
        def.append(String.format("%-25s : %s\n", "amhs_recipients",      recipient));
        def.append(String.format("%-25s : %s\n", "amqp_body_type",       "AMQP_VALUE"));
        def.append(String.format("%-25s : %s\n", "amhs_service_level",   "basic"));
        def.append(String.format("%-25s : %s\n", "amhs_ats_ft",          "(derived from creation-time)"));
        def.append(String.format("%-25s : %s\n", "target_topic",         topic));
        def.append(String.format("%-25s : %s\n", "payload / file_path",  "[EDIT] Default payload text"));
        def.append("-------------------------------------------\n");
        amqpConfigArea.setText(def.toString());
        amqpConfigArea.setCaretPosition(0);

        // Hook logger
        Logger.setCaseLogListener(tc.getTestCaseId(), message -> SwingUtilities.invokeLater(() -> appendLog(message)));
        appendLogBanner(tc);
        SwingUtilities.invokeLater(() -> {
            descriptionArea.setCaretPosition(0);
            updateUIFlags();
        });
    }

    public void onMessageSelected(TestMessage msg) {
        currentMsg = msg;
        attachedFileData = null;
        attachedFileName = null;
        
        // build property block in structured AMQP metadata format
        StringBuilder sb = new StringBuilder();
        sb.append("[DEEP INSPECTION] AMQP 1.0 MESSAGE METADATA\n");
        sb.append("-------------------------------------------\n");
        
        // Contextually parse the requirement text to approximate AMQP properties
        String txt = msg.getMinText().toLowerCase();
        
        String prio = "4";
        if (txt.contains("priority=")) {
            int idx = txt.indexOf("priority=") + 9;
            int end = idx;
            while(end < txt.length() && Character.isDigit(txt.charAt(end))) end++;
            if (end > idx) {
                prio = txt.substring(idx, end);
            }
        }
        sb.append(String.format("%-25s : %s\n", "amqp_priority", prio));
        
        String ctype = "text/plain; charset=\"utf-8\"";
        String btype = "AMQP_VALUE";
        if (txt.contains("binary") || txt.contains("application/octet-stream")) {
            ctype = "application/octet-stream";
            btype = "DATA";
        }
        if (txt.contains("charset=\"utf-16\"")) ctype = "text/plain; charset=\"utf-16\"";
        
        sb.append(String.format("%-25s : %s\n", "content_type", ctype));
        sb.append(String.format("%-25s : %s\n", "amqp_broker_profile", "STANDARD"));
        sb.append(String.format("%-25s : %s\n", "amhs_recipients", "VVTSYMYX"));
        sb.append(String.format("%-25s : %s\n", "amqp_body_type", btype));

        if (txt.contains("amhs_service_level=")) {
            String sl = "basic";
            if (txt.contains("extended")) sl = "extended";
            else if (txt.contains("content-based")) sl = "content-based";
            else if (txt.contains("recipient-based")) sl = "recipient-based";
            sb.append(String.format("%-25s : %s\n", "amhs_service_level", sl));
        }
        if (txt.contains("amhs_ats_pri=")) {
             int idx = txt.indexOf("amhs_ats_pri=");
             if (idx + 13 + 2 <= txt.length()) {
                  sb.append(String.format("%-25s : %s\n", "amhs_ats_pri", txt.substring(idx+13, idx+15).toUpperCase()));
             }
        }
        if (txt.contains("amhs_ats_ft=")) {
             sb.append(String.format("%-25s : %s\n", "amhs_ats_ft", txt.contains("empty") ? "empty" : "250102"));
        }
        
        // Editable fields corresponding to the payload protocol of the test case
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

        for (int i = 0; i < parts.length; i++) {
            String label = (i < labels.length) ? labels[i] : "extra_param_" + i;
            sb.append(String.format("%-25s : [EDIT] %s\n", label, parts[i].trim()));
        }
        sb.append("-------------------------------------------\n");
        
        amqpConfigArea.setText(sb.toString());
        amqpConfigArea.setCaretPosition(0);

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
        String[] lines = amqpConfigArea.getText().split("\n");
        
        // Parse [EDIT] fields and re-combine them with pipe separator
        StringBuilder pipeBuilder = new StringBuilder();
        for (String line : lines) {
            int editIdx = line.indexOf("[EDIT]");
            if (editIdx != -1) {
                if (pipeBuilder.length() > 0) pipeBuilder.append(" | ");
                pipeBuilder.append(line.substring(editIdx + 6).trim());
            }
        }
        
        String finalPayload = pipeBuilder.toString();
        // Fallback for custom or unbroken old layouts
        if (finalPayload.isEmpty() && lines.length > 0) {
            finalPayload = lines[lines.length - 1];
        }
        
        // If a file is attached, use the binary data instead of text payload
        if (attachedFileData != null && currentMsg.isFile()) {
            // For binary messages, store the file path reference in the payload field
            // The actual file reading happens in the test case execution
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
                
                // Unlock the message result state (Pos 0)
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
                
                // Update the display to show the attached file info
                String currentText = amqpConfigArea.getText();
                StringBuilder sb = new StringBuilder();
                sb.append(currentText);
                if (!currentText.trim().endsWith("\n")) {
                    sb.append("\n");
                }
                sb.append("-------------------------------------------\n");
                sb.append(String.format("%-25s : [ATTACHED] %s (%.2f KB)\n", "attached_file", 
                    attachedFileName, attachedFileData.length / 1024.0));
                sb.append("-------------------------------------------\n");
                
                amqpConfigArea.setText(sb.toString());
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
        amqpConfigArea.setText("");
        descriptionArea.setText("");
        logArea.setText("");
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
}
