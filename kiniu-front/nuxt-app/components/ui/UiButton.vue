<script setup lang="ts">
withDefaults(defineProps<{
  /** primary=蓝色主行动 accent=橙色强调 secondary=描边次级 danger=危险 ghost=透明 */
  variant?: 'primary' | 'accent' | 'secondary' | 'danger' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
  loading?: boolean
  block?: boolean
}>(), {
  variant: 'primary',
  size: 'md',
  type: 'button',
  disabled: false,
  loading: false,
  block: false
})
</script>

<template>
  <button
    class="ui-button"
    :class="[`ui-button--${variant}`, `ui-button--${size}`, { 'ui-button--block': block, 'is-loading': loading }]"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <span v-if="loading" class="ui-button__spinner" aria-hidden="true" />
    <span class="ui-button__content"><slot /></span>
  </button>
</template>

<style scoped>
.ui-button {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  border-radius: var(--radius);
  font-weight: 800;
  white-space: nowrap;
  transition: background 180ms var(--ease), border-color 180ms var(--ease), box-shadow 180ms var(--ease), opacity 180ms var(--ease);
}
.ui-button:disabled {
  opacity: .5;
  cursor: not-allowed;
  box-shadow: none;
}

/* ---- 尺寸（字号继承上下文，避免影响现有排版） ---- */
.ui-button--sm {
  min-height: 34px;
  padding: 7px 12px;
}
.ui-button--md {
  min-height: 38px;
  padding: 0 13px;
}
.ui-button--lg {
  min-height: 44px;
  padding: 0 16px;
}
.ui-button--block {
  width: 100%;
}

/* ---- 变体 ---- */
.ui-button--primary {
  border: 0;
  background: var(--color-primary);
  color: var(--color-on-primary);
}
.ui-button--primary:hover:not(:disabled) {
  background: var(--color-primary-strong);
}
.ui-button--accent {
  border: 0;
  background: var(--color-accent);
  color: var(--color-on-accent);
}
.ui-button--accent:hover:not(:disabled) {
  background: var(--color-accent-hover);
  box-shadow: var(--shadow-accent);
}
.ui-button--secondary {
  border: 1px solid var(--color-border);
  background: var(--color-input);
  color: var(--color-primary-strong);
}
.ui-button--secondary:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: var(--color-hover);
}
.ui-button--danger {
  border: 0;
  background: var(--color-danger-action);
  color: var(--color-on-primary);
}
.ui-button--danger:hover:not(:disabled) {
  filter: brightness(1.08);
}
.ui-button--ghost {
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-primary-strong);
}
.ui-button--ghost:hover:not(:disabled) {
  background: var(--color-hover);
}

/* ---- loading ---- */
.ui-button__spinner {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  opacity: .85;
  animation: ui-button-spin 720ms linear infinite;
}
@keyframes ui-button-spin {
  to {
    transform: rotate(360deg);
  }
}
.ui-button__content {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
@media (prefers-reduced-motion: reduce) {
  .ui-button {
    transition: none;
  }
  .ui-button__spinner {
    animation-duration: 1.8s;
  }
}
</style>
