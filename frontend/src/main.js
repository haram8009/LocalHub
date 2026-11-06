import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { restoreSession } from './services/auth'
import { useAuthStore } from './stores/auth'
import './assets/styles/base.css'

const app = createApp(App)

app.use(createPinia())
try {
  useAuthStore().user = await restoreSession()
} catch (error) {
  console.error('로그인 복원 실패:', error.message)
}
app.use(router)

app.mount('#app')
