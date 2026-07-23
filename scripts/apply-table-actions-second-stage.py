from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "frontend-fantastic-admin" / "src"


def insert_after_function(source: str, signature: str, addition: str) -> str:
    position = source.find(signature)
    if position < 0:
        raise RuntimeError(f"未找到函数：{signature}")
    opening = source.find("{", position)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[: index + 1] + addition + source[index + 1 :]
    raise RuntimeError(f"函数没有闭合：{signature}")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if old not in source:
        raise RuntimeError(f"未找到替换位置：{label}")
    return source.replace(old, new, 1)


def update_oss_migration() -> None:
    path = ROOT / "views/oss-migration/index.vue"
    source = path.read_text(encoding="utf-8")
    if "const jobActions: MrrTableAction[]" in source:
        return

    source = replace_once(
        source,
        "import type { MigrationLogRecord, MigrationStatistics, OssUploadResult } from '@/api/types'\n",
        "import type { MigrationLogRecord, MigrationStatistics, OssUploadResult } from '@/api/types'\n"
        "import type { MrrTableAction } from '@/components/MrrTableActions/types'\n",
        "OSS 操作类型导入",
    )
    source = replace_once(
        source,
        "} from '@/api/modules/oss'\n\ndefineOptions",
        "} from '@/api/modules/oss'\n"
        "import MrrTableActions from '@/components/MrrTableActions/index.vue'\n"
        "import { useTableActionLayout } from '@/composables/useTableActionLayout'\n\ndefineOptions",
        "OSS 操作组件导入",
    )
    source = replace_once(
        source,
        "const JOB_POLL_INTERVAL = 2_000\n",
        """const JOB_POLL_INTERVAL = 2_000

const jobActions: MrrTableAction[] = [{
  key: 'detail',
  label: '查看任务详情',
  icon: 'i-ri:eye-line',
  tone: 'primary',
  placement: 'inline',
}]
const logViewAction: MrrTableAction = {
  key: 'view-image',
  label: '查看 OSS 图片',
  icon: 'i-ri:image-line',
  tone: 'primary',
  placement: 'inline',
}
const {
  maxInlineActions: jobMaxInlineActions,
  actionColumnWidth: jobActionColumnWidth,
} = useTableActionLayout(jobActions.length, 1)
const {
  maxInlineActions: logMaxInlineActions,
  actionColumnWidth: logActionColumnWidth,
} = useTableActionLayout(1, 1)
""",
        "OSS 操作定义",
    )
    source = insert_after_function(
        source,
        "function openOssUrl(url?: string) {",
        """

function logActions(row: MigrationLogRecord): MrrTableAction[] {
  return row.ossUrl ? [logViewAction] : []
}

function handleJobAction(action: string, row: MigrationJob) {
  if (action === 'detail') {
    void showJobDetail(row)
  }
}

function handleLogAction(action: string, row: MigrationLogRecord) {
  if (action === 'view-image' && row.ossUrl) {
    openOssUrl(row.ossUrl)
  }
}
""",
    )
    source = replace_once(
        source,
        """        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showJobDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>""",
        """        <el-table-column
          label="操作"
          :width="jobActionColumnWidth"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <MrrTableActions
              :actions="jobActions"
              :max-inline="jobMaxInlineActions"
              @select="handleJobAction($event, row)"
            />
          </template>
        </el-table-column>""",
        "OSS 任务操作列",
    )
    source = replace_once(
        source,
        """        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.ossUrl"
              link
              type="primary"
              @click="openOssUrl(row.ossUrl)"
            >
              查看图片
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>""",
        """        <el-table-column
          label="操作"
          :width="logActionColumnWidth"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <MrrTableActions
              :actions="logActions(row)"
              :max-inline="logMaxInlineActions"
              @select="handleLogAction($event, row)"
            />
          </template>
        </el-table-column>""",
        "OSS 日志操作列",
    )
    path.write_text(source, encoding="utf-8")


