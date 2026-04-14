package com.amhs.swim.test.gui;

import com.amhs.swim.test.testcase.*;
import com.amhs.swim.test.config.TestConfig;
import com.amhs.swim.test.util.CaseSessionState;
import com.amhs.swim.test.util.ExcelReportExporter;
import com.amhs.swim.test.util.Logger;
import com.amhs.swim.test.util.ResultManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicPasswordFieldUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Main Graphical User Interface for the AMHS/SWIM Gateway Test Tool.
 * 
 * Provides a split-pane interface with:
 * - Left Sidebar: Tabbed navigation for test case selection and system settings.
 * - Right Panel: Dynamic execution environment ({@link TestCasePanel}) featuring 
 *   per-test configuration and automated ICAO-format logging.
 * 
 * Implements a modern, responsive theme that adapts to the system's preferred look and feel.
 */
public class TestFrame extends JFrame {

    private Color bgLight;
    private Color bgSidebar;
    private Color bgHeader;
    private Color clrAccent;
    private Color clrFg;
    private Color clrDim;
    private Color clrSep;
    private Color btnNormal;
    private Color btnHover;

    // ── References ────────────────────────────────────────────────────────────
    private SwimToAmhsTests swimToAmhsTests;
    private TestCasePanel   casePanel;      // right-side panel
    private BaseTestCase    activatedCase;  // currently shown case

    // ─────────────────────────────────────────────────────────────────────────

