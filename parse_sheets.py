import pandas as pd
import urllib.request
import os

url = 'https://docs.google.com/spreadsheets/d/12vmw6RrUK3KSYibyYJNxj3e2JC30GvfuNfzWUuTI5k8/export?format=xlsx'
filename = 'spreadsheet.xlsx'
print(f"Downloading {url}...")
urllib.request.urlretrieve(url, filename)

print("Parsing Excel file...")
xl = pd.ExcelFile(filename)
out_file = 'sheets_data.txt'
with open(out_file, 'w', encoding='utf-8') as f:
    for sheet_name in xl.sheet_names:
        if sheet_name.upper().startswith('CTSW1'):
            df = xl.parse(sheet_name)
            f.write(f'--- SHEET: {sheet_name} ---\n')
            df.to_csv(f, index=False)
            f.write('\n\n')
print(f"Data written to {out_file}")
