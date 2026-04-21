# Java Codebase Analysis: Description Parsing, AMQP Properties, and GZIP Handling

## 1. DESCRIPTION PARSING & DISPLAY LOGIC

### Primary Location: `TestbookLoader.java`
**File Path:** [src/main/java/com/amhs/swim/test/util/TestbookLoader.java](src/main/java/com/amhs/swim/test/util/TestbookLoader.java)

#### Key Methods:
1. **`getDescription(String caseId)`** (Lines 39-47)
   - Loads full description text for a specific case ID from JSON data
   - Calls `cleanDescription()` to process raw text
   - Returns formatted description or default message if not found

2. **`cleanDescription(String raw)`** (Lines 51-74)
   - **Mini-Section Mark Parsing:** Line 73 contains the key regex pattern:
     ```java
     trimmed.matches("^\\d+\\.\\d+.*$") // section numbers like "4.5.2.2"
     ```
   - This pattern **FILTERS OUT** section numbers during cleanup, removing lines matching patterns like:
     - "4.5.4.27"
     - "4.5.2.10"
     - "4.5.2.2"
     - etc.
   
   - Also removes:
     - Page numbers (`^\d+$`)
     - Dates (`^\d{2}/\d{2}/\d{4}$`)
     - Document references ("EUR Doc 047", "Ref Doc 047")
     - Form feeds and blank lines

#### Note on Hyperlinks:
- **NO hyperlink conversion found** in the codebase. Section marks are **removed** during cleaning, not converted to hyperlinks.

---

## 2. GUI DESCRIPTION DISPLAY

### Location: `TestCasePanel.java`
**File Path:** [src/main/java/com/amhs/swim/test/gui/TestCasePanel.java](src/main/java/com/amhs/swim/test/gui/TestCasePanel.java)

#### Description Display Components:
1. **Field Declaration** (Line 53)
   ```java
   private JTextArea descriptionArea;
   ```

2. **UI Setup** (Lines 306-324)
   - Creates a read-only `JTextArea` with line wrapping enabled
   - Styled with:
     - Background: `bgRowEven`
     - Foreground: `clrFg`
     - Font: Default
   - Placed in a `JScrollPane` for scrollable display

3. **Populating Description** (Lines 421, 556)
   - **Line 421:** Called when loading a test case with no messages:
     ```java
     descriptionArea.setText(TestbookLoader.getDescription(tc.getTestCaseId()));
     ```
   - **Line 556:** Adds scenario header and description when loading a case:
     ```java
     descriptionArea.setText("[SCENARIO DESCRIPTION]\n" + 
         TestbookLoader.getDescription(currentCase.getTestCaseId()));
     ```

#### Caret Position Control:
- Lines 416, 423, 558: Resets caret to position 0 for clean scrolling

---

## 3. AMQP PROPERTIES PARSING & HANDLING

### Primary Location: `Validator.java`
**File Path:** [src/main/java/com/amhs/swim/test/util/Validator.java](src/main/java/com/amhs/swim/test/util/Validator.java)

#### AMQPProperties Class (Lines 103-134)
Inner static class containing:
- **`Integer priority`** - AMQP message priority
- **`String messageId`** - Unique message identifier
- **`Long creationTime`** - Message creation timestamp
- **`byte[] data`** - Binary payload
- **`String amqpValue`** - Text/string payload
- **`String recipients`** - AMQP recipients
- **`String contentType`** - Message content type

#### Validation Method: `validateAmqpMinimumFields()` (Lines 73-102)
Validates mandatory AMQP fields per EUR Doc 047 CTSW102:
- Priority (non-null)
- Message-ID (non-empty)
- Creation-Time (non-null)
- Recipients (non-empty list)
- Content-Type (non-empty)
- Data OR amqp-value (at least one must exist)

#### Extended AMQP Properties in SwimDriver
**File:** [src/main/java/com/amhs/swim/test/driver/SwimDriver.java](src/main/java/com/amhs/swim/test/driver/SwimDriver.java)

Class `AMQPProperties` (SwimDriver inner class) includes:
- `amqpPriority`
- `ipmId` (IPM Identifier)
- `registeredId` (Registered Identifier)
- `userVisibleStr`
- `contentType`
- `bodyType` (DATA, AMQP_VALUE, AMQP_SEQUENCE)
- `recipients` (parsed as List<String>)
- URI/address parsing capabilities

#### URI/Address Parsing:
- **File Loading:** `loadAddressFile()` in [SwimToAmhsTests.java](src/main/java/com/amhs/swim/test/testcase/SwimToAmhsTests.java) (Lines 63-86)
  - Parses AFTN address files line-by-line
  - Filters blank lines
  - Returns array of valid addresses
  
- **Property Parsing in QpidSwimAdapter:**
  - **Recipients parsing** (Line 415-427):
    ```java
    if (key.equals("amhs_recipients") && value instanceof String) {
        String recips = (String) value;
        if (!recips.isEmpty()) {
            String[] split = recips.split(",");
            java.util.List<String> list = new java.util.ArrayList<>();
            for (String s : split) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
            }
            value = list;
        }
    }
    ```

---

## 4. GZIP COMPRESSION HANDLING

