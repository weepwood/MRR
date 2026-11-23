<script setup lang="ts">
import type { ArchiveSearchHistoryItem } from '@/views/statistics/archive/composables/useArchiveSearchHistory'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getArchiveLookupValidationMessage, normalizeMedicalRecordCode, requiresSjhForBah } from '@/utils/medical-record-code'
import ArchiveRecentLookups from '@/views/statistics/archive/components/ArchiveRecentLookups.vue'

/*
 * 首页的主任务入口：直接按病案号 / 上架号打开影像档案袋，并列出本人最近成功的查询。
 * 校验与档案袋页共用 getArchiveLookupValidationMessage，跳转后由档案袋页按路由参数发起查询，
 * 不在首页重复请求影像接口。最近查询列表与档案袋空态共用 ArchiveRecentLookups。
 */

defineOptions({ name: 'HomeArchiveLookup' })

const ARCHIVE_PATH = '/archive/embed'

const router = useRouter()
const form = reactive({ bah: '', sjh: '' })
const errorMessage = ref('')

const needsSjh = computed(() => {
  const bah = normalizeMedicalRecordCode(form.bah)
  return Boolean(bah) && requiresSjhForBah(bah) && !normalizeMedicalRecordCode(form.sjh)
})

function openArchive(bah: string, sjh: string) {
  const query: Record<string, string> = {}
  if (bah) {
    query.bah = bah
  }
  if (sjh) {
    query.sjh = sjh
  }
  return router.push({ path: ARCHIVE_PATH, query })
}

function openRecent(item: ArchiveSearchHistoryItem) {
  return openArchive(item.bah, item.sjh)
}

async function submit() {
  const bah = normalizeMedicalRecordCode(form.bah)
  const sjh = normalizeMedicalRecordCode(form.sjh)
  const message = getArchiveLookupValidationMessage(bah, sjh)
  errorMessage.value = message
  if (message) {
    return
  }
  await openArchive(bah, sjh)
}

function clearError() {
  errorMessage.value = ''
}
</script>

<template>
  <section class="home-lookup" aria-label="病案影像查询">
    <form class="home-lookup__search" novalidate @submit.prevent="submit">
      <div class="home-lookup__copy">
        <h2 id="home-lookup-title">
          查询病案影像
        </h2>
        <p>病案号或上架号填写其一即可；病案号大于等于 10000000 时须同时填写上架号。</p>
      </div>

      <div class="home-lookup__fields">
        <label class="home-lookup__field">
          <span>病案号</span>
          <el-input
            v-model="form.bah"
            class="home-lookup__input"
            inputmode="numeric"
            autocomplete="off"
            maxlength="8"
            clearable
            placeholder="8 位以内数字"
            :aria-invalid="Boolean(errorMessage) || undefined"
            aria-describedby="home-lookup-error"
            @input="clearError"
          />
        </label>
        <label class="home-lookup__field">
          <span>上架号<em v-if="needsSjh">（必填）</em></span>
          <el-input
            v-model="form.sjh"
            class="home-lookup__input"
            inputmode="numeric"
            autocomplete="off"
            maxlength="8"
            clearable
            placeholder="可选"
            :aria-invalid="Boolean(errorMessage) || undefined"
            aria-describedby="home-lookup-error"
            @input="clearError"
          />
        </label>
        <el-button class="home-lookup__submit" type="primary" native-type="submit">
          <FaIcon name="i-lucide:folder-open" />
          打开档案袋
        </el-button>
      </div>

      <p id="home-lookup-error" class="home-lookup__error" role="alert" aria-live="polite">
        {{ errorMessage }}
      </p>
    </form>

    <ArchiveRecentLookups class="home-lookup__recent" load @select="openRecent">
      <template #actions>
        <RouterLink class="home-lookup__more" :to="ARCHIVE_PATH">
          进入档案袋
        </RouterLink>
      </template>
      <template #empty>
        还没有查询记录。在左侧输入编号，或从影像档案袋查询后，最近 6 次会显示在这里。
      </template>
    </ArchiveRecentLookups>
  </section>
</template>

<style scoped>
/*
 * 首页主区：查询（主）+ 最近查询（辅），两栏同高、同一块卡面。
 * 只用令牌；与页面其他卡片同一套表面、描边、圆角。
 */
.home-lookup {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  min-width: 0;
  overflow: hidden;
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
  box-shadow: var(--mrr-shadow-xs);
}

.home-lookup__search {
  display: grid;
  gap: var(--mrr-space-4);
  align-content: start;
  min-width: 0;
  padding: var(--mrr-space-6) var(--mrr-card-inset);
}

.home-lookup__copy h2 {
  margin: 0;
  font-size: var(--mrr-font-size-lg);
  font-weight: 650;
  line-height: 1.35;
  color: var(--mrr-foreground);
}

.home-lookup__copy p {
  margin: var(--mrr-space-1) 0 0;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.6;
  color: var(--mrr-muted-foreground);
}

.home-lookup__fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: var(--mrr-space-3);
  align-items: end;
}

.home-lookup__field {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
}

.home-lookup__field > span {
  font-size: var(--mrr-font-size-xs);
  font-weight: 600;
  color: var(--mrr-foreground);
}

.home-lookup__field em {
  font-style: normal;
  color: var(--mrr-tone-warning);
}

.home-lookup__input :deep(.el-input__inner) {
  font-family: var(--mrr-font-mono);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.home-lookup__submit :deep(svg),
.home-lookup__submit :deep(.icon) {
  margin-right: var(--mrr-space-1);
}

/* 预留一行高度，出现校验提示时下方不跳动。 */
.home-lookup__error {
  min-height: 1.5em;
  margin: calc(var(--mrr-space-2) * -1) 0 0;
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-destructive);
}

.home-lookup__recent {
  --recent-lookups-hover: var(--mrr-card);

  padding: var(--mrr-space-6) var(--mrr-card-inset);
  background: var(--mrr-workspace-bg);
  border-left: 1px solid var(--mrr-border);
}

.home-lookup__more {
  font-size: var(--mrr-font-size-sm);
  color: var(--mrr-primary);
  text-decoration: none;
}

.home-lookup__more:hover {
  text-decoration: underline;
}

.home-lookup__more:focus-visible {
  outline: 2px solid var(--mrr-ring);
  outline-offset: 2px;
}

@media (max-width: 1100px) {
  .home-lookup {
    grid-template-columns: minmax(0, 1fr);
  }

  .home-lookup__recent {
    border-top: 1px solid var(--mrr-border);
    border-left: 0;
  }
}

@media (max-width: 760px) {
  .home-lookup__search,
  .home-lookup__recent {
    padding: var(--mrr-space-5) var(--mrr-card-inset-compact);
  }

  .home-lookup__fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .home-lookup__submit {
    width: 100%;
  }
}
</style>
