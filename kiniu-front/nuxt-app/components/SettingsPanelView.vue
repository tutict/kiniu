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
      <p>这里统一管理游戏后端与上游模型接口配置。游戏页和创作页都会复用这一组设置。</p>
    </div>

    <div class="settings-grid">
      <label class="field">
        <span>游戏后端地址</span>
        <input v-model="settings.backendUrl" type="url" placeholder="http://localhost:8080">
        <small>用于请求 `/game/next` 与 `/game/story`。</small>
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
        <small>后端后续接模型时可以直接消费这项。</small>
      </label>
    </div>

    <div class="settings-actions">
      <button class="primary-button" type="button" @click="emit('persist')">保存设置</button>
      <button class="secondary-button" type="button" @click="emit('reset')">恢复默认</button>
      <span v-if="saveStatus" class="status success">{{ saveStatus }}</span>
    </div>

    <div class="settings-note">
      <h3>当前接线方式</h3>
      <p>剧情请求仍然走 Spring Boot `/game/next`。</p>
      <p>创作页从 `/game/story` 读取剧情目录，进入本地草稿编辑模式。</p>
    </div>
  </section>
</template>

<style scoped>
.settings-view{display:grid;gap:24px;align-content:start;padding:28px;border:1px solid rgba(255,255,255,.08);border-radius:32px;background:rgba(10,14,19,.64);backdrop-filter:blur(18px)}
.settings-hero,.settings-note{display:grid;gap:12px}
.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:.24em;text-transform:uppercase;color:#b9a988}
h2,h3,p{margin:0}
h2{font-size:clamp(28px,4vw,40px);line-height:1}
.settings-hero p:last-child,.settings-note p{color:#cfc7ba;line-height:1.7}
.settings-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}
.field{display:grid;gap:10px}
.field span{font-size:14px;color:#f7efde}
.field input{width:100%;padding:14px 16px;border:1px solid rgba(255,255,255,.1);border-radius:16px;outline:none;color:#f7f0e2;background:rgba(255,255,255,.04);font:inherit}
.field input:focus{border-color:rgba(229,199,138,.54);box-shadow:0 0 0 4px rgba(229,199,138,.08)}
.field small{color:#999387;line-height:1.6}
.settings-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.primary-button,.secondary-button{appearance:none;border:0;cursor:pointer;padding:12px 18px;border-radius:999px;transition:transform 160ms ease,background 160ms ease}
.primary-button{background:#e5c78a;color:#11161d}
.secondary-button{background:rgba(255,255,255,.06);color:#f4ede0}
.primary-button:hover,.secondary-button:hover{transform:translateY(-1px)}
.settings-note{padding-top:8px;border-top:1px solid rgba(255,255,255,.08)}
.settings-note h3{font-size:15px;color:#efe7d6}
.status{display:inline-flex;align-items:center;padding:12px 14px;border-radius:14px;line-height:1.5}
.status.success{color:#dff7d6;background:rgba(117,198,122,.14)}
@media (max-width:960px){.settings-grid{grid-template-columns:1fr}}
</style>
