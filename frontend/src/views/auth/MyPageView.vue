<script setup>
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import PasswordField from '@/components/auth/PasswordField.vue'
import { changePassword as changePasswordRequest } from '@/services/auth'
import { deletePost as deletePostRequest, getPosts } from '@/services/posts'
import { useAuthStore } from '@/stores/auth'
import {
  validateEmail,
  validateName,
  validatePassword,
  validatePasswordConfirm,
} from '@/utils/validators'

const router = useRouter()
const authStore = useAuthStore()

const profile = reactive({ name: authStore.user?.name ?? '', email: authStore.user?.email ?? '' })
const profileErrors = reactive({ name: '', email: '' })
const profileMessage = ref({ type: '', text: '' })
const isSavingProfile = ref(false)

const myPosts = ref([])
const myPostsPage = ref(1)
const myPostsTotalPages = ref(1)
const myPostsLoading = ref(false)
const myPostsError = ref('')
const deletingPostId = ref(null)
const MY_POSTS_PAGE_SIZE = 5

const passwordForm = reactive({ currentPassword: '', newPassword: '', newPasswordConfirm: '' })
const passwordErrors = reactive({ currentPassword: '', newPassword: '', newPasswordConfirm: '' })
const passwordMessage = ref({ type: '', text: '' })
const isChangingPassword = ref(false)

const isWithdrawModalOpen = ref(false)
const withdrawPassword = ref('')
const withdrawErrorMessage = ref('')
const isWithdrawing = ref(false)

function formatPostDate(value) {
  if (!value) return '-'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
    .format(date)
    .replaceAll(' ', '')
}

async function loadMyPosts(page = myPostsPage.value) {
  myPostsLoading.value = true
  myPostsError.value = ''

  try {
    const result = await getPosts({
      authorId: authStore.user.id,
      page,
      size: MY_POSTS_PAGE_SIZE,
    })
    myPosts.value = result.items
    myPostsPage.value = result.page
    myPostsTotalPages.value = Math.max(result.totalPages, 1)
  } catch (error) {
    myPostsError.value =
      error instanceof Error ? error.message : '작성한 게시글을 불러오지 못했습니다.'
  } finally {
    myPostsLoading.value = false
  }
}

async function deleteMyPost(post) {
  if (deletingPostId.value !== null) return
  if (!window.confirm(`"${post.title}" 게시글을 삭제할까요?`)) return

  deletingPostId.value = post.id
  myPostsError.value = ''

  try {
    await deletePostRequest(post.id)
    const nextPage =
      myPosts.value.length === 1 && myPostsPage.value > 1
        ? myPostsPage.value - 1
        : myPostsPage.value
    await loadMyPosts(nextPage)
  } catch (error) {
    myPostsError.value = error instanceof Error ? error.message : '게시글을 삭제하지 못했습니다.'
  } finally {
    deletingPostId.value = null
  }
}

function changeMyPostsPage(page) {
  if (page < 1 || page > myPostsTotalPages.value || page === myPostsPage.value) return
  loadMyPosts(page)
}

async function saveProfile() {
  if (isSavingProfile.value) return

  profileMessage.value = { type: '', text: '' }
  profileErrors.name = validateName(profile.name)
  profileErrors.email = validateEmail(profile.email)
  if (profileErrors.name || profileErrors.email) return

  isSavingProfile.value = true

  try {
    await authStore.updateProfile(profile)
    profile.name = authStore.user.name
    profile.email = authStore.user.email
    profileMessage.value = { type: 'success', text: '회원 정보가 수정되었습니다.' }
  } catch (error) {
    if (error?.data?.field === 'email') {
      profileErrors.email = error.message
    } else {
      profileMessage.value = {
        type: 'error',
        text: error instanceof Error ? error.message : '회원 정보를 수정하지 못했습니다.',
      }
    }
  } finally {
    isSavingProfile.value = false
  }
}