def update_data_relations() -> None:
    path = ROOT / "views/data-relations/index.vue"
    source = path.read_text(encoding="utf-8")
    if "const issueActions: MrrTableAction[]" in source:
        return

    source = replace_once(
        source,
        "} from '@/api/modules/data-relations'\nimport { ElMessage }",
        "} from '@/api/modules/data-relations'\n"
        "import type { MrrTableAction } from '@/components/MrrTableActions/types'\n"
        "import { ElMessage }",
        "数据关系操作类型导入",
    )
    source = replace_once(
        source,
        "} from '@/api/modules/data-relations'\n\ndefineOptions",
        "} from '@/api/modules/data-relations'\n"
        "import MrrTableActions from '@/components/MrrTableActions/index.vue'\n"
        "import { useTableActionLayout } from '@/composables/useTableActionLayout'\n\ndefineOptions",
        "数据关系操作组件导入",
    )
    source = replace_once(
        source,
        "const previewVisible = ref(false)\n",
        """const previewVisible = ref(false)

const issueActions: MrrTableAction[] = [
  {
    key: 'locate',
    label: '定位病案',
    icon: 'i-ri:map-pin-line',
    tone: 'primary',
    placement: 'inline',
  },
  {
    key: 'preview',
    label: '修复预览',
    icon: 'i-ri:tools-line',
    tone: 'warning',
  },
]
const {
  maxInlineActions: issueMaxInlineActions,
  actionColumnWidth: issueActionColumnWidth,
} = useTableActionLayout(issueActions.length, 2)
""",
        "数据关系操作定义",
    )
    source = insert_after_function(
        source,
        "async function locateIssueArchive(issue: DataQualityIssue) {",
        """

function handleIssueAction(action: string, issue: DataQualityIssue) {
  if (action === 'locate') {
    void locateIssueArchive(issue)
  }
  else if (action === 'preview') {
    void showRepairPreview(issue)
  }
}
""",
    )
    source = replace_once(
        source,
        """              <el-table-column label="操作" width="210" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="locateIssueArchive(row)">
                    定位病案
                  </el-button>
                  <el-button link type="warning" @click="showRepairPreview(row)">
                    修复预览
                  </el-button>
                </template>
              </el-table-column>""",
        """              <el-table-column
                label="操作"
                :width="issueActionColumnWidth"
                fixed="right"
                align="center"
              >
                <template #default="{ row }">
                  <MrrTableActions
                    :actions="issueActions"
                    :max-inline="issueMaxInlineActions"
                    @select="handleIssueAction($event, row)"
                  />
                </template>
              </el-table-column>""",
        "数据关系异常操作列",
    )
    path.write_text(source, encoding="utf-8")


def update_statistics_detail() -> None:
    path = ROOT / "views/statistics-detail/ArchiveDetailContent.vue"
    source = path.read_text(encoding="utf-8")
    if "const archiveActions: MrrTableAction[]" in source:
        return

    source = replace_once(
        source,
        "import type { StatisticsRecord, StatisticsSummary, TypeStatistics } from '@/api/types'\n",
        "import type { StatisticsRecord, StatisticsSummary, TypeStatistics } from '@/api/types'\n"
        "import type { MrrTableAction } from '@/components/MrrTableActions/types'\n",
        "统计明细操作类型导入",
    )
    source = replace_once(
        source,
        "import AppLoading from '@/components/AppLoading/index.vue'\n",
        "import AppLoading from '@/components/AppLoading/index.vue'\n"
        "import MrrTableActions from '@/components/MrrTableActions/index.vue'\n"
        "import { useTableActionLayout } from '@/composables/useTableActionLayout'\n",
        "统计明细操作组件导入",
    )
    source = replace_once(
        source,
        "const selectedArchiveKey = ref('')\n",
        """const selectedArchiveKey = ref('')

const archiveActions: MrrTableAction[] = [{
  key: 'view',
  label: '查看影像',
  icon: 'i-ri:eye-line',
  tone: 'primary',
  placement: 'inline',
}]
const {
  maxInlineActions: archiveMaxInlineActions,
  actionColumnWidth: archiveActionColumnWidth,
} = useTableActionLayout(archiveActions.length, 1)
""",
        "统计明细操作定义",
    )
    source = insert_after_function(
        source,
        "function openArchive(item = selectedArchive.value) {",
        """

function handleArchiveAction(action: string, item: ArchiveItem) {
  if (action === 'view') {
    openArchive(item)
  }
}
""",
    )
    source = replace_once(
        source,
        """            <el-table-column label="操作" width="96" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openArchive(row)">
                  查看影像
                </el-button>
              </template>
            </el-table-column>""",
        """            <el-table-column
              label="操作"
              :width="archiveActionColumnWidth"
              fixed="right"
              align="center"
            >
              <template #default="{ row }">
                <MrrTableActions
                  :actions="archiveActions"
                  :max-inline="archiveMaxInlineActions"
                  @select="handleArchiveAction($event, row)"
                />
              </template>
            </el-table-column>""",
        "统计明细影像操作列",
    )
    path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    update_oss_migration()
    update_data_relations()
    update_statistics_detail()
