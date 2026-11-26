<script setup lang="ts">
import type { ExampleId } from './catalog'
import { computed, reactive, ref } from 'vue'
import { categories, examples } from './catalog'
import BasicExamples from './components/BasicExamples.vue'
import BusinessExamples from './components/BusinessExamples.vue'
import DataExamples from './components/DataExamples.vue'
import FeedbackExamples from './components/FeedbackExamples.vue'
import PatternExamples from './components/PatternExamples.vue'
import SpecialExamples from './components/SpecialExamples.vue'
import TokenExamples from './components/TokenExamples.vue'

defineOptions({ name: 'UiComponentGallery' })

const keyword = ref('')
const category = ref<string>('all')
const revision = ref(0)
const exampleRevisions = reactive<Partial<Record<ExampleId, number>>>({})
const compactExamples = reactive<Partial<Record<ExampleId, boolean>>>({})
function resetExample(id: ExampleId) {
  exampleRevisions[id] = (exampleRevisions[id] ?? 0) + 1
}
function togglePreview(id: ExampleId) {
  compactExamples[id] = !compactExamples[id]
}
const visibleExamples = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return examples.filter(example => (category.value === 'all' || example.category === category.value)
    && [example.title, example.description, ...example.components].join(' ').toLowerCase().includes(query))
})

function resetExamples() {
  keyword.value = ''
  category.value = 'all'
  revision.value += 1
  for (const id of Object.keys(compactExamples) as ExampleId[]) { delete compactExamples[id] }
}
</script>

<template>
  <MrrPageShell class="component-gallery">
    <MrrPageHeader title="UI 组件" description="集中查看项目组件的真实外观与交互。使用合成示例，方便设计验收与页面搭建。">
      <template #actions>
        <el-button class="mrr-button-flow" :auto-insert-space="false" @click="resetExamples">
          <FaIcon name="i-lucide:rotate-ccw" aria-hidden="true" />
          重置所有示例
        </el-button>
      </template>
    </MrrPageHeader>

    <div class="gallery-layout">
      <aside class="gallery-directory mrr-directory-panel" aria-label="组件目录">
        <div class="gallery-search">
          <FaIcon name="i-lucide:search" class="gallery-search-icon" aria-hidden="true" />
          <el-input id="gallery-search" v-model="keyword" type="search" placeholder="名称或组件名" aria-label="查找组件" class="gallery-search-input" />
        </div>
        <nav class="gallery-categories" aria-label="组件分类">
          <button
            v-for="item in categories" :key="item.id" type="button"
            :aria-pressed="category === item.id" class="gallery-category mrr-navigation-control mrr-nav-leaf mrr-directory-nav-item" :title="item.label"
            @click="category = item.id"
          >
            <FaIcon :name="item.icon" class="gallery-category-icon mrr-directory-nav-icon" aria-hidden="true" />
            <span class="mrr-directory-nav-label">{{ item.label }}</span>
            <span class="gallery-category-count mrr-directory-nav-count">{{ item.id === 'all' ? examples.length : examples.filter(example => example.category === item.id).length }}</span>
          </button>
        </nav>
      </aside>

      <main class="gallery-content" aria-label="组件预览">
        <p class="gallery-results" role="status">
          显示 {{ visibleExamples.length }} 组示例
        </p>
        <AppEmpty v-if="!visibleExamples.length" description="没有匹配的组件，试试其他名称或分类。" action-text="显示全部组件" @action="resetExamples" />
        <article v-for="example in visibleExamples" :key="`${example.id}-${revision}`" class="gallery-example" :aria-labelledby="`example-${example.id}`">
          <MrrSectionCard class="gallery-example-card">
            <template #header>
              <div class="gallery-card-header">
                <div class="gallery-example-heading">
                  <h2 :id="`example-${example.id}`">
                    {{ example.title }}
                  </h2>
                  <p>{{ example.description }}</p>
                  <div class="gallery-component-names">
                    <code v-for="name in example.components" :key="name">{{ name }}</code>
                  </div>
                </div>
                <div class="gallery-card-actions">
                  <el-button v-if="['buttons', 'navigation', 'fields', 'selection'].includes(example.id)" :aria-pressed="!!compactExamples[example.id]" :aria-label="`窄宽预览：${example.title}`" @click="togglePreview(example.id)">
                    <FaIcon :name="compactExamples[example.id] ? 'i-lucide:maximize-2' : 'i-lucide:smartphone'" aria-hidden="true" />
                    {{ compactExamples[example.id] ? '恢复宽度' : '窄宽预览' }}
                  </el-button>
                  <el-button :aria-label="`重置示例：${example.title}`" @click="resetExample(example.id)">
                    <FaIcon name="i-lucide:rotate-ccw" aria-hidden="true" />重置
                  </el-button>
                </div>
              </div>
            </template>
            <div :key="exampleRevisions[example.id] ?? 0" class="gallery-preview" :class="{ 'gallery-preview--compact': compactExamples[example.id] }">
              <SpecialExamples v-if="example.id === 'special-controls'" />
              <BasicExamples v-else-if="['buttons', 'navigation', 'fields', 'selection'].includes(example.id)" :example="example.id" />
              <DataExamples v-else-if="['metrics', 'table'].includes(example.id)" :example="example.id" />
              <BusinessExamples v-else-if="['business-patterns', 'charts'].includes(example.id)" :example="example.id" />
              <FeedbackExamples v-else-if="['feedback', 'overlays', 'glass'].includes(example.id)" :example="example.id" />
              <PatternExamples v-else-if="['page-header', 'motion'].includes(example.id)" :example="example.id" />
              <TokenExamples v-else />
            </div>
          </MrrSectionCard>
        </article>
      </main>
    </div>
  </MrrPageShell>