async function changePassword() {
  if (isChangingPassword.value) return

  passwordMessage.value = { type: '', text: '' }
  passwordErrors.currentPassword = passwordForm.currentPassword
    ? ''
    : '현재 비밀번호를 입력해 주세요.'
  passwordErrors.newPassword = validatePassword(passwordForm.newPassword)
  passwordErrors.newPasswordConfirm = validatePasswordConfirm(
    passwordForm.newPassword,
    passwordForm.newPasswordConfirm,
  )
  if (Object.values(passwordErrors).some(Boolean)) return

  isChangingPassword.value = true

  try {
    await changePasswordRequest(passwordForm)
    Object.assign(passwordForm, { currentPassword: '', newPassword: '', newPasswordConfirm: '' })
    passwordMessage.value = { type: 'success', text: '비밀번호가 변경되었습니다.' }
  } catch (error) {
    if (error?.status === 403) {
      passwordErrors.currentPassword = error.message
    } else if (error?.status === 422) {
      passwordErrors.newPassword = error.message
    } else {
      passwordMessage.value = {
        type: 'error',
        text: error instanceof Error ? error.message : '비밀번호를 변경하지 못했습니다.',
      }
    }
  } finally {
    isChangingPassword.value = false
  }
}

async function logout() {
  await authStore.logout()
  await router.push('/')
}

function openWithdrawModal() {
  withdrawPassword.value = ''
  withdrawErrorMessage.value = ''
  isWithdrawModalOpen.value = true
}

function closeWithdrawModal() {
  if (isWithdrawing.value) return
  isWithdrawModalOpen.value = false
}

async function withdraw() {
  if (isWithdrawing.value) return

  if (!withdrawPassword.value) {
    withdrawErrorMessage.value = '비밀번호를 입력해 주세요.'
    return
  }

  isWithdrawing.value = true
  withdrawErrorMessage.value = ''

  try {
    await authStore.withdraw(withdrawPassword.value)
    isWithdrawModalOpen.value = false
    window.alert('회원 탈퇴가 완료되었습니다.')
    await router.push('/')
  } catch (error) {
    withdrawErrorMessage.value = error instanceof Error ? error.message : '탈퇴하지 못했습니다.'
  } finally {
    isWithdrawing.value = false
  }
}

onMounted(() => loadMyPosts(1))
</script>

