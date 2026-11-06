import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
vi.mock('@/services/tourSource', () => ({ USE_TOUR_DATABASE: true }))
vi.mock('@/services/tourCodes', async (original) => ({
  ...(await original()), getSidoCodes: async () => [{ code: '1', name: '서울' }],
  getSigunguCodes: async () => [],
}))
import LocationFilterBar from '@/components/location/LocationFilterBar.vue'
import { normalizeLocationFilters } from '@/services/locationFilters'
import { LOCATION_CATEGORIES } from '@/services/tourApi'

describe('database tourism filters', () => {
  it('selects supported content types without sending unavailable classification codes', async () => {
    const wrapper = mount(LocationFilterBar, { props: { filters: normalizeLocationFilters({}) } })
    await flushPromises()
    const buttons = wrapper.findAll('.location-filter__category')
    expect(buttons.map(b => b.text())).toEqual(LOCATION_CATEGORIES)
    await buttons.find(b => b.text() === '음식점').trigger('click')
    const filters = wrapper.emitted('change').at(-1)[0]
    expect(filters.category).toBe('음식점')
    expect(Object.values(filters.classification).every(value => value === '')).toBe(true)
    expect(wrapper.findComponent({ name: 'ClassificationFilter' }).exists()).toBe(false)
  })
})
