import re
import os

def process_xml(path):
    if not os.path.exists(path):
        return
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    def replace_case(m_case):
        case_id = m_case.group(1)
        case_body = m_case.group(2)
        
        def replace_msg(m_msg):
            idx = m_msg.group(1)
            rest = m_msg.group(2)
            cdata_content = m_msg.group(3)
            
            if cdata_content == '' or cdata_content == 'N/A' or cdata_content.startswith('src/main/') or cdata_content.startswith('OVER max'):
                return m_msg.group(0)
                
            clean_text = re.sub(r'^\[?CTSW\d+(-\d+)?\]?\s*', '', cdata_content)
            clean_text = re.sub(r'^CTSW\d+\s+', '', clean_text)
            
            new_text = f'[{case_id}-{idx}] {clean_text}'
            return f'<msg idx="{idx}"{rest}><![CDATA[{new_text}]]></msg>'

        new_body = re.sub(r'<msg idx="(\d+)"([^>]*)><!\[CDATA\[(.*?)\]\]></msg>', replace_msg, case_body)
        return f'<case id="{case_id}">{new_body}</case>'

    new_content = re.sub(r'<case id="(CTSW\d+)">(.*?)</case>', replace_case, content, flags=re.DOTALL)
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print(f'Updated {path}')

process_xml('config/default_case_payloads.xml')
process_xml('src/main/resources/config/default_case_payloads.xml')
