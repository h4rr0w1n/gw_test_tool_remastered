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
import javax.swing.text.BadLocationException;

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

    // UI Components
    private JPanel topBar;
    
    // Config Panel (Left)
    private JPanel configFormPanel;
    private Map<String, JTextField> configFields = new java.util.LinkedHashMap<>();
    private JTextField priorityField;
    private JComboBox<String> contentTypeCombo;
    private JComboBox<String> brokerProfileField;
    private JTextField amhsRecipientsField;
    private JTextField bodyTypeField;
    private JTextArea descriptionArea;
    private JButton btnSend;
    private JButton btnRunCase;
    private JButton btnViewFull;
    
    // Log Panel (Right)
    private JTextArea logArea;
    private static final int MAX_LOG_LINES = 2000;

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
    private JLabel lblTopicDisplay;



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
        txtCaseNote = new JTextField(10);
        
        // Message manual states
        chkMsgPass = new JCheckBox("Pass (Msg)"); chkMsgPass.setBackground(bgHeader); chkMsgPass.setForeground(clrFg);
        chkMsgFail = new JCheckBox("Fail (Msg)"); chkMsgFail.setBackground(bgHeader); chkMsgFail.setForeground(clrFg);
        ButtonGroup bgMsg = new ButtonGroup(); bgMsg.add(chkMsgPass); bgMsg.add(chkMsgFail);
        txtMsgNote = new JTextField(10);
        
        btnSaveMsgResult = new JButton("Save Msg");
        btnSaveMsgResult.addActionListener(e -> {
            saveMsgState();
            if (currentCase != null && currentMsg != null) {
                TestResult tr = ResultManager.getInstance().getLatestMessageResult(currentCase.getTestCaseId(), currentMsg.getIndex());
                if (tr != null) {
                    tr.setLocked(true);
                }
                updateUIFlags();
            }
        });

        btnDisplayResult = new JButton("Display Result");
        
        JButton btnExport = new JButton("Export XLSX");
        btnExport.addActionListener(e -> doExport());

        btnSettings = new JButton("Tool Settings");
        
        // SWIM Topic Display - reads from Tool Settings config
        lblTopicDisplay = new JLabel("TEST.TOPIC");
        lblTopicDisplay.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblTopicDisplay.setForeground(clrAccent);
        lblTopicDisplay.setBorder(new EmptyBorder(0, 8, 0, 8));

        topBar.add(btnRecordingTime);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(chkCasePass); topBar.add(chkCaseFail); topBar.add(txtCaseNote);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(chkMsgPass); topBar.add(chkMsgFail); topBar.add(txtMsgNote); topBar.add(btnSaveMsgResult);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(btnDisplayResult); topBar.add(btnExport);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(btnSettings);
        topBar.add(new JSeparator(SwingConstants.VERTICAL));
        topBar.add(lblTopicDisplay);

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
        priorityField = new JTextField(10);
        configFormPanel.add(priorityField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton btnUploadPriority = new JButton("Upload");
        btnUploadPriority.addActionListener(e -> doUploadField("AMQP PRIORITY"));
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
        btnUploadContentType.addActionListener(e -> doUploadField("CONTENT TYPE"));
        configFormPanel.add(btnUploadContentType, gbc);
        row++;
        
        // AMQP Broker Profile Row - Dropdown with predefined profiles
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        configFormPanel.add(new JLabel("AMQP BROKER PROFILE:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        String[] brokerProfiles = {"STANDARD", "AZURE_SERVICE_BUS", "IBM_MQ", "RABBITMQ", "SOLACE"};
        brokerProfileField = new JComboBox<>(brokerProfiles);
        brokerProfileField.setEditable(false);
        // Pre-fill from TestConfig
        try {
            String defaultProfile = com.amhs.swim.test.config.TestConfig.getInstance().getProperty("amqp_broker_profile", "STANDARD");
            brokerProfileField.setSelectedItem(defaultProfile);
        } catch (Exception ex) {
            brokerProfileField.setSelectedIndex(0);
        }
        // Auto-save on selection change
        brokerProfileField.addActionListener(e -> {
            try {
                com.amhs.swim.test.config.TestConfig.getInstance().setProperty("amqp_broker_profile", (String) brokerProfileField.getSelectedItem());
            } catch (Exception ex) {
                Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI", "ERROR", "Failed to save broker profile: " + ex.getMessage());
            }
        });
        configFormPanel.add(brokerProfileField, gbc);
        row++;
        
        // AMHS Recipients Row
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        configFormPanel.add(new JLabel("AMHS RECIPIENTS:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        amhsRecipientsField = new JTextField("");
        configFormPanel.add(amhsRecipientsField, gbc);
        configFields.put("amhs_recipients", amhsRecipientsField);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton btnUploadRecipients = new JButton("Upload");
        btnUploadRecipients.addActionListener(e -> doUploadField("AMHS RECIPIENTS"));
        configFormPanel.add(btnUploadRecipients, gbc);
        row++;
        
        // AMQP Body Type Row
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        configFormPanel.add(new JLabel("AMQP BODY TYPE:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        bodyTypeField = new JTextField(10);
        bodyTypeField.setText("AMQP_VALUE");
        configFormPanel.add(bodyTypeField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        JButton btnUploadBodyType = new JButton("Upload");
        btnUploadBodyType.addActionListener(e -> doUploadField("AMQP BODY TYPE"));
        configFormPanel.add(btnUploadBodyType, gbc);
        row++;
        
        
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
        midSplit.setResizeWeight(0.5); // Description zone half the height
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

        // Add Clear Logs context menu
        JPopupMenu logMenu = new JPopupMenu();
        JMenuItem clearLogItem = new JMenuItem("Clear Logs");
        clearLogItem.addActionListener(e -> logArea.setText(""));
        logMenu.add(clearLogItem);
        logArea.setComponentPopupMenu(logMenu);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, midPanel, logPanel);
        mainSplit.setResizeWeight(0.45);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);
    }

    public JPanel getTopBar() { return topBar; }
    
    public void setOnCancel(Runnable r) { this.onCancel = r; }

    public void setOnDisplayResult(Runnable r) {
        btnDisplayResult.addActionListener(e -> r.run());
    }

    public void setOnSettings(Runnable r) {
        btnSettings.addActionListener(e -> {
            r.run();
            // Update topic display after settings dialog closes
            updateTopicDisplay();
        });
    }

    private void updateTopicDisplay() {
        String topic = com.amhs.swim.test.config.TestConfig.getInstance().getProperty("gateway.default_topic", "TEST.TOPIC");
        lblTopicDisplay.setText(topic);
    }

    public void loadTestCase(BaseTestCase tc) {
        currentCase = tc;
        currentMsg = null;
        logArea.setText("");
        
        // Initialize topic display from config
        updateTopicDisplay();

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
        priorityField.setText("4");
        contentTypeCombo.setSelectedItem("text/plain; charset=\"utf-8\"");

        // Hook logger
        Logger.setCaseLogListener(tc.getTestCaseId(), message -> SwingUtilities.invokeLater(() -> appendLog(message)));
        appendLogBanner(tc);
        
        // Auto-select the first message to populate description and dynamic fields
        List<BaseTestCase.TestMessage> msgs = tc.getMessages();
        if (msgs != null && !msgs.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                onMessageSelected(msgs.get(0));
                descriptionArea.setCaretPosition(0);
                updateUIFlags();
            });
        } else {
            // No messages, just show case description
            descriptionArea.setText(TestbookLoader.getDescription(tc.getTestCaseId()));
            SwingUtilities.invokeLater(() -> {
                descriptionArea.setCaretPosition(0);
                updateUIFlags();
            });
        }
    }

    private void clearConfigForm() {
        configFields.clear();
        // Standard baseline component count:
        // row0 priority:     JLabel + JTextField + JButton      = 3
        // row1 content-type: JLabel + JComboBox  + JButton      = 3
        // row2 broker:       JLabel + JComboBox  (no Upload!)   = 2  ← only 2!
        // row3 recipients:   JLabel + JTextField + JButton      = 3
        // row4 body-type:    JLabel + JTextField + JButton      = 3
        //                                              TOTAL   = 14
        final int BASELINE = 14;
        while (configFormPanel.getComponentCount() > BASELINE) {
            configFormPanel.remove(configFormPanel.getComponentCount() - 1);
        }
    }

    public void onMessageSelected(TestMessage msg) {
        currentMsg = msg;

        // Parse priority hint from message requirement text
        String txt = msg.getMinText().toLowerCase();
        String prio = "4";
        if (txt.contains("priority=")) {
            int idx = txt.indexOf("priority=") + 9;
            int end = idx;
            while (end < txt.length() && Character.isDigit(txt.charAt(end))) end++;
            if (end > idx) prio = txt.substring(idx, end);
        }
        priorityField.setText(prio);

        // Infer content-type / body-type from message requirement text
        String ctype = "text/plain; charset=\"utf-8\"";
        String btype = "AMQP_VALUE";
        if (txt.contains("binary") || txt.contains("application/octet-stream")) {
            ctype = "application/octet-stream";
            btype = "DATA";
        }
        if (txt.contains("charset=\"utf-16\"")) ctype = "text/plain; charset=\"utf-16\"";
        contentTypeCombo.setSelectedItem(ctype);
        bodyTypeField.setText(btype);

        String caseId = currentCase.getTestCaseId();
        int mIdx = msg.getIndex();
        CaseConfigManager cfgMgr = CaseConfigManager.getInstance();

        // Build explicit field specs for this case/message.
        // Each entry: { inputKey, displayLabel, defaultValue }
        // These are the EXTRA fields beyond the 5 standard AMQP rows.
        // executeSingle methods read from these exact inputKey names.
        java.util.List<String[]> fieldSpecs = buildExtraFieldSpecs(caseId, mIdx, cfgMgr);

        // Populate the standard AMHS RECIPIENTS field from config if applicable
        String configDefault = cfgMgr.getPayload(caseId, mIdx);
        if (configDefault == null) configDefault = msg.getDefaultData();
        if (configDefault == null) configDefault = "";
        // Only auto-fill recipients field if not a multi-address case (CTSW112)
        if (!caseId.equals("CTSW112")) {
            amhsRecipientsField.setText(""); // cleared; user fills via Tool Settings / prior session
        }

        // Clear and rebuild dynamic extra fields
        clearConfigForm();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        int row = 5; // rows 0-4 are the 5 standard fields

        for (String[] spec : fieldSpecs) {
            String key   = spec[0];
            String label = spec[1];
            String defVal = spec[2];

            gbc.gridy = row;
            gbc.gridx = 0; gbc.weightx = 0;
            configFormPanel.add(new JLabel(label + ":"), gbc);

            gbc.gridx = 1; gbc.weightx = 1.0;
            // Determine if this field holds a file path (not inline text content).
            // File-path fields show only the basename in the text box but expose
            // the full path via tooltip and inputs map, preventing panel overflow.
            boolean isFilePath = key.toUpperCase().contains("FILE")
                              || key.toUpperCase().contains("BIN")
                              || key.toUpperCase().contains("ADDRESS")
                              || key.toUpperCase().contains("PATH");

            // For file-path fields: the stored/sent value is always the full path.
            // Display value: basename only (avoids horizontal overflow in the narrow field).
            String displayVal = defVal;
            if (isFilePath && defVal != null && (defVal.contains("/") || defVal.contains("\\"))) {
                int lastSep = Math.max(defVal.lastIndexOf('/'), defVal.lastIndexOf('\\'));
                displayVal = defVal.substring(lastSep + 1);
            }

            JTextField tf = new JTextField(displayVal);
            tf.setEditable(true);
            // Store the FULL path as the tooltip so doSendSingle can read it back
            // via configFields.get(key).getText() — when user uploads a new file,
            // doUploadField will set the full path as the field text.
            if (isFilePath && !defVal.equals(displayVal)) {
                tf.setToolTipText("Full path: " + defVal);
                // Tag the field so doSendSingle uses the full path, not the truncated basename.
                // We keep defVal as the real value via a client property.
                tf.putClientProperty("fullPath", defVal);
            }
            configFormPanel.add(tf, gbc);
            configFields.put(key, tf);

            gbc.gridx = 2; gbc.weightx = 0;
            JButton btnUpload = new JButton("Upload");
            final String fKey = key;
            btnUpload.addActionListener(e -> doUploadField(fKey));
            configFormPanel.add(btnUpload, gbc);

            row++;
        }

        configFormPanel.revalidate();
        configFormPanel.repaint();

        // Description area
        String desc = "[MESSAGE REQUIREMENT (ICAO Testbook)]\n" + msg.getMinText() + "\n\n"
                    + "[SCENARIO DESCRIPTION]\n" + TestbookLoader.getDescription(currentCase.getTestCaseId());
        descriptionArea.setText(desc);
        SwingUtilities.invokeLater(() -> descriptionArea.setCaretPosition(0));

        // Restore message pass/fail state from last result
        TestResult tr = ResultManager.getInstance().getLatestMessageResult(currentCase.getTestCaseId(), msg.getIndex());
        if (tr != null) {
            Boolean pass = tr.getMsgPass();
            if (pass != null) {
                if (pass) chkMsgPass.setSelected(true);
                else chkMsgFail.setSelected(true);
            } else {
                chkMsgPass.setSelected(false);
                chkMsgFail.setSelected(false);
            }
            txtMsgNote.setText(tr.getMsgNote());
        } else {
            chkMsgPass.setSelected(false);
            chkMsgFail.setSelected(false);
            txtMsgNote.setText("");
        }
        updateUIFlags();
    }

    /**
     * Returns the list of extra config fields for a given case/message.
     * Each entry is String[3]: { inputKey, displayLabel, defaultValue }.
     *
     * Keys must match exactly what the executeSingle method reads from inputs.
     * Defaults are loaded from CaseConfigManager (XML) where applicable.
     */
    private java.util.List<String[]> buildExtraFieldSpecs(String caseId, int mIdx, CaseConfigManager cfgMgr) {
        java.util.List<String[]> specs = new java.util.ArrayList<>();
        String raw = cfgMgr.getPayload(caseId, mIdx);
        if (raw == null) raw = "";

        switch (caseId) {
            // ── CTSW102 ──────────────────────────────────────────────────────────
            // msgs 1-6: text payload key "payload"
            // msgs 7-12: binary file key "binPayload_N"
            // msg 11 extra: recip_11 (>8-char recipient per EUR Doc 047 §4.5.1.4)
            case "CTSW102": {
                if (mIdx >= 7 && mIdx <= 12) {
                    // Parse pipe: filePath|recip_11 (only msg 11 has second segment)
                    String[] parts = raw.split("\\|", 2);
                    String defPath = parts[0].trim();
                    specs.add(new String[]{"binPayload_" + mIdx, "BINARY FILE (msg " + mIdx + ")", defPath});
                    if (mIdx == 11) {
                        String defRecip = parts.length > 1 ? parts[1].trim() : "LONGADDRESSXXXXX";
                        specs.add(new String[]{"recip_11", "RECIP_11 (>8 chars → REJECT)", defRecip});
                    }
                } else {
                    specs.add(new String[]{"payload", "TEXT PAYLOAD", raw});
                }
                break;
            }
            // ── CTSW103 ──────────────────────────────────────────────────────────
            // msgs 2,4 are binary (file key); others text (payload key)
            case "CTSW103": {
                if (mIdx == 2 || mIdx == 4) {
                    specs.add(new String[]{"binFile_" + mIdx, "BINARY FILE (msg " + mIdx + ")", raw});
                } else {
                    specs.add(new String[]{"payload", "TEXT PAYLOAD", raw});
                }
                break;
            }
            // ── CTSW104 ──────────────────────────────────────────────────────────
            case "CTSW104":
                specs.add(new String[]{"payload", "TEXT PAYLOAD", raw});
                break;
            // ── CTSW105 ──────────────────────────────────────────────────────────
            // msg 2 has a filing time (always 250102); rest is payload
            case "CTSW105": {
                specs.add(new String[]{"p" + mIdx, "TEXT PAYLOAD", raw});
                if (mIdx == 2) {
                    specs.add(new String[]{"amhs_ats_ft", "AMHS ATS FILING TIME (DDhhmm)", "250102"});
                }
                break;
            }
            // ── CTSW106 ──────────────────────────────────────────────────────────
            // executeSingle reads inputs.get("p"+idx), splits by "|": part[0]=ohi, part[1]=body
            // We expose both as separate fields using the SAME p<N> key split logic
            // but to avoid double-read issues we expose them as separate keys and
            // override the default pipe delivery:
            // Actually CTSW106's executeSingle reads "p"+idx as raw pipe string.
            // So expose as single "p<N>" = "OHI|BODY" format for user to edit directly.
            case "CTSW106": {
                String[] parts = raw.split("\\|", 2);
                String defOhi  = parts.length > 0 ? parts[0].trim() : "";
                String defBody = parts.length > 1 ? parts[1].trim() : "OHI Content";
                specs.add(new String[]{"amhs_ats_ohi_" + mIdx, "OHI (amhs_ats_ohi, msg " + mIdx + ")", defOhi});
                specs.add(new String[]{"body_" + mIdx,         "BODY PAYLOAD",                         defBody});
                break;
            }
            // ── CTSW107 ──────────────────────────────────────────────────────────
            // executeSingle reads "p"+idx as raw pipe string
            // msg1: subject|body  msg2: subject|body  msg3: amhs_subject|body  msg4: subject|amhs_subject|body
            case "CTSW107": {
                String[] parts = raw.split("\\|", -1);
                if (mIdx == 5) {
                    specs.add(new String[]{"subject_" + mIdx,      "AMQP SUBJECT",          parts.length > 0 ? parts[0].trim() : ""});
                    specs.add(new String[]{"amhs_subject_" + mIdx, "AMHS_SUBJECT (wins)",    parts.length > 1 ? parts[1].trim() : ""});
                    specs.add(new String[]{"body_" + mIdx,         "BODY PAYLOAD",           parts.length > 2 ? parts[2].trim() : ""});
                } else if (mIdx == 4) {
                    specs.add(new String[]{"amhs_subject_" + mIdx, "AMHS_SUBJECT (app prop)", parts.length > 0 ? parts[0].trim() : ""});
                    specs.add(new String[]{"body_" + mIdx,         "BODY PAYLOAD",             parts.length > 1 ? parts[1].trim() : ""});
                } else if (mIdx == 1) {
                    specs.add(new String[]{"body_" + mIdx,         "BODY PAYLOAD",             parts.length > 1 ? parts[1].trim() : "Empty Subject Msg"});
                } else {
                    specs.add(new String[]{"subject_" + mIdx,      "AMQP SUBJECT",  parts.length > 0 ? parts[0].trim() : ""});
                    specs.add(new String[]{"body_" + mIdx,         "BODY PAYLOAD",   parts.length > 1 ? parts[1].trim() : ""});
                }
                break;
            }
            // ── CTSW108 ──────────────────────────────────────────────────────────
            // executeSingle reads "p1" as "originator|body"
            case "CTSW108": {
                String[] parts = raw.split("\\|", 2);
                specs.add(new String[]{"originator_108", "AMHS ORIGINATOR (known 8-char)", parts.length > 0 ? parts[0].trim() : "VVTSYMYX"});
                specs.add(new String[]{"body_108",        "BODY PAYLOAD",                  parts.length > 1 ? parts[1].trim() : "Known Orig Body"});
                break;
            }
            // ── CTSW109 ──────────────────────────────────────────────────────────
            case "CTSW109": {
                String[] parts = raw.split("\\|", 2);
                specs.add(new String[]{"originator_109", "AMHS ORIGINATOR (unknown → fallback)", parts.length > 0 ? parts[0].trim() : "UNKNOWN1"});
                specs.add(new String[]{"body_109",        "BODY PAYLOAD",                         parts.length > 1 ? parts[1].trim() : "Unknown Orig Body"});
                break;
            }
            // ── CTSW110 ──────────────────────────────────────────────────────────
            case "CTSW110": {
                if (mIdx == 2 || mIdx == 3) {
                    specs.add(new String[]{"binFile_" + mIdx, "BINARY FILE", raw});
                } else if (mIdx == 4 || mIdx == 5 || mIdx == 6) {
                    specs.add(new String[]{"text_" + mIdx, "TEXT PAYLOAD", raw});
                }
                // msg 1 has no editable payload (empty by design)
                break;
            }
            // ── CTSW111 ──────────────────────────────────────────────────────────
            // All 4 messages have editable payloads for user testing.
            // Defaults from XML: msgs 1/3 = text payload, msgs 2/4 = binary file path.
            // EUR Doc 047 §4.5.1.7 — size limit testing.
            case "CTSW111": {
                if (mIdx == 1) {
                    specs.add(new String[]{"maxSizeText",    "TEXT PAYLOAD (≤ max, auto-padded to 1 KB if shorter)", raw});
                } else if (mIdx == 2) {
                    specs.add(new String[]{"maxSizeBin",     "BINARY FILE (≤ max)",   raw});
                } else if (mIdx == 3) {
                    specs.add(new String[]{"maxSizeTextOver", "TEXT PAYLOAD (> max, padded to max+1 KB if shorter)", raw});
                } else if (mIdx == 4) {
                    specs.add(new String[]{"maxSizeBinOver", "BINARY FILE (> max)",    raw});
                }
                break;
            }
            // ── CTSW112 ──────────────────────────────────────────────────────────
            // executeSingle reads addressFile_a / addressFile_b
            // The field stores the FILE PATH only; actual addresses are loaded
            // at send-time by loadAddressFile(). Display a short label to avoid overflow.
            case "CTSW112": {
                String fileKey = (mIdx == 1) ? "addressFile_a" : "addressFile_b";
                String bodyKey = "p" + mIdx + "_body";
                // raw is a file path — show only the filename part in the field to avoid overflow,
                // but store the full path so executeSingle can load the file
                String displayPath = raw;
                if (raw != null && raw.contains("/")) {
                    displayPath = raw.substring(raw.lastIndexOf('/') + 1).trim();
                } else if (raw != null && raw.contains("\\")) {
                    displayPath = raw.substring(raw.lastIndexOf('\\') + 1).trim();
                }
                String expectedCount = (mIdx == 1) ? "512" : "513";
                specs.add(new String[]{fileKey,
                    "ADDRESS FILE (" + expectedCount + " lines, one address per line)",
                    displayPath});
                specs.add(new String[]{bodyKey, "MESSAGE BODY", "Msg " + (mIdx == 1 ? "512" : "513")});
                break;
            }
            // ── CTSW113 ──────────────────────────────────────────────────────────
            // executeSingle reads "p<N>" as payload, "amhs_notification_request" as notif field
            // Default notif_request = rn,nrn (EUR Doc 047 §4.4.7.3), editable per-session
            case "CTSW113": {
                String[] parts = raw.split("\\|", 2);
                String defPayload = parts.length > 0 ? parts[0].trim() : "";
                String defNotif   = parts.length > 1 ? parts[1].trim() : "rn,nrn";
                specs.add(new String[]{"p" + mIdx, "TEXT PAYLOAD", defPayload});
                specs.add(new String[]{"amhs_notification_request", "NOTIF REQUEST (rn,nrn per §4.4.7.3)", defNotif});
                break;
            }
            // ── CTSW114 ──────────────────────────────────────────────────────────
            // NDR trigger: executeSingle reads p1 (payload), amhs_originator, amhs_notification_request_114
            // amhs_originator  — REQUIRED so AMHS knows where to return the NDR (EUR Doc 047 §4.4.1.3)
            // amhs_notification_request — must include 'nrn' so AMHS generates NDR on delete (§4.4.7.3)
            case "CTSW114": {
                String[] parts = raw.split("\\|", 3);
                String defPayload = parts.length > 0 ? parts[0].trim() : "CTSW114 NDR Trigger";
                String defOrig    = parts.length > 1 ? parts[1].trim() : "XXXXXXXX";
                String defNotif   = parts.length > 2 ? parts[2].trim() : "nrn";
                specs.add(new String[]{"p1",                            "TEXT PAYLOAD",                                      defPayload});
                specs.add(new String[]{"amhs_originator",               "AMHS ORIGINATOR (NDR return address, EUR Doc §4.4.1.3)", defOrig});
                specs.add(new String[]{"amhs_notification_request_114", "NOTIF REQUEST (must include 'nrn' per §4.4.7.3)",          defNotif});
                break;
            }
            // ── CTSW115 ──────────────────────────────────────────────────────────
            // executeSingle reads keys[i], amhs_bodypart_type, amhs_content_encoding
            // EUR Doc 047 §4.5.2.4-4.5.2.5, Table 10-11
            case "CTSW115": {
                String[] parts = raw.split("\\|", 3);
                String[] specBP  = {"ia5-text","ia5_text_body_part","general-text-body-part","general-text-body-part"};
                String[] specEnc = {"IA5","IA5","ISO-646","ISO-8859-1"};
                String[] pkeys   = {"p1","p2","p3","p4"};
                int i = mIdx - 1;
                String defPay = parts.length > 0 ? parts[0].trim() : "";
                String defBP  = parts.length > 1 ? parts[1].trim() : (i >= 0 && i < specBP.length ? specBP[i] : "ia5-text");
                String defEnc = parts.length > 2 ? parts[2].trim() : (i >= 0 && i < specEnc.length ? specEnc[i] : "IA5");
                specs.add(new String[]{pkeys[i],                 "TEXT PAYLOAD",                                        defPay});
                specs.add(new String[]{"amhs_bodypart_type",     "BODYPART TYPE (EUR Doc 047 Table 10)",                 defBP});
                specs.add(new String[]{"amhs_content_encoding",  "CONTENT ENCODING (EUR Doc 047 Table 11)",              defEnc});
                break;
            }
            // ── CTSW116 ──────────────────────────────────────────────────────────
            // executeSingle reads binFile/binFile2, amhs_ftbp_last_mod
            // EUR Doc 047 §4.5.2.6-4.5.2.8
            case "CTSW116": {
                String[] parts = raw.split("\\|", 2);
                String defPath    = parts.length > 0 ? parts[0].trim() : "src/main/resources/sample.pdf";
                String defLastMod = parts.length > 1 ? parts[1].trim() : "240101120000Z";
                String fileKey = (mIdx == 1) ? "binFile" : "binFile2";
                specs.add(new String[]{fileKey,              "BINARY FILE",                              defPath});
                specs.add(new String[]{"amhs_ftbp_last_mod", "FTBP LAST MOD (DDMMYYhhmmssZ per §4.5.2.7)", defLastMod});
                break;
            }
            default:
                // Generic: expose the config default as a plain payload field
                if (!raw.isEmpty()) {
                    specs.add(new String[]{"payload", "PAYLOAD", raw});
                }
                break;
        }
        return specs;
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
                String lookup = fieldName == null ? "" : fieldName.trim().toUpperCase();
                
                // Fields that store a FILE PATH (not file contents):
                // - any field whose key contains "bin", "file", "path", or "address" (case-insensitive)
                // - the standard AMHS RECIPIENTS field (line-separated address file)
                boolean isPathField =
                       lookup.contains("BIN")
                    || lookup.contains("FILE")
                    || lookup.contains("PATH")
                    || lookup.contains("ADDRESS")
                    || (currentMsg != null && currentMsg.isFile() && "PAYLOAD".equalsIgnoreCase(lookup));

                if (lookup.startsWith("AMHS RECIPIENTS")) {
                    // Parse addresses from file content into the standard recipients field
                    String content = Files.readString(selectedFile.toPath());
                    String addresses = parseAddressesList(content);
                    amhsRecipientsField.setText(addresses);
                    Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI",
                        "INFO", "Loaded addresses from file: " + selectedFile.getName()
                               + " → " + addresses.split(",").length + " address(es)");
                    return;
                }

                if (isPathField) {
                    // Store the absolute path — executeSingle reads and resolves the file itself
                    JTextField tf = configFields.get(fieldName);
                    if (tf != null) {
                        tf.setText(selectedFile.getAbsolutePath());
                        Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI",
                            "INFO", "Set file path for [" + fieldName + "]: " + selectedFile.getAbsolutePath());
                    }
                    return;
                }

                // Default: read text content into the field
                String content = Files.readString(selectedFile.toPath());
                JTextField tf = null;
                if (lookup.startsWith("AMQP PRIORITY")) {
                    tf = priorityField;
                } else if (lookup.startsWith("CONTENT TYPE")) {
                    contentTypeCombo.setSelectedItem(content.trim());
                    return;
                } else if (lookup.startsWith("AMQP BROKER")) {
                    JOptionPane.showMessageDialog(this,
                        "AMQP Broker Profile is a dropdown selector.\nPlease select from the available options.",
                        "Info", JOptionPane.INFORMATION_MESSAGE);
                    return;
                } else if (lookup.startsWith("AMQP BODY TYPE")) {
                    tf = bodyTypeField;
                } else {
                    tf = configFields.get(fieldName);
                }
                if (tf != null) tf.setText(content.trim());
                Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI",
                    "INFO", "Loaded [" + fieldName + "] from file: " + selectedFile.getName());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to read file: " + e.getMessage());
                Logger.logCase(currentCase != null ? currentCase.getTestCaseId() : "UI",
                    "ERROR", "Failed to load [" + fieldName + "]: " + e.getMessage());
            }
        }
    }


    /**
     * Parse addresses from file content or comma-separated list.
     * Supports both formats: one address per line OR comma-separated on single line.
     * Returns comma-separated list of validated addresses.
     */
    private String parseAddressesList(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        java.util.Set<String> addresses = new java.util.LinkedHashSet<>();
        
        // Try to detect format: if contains comma, assume comma-separated
        // Otherwise, assume one address per line
        if (content.contains(",")) {
            // Comma-separated format
            String[] parts = content.split(",");
            for (String addr : parts) {
                String trimmed = addr.trim();
                if (!trimmed.isEmpty()) {
                    addresses.add(trimmed);
                }
            }
        } else {
            // Line-separated format
            String[] lines = content.split("\\n|\\r");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    addresses.add(trimmed);
                }
            }
        }
        
        // Return as comma-separated list
        return String.join(", ", addresses);
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
        TestResult tr = ResultManager.getInstance().getLatestMessageResult(currentCase.getTestCaseId(), currentMsg.getIndex());
        if (tr != null && !tr.isLocked()) {
            if (chkMsgPass.isSelected()) tr.setMsgPass(true);
            else if (chkMsgFail.isSelected()) tr.setMsgPass(false);
            else tr.setMsgPass(null);
            tr.setMsgNote(txtMsgNote.getText());
        }
    }

    private void doSendSingle() {
        if (currentCase == null) {
            JOptionPane.showMessageDialog(this, "Select a case to send.");
            return;
        }
        
        // If no specific message is selected, use the first/default message from the case
        if (currentMsg == null) {
            List<BaseTestCase.TestMessage> msgs = currentCase.getMessages();
            if (msgs == null || msgs.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No messages available in this case.");
                return;
            }
            currentMsg = msgs.get(0);
            onMessageSelected(currentMsg);
        }
        
        int msgIndex = currentMsg.getIndex();
        int attempt = ResultManager.getInstance().getNextAttempt(currentCase.getTestCaseId(), msgIndex);
        
        Map<String, String> inputs = new HashMap<>();
        
        String priority      = priorityField.getText().trim();
        String contentType   = (String) contentTypeCombo.getSelectedItem();
        String brokerProfile = (String) brokerProfileField.getSelectedItem();
        String bodyType      = bodyTypeField.getText();

        // For CTSW112 the address file field drives recipients, NOT the standard field.
        // For all other cases: AMHS RECIPIENTS standard field drives recip().
        String caseId = currentCase.getTestCaseId();
        String amhsRecipients;
        if (caseId.equals("CTSW112")) {
            // Leave empty — executeSingle loads addresses from the addressFile_a/b extra field
            amhsRecipients = "";
        } else {
            amhsRecipients = parseAddressesList(amhsRecipientsField.getText());
        }
        
        // Add standard AMQP properties to inputs
        inputs.put("amqp_priority",  priority);
        inputs.put("content_type",   contentType);
        inputs.put("broker_profile", brokerProfile);
        inputs.put("recipient",      amhsRecipients);  // key read by recip() helper
        inputs.put("body_type",      bodyType);
        
        // Put each extra config field value under its own key — this is the critical fix.
        // Previously all fields were concatenated into one pipe-string; that broke per-field
        // key lookups (e.g. originator_108, amhs_ats_ohi_1, subject_1, addressFile_a, etc.).
        for (Map.Entry<String, JTextField> entry : configFields.entrySet()) {
            String val = entry.getValue().getText();
            if (val != null && !val.isEmpty()) {
                inputs.put(entry.getKey(), val);
            }
        }

        // Also build a human-readable payload summary for the primary custom key and log
        StringBuilder payloadBuilder = new StringBuilder();
        for (Map.Entry<String, JTextField> entry : configFields.entrySet()) {
            String val = entry.getValue().getText();
            if (val == null || val.isEmpty()) continue;
            if (payloadBuilder.length() > 0) payloadBuilder.append(" | ");
            payloadBuilder.append(entry.getKey()).append("=").append(val);
        }
        String finalPayload = payloadBuilder.toString();
        // Also expose under the message's primary customKey for backward compatibility
        if (!inputs.containsKey(currentMsg.getCustomKey()) || inputs.get(currentMsg.getCustomKey()).isEmpty()) {
            inputs.put(currentMsg.getCustomKey(), finalPayload);
        }

        // Resolve final topic/queue from TestConfig (these are always the live values,
        // regardless of whether they are TEST.TOPIC/TEST.QUEUE or anything else configured)
        com.amhs.swim.test.config.TestConfig liveConfig = com.amhs.swim.test.config.TestConfig.getInstance();
        String resolvedTopic = liveConfig.getProperty("gateway.default_topic", "TEST.TOPIC");
        String resolvedQueue = liveConfig.getProperty("gateway.default_queue", "TEST.QUEUE");

        // Log the prepared message and properties
        String recipDisplay = caseId.equals("CTSW112")
            ? "[loaded from address file]"
            : amhsRecipients;
        String logMsg = "Prepared message " + msgIndex + " properties: amqp_priority=" + priority
            + ", content_type=" + contentType + ", broker_profile=" + brokerProfile
            + ", amhs_recipients=" + recipDisplay + ", body_type=" + bodyType
            + " | PAYLOAD: " + finalPayload;
        Logger.logCase(currentCase.getTestCaseId(), "INFO", logMsg);
        Logger.logCase(currentCase.getTestCaseId(), "INFO",
            "Destinations → Topic: " + resolvedTopic + "  |  Queue: " + resolvedQueue);

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
            TestResult tr = ResultManager.getInstance().getLatestMessageResult(currentCase.getTestCaseId(), mIdx);
            
            boolean msgSent = (tr != null);
            boolean isLocked = (tr != null) && tr.isLocked();
            
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
    }

    private void appendLog(String message) {
        logArea.append(message + "\n");
        
        // Prune old lines if exceeding MAX_LOG_LINES
        int lines = logArea.getLineCount();
        if (lines > MAX_LOG_LINES) {
            try {
                // Remove the oldest 200 lines to avoid frequent pruning
                int endOffset = logArea.getLineEndOffset(lines - MAX_LOG_LINES + 200);
                logArea.getDocument().remove(0, endOffset);
                logArea.append("... [Old logs pruned for memory stability] ...\n");
            } catch (BadLocationException e) {
                // Ignore silent errors in pruning
            }
        }
        
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
        
        // Build full payload display from form fields and defaults
        String priority = priorityField.getText().trim();
        String contentType = (String) contentTypeCombo.getSelectedItem();
        String brokerProfile = ((String) brokerProfileField.getSelectedItem()).trim();
        String amhsRecipients = amhsRecipientsField.getText().trim();
        String bodyType = bodyTypeField.getText().trim();

        CaseConfigManager cfgMgr = CaseConfigManager.getInstance();
        String configuredDefault = cfgMgr.getPayload(currentCase.getTestCaseId(), currentMsg.getIndex());
        String defaultData = (configuredDefault != null && !configuredDefault.isEmpty()) ? configuredDefault : currentMsg.getDefaultData();

        StringBuilder sb = new StringBuilder();
        sb.append("=== FULL PAYLOAD VIEW ===\n\n");
        sb.append("CASE: ").append(currentCase.getTestCaseId()).append("    MSG_INDEX: ").append(currentMsg.getIndex()).append("\n\n");
        sb.append("AMQP PROPERTIES:\n");
        sb.append("  AMQP PRIORITY: ").append(priority).append("\n");
        sb.append("  CONTENT TYPE: ").append(contentType).append("\n");
        sb.append("  AMQP BROKER PROFILE: ").append(brokerProfile).append("\n");
        sb.append("  AMHS RECIPIENTS: ").append(amhsRecipients).append("\n");
        sb.append("  AMQP BODY TYPE: ").append(bodyType).append("\n\n");

        sb.append("DEFAULT DATA (raw):\n");
        sb.append(defaultData == null ? "(none)" : defaultData).append("\n\n");

        sb.append("--- FIELD VALUES ---\n");
        for (Map.Entry<String, JTextField> entry : configFields.entrySet()) {
            sb.append("  ").append(entry.getKey().toUpperCase()).append(": ").append(entry.getValue().getText()).append("\n");
        }

        // Show assembled payload that will be sent
        StringBuilder payloadBuilder = new StringBuilder();
        for (Map.Entry<String, JTextField> entry : configFields.entrySet()) {
            String txt = entry.getValue().getText();
            if (txt == null || txt.isEmpty()) continue;
            if (payloadBuilder.length() > 0) payloadBuilder.append(" | ");
            payloadBuilder.append(txt);
        }
        sb.append("\n--- COMPOSED PAYLOAD ---\n");
        sb.append(payloadBuilder.toString()).append("\n");
        
        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        
        JOptionPane.showMessageDialog(this, scrollPane, "Full Payload View", JOptionPane.INFORMATION_MESSAGE);
    }
}