<template>
  <main class="mypage-view">
    <div class="container mypage">
      <header class="page-header">
        <p class="eyebrow">MY PAGE</p>
        <h1>마이페이지</h1>
        <p>{{ authStore.user?.name }}님의 회원 정보를 관리합니다.</p>
      </header>

      <section class="panel" aria-labelledby="my-posts-title">
        <header class="panel__header panel__header--with-action">
          <div>
            <h2 id="my-posts-title">내가 작성한 게시글</h2>
            <p class="panel__description">
              작성한 게시글을 확인하고 수정하거나 삭제할 수 있습니다.
            </p>
          </div>
          <RouterLink to="/posts/create" class="auth-button auth-button--primary">
            글쓰기
          </RouterLink>
        </header>

        <div class="panel__content my-posts">
          <p v-if="myPostsLoading" class="my-posts__state" role="status">
            작성한 게시글을 불러오는 중입니다.
          </p>
          <div v-else-if="myPostsError" class="my-posts__state my-posts__state--error" role="alert">
            <p>{{ myPostsError }}</p>
            <button type="button" class="auth-button" @click="loadMyPosts()">다시 시도</button>
          </div>
          <div v-else-if="myPosts.length === 0" class="my-posts__state">
            <p>아직 작성한 게시글이 없습니다.</p>
            <RouterLink to="/posts/create" class="my-posts__write-link">
              첫 게시글 작성하기
            </RouterLink>
          </div>
          <ul v-else class="my-posts__list">
            <li v-for="post in myPosts" :key="post.id" class="my-posts__item">
              <div class="my-posts__info">
                <RouterLink :to="`/posts/${post.id}`" class="my-posts__title">
                  {{ post.title }}
                </RouterLink>
                <p class="my-posts__meta">
                  <span>{{ post.categoryLabel }}</span>
                  <span aria-hidden="true">·</span>
                  <time :datetime="post.createdAt">{{ formatPostDate(post.createdAt) }}</time>
                  <span aria-hidden="true">·</span>
                  <span>여행지 {{ post.locationCount }}곳</span>
                </p>
              </div>
              <div class="my-posts__actions">
                <RouterLink :to="`/posts/${post.id}/edit`" class="auth-button">수정</RouterLink>
                <button
                  type="button"
                  class="auth-button auth-button--danger"
                  :disabled="deletingPostId !== null"
                  @click="deleteMyPost(post)"
                >
                  {{ deletingPostId === post.id ? '삭제 중...' : '삭제' }}
                </button>
              </div>
            </li>
          </ul>

          <nav
            v-if="!myPostsLoading && !myPostsError && myPostsTotalPages > 1"
            class="my-posts__pagination"
            aria-label="내 게시글 페이지"
          >
            <button
              type="button"
              class="auth-button"
              :disabled="myPostsPage === 1"
              @click="changeMyPostsPage(myPostsPage - 1)"
            >
              이전
            </button>
            <span>{{ myPostsPage }} / {{ myPostsTotalPages }}</span>
            <button
              type="button"
              class="auth-button"
              :disabled="myPostsPage === myPostsTotalPages"
              @click="changeMyPostsPage(myPostsPage + 1)"
            >
              다음
            </button>
          </nav>
        </div>
      </section>

      <section class="panel">
        <header class="panel__header"><h2>내 정보</h2></header>
        <form class="panel__content auth-form" novalidate @submit.prevent="saveProfile">
          <label class="auth-field">
            <span>아이디</span>
            <input :value="authStore.user?.id" class="auth-control" type="text" readonly />
            <span class="auth-hint">아이디는 변경할 수 없습니다.</span>
          </label>

          <label class="auth-field">
            <span>이름 <span class="auth-field__required">*</span></span>
            <input
              v-model="profile.name"
              class="auth-control"
              :class="{ 'auth-control--invalid': profileErrors.name }"
              type="text"
              autocomplete="name"
              maxlength="20"
            />
            <p v-if="profileErrors.name" class="auth-error">{{ profileErrors.name }}</p>
          </label>

          <label class="auth-field">
            <span>이메일 <span class="auth-field__required">*</span></span>
            <input
              v-model="profile.email"
              class="auth-control"
              :class="{ 'auth-control--invalid': profileErrors.email }"
              type="email"
              autocomplete="email"
              maxlength="100"
            />
            <p v-if="profileErrors.email" class="auth-error">{{ profileErrors.email }}</p>
          </label>

          <p
            v-if="profileMessage.text"
            class="auth-alert"
            :class="{ 'auth-alert--success': profileMessage.type === 'success' }"
            role="status"
          >
            {{ profileMessage.text }}
          </p>

          <div class="panel__actions">
            <button
              type="submit"
              class="auth-button auth-button--primary"
              :disabled="isSavingProfile"
            >
              {{ isSavingProfile ? '저장 중...' : '정보 저장' }}
            </button>
          </div>
        </form>
      </section>

      <section class="panel">
        <header class="panel__header"><h2>비밀번호 변경</h2></header>
        <form class="panel__content auth-form" novalidate @submit.prevent="changePassword">
          <PasswordField
            v-model="passwordForm.currentPassword"
            label="현재 비밀번호"
            :error="passwordErrors.currentPassword"
          />
          <PasswordField
            v-model="passwordForm.newPassword"
            label="새 비밀번호"
            autocomplete="new-password"
            show-rules
            :error="passwordErrors.newPassword"
          />
          <PasswordField
            v-model="passwordForm.newPasswordConfirm"
            label="새 비밀번호 확인"
            autocomplete="new-password"
            :error="passwordErrors.newPasswordConfirm"
          />

          <p
            v-if="passwordMessage.text"
            class="auth-alert"
            :class="{ 'auth-alert--success': passwordMessage.type === 'success' }"
            role="status"
          >
            {{ passwordMessage.text }}
          </p>

          <div class="panel__actions">
            <button
              type="submit"
              class="auth-button auth-button--primary"
              :disabled="isChangingPassword"
            >
              {{ isChangingPassword ? '변경 중...' : '비밀번호 변경' }}
            </button>
          </div>
        </form>
      </section>

      <section class="panel">
        <header class="panel__header"><h2>계정</h2></header>
        <div class="panel__content account-actions">
          <div>
            <strong>로그아웃</strong>
            <p>이 브라우저 탭의 로그인 정보를 지웁니다.</p>
          </div>
          <button type="button" class="auth-button" @click="logout">로그아웃</button>

          <div>
            <strong>회원 탈퇴</strong>
            <p>탈퇴하면 계정을 복구할 수 없습니다. 작성한 게시글은 삭제되지 않습니다.</p>
          </div>
          <button type="button" class="auth-button auth-button--danger" @click="openWithdrawModal">
            회원 탈퇴
          </button>
        </div>
      </section>
    </div>

    <Teleport to="body">
      <div v-if="isWithdrawModalOpen" class="overlay" @click.self="closeWithdrawModal">
        <section class="modal" role="dialog" aria-modal="true" aria-labelledby="withdraw-title">
          <header>
            <div>
              <p class="eyebrow">WITHDRAW</p>
              <h2 id="withdraw-title">회원 탈퇴</h2>
            </div>
            <button
              type="button"
              class="modal__close"
              aria-label="닫기"
              :disabled="isWithdrawing"
              @click="closeWithdrawModal"
            >
              ×
            </button>
          </header>
          <p>본인 확인을 위해 비밀번호를 입력해 주세요. 탈퇴한 계정은 복구할 수 없습니다.</p>
          <form class="auth-form" novalidate @submit.prevent="withdraw">
            <PasswordField v-model="withdrawPassword" :error="withdrawErrorMessage" />
            <div class="modal__actions">
              <button
                type="button"
                class="auth-button"
                :disabled="isWithdrawing"
                @click="closeWithdrawModal"
              >
                취소
              </button>
              <button
                type="submit"
                class="auth-button auth-button--danger-fill"
                :disabled="isWithdrawing"
              >
                {{ isWithdrawing ? '탈퇴 중...' : '탈퇴하기' }}
              </button>
            </div>
          </form>
        </section>
      </div>
    </Teleport>
  </main>
