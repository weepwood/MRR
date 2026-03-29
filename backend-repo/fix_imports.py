import os
import re

base_dir = r'd:\Project\MRR\backend-repo\src'
class_pkg = {
    'AuthRole': 'entity', 'AuthUser': 'entity', 'Log': 'entity', 'Patient': 'entity',
    'PathDO': 'entity', 'Scan': 'entity', 'Statistics': 'entity', 'User': 'entity',
    'AuthUserProfileDTO': 'dto.resp', 'BAHDataResponseDTO': 'dto.resp', 
    'BAHStatisticsDTO': 'dto.resp', 'DateStatisticsDTO': 'dto.resp',
    'LoginResponseDTO': 'dto.resp', 'LogRetentionCleanupResult': 'dto.resp',
    'AuthUserUpdateRequest': 'dto.req', 'BatchDownloadRequest': 'dto.req',
    'IdRequest': 'dto.req', 'ImageRequest': 'dto.req',
    'ScanRequest': 'dto.req', 'UserRequest': 'dto.req',
    'AuthSession': 'common', 'Result': 'common'
}

def fix_imports(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    has_wildcard = 'import com.zjcxph.imgapi.pojo.*;' in content
    if has_wildcard:
        new_imports = "import com.zjcxph.imgapi.entity.*;\nimport com.zjcxph.imgapi.dto.req.*;\nimport com.zjcxph.imgapi.dto.resp.*;\nimport com.zjcxph.imgapi.common.*;"
        content = content.replace('import com.zjcxph.imgapi.pojo.*;', new_imports)
        
    for cls, pkg in class_pkg.items():
        content = content.replace(f'import com.zjcxph.imgapi.pojo.{cls};', f'import com.zjcxph.imgapi.{pkg}.{cls};')

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

for root, dirs, files in os.walk(base_dir):
    for f in files:
        if f.endswith('.java'):
            fix_imports(os.path.join(root, f))
