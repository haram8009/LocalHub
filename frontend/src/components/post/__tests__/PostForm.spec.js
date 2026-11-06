import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const locationService = vi.hoisted(() => ({ DEFAULT_PAGE_SIZE: 20, getLocationPage: vi.fn() }))
const tourCodes = vi.hoisted(() => ({ getSidoCodes: vi.fn(), getSigunguCodes: vi.fn() }))

vi.mock('@/services/locationService', () => locationService)
vi.mock('@/services/tourCodes', async (importOriginal) => ({
  ...(await importOriginal()),
  ...tourCodes,
}))

import PostForm from '@/components/post/PostForm.vue'

function location(id) {
  return {
    id,
    contentId: id,
    name: `여행지 ${id}`,
    categoryLabel: '관광지',
    address: '부산광역시 해운대구',
    latitude: 35.16,
    longitude: 129.16,
  }
}

function page(items, { totalCount = items.length, totalPages = 1 } = {}) {
  return { items, totalCount, page: 1, size: 20, totalPages }
}

function lastQuery() {
  return locationService.getLocationPage.mock.calls.at(-1)[0]
}

function resultNames(wrapper) {
  return wrapper.findAll('.result-list strong').map((name) => name.text())
}

function clickButton(wrapper, selector, text) {
  return wrapper
    .findAll(selector)
    .find((button) => button.text() === text)
    .trigger('click')
}

async function mountForm() {
  const wrapper = mount(PostForm, { global: { stubs: { CourseKakaoMap: true } } })
  await flushPromises()

  return wrapper
}

describe('PostForm location search', () => {
  beforeEach(() => {
    locationService.getLocationPage.mockReset()
    locationService.getLocationPage.mockResolvedValue(
      page([location('1')], { totalCount: 1234, totalPages: 62 }),
    )
    tourCodes.getSidoCodes.mockReset()
    tourCodes.getSigunguCodes.mockReset()
    tourCodes.getSidoCodes.mockResolvedValue([{ code: '26', name: '부산광역시' }])
    tourCodes.getSigunguCodes.mockResolvedValue([{ code: '350', name: '해운대구' }])
  })

  it('lists nationwide locations first and shows the total count', async () => {
    const wrapper = await mountForm()

    expect(locationService.getLocationPage).toHaveBeenCalledTimes(1)
    expect(lastQuery()).toMatchObject({
      mode: 'area',
      keyword: '',
      category: '',
      lclsSystm1: 'FD',
      areaCode: '',
      page: 1,
      size: 20,
    })
    expect(resultNames(wrapper)).toEqual(['여행지 1'])
    expect(wrapper.text()).toContain('1,234개')
  })

  it('searches by the region and classification of the shared filter bar', async () => {
    const wrapper = await mountForm()

    await wrapper.find('.region-select__control').setValue('26')
    await flushPromises()
    expect(lastQuery()).toMatchObject({ mode: 'area', areaCode: '26', sigunguCode: '' })

    await wrapper.findAll('.region-select__control')[1].setValue('350')
    await flushPromises()
    expect(lastQuery()).toMatchObject({ areaCode: '26', sigunguCode: '350' })

    await clickButton(wrapper, '.location-filter__category', '숙박')
    await flushPromises()
    expect(
      wrapper
        .findAll('.location-filter__category')
        .find((button) => button.text() === '숙박')
        .classes(),
    ).toContain('location-filter__category--active')
    expect(wrapper.findAll('.location-filter__category').map((button) => button.text())).not.toContain('전체')
    await clickButton(wrapper, '.classification-filter__option', '펜션/민박')
    await flushPromises()
    expect(lastQuery()).toMatchObject({
      mode: 'area',
      category: '',
      areaCode: '26',
      sigunguCode: '350',
      lclsSystm1: 'AC',
      lclsSystm2: 'AC03',
      page: 1,
    })

    // 대분류를 바꾸면 이전 중·소분류는 따라가지 않습니다.
    await clickButton(wrapper, '.location-filter__category', '음식')
    await flushPromises()
    expect(lastQuery()).toMatchObject({ category: '', lclsSystm1: 'FD', lclsSystm2: '' })

    await clickButton(wrapper, '.location-filter__reset-button', '필터 초기화')
    await flushPromises()
    expect(lastQuery()).toMatchObject({
      mode: 'area',
      category: '',
      lclsSystm1: 'FD',
      areaCode: '',
      keyword: '',
    })
  })

  it('searches by keyword without submitting the post', async () => {
    const wrapper = await mountForm()
    const input = wrapper.find('.location-filter__input')
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })

    await input.setValue('해운대')
    input.element.dispatchEvent(enter)
    await flushPromises()

    expect(enter.defaultPrevented).toBe(true)
    expect(lastQuery()).toMatchObject({ mode: 'keyword', keyword: '해운대', page: 1 })

    await wrapper.find('.location-filter__search-button').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('appends the next page with 더 보기 and hides the button on the last page', async () => {
    locationService.getLocationPage.mockImplementation(async ({ page: pageNo }) =>
      pageNo === 1
        ? page([location('1'), location('2')], { totalCount: 3, totalPages: 2 })
        : page([location('2'), location('3')], { totalCount: 3, totalPages: 2 }),
    )
    const wrapper = await mountForm()

    await wrapper.find('.more-button').trigger('click')
    await flushPromises()

    expect(lastQuery()).toMatchObject({ page: 2, size: 20 })
    expect(resultNames(wrapper)).toEqual(['여행지 1', '여행지 2', '여행지 3'])
    expect(wrapper.find('.more-button').exists()).toBe(false)
  })

  it('shows a message when nothing matches or the search fails', async () => {
    locationService.getLocationPage.mockResolvedValue(page([]))
    const wrapper = await mountForm()

    expect(wrapper.text()).toContain('조건에 맞는 여행지가 없습니다.')
    expect(wrapper.findComponent({ name: 'CourseKakaoMap' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'CourseKakaoMap' }).props('locations')).toEqual([])

    locationService.getLocationPage.mockRejectedValue(new Error('network error'))
    await clickButton(wrapper, '.location-filter__category', '쇼핑')
    await flushPromises()

    expect(wrapper.find('.location-state--error').text()).toBe('network error')
    expect(wrapper.findComponent({ name: 'CourseKakaoMap' }).exists()).toBe(true)
  })

  it('keeps the map visible when the selected place has no displayable coordinates', async () => {
    const initialPost = {
      locations: [{ ...location('unknown-position'), latitude: null, longitude: null }],
    }
    const wrapper = mount(PostForm, {
      props: { mode: 'edit', initialPost },
      global: { stubs: { CourseKakaoMap: true } },
    })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'CourseKakaoMap' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'CourseKakaoMap' }).props('locations')).toHaveLength(1)
  })

  it('submits the picked locations in visit order', async () => {
    locationService.getLocationPage.mockResolvedValue(page([location('1'), location('2')]))
    const wrapper = await mountForm()
    const addButtons = wrapper.findAll('.result-list button')

    await addButtons[1].trigger('click')
    await addButtons[0].trigger('click')
    await wrapper.find('input[maxlength="100"]').setValue('부산 코스')
    await wrapper.find('textarea').setValue('해운대에서 시작합니다.')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')).toHaveLength(1)
    expect(wrapper.emitted('submit')[0][0]).toMatchObject({
      title: '부산 코스',
      locations: [
        { contentId: '2', visitOrder: 1 },
        { contentId: '1', visitOrder: 2 },
      ],
    })
  })
})
