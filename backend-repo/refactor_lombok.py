import os
import re

base_dir = r'd:\Project\MRR\backend-repo\src\main\java\com\zjcxph\imgapi'

def process_file(filepath, new_pkg):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Replace package
    content = re.sub(r'package com\.zjcxph\.imgapi\.pojo;', f'package {new_pkg};', content)
    
    # Add lombok imports if not present
    if 'import lombok.Data;' not in content and 'common' not in new_pkg:
        content = re.sub(r'package (.*?);\n+', r'package \1;\n\nimport lombok.Data;\n\n', content)
        
        # Add @Data before public class
        if '@Data' not in content:
            content = re.sub(r'(public class )', r'@Data\n\1', content, count=1)
            
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

def main():
    mappings = {
        'entity': 'com.zjcxph.imgapi.entity',
        'dto/req': 'com.zjcxph.imgapi.dto.req',
        'dto/resp': 'com.zjcxph.imgapi.dto.resp',
        'common': 'com.zjcxph.imgapi.common'
    }
    for folder, pkg in mappings.items():
        folder_path = os.path.join(base_dir, folder)
        for fname in os.listdir(folder_path):
            if fname.endswith('.java'):
                process_file(os.path.join(folder_path, fname), pkg)
                
if __name__ == '__main__':
    main()
