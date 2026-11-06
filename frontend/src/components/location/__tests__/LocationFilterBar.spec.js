import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const tourCodes = vi.hoisted(() => ({ getSidoCodes: vi.fn(), getSigunguCodes: vi.fn() }))

vi.mock('@/services/tourCodes', async (importOriginal) => ({
  ...(await importOriginal()),
  ...tourCodes,
}))

import LocationFilterBar from '@/components/location/LocationFilterBar.vue'
import { normalizeLocationFilters } from '@/services/locationFilters'
import { ALL_CATEGORY } from '@/services/tourApi'
import { getClassificationCodes } from '@/services/tourCodes'

async function mountBar(filters = {}, props = {}) {
  const wrapper = mount(LocationFilterBar, {
    props: { filters: normalizeLocationFilters(filters), ...props },
  })
  await flushPromises()

  return wrapper
}

function clickButton(wrapper, selector, text) {
  return wrapper
    .findAll(selector)
    .find((button) => button.text() === text)
    .trigger('click')
}

function lastChange(wrapper) {
  return wrapper.emitted('change').at(-1)[0]
}

describe('LocationFilterBar', () => {
  beforeEach(() => {
    tourCodes.getSidoCodes.mockReset()
    tourCodes.getSigunguCodes.mockReset()
    tourCodes.getSidoCodes.mockResolvedValue([{ code: '26', name: '부산광역시' }])
    tourCodes.getSigunguCodes.mockResolvedValue([{ code: '350', name: '해운대구' }])
  })

  it('shows the main classes from the classification table', async () => {
    const wrapper = await mountBar({
      category: '여행코스',
      classification: { lclsSystm1: 'C01' },
    })
    const categories = wrapper.findAll('.location-filter__category')
    const names = getClassificationCodes({ category: ALL_CATEGORY }).map(({ name }) => name)

    expect(categories.map((category) => category.text())).toEqual([
      '음식',
      ...names.filter((name) => name !== '음식' && name !== '추천코스'),
      '추천코스',
    ])
    expect(categories.map((category) => category.text())).not.toContain(ALL_CATEGORY)
    expect(
      categories
        .filter((category) => category.attributes('aria-pressed') === 'true')
        .map((category) => category.text()),
    ).toEqual(['추천코스'])
  })

  it('selects a main class and clears its middle and sub classes', async () => {
    const wrapper = await mountBar({
      keyword: '바다',
      category: '숙박',
      region: { sido: '26', sigungu: '350' },
      classification: { lclsSystm1: 'AC', lclsSystm2: 'AC03', lclsSystm3: 'AC030100' },
    })

    // 대분류 선택은 관광타입 필터를 해제하고 해당 대분류의 하위 분류를 새로 고릅니다.
    await clickButton(wrapper, '.location-filter__category', '레저스포츠')

    expect(lastChange(wrapper)).toEqual({
      keyword: '바다',
      category: ALL_CATEGORY,
      region: { sido: '26', sigungu: '350' },
      classification: { lclsSystm1: 'LS', lclsSystm2: '', lclsSystm3: '' },
    })
    await wrapper.setProps({ filters: lastChange(wrapper) })
    expect(
      wrapper
        .findAll('.location-filter__category')
        .find((button) => button.text() === '레저스포츠')
        .classes(),
    ).toContain('location-filter__category--active')
    expect(wrapper.findAll('.classification-filter__label').map((label) => label.text())).toEqual([
      '중분류',
    ])
  })

  it('emits a middle-class selection under the selected main class', async () => {
    const wrapper = await mountBar({ classification: { lclsSystm1: 'AC' } })

    await clickButton(wrapper, '.classification-filter__option', '호텔')

    expect(lastChange(wrapper)).toMatchObject({
      category: ALL_CATEGORY,
      classification: { lclsSystm1: 'AC', lclsSystm2: 'AC01', lclsSystm3: '' },
    })
  })

  it('emits the selected region and clears the sigungu when the sido changes', async () => {
    const wrapper = await mountBar({ region: { sido: '26', sigungu: '350' } })
    const [sidoSelect, sigunguSelect] = wrapper.findAll('.region-select__control')

    await sigunguSelect.setValue('')
    expect(lastChange(wrapper).region).toEqual({ sido: '26', sigungu: '' })

    await sigunguSelect.setValue('350')
    expect(lastChange(wrapper).region).toEqual({ sido: '26', sigungu: '350' })

    await sidoSelect.setValue('')
    expect(lastChange(wrapper).region).toEqual({ sido: '', sigungu: '' })
  })

  it('searches with the trimmed keyword on the button and on Enter', async () => {
    const wrapper = await mountBar({ category: '쇼핑' })
    const input = wrapper.find('.location-filter__input')

    await input.setValue('  시장 ')
    await wrapper.find('.location-filter__search-button').trigger('click')
    expect(lastChange(wrapper)).toMatchObject({ keyword: '시장', category: '쇼핑' })

    await input.setValue('해운대')
    await input.trigger('keydown', { key: 'Enter', isComposing: true })
    expect(wrapper.emitted('change')).toHaveLength(1)

    await input.trigger('keydown', { key: 'Enter' })
    expect(lastChange(wrapper)).toMatchObject({ keyword: '해운대' })
  })

  it('never submits a surrounding form', async () => {
    const submit = vi.fn((event) => event.preventDefault())
    const Host = defineComponent({
      components: { LocationFilterBar },
      setup: () => ({ filters: normalizeLocationFilters({ category: '숙박' }), submit }),
      template: '<form @submit="submit"><LocationFilterBar :filters="filters" /></form>',
    })
    const wrapper = mount(Host, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.findAll('form')).toHaveLength(1)

    for (const button of wrapper.findAll('button')) {
      await button.trigger('click')
    }

    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
    wrapper.find('.location-filter__input').element.dispatchEvent(enter)

    // 브라우저는 취소되지 않은 Enter에만 폼을 제출합니다.
    expect(enter.defaultPrevented).toBe(true)
    expect(submit).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('clears the typed keyword and asks the parent to reset', async () => {
    const wrapper = await mountBar({ keyword: '시장' })
    const input = wrapper.find('.location-filter__input')

    expect(input.element.value).toBe('시장')

    await wrapper.find('.location-filter__reset-button').trigger('click')

    expect(wrapper.emitted('reset')).toHaveLength(1)
    expect(input.element.value).toBe('')
  })

  it('disables every control while loading', async () => {
    const wrapper = await mountBar({ category: '숙박' }, { loading: true })
    const controls = wrapper.findAll('button, input, select')

    expect(controls.length).toBeGreaterThan(0)
    expect(controls.every((control) => control.element.disabled)).toBe(true)
  })
})
