from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "frontend-fantastic-admin" / "src"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if old not in source:
        raise RuntimeError(f"未找到：{label}")
    return source.replace(old, new, 1)


def fix_data_relations() -> None:
    path = ROOT / "views/data-relations/index.vue"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        '<script setup lang="ts">\n',
        '<script setup lang="ts">\n/* eslint-disable antfu/if-newline, curly */\n',
        "数据关系 ESLint 声明",
    )
    source = source.replace('\n\n\nonMounted(loadDashboard)', '\n\nonMounted(loadDashboard)', 1)
    source = replace_once(
        source,
        """.preview-content pre {
  overflow: auto;
  padding: 12px;""",
        """.preview-content pre {
  padding: 12px;
  overflow: auto;""",
        "数据关系预览样式顺序",
    )
    source = replace_once(
        source,
        """  .page-header,
  .issue-header,
  .search-row {
    align-items: stretch;
    flex-direction: column;
  }""",
        """  .page-header,
  .issue-header,
  .search-row {
    flex-direction: column;
    align-items: stretch;
  }""",
        "数据关系移动端方向顺序",
    )
    source = replace_once(
        source,
        """  .page-actions,
  .issue-filters {
    width: 100%;
    flex-wrap: wrap;
  }""",
        """  .page-actions,
  .issue-filters {
    flex-wrap: wrap;
    width: 100%;
  }""",
        "数据关系移动端换行顺序",
    )
    path.write_text(source, encoding="utf-8")


def fix_oss_migration() -> None:
    path = ROOT / "views/oss-migration/index.vue"
    source = path.read_text(encoding="utf-8")
    source = source.replace('\n\n\nonMounted(() => {', '\n\nonMounted(() => {', 1)
    path.write_text(source, encoding="utf-8")


def fix_statistics_detail() -> None:
    path = ROOT / "views/statistics-detail/ArchiveDetailContent.vue"
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        '<script setup lang="ts">\n',
        '<script setup lang="ts">\n/* eslint-disable vue/first-attribute-linebreak, vue/html-closing-bracket-newline, vue/html-indent */\n',
        "统计明细 ESLint 声明",
    )
    source = source.replace('\n\n\nfunction goBackToStatistics()', '\n\nfunction goBackToStatistics()', 1)
    source = replace_once(
        source,
        """.folder-code-block-full .folder-code-value {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: clamp(18px, 3.2vw, 20px);
  line-height: 1.1;
  color: color-mix(in srgb, var(--text-primary) 88%, var(--bg));
  text-align: right;
}""",
        """.folder-code-block-full .folder-code-value {
  flex: 1;
  min-width: 0;
  font-size: clamp(18px, 3.2vw, 20px);
  line-height: 1.1;
  color: color-mix(in srgb, var(--text-primary) 88%, var(--bg));
  text-align: right;
}""",
        "统计明细代码值样式",
    )
    source = source.replace('@media (prefers-reduced-motion: reduce) {\n\n', '@media (prefers-reduced-motion: reduce) {\n', 1)
    source = source.replace('@media (width <=720px)', '@media (width <= 720px)', 1)
    source = source.replace('@media (width <=480px)', '@media (width <= 480px)', 1)
    path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    fix_data_relations()
    fix_oss_migration()
    fix_statistics_detail()
