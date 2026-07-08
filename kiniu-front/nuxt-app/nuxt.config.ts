const devtoolsEnabled = process.env.NUXT_DEVTOOLS_ENABLED === 'true'

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  ssr: false,
  devtools: { enabled: devtoolsEnabled },
  nitro: {
    preset: 'static'
  }
})