### Import Declaration
**File:** [src/main/java/com/amhs/swim/test/testcase/SwimToAmhsTests.java](src/main/java/com/amhs/swim/test/testcase/SwimToAmhsTests.java#L15)
```java
import java.util.zip.GZIPOutputStream;
```

### Technical Metadata Flag: `swim_compression`
**Location:** Referenced in both adapter implementations

1. **QpidSwimAdapter** [src/main/java/com/amhs/swim/test/driver/QpidSwimAdapter.java](src/main/java/com/amhs/swim/test/driver/QpidSwimAdapter.java)
   - Line 394: Property check for `swim_compression`
   - Line 823-830: `isTechnicalProperty()` method:
     ```java
     private boolean isTechnicalProperty(String key) {
         return key.equals("swim_compression") || 
                key.startsWith("amhs_ftbp_") || 
                key.equals("amhs_ipm_id") ||
                key.equals("amhs_registered_identifier") ||
                key.equals("amhs_bodypart_type") ||
                key.equals("content_type") ||
                key.startsWith("amqp_");
     }
     ```

2. **SolaceSwimAdapter** [src/main/java/com/amhs/swim/test/driver/SolaceSwimAdapter.java](src/main/java/com/amhs/swim/test/driver/SolaceSwimAdapter.java)
   - Line 375: Same `isTechnicalProperty()` implementation
   - Prevents sanitization of compression metadata on Solace broker

### Usage Context:
- `swim_compression` is treated as **technical metadata** that should NOT be sanitized
- It's passed through to AMQP application properties without modification
- GZIP compression operations are imported but the actual compression logic is not fully visible in the shown code sections
- Likely used for payload compression when specified in AMQP properties

---

## 5. URI SANITIZATION FOR BROKERS

### Solace-Specific Sanitization
**File:** [SolaceSwimAdapter.java](src/main/java/com/amhs/swim/test/driver/SolaceSwimAdapter.java) Lines 381-387

```java
private String sanitizeForSolace(String input) {
    if (input == null) return null;
    // Solace WebUI throws 'Malformed URI sequence' if it sees a stray % 
    // We replace it to be safe for display purposes in the manager console.
    return input.replace("%", "_pct_");
}
```

**Purpose:** Prevents "Malformed URI sequence" errors in Solace Web UI by replacing percent signs in properties

---

## 6. PAYLOAD CONFIGURATION MANAGEMENT

### Location: `CaseConfigManager.java`
**File Path:** [src/main/java/com/amhs/swim/test/config/CaseConfigManager.java](src/main/java/com/amhs/swim/test/config/CaseConfigManager.java)

#### Supported Configuration Formats:
1. **XML Format** (preferred): `config/default_case_payloads.xml`
   ```xml
   <case id="CTSW101">
       <msg idx="1"><![CDATA[Payload text...]]></msg>
   </case>
   ```

2. **Text Format** (fallback): `config/default_case_payloads.txt`
   ```
   CTSW101|1|Payload text (may contain '|' characters)
   ```

#### Key Methods:
- `getPayload(String caseId, int msgIndex)` - Retrieves configured or default payload
- `registerDefault()` - Sets default payload for a case message

---

## 7. TEST CASE STRUCTURE & REFERENCES

### Mini-Section References in Test Cases
**File:** [SwimToAmhsTests.java](src/main/java/com/amhs/swim/test/testcase/SwimToAmhsTests.java)

#### Example References (NOT converted to hyperlinks, just text):
- Line 125: `§4.5.4.27–4.5.4.31` (trace-information compliance)
- Line 128: `§4.5.4.15` (per-recipient-indicators)
- Line 272: `§4.5.1.1–4.5.1.5, §4.5.2.2, §4.5.2.10`
- Line 399: `§4.5.1.4` (long address validation)
- Line 468: `§3.3.3, §4.5.3.7–4.5.3.9`
- Line 648: `§4.5.2.2` (Priority Mapping Table 9)
- Line 737: `§4.5.2.10, §4.5.2.10.1.b`
- Line 811: `§4.5.2.11`

These are stored as **plain text references** in the criteria strings and manual guides, NOT as hyperlinks.

---

## 8. SUMMARY TABLE

| Feature | Location | Implementation |
|---------|----------|-----------------|
| **Description Loading** | TestbookLoader.java | Loads from cases.json |
| **Section Mark Parsing** | TestbookLoader.java:73 | Regex `^\d+\.\d+.*$` - **REMOVES** marks |
| **Hyperlink Conversion** | N/A | **NOT IMPLEMENTED** - marks are removed |
| **GUI Display** | TestCasePanel.java | JTextArea (read-only, plain text) |
| **AMQP Validation** | Validator.java | validateAmqpMinimumFields() |
| **AMQP Properties** | SwimDriver.java | AMQPProperties class |
| **GZIP Support** | SwimToAmhsTests.java | Imported but logic hidden |
| **Compression Flag** | Both Adapters | `swim_compression` treated as technical metadata |
| **URI Sanitization** | SolaceSwimAdapter.java | Replace `%` with `_pct_` |
| **Payload Config** | CaseConfigManager.java | XML and text-based configuration |

---

## KEY FINDINGS

1. **Description Section Marks:** Mini-section marks (e.g., "4.5.2.10") are **FILTERED OUT** from displayed descriptions during the `cleanDescription()` process—they are not converted to hyperlinks.

2. **Plain Text References:** Section references in test case criteria are stored as **plain text** (e.g., "Ref: EUR Doc 047 §4.5.1.1–4.5.1.5") without hyperlink functionality.

3. **AMQP Property Support:** Full support for AMQP properties including priority, recipients, content-type, creation-time, message-ID, and body type selection.

4. **GZIP Compression:** GZIP support is imported but the actual compression/decompression logic is not visible in the current code analysis. The `swim_compression` property is treated as technical metadata that bypasses sanitization.

5. **Broker Compatibility:** Solace broker has special URI sanitization to prevent malformed sequences, while Qpid/AMQP follows standard protocols.

6. **Configuration Sources:** Payloads can be loaded from XML configuration files, text files, or specified via UI inputs.
