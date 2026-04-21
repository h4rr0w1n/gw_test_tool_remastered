import json
import re

with open('cases.json', 'r', encoding='utf-8') as f:
    cases = json.load(f)

# Hardcoded table definitions representing EUR Doc 047 Sec 4.4/4.5 mappings for the tool payloads
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

updated_cases = {}
for case_id, desc in cases.items():
    # 1. Clean up "EUR Doc 047" appendix junk from text
    cleaned_desc = re.sub(r'(?i)EUR Doc 047.*?AST PG.*?\n\n|Ref Doc 047.*|Test class.*', '', desc, flags=re.DOTALL)
    
    # 2. Extract requirement text ("From..." to before "Check"/"Verify" if possible, but the prompt says 
    # "copy from the relevant testing requirement, text only". The cleaned_desc is essentially that.)
    # We will remove leading "Scenario\ndescription\n"
    req_text = re.sub(r'(?i)Scenario\s*description', '', cleaned_desc).strip()
    
    # 3. Construct the layout
    layout = f"""---------------------------------------------------------------------------------
CASE {case_id}
---------------------------------------------------------------------------------
TEST REQUIREMENT: 
{req_text}
---------------------------------------------------------------------------------
PAYLOAD:
{tables.get(case_id, "Conversion table missing for this specific case")}
---------------------------------------------------------------------------------"""

    updated_cases[case_id] = layout

with open('cases.json', 'w', encoding='utf-8') as f:
    json.dump(updated_cases, f, indent=2)

print('Updated cases.json!')
