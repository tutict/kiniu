<script setup lang="ts">
export interface UiTabItem {
  id: string
  label: string
  meta?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  items: UiTabItem[]
  modelValue: string
  /** card=卡片式标签（带标题+说明） underline=下划线式标签 */
  variant?: 'card' | 'underline'
  columns?: number
  ariaLabel?: string
}>(), {
  variant: 'card',
  columns: 3,
  ariaLabel: ''
})

const emit = defineEmits<{
  (event: 'update:modelValue', id: string): void
}>()

function select(item: UiTabItem) {
  if (item.disabled) return
  emit('update:modelValue', item.id)
}
</script>

<template>
  <div
    class="ui-tabs"
    :class="[`ui-tabs--${variant}`]"
    :style="variant === 'card' ? { '--ui-tabs-columns': String(columns) } : undefined"
    role="tablist"
    :aria-label="ariaLabel || undefined"
  >
    <button
      v-for="item in items"
      :key="item.id"
      class="ui-tabs__tab"
      :class="{ active: modelValue === item.id }"
      type="button"
      role="tab"
      :aria-selected="modelValue === item.id"
      :disabled="item.disabled"
      @click="select(item)"
    >
      <strong>{{ item.label }}</strong>
      <span v-if="item.meta">{{ item.meta }}</span>
    </button>
  </div>
</template>

<style scoped>
.ui-tabs {
  min-width: 0;
}
.ui-tabs__tab {
  appearance: none;
  cursor: pointer;
  text-align: left;
}
.ui-tabs__tab:disabled {
  cursor: not-allowed;
  opacity: .55;
}

/* ---- card 变体 ---- */
.ui-tabs--card {
  display: grid;
  grid-template-columns: repeat(var(--ui-tabs-columns, 3), minmax(0, 1fr));
  gap: 8px;
}
.ui-tabs--card .ui-tabs__tab {
  display: grid;
  gap: 3px;
  min-height: 52px;
  padding: 9px 12px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius);
  background: var(--color-surface-panel);
  color: var(--color-muted);
  transition: background 180ms var(--ease), border-color 180ms var(--ease), box-shadow 180ms var(--ease), color 180ms var(--ease);
}
.ui-tabs--card .ui-tabs__tab strong {
  min-width: 0;
  font-size: 14px;
  line-height: 1.25;
  color: inherit;
  overflow-wrap: anywhere;
}
.ui-tabs--card .ui-tabs__tab span {
  min-width: 0;
  font-size: 12px;
  line-height: 1.25;
  color: var(--color-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ui-tabs--card .ui-tabs__tab.active {
  border-color: var(--color-border-strong);
  background: var(--color-surface-muted);
  color: var(--color-primary-strong);
  box-shadow: var(--shadow-active);
}
.ui-tabs--card .ui-tabs__tab:hover:not(:disabled) {
  border-color: var(--color-border-strong);
  background: var(--color-hover);
  color: var(--color-primary-strong);
}

/* ---- underline 变体 ---- */
.ui-tabs--underline {
  display: flex;
  gap: 0;
  overflow: auto;
  border-bottom: 1px solid var(--color-border);
}
.ui-tabs--underline .ui-tabs__tab {
  display: block;
  padding: 7px 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--color-muted);
  white-space: nowrap;
}
.ui-tabs--underline .ui-tabs__tab strong {
  font-weight: inherit;
  font-size: inherit;
}
.ui-tabs--underline .ui-tabs__tab.active {
  border-color: var(--color-primary);
  background: transparent;
  color: var(--color-primary-strong);
}
@media (max-width: 720px) {
  .ui-tabs--card {
    grid-template-columns: 1fr;
  }
}
@media (prefers-reduced-motion: reduce) {
  .ui-tabs--card .ui-tabs__tab {
    transition: none;
  }
}
</style>
