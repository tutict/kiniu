// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  ssr: false,
  devtools: { enabled: process.env.NODE_ENV === 'development' },
  nitro: {
    preset: 'static'
  }
})
