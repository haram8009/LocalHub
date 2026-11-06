import { createRouter, createWebHistory } from 'vue-router'

import { getSessionUser } from '@/services/auth'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior() {
    return { top: 0, left: 0, behavior: 'instant' }
  },

  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/posts',
      name: 'post-list',
      component: () => import('@/views/PostListView.vue'),
    },
    {
      path: '/posts/create',
      name: 'post-create',
      component: () => import('@/views/PostCreateView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/posts/new',
      redirect: (to) => ({ name: 'post-create', query: to.query }),
    },
    {
      path: '/posts/:postId/edit',
      name: 'post-edit',
      component: () => import('@/views/PostEditView.vue'),
      props: true,
      meta: { requiresAuth: true },
    },
    {
      path: '/posts/:postId',
      name: 'post-detail',
      component: () => import('@/views/PostDetailView.vue'),
      props: true,
    },
    {
      path: '/map',
      name: 'map',
      component: () => import('@/views/MapView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('@/views/auth/SignupView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/find-id',
      name: 'find-id',
      component: () => import('@/views/auth/FindIdView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/find-password',
      name: 'find-password',
      component: () => import('@/views/auth/FindPasswordView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/views/auth/ResetPasswordView.vue'),
      meta: { guestOnly: true },
      // 비밀번호 찾기에서 본인 확인을 거치지 않으면 들어올 수 없습니다.
      beforeEnter: () => (useAuthStore().resetTarget ? true : { name: 'find-password' }),
    },
    {
      path: '/mypage',
      name: 'mypage',
      component: () => import('@/views/auth/MyPageView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

router.beforeEach((to) => {
  const isLoggedIn = Boolean(getSessionUser())

  if (to.meta.requiresAuth && !isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.guestOnly && isLoggedIn) {
    return { name: 'home' }
  }

  return true
})

export default router
