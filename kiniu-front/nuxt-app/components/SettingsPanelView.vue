<script setup lang="ts">
import type { ApiSettings } from '../types/game'

defineProps<{
  settings: ApiSettings
  saveStatus: string
}>()

const emit = defineEmits<{
  persist: []
  reset: []
}>()
</script>

<template>
  <section class="settings-view">
    <div class="settings-hero">
      <p class="eyebrow">API 接入</p>
      <h2>设置页</h2>
      <p>这里统一管理 Agent 容器后端与上游模型接口配置。对话页和 Agent Studio 都会复用这一组设置。</p>
    </div>

    <div class="settings-grid">
      <label class="field">
        <span>容器后端地址</span>
        <input v-model="settings.backendUrl" type="url" placeholder="http://localhost:8080">
        <small>用于请求 `/agent/next`、`/agent/story` 与 `/agent/agents`。</small>
      </label>

      <label class="field">
        <span>上游 API Base URL</span>
        <input v-model="settings.providerUrl" type="url" placeholder="https://api.openai.com/v1">
        <small>预留给真实 LLM 接入，当前通过请求头透传。</small>
      </label>

      <label class="field">
        <span>API Key</span>
        <input v-model="settings.apiKey" type="password" placeholder="sk-...">
        <small>本地持久化，请求时附带 `Authorization` 与 `X-API-Key`。</small>
      </label>

      <label class="field">
        <span>模型名</span>
        <input v-model="settings.model" type="text" placeholder="gpt-4.1-mini">
        <small>后端会把这一项透传给 OpenAI-compatible chat completions 接口。</small>
      </label>
    </div>

    <div class="settings-actions">
      <button class="primary-button" type="button" @click="emit('persist')">保存设置</button>
      <button class="secondary-button" type="button" @click="emit('reset')">恢复默认</button>
      <span v-if="saveStatus" class="status success">{{ saveStatus }}</span>
    </div>

    <div class="settings-note">
      <h3>当前接线方式</h3>
      <p>对话请求走 Spring Boot `/agent/next`，旧的 `/game/next` 仍保留兼容。</p>
      <p>Agent Studio 从 `/agent/story` 与 `/agent/agents` 读取任务流和 Agent 目录，进入本地草稿编辑模式。</p>
    </div>
  </section>
</template>

<style scoped>
.settings-view{display:grid;gap:24px;align-content:start;padding:24px;border:1px solid var(--color-border);border-radius:var(--radius);background:rgba(255,255,255,.9);box-shadow:var(--shadow-card)}
.settings-hero,.settings-note{display:grid;gap:12px}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.18em;text-transform:uppercase;color:var(--color-primary-strong);font-weight:800}
h2,h3,p{margin:0}
h2{font-size:clamp(28px,4vw,40px);line-height:1.05;color:#102f2d}
.settings-hero p:last-child,.settings-note p{color:var(--color-muted);line-height:1.65}
.settings-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}
.field{display:grid;gap:10px}
.field span{font-size:14px;color:var(--color-text);font-weight:700}
.field input{width:100%;min-height:44px;padding:10px 14px;border:1px solid var(--color-border);border-radius:var(--radius);outline:none;color:var(--color-text);background:#fff;font:inherit}
.field input:focus{border-color:var(--color-primary);box-shadow:0 0 0 4px rgba(13,148,136,.12)}
.field small{color:var(--color-faint);line-height:1.55}
.settings-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.primary-button,.secondary-button{appearance:none;border:0;cursor:pointer;min-height:44px;padding:0 18px;border-radius:var(--radius);font-weight:800;transition:background 180ms var(--ease),border-color 180ms var(--ease),box-shadow 180ms var(--ease)}
.primary-button{background:var(--color-accent);color:#fff}
.secondary-button{border:1px solid var(--color-border);background:#fff;color:var(--color-primary-strong)}
.primary-button:hover{background:#c2410c;box-shadow:0 10px 22px rgba(234,88,12,.18)}
.secondary-button:hover{border-color:var(--color-primary);background:#ecfdf5}
.settings-note{padding-top:8px;border-top:1px solid #d7eeea}
.settings-note h3{font-size:15px;color:var(--color-text)}
.status{display:inline-flex;align-items:center;min-height:44px;padding:10px 14px;border-radius:var(--radius);line-height:1.5}
.status.success{color:#166534;background:#dcfce7;border:1px solid #bbf7d0}
@media (max-width:960px){.settings-grid{grid-template-columns:1fr}}
</style>
