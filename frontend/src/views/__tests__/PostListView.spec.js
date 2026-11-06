import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getPosts } = vi.hoisted(() => ({ getPosts: vi.fn() }))
vi.mock('@/services/posts', () => ({ getPosts }))
import PostListView from '@/views/PostListView.vue'

async function open(path = '/posts') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/posts', component: PostListView },
      { path: '/posts/:id', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  const wrapper = mount(PostListView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}
describe('post search controls', () => {
  beforeEach(() => {
    getPosts.mockReset()
    getPosts.mockResolvedValue({
      items: [{ id: 42, title: '부산에서 바다를', relevanceScore: 4, locations: [] }],
      total: 21,
    })
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })
  it('submits multiple terms without a mode selector or helper and preserves them across pages', async () => {
    const { wrapper, router } = await open()
    await wrapper.find('#post-search').setValue('부산 바다')
    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.find('.search-help').exists()).toBe(false)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(getPosts).toHaveBeenLastCalledWith(
      expect.objectContaining({ query: '부산 바다', page: 1 }),
    )
    expect(getPosts.mock.lastCall[0]).not.toHaveProperty('mode')
    expect(router.currentRoute.value.query).toEqual({ query: '부산 바다' })
    expect(wrapper.find('.relevance-score').text()).toBe('관련도 4')
    expect(wrapper.find('.post-number').text()).toBe('1')
    await wrapper
      .findAll('.pagination-button')
      .find((button) => button.text() === '2')
      .trigger('click')
    await flushPromises()
    expect(getPosts).toHaveBeenLastCalledWith(
      expect.objectContaining({ query: '부산 바다', page: 2 }),
    )
    expect(wrapper.find('.post-number').text()).toBe('11')
    expect(wrapper.find('.post-title-link').attributes('href')).toBe('/posts/42')
  })
  it('ignores the mode from old shared URLs and reset clears search conditions', async () => {
    const { wrapper, router } = await open('/posts?query=부산&mode=OR&page=2')
    expect(getPosts.mock.lastCall[0]).not.toHaveProperty('mode')
    await wrapper.find('.search-button--reset').trigger('click')
    await flushPromises()
    expect(getPosts).toHaveBeenLastCalledWith(expect.objectContaining({ query: '', page: 1 }))
    expect(router.currentRoute.value.query).toEqual({})
  })
})
