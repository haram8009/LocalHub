<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import PostForm from '@/components/post/PostForm.vue'
import { getPost, updatePost as updatePostRequest } from '@/services/posts'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isLoading = ref(true)
const isSubmitting = ref(false)
const post = ref(null)

async function fetchPost() {
  isLoading.value = true

  try {
    const fetchedPost = await getPost(route.params.postId)

    // 주소를 직접 입력해 들어온 경우에도 작성자만 수정 화면을 볼 수 있습니다.
    if (!fetchedPost.authorId || fetchedPost.authorId !== authStore.user?.id) {
      window.alert('작성자만 수정할 수 있습니다.')
      await router.replace(`/posts/${route.params.postId}`)
      return
    }

    post.value = fetchedPost
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '게시글을 불러오지 못했습니다.')
    await router.replace('/posts')
  } finally {
    isLoading.value = false
  }
}

async function updatePost(formData) {
  if (isSubmitting.value) return

  isSubmitting.value = true

  try {
    await updatePostRequest(route.params.postId, formData)
    window.alert('게시글이 수정되었습니다.')
    await router.push(`/posts/${route.params.postId}`)
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '게시글 수정 중 오류가 발생했습니다.')
  } finally {
    isSubmitting.value = false
  }
}

onMounted(fetchPost)
</script>

<template>
  <div v-if="isLoading" class="loading">게시글을 불러오고 있습니다.</div>
  <PostForm
    v-else-if="post"
    mode="edit"
    :initial-post="post"
    :loading="isSubmitting"
    @submit="updatePost"
    @cancel="router.push(`/posts/${route.params.postId}`)"
  />
</template>

<style scoped>
.loading {
  display: grid;
  min-height: calc(100vh - var(--header-height));
  color: var(--color-text-secondary);
  place-items: center;
}
</style>
