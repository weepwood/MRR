from pathlib import Path

path = Path('frontend-fantastic-admin/src/views/users/index.vue')
text = path.read_text(encoding='utf-8')

replacements = [
    (
        "} = useTableActionLayout(3, 1)",
        "} = useTableActionLayout(2, 2)",
    ),
    (
        """function userInlineLimit(row: CredentialAwareUser) {
  if (userResponsiveInlineActions.value === 0) {
    return 0
  }
  return isPending(row) ? 2 : 1
}
""",
        """function userHasActions(row: CredentialAwareUser) {
  return ['pending', 'active', 'disabled'].includes(normalizeStatus(row.status))
}

function userInlineLimit(row: CredentialAwareUser) {
  return isPending(row)
    ? userResponsiveInlineActions.value
    : Math.min(userResponsiveInlineActions.value, 1)
}
""",
    ),
    (
        """            <MrrTableActions
              :actions=\"userActions(row)\"
              :max-inline=\"userInlineLimit(row)\"
              @select=\"handleUserAction($event, row)\"
            />
""",
        """            <MrrTableActions
              v-if=\"userHasActions(row)\"
              :actions=\"userActions(row)\"
              :max-inline=\"userInlineLimit(row)\"
              @select=\"handleUserAction($event, row)\"
            />
            <span v-else class=\"no-perm\">审核已结束</span>
""",
    ),
]

for old, new in replacements:
    if old not in text:
        raise SystemExit(f'未找到预期代码片段：{old[:80]!r}')
    text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
