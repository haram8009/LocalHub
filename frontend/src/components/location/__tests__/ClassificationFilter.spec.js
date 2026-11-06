import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ClassificationFilter from '@/components/location/ClassificationFilter.vue'

function classification(lclsSystm1 = '', lclsSystm2 = '', lclsSystm3 = '') {
  return { lclsSystm1, lclsSystm2, lclsSystm3 }
}

function rowLabels(wrapper) {
  return wrapper.findAll('.classification-filter__label').map((label) => label.text())
}

function rowOptions(wrapper, label) {
  return wrapper
    .findAll('.classification-filter__row')
    .find((row) => row.find('.classification-filter__label').text() === label)
    .findAll('.classification-filter__option')
}

function optionNames(wrapper, label) {
  return rowOptions(wrapper, label).map((option) => option.text())
}

function clickOption(wrapper, label, name) {
  return rowOptions(wrapper, label)
    .find((option) => option.text() === name)
    .trigger('click')
}

function lastChange(wrapper) {
  return wrapper.emitted('change').at(-1)[0]
}

describe('ClassificationFilter', () => {
  it('shows all main classes for the all category', () => {
    const wrapper = mount(ClassificationFilter, { props: { category: '전체' } })

    expect(rowLabels(wrapper)).toEqual(['대분류'])
    expect(optionNames(wrapper, '대분류')).toContain('숙박')
  })

  it('reveals one more depth each time a code is selected', async () => {
    const wrapper = mount(ClassificationFilter, { props: { category: '관광지' } })

    expect(rowLabels(wrapper)).toEqual(['대분류'])
    // 대분류는 명세에 적힌 순서 그대로 보여줍니다.
    expect(optionNames(wrapper, '대분류')).toEqual([
      '전체',
      '체험관광',
      '역사관광',
      '자연관광',
      '문화관광',
    ])

    await clickOption(wrapper, '대분류', '역사관광')
    expect(lastChange(wrapper)).toEqual(classification('HS'))

    await wrapper.setProps({ classification: classification('HS') })
    expect(rowLabels(wrapper)).toEqual(['대분류', '중분류'])
    expect(optionNames(wrapper, '중분류')).toEqual([
      '전체',
      '역사유적지',
      '역사유물',
      '종교성지',
      '안보관광지',
    ])

    await clickOption(wrapper, '중분류', '종교성지')
    expect(lastChange(wrapper)).toEqual(classification('HS', 'HS03'))

    await wrapper.setProps({ classification: classification('HS', 'HS03') })
    expect(rowLabels(wrapper)).toEqual(['대분류', '중분류', '소분류'])

    await clickOption(wrapper, '소분류', '불교')
    expect(lastChange(wrapper)).toEqual(classification('HS', 'HS03', 'HS030100'))
  })

  it('offers only the classes that belong to the category', async () => {
    const wrapper = mount(ClassificationFilter, {
      props: { category: '숙박', classification: classification('AC') },
    })

    expect(optionNames(wrapper, '대분류')).toEqual(['전체', '숙박', '문화관광'])
    expect(optionNames(wrapper, '중분류')).not.toContain('캠핑')

    await wrapper.setProps({ category: '레포츠' })
    expect(optionNames(wrapper, '대분류')).toEqual(['전체', '숙박', '레저스포츠', '문화관광'])
    expect(optionNames(wrapper, '중분류')).toEqual(['전체', '캠핑'])

    await wrapper.setProps({ classification: classification('VE', 'VE12') })
    expect(optionNames(wrapper, '중분류')).toEqual(['전체', '레저스포츠시설', '기타문화관광지'])
  })

  it('offers nothing that the definition table does not list', async () => {
    const wrapper = mount(ClassificationFilter, {
      props: { category: '관광지', classification: classification('VE') },
    })

    // 교통시설(VE11)은 API 코드 목록에는 있지만 명세에 없어 선택지로 내놓지 않습니다.
    expect(optionNames(wrapper, '중분류')).toEqual([
      '전체',
      '랜드마크관광',
      '테마공원',
      '도시공원',
      '도시.지역문화관광',
      '복합관광시설',
    ])

    await wrapper.setProps({ classification: classification('VE', 'VE11') })
    expect(rowLabels(wrapper)).toEqual(['대분류', '중분류'])
  })

  it('hides the last depth when the middle class has a single sub class', async () => {
    const wrapper = mount(ClassificationFilter, {
      props: { category: '숙박', classification: classification('AC', 'AC01') },
    })

    expect(rowLabels(wrapper)).toEqual(['대분류', '중분류'])

    await wrapper.setProps({ classification: classification('AC', 'AC03') })
    expect(optionNames(wrapper, '소분류')).toEqual([
      '전체',
      '펜션',
      '한옥스테이',
      '농어촌민박',
      '홈스테이',
    ])
  })

  it('skips the first depth when the category has a single main class', async () => {
    const wrapper = mount(ClassificationFilter, { props: { category: '쇼핑' } })

    expect(rowLabels(wrapper)).toEqual(['중분류'])

    await clickOption(wrapper, '중분류', '면세점')
    expect(lastChange(wrapper)).toEqual(classification('SH', 'SH04'))

    await wrapper.setProps({ classification: classification('SH', 'SH04') })
    expect(rowLabels(wrapper)).toEqual(['중분류', '소분류'])

    await clickOption(wrapper, '소분류', '시내면세점')
    expect(lastChange(wrapper)).toEqual(classification('SH', 'SH04', 'SH040200'))

    await clickOption(wrapper, '중분류', '전체')
    expect(lastChange(wrapper)).toEqual(classification())
  })

  it('ignores a selection that does not belong to the category', () => {
    const wrapper = mount(ClassificationFilter, {
      props: { category: '숙박', classification: classification('AC', 'AC05', 'AC050100') },
    })

    expect(rowLabels(wrapper)).toEqual(['대분류', '중분류'])
    expect(
      rowOptions(wrapper, '중분류')
        .filter((option) => option.attributes('aria-pressed') === 'true')
        .map((option) => option.text()),
    ).toEqual(['전체'])
  })
})