    public TestFrame() {
        super("AMHS/SWIM Gateway Test Tool  v1.1  |  EUR Doc 047 Appendix A");
        swimToAmhsTests = new SwimToAmhsTests();

        setupTheme();
        initComponents();

        setSize(1280, 780);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                TestConfig.getInstance().saveConfig();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Init
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initializes the theme engine based on the current system Look and Feel.
     */
    private void setupTheme() {
        Color base = UIManager.getColor("Panel.background");
        boolean isDark = false;
        if (base != null) {
            double l = (0.2126 * base.getRed() + 0.7152 * base.getGreen() + 0.0722 * base.getBlue()) / 255;
            isDark = l < 0.5;
        }

        clrAccent = new Color(0x3B, 0x82, 0xF6); // Blue 500

        if (isDark) {
            bgLight   = new Color(0x0F, 0x17, 0x2A); // Slate 900
            bgSidebar = new Color(0x1E, 0x29, 0x3B); // Slate 800
            bgHeader  = new Color(0x11, 0x18, 0x27); // Gray 900
            clrFg     = new Color(0xF8, 0xFA, 0xFC); // Slate 50
            clrDim    = new Color(0x94, 0xA3, 0xB8); // Slate 400
            clrSep    = new Color(0x33, 0x41, 0x55); // Slate 700
            btnNormal = new Color(0x1E, 0x29, 0x3B); // Slate 800
            btnHover  = new Color(0x33, 0x41, 0x55); // Slate 700
        } else {
            bgLight   = new Color(0xF1, 0xF5, 0xF9); // Slate 100
            bgSidebar = new Color(0xFF, 0xFF, 0xFF); // White
            bgHeader  = new Color(0xF8, 0xFA, 0xFC); // Slate 50
            clrFg     = new Color(0x1E, 0x29, 0x3B); // Slate 800
            clrDim    = new Color(0x64, 0x74, 0x8B); // Slate 500
            clrSep    = new Color(0xE2, 0xE8, 0xF0); // Slate 200
            btnNormal = new Color(0xF1, 0xF5, 0xF9); // Slate 100
            btnHover  = new Color(0xE2, 0xE8, 0xF0); // Slate 200
        }
    }

    private void initComponents() {
        getContentPane().setBackground(bgLight);

        // LEFT: case tree panel (no tabbed pane — Settings is a popup dialog)
        // ──────────────────────────────────────────────
        JPanel leftOuter = new JPanel(new BorderLayout());
        leftOuter.setBackground(bgSidebar);

        JLabel treeHeader = new JLabel("  Test Cases");
        treeHeader.setForeground(clrAccent);
        treeHeader.setFont(new Font("SansSerif", Font.BOLD, 12));
        treeHeader.setBorder(new EmptyBorder(6, 4, 6, 4));
        treeHeader.setOpaque(true);
        treeHeader.setBackground(bgHeader);
        leftOuter.add(treeHeader, BorderLayout.NORTH);
        leftOuter.add(buildCaseTree(), BorderLayout.CENTER);

        // ──────────────────────────────────────────────
        // RIGHT: TestCasePanel
        // ──────────────────────────────────────────────
        casePanel = new TestCasePanel();
        casePanel.setOnSettings(() -> showSettingsDialog());
        casePanel.setOnDisplayResult(() -> showResultsDialog());

        // ──────────────────────────────────────────────
        // Horizontal split: left (sidebar) | right (panel)
        // ──────────────────────────────────────────────
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftOuter, casePanel);
        mainSplit.setDividerLocation(300);
        mainSplit.setDividerSize(4);
        mainSplit.setResizeWeight(0.25);
        mainSplit.setBackground(bgLight);

        add(casePanel.getTopBar(), BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Case Tree builder (replaces tabbed pane — Settings is now a popup dialog)
    // ─────────────────────────────────────────────────────────────────────────

    private JScrollPane buildCaseTree() {
        BaseTestCase[] cases = new BaseTestCase[]{
            swimToAmhsTests.CTSW101, swimToAmhsTests.CTSW102, swimToAmhsTests.CTSW103,
            swimToAmhsTests.CTSW104, swimToAmhsTests.CTSW105, swimToAmhsTests.CTSW106,
            swimToAmhsTests.CTSW107, swimToAmhsTests.CTSW108, swimToAmhsTests.CTSW109,
            swimToAmhsTests.CTSW110, swimToAmhsTests.CTSW111, swimToAmhsTests.CTSW112,
            swimToAmhsTests.CTSW113, swimToAmhsTests.CTSW114, swimToAmhsTests.CTSW115,
            swimToAmhsTests.CTSW116
        };

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("SWIM → AMHS  (CTSW1xx)");
        for (BaseTestCase tc : cases) {
            DefaultMutableTreeNode caseNode = new DefaultMutableTreeNode(new CaseNode(tc));
            if (tc.getMessages() != null && !tc.getMessages().isEmpty()) {
                for (BaseTestCase.TestMessage msg : tc.getMessages()) {
                    caseNode.add(new DefaultMutableTreeNode(new MsgNode(msg)));
                }
            } else {
                caseNode.add(new DefaultMutableTreeNode("default message"));
            }
            root.add(caseNode);
        }

        JTree caseTree = new JTree(root);
        caseTree.setBackground(bgSidebar);
        caseTree.setForeground(clrFg);
        caseTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) caseTree.getLastSelectedPathComponent();
            if (node == null) return;
            Object usr = node.getUserObject();
            if (usr instanceof CaseNode) {
                selectTestCase(((CaseNode)usr).tc);
            } else if (usr instanceof MsgNode) {
                MsgNode mn = (MsgNode)usr;
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                if (parent != null && parent.getUserObject() instanceof CaseNode) {
                    BaseTestCase tc = ((CaseNode)parent.getUserObject()).tc;
                    selectTestCase(tc);
                    SwingUtilities.invokeLater(() -> casePanel.onMessageSelected(mn.msg));
                }
            }
        });

