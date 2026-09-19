<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  copy?: string
  /** comfortable=宽松（大区域） compact=紧凑 */
  density?: 'comfortable' | 'compact'
}>(), {
  copy: '',
  density: 'comfortable'
})
</script>

<template>
  <div class="ui-empty" :class="[`ui-empty--${density}`]">
    <div v-if="$slots.icon" class="ui-empty__icon">
      <slot name="icon" />
    </div>
    <strong class="ui-empty__title">{{ title }}</strong>
    <span v-if="copy || $slots.copy" class="ui-empty__copy"><slot name="copy">{{ copy }}</slot></span>
    <div v-if="$slots.default" class="ui-empty__actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.ui-empty {
  display: grid;
  gap: 5px;
  place-items: center;
  padding: 20px;
  border: 1px dashed var(--color-border);
  border-radius: 4px;
  background: var(--color-row);
  text-align: center;
}
.ui-empty--comfortable {
  min-height: 160px;
}
.ui-empty--compact {
  min-height: 0;
}
.ui-empty__icon {
  color: var(--color-faint);
}
.ui-empty__title {
  color: var(--color-heading);
}
.ui-empty__copy {
  max-width: 250px;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.6;
}
.ui-empty__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 6px;
}
</style>