</template>

<style scoped>
.gallery-layout {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: var(--mrr-page-gap);
  align-items: start;
}

.gallery-directory {
  position: sticky;
  top: calc(var(--g-header-actual-height, 0px) + var(--g-toolbar-height) + var(--g-tabbar-actual-height, 0px) + var(--mrr-space-4));
  min-width: 0;
}

.gallery-search {
  position: relative;
}

.gallery-search-icon {
  position: absolute;
  top: 50%;
  left: 10px;
  z-index: 1;
  width: 14px;
  height: 14px;
  color: var(--mrr-muted-foreground);
  pointer-events: none;
  transform: translateY(-50%);
}

.gallery-search-input {
  width: 100%;
}

.gallery-search-input :deep(.el-input__wrapper) {
  padding-left: var(--mrr-space-8);
}

.gallery-categories {
  display: grid;
  gap: var(--mrr-space-1);
}

.gallery-results {
  margin: 0 0 var(--mrr-space-5);
  font-size: 12px;
  line-height: 1.7;
  color: var(--mrr-muted-foreground);
}

.gallery-content,
.gallery-example {
  min-width: 0;
}

.gallery-example + .gallery-example {
  margin-top: var(--mrr-page-gap);
}

.gallery-card-header {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
  align-items: flex-start;
  justify-content: space-between;
}

.gallery-example-heading {
  flex: 1 1 220px;
  min-width: 0;
}

.gallery-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
  align-items: center;
}

.gallery-card-actions :deep(.el-button) {
  margin: 0;
}

.gallery-example-heading h2 {
  margin: 0;
  font-size: var(--mrr-font-size-md);
  font-weight: var(--mrr-font-weight-heading);
}

.gallery-example-heading p {
  margin: var(--mrr-space-2) 0;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.7;
  color: var(--mrr-muted-foreground);
}

.gallery-component-names {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
}

.gallery-component-names code {
  font-family: var(--mrr-font-mono);
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
}

.gallery-preview {
  min-width: 0;
  max-width: 100%;
}

.gallery-preview--compact {
  max-width: 420px;
  padding: var(--mrr-space-3);
  margin-inline: auto;
  background: var(--mrr-control-bg);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-md);
}

.gallery-preview--compact :deep(.example-field-grid) {
  grid-template-columns: minmax(0, 1fr);
}

.gallery-preview :deep(.example-row) {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-3);
  align-items: center;
}

.gallery-preview :deep(.example-stack) {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--mrr-space-5);
}

.gallery-preview :deep(.example-field-grid) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--mrr-space-4);
}

.gallery-preview :deep(.example-field) {
  display: grid;
  gap: var(--mrr-space-2);
  min-width: 0;
  font-size: 13px;
}

.gallery-preview :deep(.example-full-width) {
  width: 100%;
  min-width: 0;
}

.gallery-preview :deep(.example-slider) {
  max-width: 400px;
}

.gallery-preview :deep(.example-field-error) {
  font-size: var(--mrr-font-size-xs);
  color: var(--mrr-destructive);
}

.gallery-preview :deep(.example-note) {
  margin: 0;
  font-size: var(--mrr-font-size-sm);
  line-height: 1.7;
  color: var(--mrr-muted-foreground);
}

@media (max-width: 1100px) {
  .gallery-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--mrr-space-5);
  }

  .gallery-directory {
    position: static;
  }

  .gallery-categories {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .gallery-categories {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .gallery-preview :deep(.example-field-grid) {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
