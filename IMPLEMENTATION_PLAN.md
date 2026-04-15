# Implementation Plan for AMHS/SWIM Gateway Test Tool Enhancements

## Overview
This document outlines the required changes to implement configurable case payloads, modernize the UI, and improve guideline handling.

---

## 1. Configurable Case Payloads via Config File

### Objective
Allow each test case's payload to be configured via a JSON config file instead of loading all defaults each time. Include a "Revert To Default" button.

### Files to Modify
- **New**: `src/main/java/com/amhs/swim/test/config/CaseConfigManager.java` ✓ (Already created)
- **Modified**: `src/main/java/com/amhs/swim/test/gui/TestCasePanel.java`
- **Modified**: `src/main/java/com/amhs/swim/test/testcase/SwimToAmhsTests.java`

### Implementation Steps

#### Step 1.1: Register Default Payloads in SwimToAmhsTests.java
For each test case, register default payloads when messages are created:
```java
// In each TestCase's getMessages() or constructor
CaseConfigManager.getInstance().registerDefault("CTSW101", 1, "CTSW101 Text Payload");
```

#### Step 1.2: Load Configured Payloads in TestCasePanel.java
In `loadTestCase()` and `onMessageSelected()`:
- Check if custom config exists for the case/message
- Load from config file if available, otherwise use defaults

#### Step 1.3: Add Revert To Default Button
Add buttons in the action panel:
- "Revert Message to Default" - reverts current message
- "Revert Case to Defaults" - reverts all messages in current case
- "Revert All to Defaults" - global revert

#### Step 1.4: Save Config on Changes
When user edits payload and sends, save to config file via `CaseConfigManager.saveConfig()`

---

## 2. CTSW1xx Default Payloads Configuration

### Objective
Ensure CTSW1xx cases have configurable default payloads that can be sent like other cases.

### Files to Modify
- **Modified**: `src/main/java/com/amhs/swim/test/testcase/SwimToAmhsTests.java`
- **Modified**: `src/main/java/com/amhs/swim/test/gui/TestFrame.java`

### Implementation Steps

#### Step 2.1: Verify CTSW1xx Messages Have Defaults
Check that all CTSW101-CTSW116 cases properly define default payloads in their `TestMessage` objects.

#### Step 2.2: Enable Configuration for CTSW1xx
The same `CaseConfigManager` mechanism will work for CTSW1xx cases since they use the same message structure.

#### Step 2.3: Ensure Tree Selection Works
In `TestFrame.java`, clicking on CTSW1xx nodes should:
- Select the case
- Allow message selection
- Display configurable payloads

---

## 3. Modernize Payload Display & Add File/Binary Picker

### Objective
Modernize the AMQP content display area and add a button to attach files/binaries from the system (like Ubuntu/Linux Mint file picker).

### Files to Modify
- **Modified**: `src/main/java/com/amhs/swim/test/gui/TestCasePanel.java`

### Implementation Steps

#### Step 3.1: Redesign AMQP Config Area
Replace plain `JTextArea` with a more modern panel containing:
- Structured property display (read-only metadata section)
- Editable payload fields with clear labels
- Syntax highlighting or better formatting

#### Step 3.2: Add File Attachment Button
Add a button "📎 Attach File" or "Browse..." next to payload fields:
```java
JButton btnAttachFile = new JButton("Attach File");
btnAttachFile.addActionListener(e -> {
    JFileChooser fc = new JFileChooser();
    fc.setDialogTitle("Select Binary File");
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    if (fc.showOpenDialog(TestCasePanel.this) == JFileChooser.APPROVE_OPTION) {
        String path = fc.getSelectedFile().getAbsolutePath();
        // Update the payload field with file path
        // Mark as binary type
    }
});
```

#### Step 3.3: Support Multiple Input Types
For each editable field, detect if it's:
- Plain text → show text area
- File path → show file picker button + path label
- Binary → show hex preview option

#### Step 3.4: Visual Improvements
- Use card layout for different payload types
- Add icons for text vs binary
- Show file size and type when file is selected
- Add "Clear" button to remove attachment

---

## 4. Guideline Editing for Current Case

### Objective
Edit the guideline to fit the currently chosen case.

### Files Involved
- **Parsed in**: `src/main/java/com/amhs/swim/test/util/TestbookLoader.java`
- **Source**: `cases.json`
- **Display location**: `TestCasePanel.java` → `descriptionArea`

### Where Guidelines Are Parsed

The guideline/description loading flow is:

1. **Source File**: `/workspace/cases.json`
   - Contains full ICAO testbook descriptions for each case ID (CTSW101, CTSW102, etc.)

2. **Loader Class**: `src/main/java/com/amhs/swim/test/util/TestbookLoader.java`
   - Method: `getDescription(String caseId)` at line 39
   - Loads JSON and returns description for specific case

3. **Display Location**: `src/main/java/com/amhs/swim/test/gui/TestCasePanel.java`
   - In `loadTestCase(BaseTestCase tc)` at line 242:
     ```java
     descriptionArea.setText(TestbookLoader.getDescription(tc.getTestCaseId()));
     ```
   - In `onMessageSelected(TestMessage msg)` at lines 371-373:
     ```java
     String desc = "[MESSAGE REQUIREMENT (ICAO Testbook)]\\n" + msg.getMinText() + "\\n\\n" + 
                   "[SCENARIO DESCRIPTION]\\n" + TestbookLoader.getDescription(currentCase.getTestCaseId());
     descriptionArea.setText(desc);
     ```

### Implementation Steps

#### Step 4.1: Dynamic Guideline Updates
The guideline already updates when case changes. To make it more case-specific:
- Parse the case ID from `currentCase.getTestCaseId()`
- Load matching description from `cases.json`
- Display both message-specific requirements AND case scenario

#### Step 4.2: Optional Enhancement - Editable Guidelines
If guidelines need to be editable:
- Add edit toggle button
- Save edited guidelines to separate config file
- Maintain original in `cases.json` as reference

---

## Summary of Files to Create/Modify

### New Files
1. `src/main/java/com/amhs/swim/test/config/CaseConfigManager.java` ✓ Created
2. `config/case_payloads.json` (auto-created on first save)

### Modified Files
1. `src/main/java/com/amhs/swim/test/gui/TestCasePanel.java`
   - Add revert buttons
   - Add file attachment functionality
   - Integrate CaseConfigManager
   - Modernize payload display

2. `src/main/java/com/amhs/swim/test/testcase/SwimToAmhsTests.java`
   - Register default payloads for all cases including CTSW1xx

3. `src/main/java/com/amhs/swim/test/gui/TestFrame.java`
   - Ensure proper case selection flow
   - Add config save on window close

4. `src/main/java/com/amhs/swim/test/config/TestConfig.java`
   - Add call to CaseConfigManager.saveConfig() in saveConfig()

---

## Execution Order

1. ✅ Create CaseConfigManager.java
2. Modify SwimToAmhsTests.java to register defaults
3. Modify TestCasePanel.java:
   - Add revert buttons
   - Add file picker
   - Integrate config manager
4. Modify TestConfig.java to save case configs
5. Test all CTSW1xx cases
6. Verify guideline display

---

## Notes

- The guideline parsing happens in `TestbookLoader.getDescription()` which reads from `cases.json`
- All CTSW1xx cases already have message structures; they just need config integration
- File picker should use native Linux dialog (JFileChooser does this automatically)
- Config file format: JSON with case IDs as keys, message indices as sub-keys
