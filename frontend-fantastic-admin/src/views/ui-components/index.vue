<route lang="yaml">
meta:
  title: UI 组件
  icon: i-lucide:component
  cache: false
</route>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { categories, examples } from './catalog'
import BasicExamples from './components/BasicExamples.vue'
import DataExamples from './components/DataExamples.vue'
import FeedbackExamples from './components/FeedbackExamples.vue'
import TokenExamples from './components/TokenExamples.vue'

defineOptions({ name: 'UiComponentGallery' })

const settingsStore = useSettingsStore()
const keyword = ref('')
const category = ref<string>('all')
const revision = ref(0)
const componentCount = new Set(examples.flatMap(example => [...example.components])).size
const visibleExamples = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return examples.filter(example => (category.value === 'all' || example.category === category.value)
    && [example.title, example.description, ...example.components].join(' ').toLowerCase().includes(query))
})

function resetExamples() {
  keyword.value = ''
  category.value = 'all'
  revision.value += 1
}
</script>

<template>
  <MrrPageShell class="component-gallery">
    <MrrPageHeader title="UI 组件" description="集中查看项目组件的真实外观与交互。使用合成示例，方便设计验收与页面搭建。">
      <template #actions>
        <FaButton variant="outline" @click="resetExamples">
          <FaIcon name="i-lucide:rotate-ccw" aria-hidden="true" />
          重置所有示例
        </FaButton>
      </template>
    </MrrPageHeader>

    <div class="gallery-layout">
      <aside class="gallery-directory" aria-label="组件目录">
        <label for="gallery-search" class="gallery-label">查找组件</label>
        <FaInput id="gallery-search" v-model="keyword" type="search" placeholder="名称或组件名" class="gallery-search" />
        <nav class="gallery-categories" aria-label="组件分类">
          <button
            v-for="item in categories" :key="item.id" type="button"
            :aria-pressed="category === item.id" class="gallery-category"
            @click="category = item.id"
          >
            <FaIcon :name="item.icon" aria-hidden="true" />
            <span>{{ item.label }}</span>
            <span class="gallery-category-count">{{ item.id === 'all' ? examples.length : examples.filter(example => example.category === item.id).length }}</span>
          </button>
        </nav>
        <p class="gallery-directory-note">
          {{ componentCount }} 个组件与样式入口<br>
          当前{{ settingsStore.currentColorScheme === 'dark' ? '暗色' : '亮色' }}主题，跟随全站外观设置。
        </p>
      </aside>

      <main class="gallery-content" aria-label="组件预览">
        <p class="gallery-results" role="status">
          显示 {{ visibleExamples.length }} 组示例
        </p>
        <AppEmpty v-if="!visibleExamples.length" description="没有匹配的组件，试试其他名称或分类。" action-text="显示全部组件" @action="resetExamples" />
        <article v-for="example in visibleExamples" :key="`${example.id}-${revision}`" class="gallery-example" :aria-labelledby="`example-${example.id}`">
          <header class="gallery-example-heading">
            <h2 :id="`example-${example.id}`">
              {{ example.title }}
            </h2>
            <p>{{ example.description }}</p>
            <div class="gallery-component-names">
              <code v-for="name in example.components" :key="name">{{ name }}</code>
            </div>
          </header>
          <div class="gallery-preview">
            <BasicExamples v-if="['buttons', 'navigation', 'fields', 'selection'].includes(example.id)" :example="example.id" />
            <DataExamples v-else-if="['metrics', 'table'].includes(example.id)" :example="example.id" />
            <FeedbackExamples v-else-if="['feedback', 'overlays'].includes(example.id)" :example="example.id" />
            <TokenExamples v-else />
          </div>
        </article>
      </main>
    </div>
  </MrrPageShell>
</template>

<style scoped>
.gallery-layout {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: var(--mrr-space-8);
  align-items: start;
}

.gallery-directory {
  position: sticky;
  top: calc(var(--g-header-actual-height, 0px) + var(--g-toolbar-height) + var(--g-tabbar-actual-height, 0px) + var(--mrr-space-4));
  min-width: 0;
}

.gallery-label {
  display: block;
  margin-bottom: var(--mrr-space-2);
  font-size: 13px;
  font-weight: 500;
}

.gallery-search {
  width: 100%;
}

.gallery-categories {
  display: grid;
  gap: var(--mrr-space-1);
  margin-top: var(--mrr-space-5);
}

.gallery-category {
  display: flex;
  gap: var(--mrr-space-2);
  align-items: center;
  min-height: 38px;
  padding: var(--mrr-space-2) var(--mrr-space-3);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--mrr-radius-md);
}

.gallery-category:hover {
  background: var(--mrr-navigation-hover);
}

.gallery-category[aria-pressed="true"] {
  font-weight: 700;
  color: var(--mrr-foreground);
  background: var(--mrr-accent);
  border-color: var(--mrr-border);
}

.gallery-category:focus-visible {
  outline: 2px solid var(--mrr-primary);
  outline-offset: 2px;
}

.gallery-category-count {
  margin-left: auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--mrr-muted-foreground);
}

.gallery-directory-note,
.gallery-results {
  margin: var(--mrr-space-5) 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--mrr-muted-foreground);
}

.gallery-results {
  margin: 0 0 var(--mrr-space-5);
}

.gallery-content,
.gallery-example {
  min-width: 0;
}

.gallery-example {
  padding-bottom: var(--mrr-space-8);
  margin-bottom: var(--mrr-space-8);
  border-bottom: 1px solid var(--mrr-border);
}

.gallery-example:last-child {
  padding-bottom: 0;
  margin-bottom: 0;
  border-bottom: 0;
}

.gallery-example-heading h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.gallery-example-heading p {
  margin: var(--mrr-space-2) 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--mrr-muted-foreground);
}

.gallery-component-names {
  display: flex;
  flex-wrap: wrap;
  gap: var(--mrr-space-2);
}

.gallery-component-names code {
  font-size: 11px;
  color: var(--mrr-muted-foreground);
  overflow-wrap: anywhere;
}

.gallery-preview {
  min-width: 0;
  padding: var(--mrr-space-5);
  margin-top: var(--mrr-space-4);
  background: var(--mrr-card);
  border: 1px solid var(--mrr-border);
  border-radius: var(--mrr-radius-card);
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

.gallery-preview :deep(.example-note) {
  margin: 0;
  font-size: 13px;
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
    margin-top: var(--mrr-space-3);
  }
}

@media (max-width: 600px) {
  .gallery-categories {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .gallery-preview {
    padding: var(--mrr-space-3);
  }

  .gallery-preview :deep(.example-field-grid) {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
