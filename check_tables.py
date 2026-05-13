import json
import re

# Load the cases.json
with open('cases.json', 'r', encoding='utf-8') as f:
    cases = json.load(f)

# Hardcoded table definitions from update_cases.py
tables = {
    'CTSW101': """| AMQP Property | Value | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| priority | 4 | ATS-message-priority (precedence) | 4.5.1.1, Table 9 |
| amqp-value | <Text Payload> | ATS-message-text (ia5-text) | 4.5.1.4, 4.5.2.14 |
| data | <Binary Payload> | file-transfer-body-part | 4.5.1.4, 4.5.2.13 |
| amhs_recipients | <Recipient Address> | primary-recipient | 4.5.2.9 |
| (creation-time) | <Filing Time> | ATS-message-Filing-Time | 4.5.2.10 |""",
    'CTSW102': """| AMQP Property | Value | Expected AMHS Action | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| priority | 10 | Reject, Log, Report | 4.5.1.1 |
| message-id | (empty) | Reject, Log, Report | 4.5.1.2 |
| creation-time | 0 | Reject, Log, Report | 4.5.1.3 |
| amqp-value | (empty) | Reject, Log, Report | 4.5.1.4 |
| data | (empty) | Reject, Log, Report | 4.5.1.4 |
| amhs_recipients | (empty or >8 char) | Reject, Log, Report | 4.5.1.5 |""",
    'CTSW103': """| AMQP Property | Value / Condition | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------------------|----------------------|-----------------|
| Service Level | basic | ATS-Message-Header | 3.3.3, 4.5.3.9 |
| Service Level | extended | originators-reference, Authorization-time, precedence | 3.3.3, 4.5.3.7 |
| Service Level | content-based | (text->basic, bin->reject/ext) | 3.3.3 |
| Service Level | recipient-based | (mixed capabilities) | 3.3.3 |""",
    'CTSW104': """| AMQP Property | Value | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| priority / amhs_ats_pri | 0-9 / SS,DD,FF,GG,KK | ATS-message-priority | 4.5.2.2, Table 9 |""",
    'CTSW105': """| AMQP Property | Value | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| amhs_ats_ft | 250102 (or empty) | ATS-message-Filing-Time | 4.5.2.10 |""",
    'CTSW106': """| AMQP Property | Value | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| amhs_ats_ohi | <53, =53, >53 chars | originators-reference (trimmed) | 4.5.2.11 |""",
    'CTSW107': """| AMQP Property | Value | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| subject / amhs_subject | >128 chars, empty | subject (trimmed to 128) | 4.5.2.3 |""",
    'CTSW108': """| AMQP Property | Value | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| amhs_originator | 8-char known address | originator (this-IPM) | 4.5.2.12, 4.5.3.5 |""",
    'CTSW109': """| AMQP Property | Value | Expected AMHS Action | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| amhs_originator | unknown address | Replaced with Default Originator, Logged | 4.5.2.12 |""",
    'CTSW110': """| AMQP Property | Value | Expected AMHS Action | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| content-type | mismatch with data/value| Reject, Log | 4.5.1.6 |""",
    'CTSW111': """| AMQP Property | Value | Expected AMHS Action | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| Payload Size | > Max Configured Bytes | Reject, Log | 4.5.1.7 |""",
    'CTSW112': """| AMQP Property | Value | Expected AMHS Action | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| amhs_recipients | > Max Num Configured | Reject, Log | 4.5.1.8 |""",
    'CTSW113': """| AMQP Property | Value | Expected AMHS Action | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| amhs_notification_request | rn, nrn | Log, Store for Control Position | 4.5.3.4, 4.4.7.3 |""",
    'CTSW114': """| AMHS Event | Generated Report | Gateway Action | EUR Doc 047 Ref |
|------------|------------------|----------------|-----------------|
| NDR (non-delivery) | unable-to-transfer | Log, Report to Control Position | 4.4.1.3 |""",
    'CTSW115': """| AMQP Property | Value | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| amhs_bodypart_type | ia5-text, general-text | original-encoded-information-types | 4.5.2.4, 4.5.4.7, Table 10 |
| amhs_content_encoding | IA5, ISO-646, ISO-8859-1 | original-encoded-information-types | 4.5.2.4, Table 11 |""",
    'CTSW116': """| AMQP Property | Value | AMHS (X.400) Element | EUR Doc 047 Ref |
|---------------|-------|----------------------|-----------------|
| amhs_ftbp_file_name | <FileName> | incomplete-pathname (file-transfer-body-part) | 4.5.2.6 |
| amhs_ftbp_object_size | <Size> | actual-values | 4.5.2.7 |
| amhs_ftbp_last_mod | <DDMMYYhhmmssZ> | date-and-time-of-last-modification | 4.5.2.8 |
| swim_compression | gzip | Auto-decompressed by Gateway | 4.5.2 |"""
}

def extract_payload_table(case_text):
    # Find the PAYLOAD section
    payload_start = case_text.find('PAYLOAD:')
    if payload_start == -1:
        return None
    # Move past 'PAYLOAD:'
    payload_start += len('PAYLOAD:')
    # Find the next separator (line of dashes) or end of string
    payload_end = case_text.find('---------------------------------------------------------------------------------', payload_start)
    if payload_end == -1:
        payload_end = len(case_text)
    # Extract the table string
    table_lines = case_text[payload_start:payload_end].strip().split('\n')
    # Remove empty lines
    table_lines = [line.strip() for line in table_lines if line.strip()]
    # Join back to a string
    return '\n'.join(table_lines)

# Check each case
misaligned = []
for case_id, case_text in cases.items():
    if case_id not in tables:
        print(f"Case {case_id} not found in hardcoded tables.")
        continue
    extracted = extract_payload_table(case_text)
    expected = tables[case_id]
    if extracted != expected:
        misaligned.append(case_id)
        print(f"Misalignment found in case {case_id}")
        print("Extracted table:")
        print(extracted)
        print("Expected table:")
        print(expected)
        print("="*50)

if not misaligned:
    print("All cases are aligned.")
else:
    print(f"Misaligned cases: {misaligned}")