        JScrollPane caseScroll = new JScrollPane(caseTree);
        caseScroll.setBackground(bgSidebar);
        caseScroll.getViewport().setBackground(bgSidebar);
        caseScroll.setBorder(null);
        return caseScroll;
    }

    private static class CaseNode {
        BaseTestCase tc;
        CaseNode(BaseTestCase tc) { this.tc = tc; }
        public String toString() { return tc.getTestCaseId(); }
    }

    private static class MsgNode {
        BaseTestCase.TestMessage msg;
        MsgNode(BaseTestCase.TestMessage msg) { this.msg = msg; }
        public String toString() {
            String txt = msg.getMinText();
            return (txt != null && !txt.isEmpty()) ? txt : "Message " + msg.getIndex();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test case selection with warning
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Switches the active test case in the right-side execution panel.
     * Includes safety checks to prevent accidental interruption of running tests.
     */
    private void selectTestCase(BaseTestCase tc) {
        // Confirmation popup removed per generic top taskbar UI model format
        activatedCase = tc;
        casePanel.loadTestCase(tc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Results Popup Dialog
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows a modal popup dialog displaying all recorded test results in the
     * same tabular format as the Excel export, including the session date/time.
     */
    private void showResultsDialog() {
        JDialog dlg = new JDialog(this, "Session Results", true);
        dlg.setSize(900, 600);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        // Header row
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(bgHeader);
        headerPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
        JLabel lblDate = new JLabel("Session Date: " +
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        lblDate.setForeground(clrAccent);
        lblDate.setFont(new Font("SansSerif", Font.BOLD, 12));
        headerPanel.add(lblDate, BorderLayout.WEST);
        dlg.add(headerPanel, BorderLayout.NORTH);

        // Table
        String[] cols = {"CASE CODE (CTSWxxx)", "Attempts", "Messages", "Detailed Message Payloads", "Result"};
        java.util.List<ResultManager.ResultRow> rows = buildResultRows();
        Object[][] data = new Object[rows.size()][cols.length];
        for (int i = 0; i < rows.size(); i++) {
            ResultManager.ResultRow r = rows.get(i);
            data[i][0] = r.caseCode;
            data[i][1] = r.attempt;
            data[i][2] = r.messageLabel;
            data[i][3] = r.payloadSummary;
            data[i][4] = r.result;
        }

        javax.swing.table.DefaultTableModel model =
            new javax.swing.table.DefaultTableModel(data, cols) {
                @Override public boolean isCellEditable(int row, int col) { return false; }
            };
        JTable table = new JTable(model);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setRowHeight(22);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] colWidths = {130, 70, 80, 350, 240};
        for (int i = 0; i < colWidths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
        }

        // Colour-code Result column
        table.getColumnModel().getColumn(4).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                String val = value == null ? "" : value.toString().toUpperCase();
                if (val.contains("PASS")) c.setForeground(new Color(0x16, 0xa3, 0x4a));
                else if (val.contains("FAIL") || val.contains("ERROR")) c.setForeground(new Color(0xdc, 0x26, 0x26));
                else c.setForeground(clrFg);
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        dlg.add(tableScroll, BorderLayout.CENTER);

        // Footer buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        footer.setBackground(bgHeader);
        JButton btnExport = settingsBtn("Export XLSX");
        btnExport.addActionListener(ev -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Save Test Report");
            fc.setSelectedFile(new java.io.File("AMHS_SWIM_Test_Report.xlsx"));
            if (fc.showSaveDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                try {
                    String path = fc.getSelectedFile().getAbsolutePath();
                    if (!path.endsWith(".xlsx")) path += ".xlsx";
                    ExcelReportExporter.exportToExcel(path);
                    JOptionPane.showMessageDialog(dlg, "Report exported to:\n" + path);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dlg, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        JButton btnClose = settingsBtn("Close");
        btnClose.addActionListener(ev -> dlg.dispose());
        footer.add(btnExport);
        footer.add(btnClose);
        dlg.add(footer, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    /**
     * Assembles result rows from the ResultManager, merging auto + manual states.
     */
    private java.util.List<ResultManager.ResultRow> buildResultRows() {
        java.util.List<ResultManager.ResultRow> out = new java.util.ArrayList<>();
        for (com.amhs.swim.test.util.TestResult res : ResultManager.getInstance().getResults()) {
            String combined = res.getAutoResult();
            CaseSessionState state = ResultManager.getInstance().getState(res.getCaseCode());
            if (state != null) {
                Boolean mp = state.getMsgPass(res.getMessageIndex());
                if (mp != null) combined += mp ? " [Manual: PASS]" : " [Manual: FAIL]";
                String note = state.getMsgNote(res.getMessageIndex());
                if (!note.isEmpty()) combined += " (" + note + ")";
            }
            out.add(new ResultManager.ResultRow(
                res.getCaseCode(),
                res.getAttempt(),
                "Msg-" + res.getMessageIndex(),
                res.getPayloadSummary(),
                combined
            ));
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settings Popup Dialog
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows the settings as a modal popup dialog instead of a sidebar tab.
     * This keeps the test case tree always visible on the left.
     */
    private void showSettingsDialog() {
        JDialog dlg = new JDialog(this, "Tool Settings", true);
        dlg.setSize(560, 520);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());

        JLabel hdr = new JLabel("  AMHS/SWIM Gateway — Tool Settings");
        hdr.setFont(new Font("SansSerif", Font.BOLD, 13));
        hdr.setForeground(clrAccent);
        hdr.setOpaque(true);
        hdr.setBackground(bgHeader);
        hdr.setBorder(new EmptyBorder(10, 12, 10, 12));
        dlg.add(hdr, BorderLayout.NORTH);
        dlg.add(buildSettingsPanel(), BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        footer.setBackground(bgHeader);
        JButton btnClose = settingsBtn("Close");
        btnClose.addActionListener(ev -> dlg.dispose());
        footer.add(btnClose);
        dlg.add(footer, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settings panel
    // ─────────────────────────────────────────────────────────────────────────

    private JScrollPane buildSettingsPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBackground(bgSidebar);
        configPanel.setBorder(new EmptyBorder(10, 12, 10, 12));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        // ── Broker Profile ──
        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 0;
        configPanel.add(label("AMQP Broker Profile:"), gc);
        gc.gridx = 1; gc.gridwidth = 2; gc.weightx = 1;
        String[] profiles = {"STANDARD","AZURE_SERVICE_BUS","IBM_MQ","RABBITMQ","SOLACE"};
        JComboBox<String> profileCombo = new JComboBox<>(profiles);
        profileCombo.setSelectedItem(TestConfig.getInstance().getProperty("amqp_broker_profile","STANDARD"));
        styleCombo(profileCombo);
        configPanel.add(profileCombo, gc);

        // ── Host & Port ──
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 0;
        configPanel.add(label("SWIM Broker Host:"), gc);
        gc.gridx = 1; gc.weightx = 0.7;
        JTextField hostField = styledField(TestConfig.getInstance().getProperty("swim.broker.host","localhost"), 14);
        configPanel.add(hostField, gc);
        gc.gridx = 2; gc.weightx = 0; row++;
        configPanel.add(label("Port:"), gc);
        gc.gridx = 3; gc.weightx = 0.3;
        JTextField portField = styledField(TestConfig.getInstance().getProperty("swim.broker.port","5672"), 6);
        configPanel.add(portField, gc); row++;

        // ── Auth ──
        gc.gridy = row; gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 0;
        configPanel.add(label("Username:"), gc);
        gc.gridx = 1; gc.weightx = 0.5;
        JTextField userField = styledField(TestConfig.getInstance().getProperty("swim.broker.user","default"), 10);
        configPanel.add(userField, gc);
        gc.gridx = 2; gc.weightx = 0;
        configPanel.add(label("Password:"), gc);
        gc.gridx = 3; gc.weightx = 0.5;
        JPasswordField passField = new JPasswordField(TestConfig.getInstance().getProperty("swim.broker.password","default"), 10);
        styleTextField(passField);
        configPanel.add(passField, gc); row++;

        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 0;
        configPanel.add(label("VPN:"), gc);
        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1;
        JTextField vpnField = styledField(TestConfig.getInstance().getProperty("swim.broker.vpn","default"), 14);
        configPanel.add(vpnField, gc);

        // ── Topics ──
        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 0;
        configPanel.add(label("Target AMQP Topic:"), gc);
        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1;
        JTextField topicField = styledField(TestConfig.getInstance().getProperty("gateway.default_topic","TEST.TOPIC"), 14);
        configPanel.add(topicField, gc);

        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 1; gc.weightx = 0;
        configPanel.add(label("Test Recipient (AMHS O/R):"), gc);
        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1;
        JTextField recipientField = styledField(TestConfig.getInstance().getProperty("gateway.test_recipient","VVTSYMYX"), 14);
        configPanel.add(recipientField, gc);

        // ── Trace ──
        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 4; gc.weightx = 1;
        JCheckBox traceCheck = new JCheckBox("Enable Deep AMQP Trace (ICAO Compliance)");
        traceCheck.setBackground(bgSidebar);
        traceCheck.setForeground(clrFg);
        boolean traceEnabled = Boolean.parseBoolean(TestConfig.getInstance().getProperty("gateway.trace_enabled","false"));
        traceCheck.setSelected(traceEnabled);
        swimToAmhsTests.getSwimDriver().setTraceEnabled(traceEnabled);
        traceCheck.addActionListener(e -> {
            boolean sel = traceCheck.isSelected();
            swimToAmhsTests.getSwimDriver().setTraceEnabled(sel);
            TestConfig.getInstance().setProperty("gateway.trace_enabled", String.valueOf(sel));
        });
        configPanel.add(traceCheck, gc);

        // ── Buttons ──
        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 0.5;
        JButton checkConnBtn = settingsBtn("Check Connection");
        checkConnBtn.addActionListener(e -> new Thread(() -> {
            swimToAmhsTests.getSwimDriver().testConnection();
        }).start());
        configPanel.add(checkConnBtn, gc);

        gc.gridx = 2; gc.gridwidth = 2; gc.weightx = 0.5;
        JButton saveBtn = settingsBtn("Save & Apply");
        saveBtn.addActionListener(e -> {
            TestConfig cfg = TestConfig.getInstance();
            cfg.setProperty("swim.broker.host", hostField.getText());
            cfg.setProperty("swim.broker.port", portField.getText());
            cfg.setProperty("swim.broker.user", userField.getText());
            cfg.setProperty("swim.broker.password", new String(passField.getPassword()));
            cfg.setProperty("swim.broker.vpn", vpnField.getText());
            cfg.setProperty("gateway.default_topic", topicField.getText());
            cfg.setProperty("gateway.test_recipient", recipientField.getText());
            cfg.setProperty("amqp_broker_profile", (String) profileCombo.getSelectedItem());
            cfg.setProperty("gateway.trace_enabled", String.valueOf(traceCheck.isSelected()));
            cfg.saveConfig();
            swimToAmhsTests = new SwimToAmhsTests();
        });
        configPanel.add(saveBtn, gc);
        row++;

        // ── Reporting Section (Requirement 4) ──
        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 4;
        JLabel rptHdr = new JLabel("Session Reporting");
        rptHdr.setFont(new Font("SansSerif", Font.BOLD, 11));
        rptHdr.setForeground(clrAccent);
        rptHdr.setBorder(new EmptyBorder(15, 0, 5, 0));
        configPanel.add(rptHdr, gc);

        gc.gridy = row++; gc.gridx = 0; gc.gridwidth = 2; gc.weightx = 0.5;
        JButton exportBtn = settingsBtn("Export Report (.xlsx)");
        exportBtn.setBackground(new Color(0x3B, 0x59, 0x48)); // muted green
        exportBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Save Test Report");
            fc.setSelectedFile(new java.io.File("AMHS_SWIM_Test_Report.xlsx"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    String path = fc.getSelectedFile().getAbsolutePath();
                    if (!path.endsWith(".xlsx")) path += ".xlsx";
                    ExcelReportExporter.exportToExcel(path);
                    JOptionPane.showMessageDialog(this, "Report exported successfully to:\n" + path);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Failed to export report: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        configPanel.add(exportBtn, gc);

        gc.gridx = 2; gc.gridwidth = 2; gc.weightx = 0.5;
        JButton clearRptBtn = settingsBtn("Clear Results");
        clearRptBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Clear all recorded test results for this session?", "Confirm Clear", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                ResultManager.getInstance().clear();
            }
        });
        configPanel.add(clearRptBtn, gc);

        // Auto-save individual fields
        autoSave(hostField,      "swim.broker.host");
        autoSave(portField,      "swim.broker.port");
        autoSave(userField,      "swim.broker.user");
        autoSave(vpnField,       "swim.broker.vpn");
        autoSave(topicField,     "gateway.default_topic");
        autoSave(recipientField, "gateway.test_recipient");
        profileCombo.addActionListener(e ->
            TestConfig.getInstance().setProperty("amqp_broker_profile", (String) profileCombo.getSelectedItem()));
        passField.getDocument().addDocumentListener(docListener(() ->
            TestConfig.getInstance().setProperty("swim.broker.password", new String(passField.getPassword()))));

        JScrollPane scroll = new JScrollPane(configPanel);
        scroll.setBackground(bgSidebar);
        scroll.getViewport().setBackground(bgSidebar);
        scroll.setBorder(null);
        return scroll;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Misc helpers
    // ─────────────────────────────────────────────────────────────────────────

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(clrFg);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return l;
    }

    private JTextField styledField(String text, int cols) {
        JTextField f = new JTextField(text, cols);
        styleTextField(f);
        return f;
    }

    private void styleTextField(JTextField f) {
        if (f instanceof JPasswordField) {
            f.setUI(new BasicPasswordFieldUI());
        } else {
            f.setUI(new BasicTextFieldUI());
        }
        f.setOpaque(true);
        f.setBackground(Color.WHITE);
        f.setForeground(Color.BLACK);
        f.setCaretColor(Color.BLACK);
        f.setFont(new Font("Monospaced", Font.PLAIN, 12));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.BLACK),
            BorderFactory.createEmptyBorder(3, 6, 3, 6)
        ));
    }

    private void styleCombo(JComboBox<?> cb) {
        cb.setUI(new BasicComboBoxUI());
        cb.setOpaque(true);
        cb.setBackground(Color.WHITE);
        cb.setForeground(Color.BLACK);
        cb.setFont(new Font("Monospaced", Font.PLAIN, 12));
        cb.setBorder(BorderFactory.createLineBorder(Color.BLACK));
    }

    private JButton settingsBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(btnNormal);
        b.setForeground(clrFg);
        b.setFont(new Font("SansSerif", Font.BOLD, 11));
        b.setFocusPainted(false);
        return b;
    }

    private void autoSave(JTextField field, String key) {
        field.getDocument().addDocumentListener(docListener(() ->
            TestConfig.getInstance().setProperty(key, field.getText())));
    }

    private javax.swing.event.DocumentListener docListener(Runnable action) {
        return new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
            public void removeUpdate (javax.swing.event.DocumentEvent e) { action.run(); }
            public void insertUpdate (javax.swing.event.DocumentEvent e) { action.run(); }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tiny helper — avoids importing CompoundBorder separately
    // ─────────────────────────────────────────────────────────────────────────

    private static class CompoundBorderHelper extends javax.swing.border.CompoundBorder {
        CompoundBorderHelper(javax.swing.border.Border outside, javax.swing.border.Border inside) {
            super(outside, inside);
        }
    }
}