</template>

<style scoped>
.mypage-view {
  min-height: calc(100vh - var(--header-height));
  padding: var(--spacing-8) 0 var(--spacing-16);
}

.mypage {
  display: grid;
  gap: var(--spacing-5);
  max-width: 720px;
}

.page-header h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
}

.page-header p {
  margin: var(--spacing-1) 0 0;
  color: var(--color-text-secondary);
}

.eyebrow {
  margin: 0 0 var(--spacing-2) !important;
  color: var(--color-primary) !important;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.12em;
}

.panel {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.panel__header {
  padding: var(--spacing-4) var(--spacing-5);
  background: var(--color-surface-muted);
  border-bottom: 1px solid var(--color-border-light);
}

.panel__header--with-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
}

.panel__description {
  margin: var(--spacing-1) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.panel__header h2 {
  margin: 0;
  font-size: var(--font-size-lg);
}

.panel__content {
  padding: var(--spacing-5);
}

.my-posts__state {
  display: grid;
  min-height: 120px;
  margin: 0;
  color: var(--color-text-secondary);
  place-content: center;
  justify-items: center;
  gap: var(--spacing-3);
  text-align: center;
}

.my-posts__state--error {
  color: var(--color-danger, #b42318);
}

.my-posts__state p {
  margin: 0;
}

.my-posts__write-link,
.my-posts__title {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  text-decoration: none;
}

.my-posts__write-link:hover,
.my-posts__title:hover {
  text-decoration: underline;
}

.my-posts__list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.my-posts__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
  padding: var(--spacing-4) 0;
  border-bottom: 1px solid var(--color-border-light);
}

.my-posts__item:first-child {
  padding-top: 0;
}

.my-posts__item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.my-posts__info {
  min-width: 0;
}

.my-posts__title {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-posts__meta {
  display: flex;
  flex-wrap: wrap;
  margin: var(--spacing-2) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xs);
  gap: var(--spacing-2);
}

.my-posts__actions {
  display: flex;
  flex: 0 0 auto;
  gap: var(--spacing-2);
}

.my-posts__pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--spacing-5);
  gap: var(--spacing-4);
}

.panel__actions {
  display: flex;
  justify-content: flex-end;
}

.account-actions {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: var(--spacing-5) var(--spacing-4);
}

.account-actions p {
  margin: var(--spacing-1) 0 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-index-modal);
  display: grid;
  place-items: center;
  padding: var(--spacing-4);
  background: var(--color-overlay);
}

.modal {
  width: min(100%, 460px);
  padding: var(--spacing-6);
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}

.modal header {
  display: flex;
  justify-content: space-between;
}

.modal h2 {
  margin: 0;
}

.modal > p {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.modal__close {
  width: 36px;
  height: 36px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-xl);
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-2);
}

@media (max-width: 560px) {
  .account-actions {
    grid-template-columns: 1fr;
    gap: var(--spacing-3);
  }

  .panel__actions .auth-button {
    width: 100%;
  }
}
</style>
