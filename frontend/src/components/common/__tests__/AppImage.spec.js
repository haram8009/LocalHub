import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import defaultImage from '@/assets/images/default-location.svg'
import AppImage from '@/components/common/AppImage.vue'

const thumbnail = 'https://example.com/thumbnail.jpg'
const original = 'https://example.com/original.jpg'

function mountImage(props) {
  return mount(AppImage, { props: { alt: '해운대 이미지', ...props } })
}

describe('AppImage', () => {
  it('shows the image with its alt text', () => {
    const image = mountImage({ src: thumbnail, fallbackSrc: original })

    expect(image.attributes('src')).toBe(thumbnail)
    expect(image.attributes('alt')).toBe('해운대 이미지')
  })

  it('tries the fallback address before the default image when loading fails', async () => {
    const image = mountImage({ src: thumbnail, fallbackSrc: original })

    await image.trigger('error')
    expect(image.attributes('src')).toBe(original)
    expect(image.attributes('alt')).toBe('해운대 이미지')

    await image.trigger('error')
    expect(image.attributes('src')).toBe(defaultImage)
    expect(image.attributes('alt')).toBe('')

    // 기본 이미지에서 다시 오류가 나도 더 바꾸지 않습니다.
    await image.trigger('error')
    expect(image.attributes('src')).toBe(defaultImage)
  })

  it('shows the default image without alt text when there is no address', () => {
    const image = mountImage({ src: '', fallbackSrc: '' })

    expect(image.attributes('src')).toBe(defaultImage)
    expect(image.attributes('alt')).toBe('')
  })

  it('uses the fallback address alone when the main one is missing', async () => {
    const image = mountImage({ src: '', fallbackSrc: original })

    expect(image.attributes('src')).toBe(original)

    await image.trigger('error')
    expect(image.attributes('src')).toBe(defaultImage)
  })

  it('starts over when the address changes', async () => {
    const image = mountImage({ src: thumbnail })

    await image.trigger('error')
    expect(image.attributes('src')).toBe(defaultImage)

    await image.setProps({ src: original })
    expect(image.attributes('src')).toBe(original)
    expect(image.attributes('alt')).toBe('해운대 이미지')
  })

  it('passes classes and attributes to the img element', () => {
    const image = mount(AppImage, {
      props: { src: thumbnail },
      attrs: { class: 'place-thumbnail', loading: 'lazy' },
    })

    expect(image.element.tagName).toBe('IMG')
    expect(image.classes()).toContain('place-thumbnail')
    expect(image.attributes('loading')).toBe('lazy')
  })
})
