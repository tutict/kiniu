const devtoolsEnabled = process.env.NUXT_DEVTOOLS_ENABLED === 'true'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  ssr: false,
  buildDir: process.env.NUXT_BUILD_DIR || '.nuxt',
  css: [
    '~/assets/css/tokens.css',
    '~/assets/css/base.css'
  ],
  runtimeConfig: {
    public: {
      kiniuBackendUrl: '',
      kiniuLocalToken: ''
    }
  },
  devtools: { enabled: devtoolsEnabled },
  nitro: {
    preset: 'static'
  }
})
