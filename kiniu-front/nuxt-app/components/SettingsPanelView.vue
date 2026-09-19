<script setup lang="ts">
import UiButton from './ui/UiButton.vue'
import { localeOptions, useUiI18n } from '../i18n'
import type { ApiSettings } from '../types/game'

defineProps<{
  settings: ApiSettings
  saveStatus: string
}>()

const emit = defineEmits<{
  persist: []
  reset: []
}>()

const { t } = useUiI18n()
const themeOptions: ApiSettings['theme'][] = ['light', 'dark']
</script>

<template>
  <section class="settings-view">
    <header class="settings-header">
      <h2>{{ t('settingsTitle') }}</h2>
      <span v-if="saveStatus" class="status success">{{ saveStatus }}</span>
    </header>

    <div class="settings-grid">
      <label class="field">
        <span>{{ t('settingsLanguage') }}</span>
        <select v-model="settings.locale">
          <option v-for="option in localeOptions" :key="option.code" :value="option.code">
            {{ option.code === 'zh-CN' ? t('localeChinese') : t('localeEnglish') }}
          </option>
        </select>
      </label>

      <div class="field theme-field">
        <span>{{ t('settingsTheme') }}</span>
        <div class="theme-toggle" role="radiogroup" :aria-label="t('settingsTheme')">
          <label
            v-for="theme in themeOptions"
            :key="theme"
            class="theme-option"
            :class="{ active: settings.theme === theme }"
          >
            <input v-model="settings.theme" type="radio" name="theme" :value="theme">
            <span class="theme-swatch" :class="theme"></span>
            <strong>{{ theme === 'light' ? t('themeLight') : t('themeDark') }}</strong>
          </label>
        </div>
      </div>

      <label class="field">
        <span>{{ t('settingsBackendUrl') }}</span>
        <input v-model="settings.backendUrl" type="url" placeholder="http://localhost:8080">
      </label>

      <label class="field">
        <span>{{ t('settingsLocalToken') }}</span>
        <input v-model="settings.localToken" type="password" placeholder="optional">
      </label>

      <label class="field">
        <span>{{ t('settingsProviderUrl') }}</span>
        <input v-model="settings.providerUrl" type="url" placeholder="https://api.openai.com/v1">
      </label>

      <label class="field">
        <span>{{ t('settingsApiKey') }}</span>
        <input v-model="settings.apiKey" type="password" placeholder="sk-...">
      </label>

      <label class="field">
        <span>{{ t('settingsModel') }}</span>
        <input v-model="settings.model" type="text" placeholder="gpt-4.1-mini">
      </label>
    </div>

    <div class="settings-actions">
      <UiButton variant="accent" size="lg" @click="emit('persist')">{{ t('actionSaveSettings') }}</UiButton>
      <UiButton variant="secondary" size="lg" @click="emit('reset')">{{ t('actionResetDefault') }}</UiButton>
    </div>

    <details class="settings-note">
      <summary>{{ t('settingsWiring') }}</summary>
      <p>{{ t('settingsWiringChat') }}</p>
      <p>{{ t('settingsWiringStudio') }}</p>
    </details>
  </section>
</template>

<style scoped>
.settings-view {
  display: grid;
  gap: 14px;
  align-content: start;
  max-width: 1120px;
  min-height: calc(100dvh - 82px);
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface-panel);
  box-shadow: var(--shadow-card);
}
.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-soft);
}
.settings-note {
  display: grid;
  gap: 10px;
}
.settings-note summary {
  cursor: pointer;
  min-height: 38px;
  color: var(--color-heading-soft);
  font-weight: 800;
}
.eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  letter-spacing: 0;
  color: var(--color-primary-strong);
  font-weight: 800;
  line-height: 1.2;
}
h3 {
  margin: 0;
}
p {
  margin: 0;
}
h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.15;
  color: var(--color-heading);
  overflow-wrap: anywhere;
}
.settings-note p {
  max-width: 72ch;
  color: var(--color-muted);
  line-height: 1.6;
}
.settings-grid {
  display: grid;
  grid-template-columns: repeat(2,minmax(0,1fr));
  gap: 18px;
}
.field {
  display: grid;
  gap: 10px;
}
.field span {
  font-size: 13px;
  color: var(--color-text);
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 0;
}
.field input {
  width: 100%;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  outline: none;
  color: var(--color-text);
  background: var(--color-input);
  font: inherit;
}
.field select {
  width: 100%;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  outline: none;
  color: var(--color-text);
  background: var(--color-input);
  font: inherit;
}
.field input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-focus-ring);
}
.field select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px var(--color-focus-ring);
}
.field small {
  color: var(--color-faint);
  line-height: 1.55;
}
.theme-field {
  align-content: start;
}
.theme-toggle {
  display: grid;
  grid-template-columns: repeat(2,minmax(0,1fr));
  gap: 8px;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-input);
}
.theme-option {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--color-muted);
  cursor: pointer;
  transition: background 180ms var(--ease),border-color 180ms var(--ease),color 180ms var(--ease),box-shadow 180ms var(--ease);
}
.theme-option input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
.theme-option.active {
  border-color: var(--color-primary);
  background: var(--color-surface-muted);
  color: var(--color-primary-strong);
  box-shadow: var(--shadow-active);
}
.theme-option:hover {
  background: var(--color-hover);
  color: var(--color-primary-strong);
}
.theme-option:has(input:focus-visible) {
  outline: 3px solid var(--color-focus-global);
  outline-offset: 2px;
}
.theme-swatch {
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  border-radius: 50%;
  border: 1px solid var(--color-border);
  box-shadow: inset 0 0 0 3px var(--color-surface);
}
/* 主题预览色卡：固定展示浅/深两套预览色，不跟随当前主题变量变化 */
.theme-swatch.light {
  background: linear-gradient(135deg, var(--color-theme-preview-light-bg) 0%, var(--color-theme-preview-light-accent) 100%);
}
.theme-swatch.dark {
  background: linear-gradient(135deg, var(--color-theme-preview-dark-bg) 0%, var(--color-theme-preview-dark-accent) 100%);
}
.theme-option strong {
  font-size: 14px;
}
.settings-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.settings-note {
  padding-top: 8px;
  border-top: 1px solid var(--color-border-soft);
}
.settings-note h3 {
  font-size: 15px;
  color: var(--color-text);
}
.status {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 14px;
  border-radius: var(--radius);
  line-height: 1.5;
}
.status.success {
  color: var(--color-success-text);
  background: var(--color-success-bg);
  border: 1px solid var(--color-success-border);
}
@media (max-width:960px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
@media (prefers-reduced-motion:reduce) {
  .theme-option {
    transition: none;
  }
}
</style>
