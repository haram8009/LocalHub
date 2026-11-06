import { describe, expect, it } from 'vitest'

import {
  getPasswordChecks,
  validateEmail,
  validateName,
  validatePassword,
  validatePasswordConfirm,
  validateUserId,
} from '@/utils/validators'

describe('validators', () => {
  it('accepts passwords with letters, digits, and special characters in 8~20 chars', () => {
    expect(validatePassword('Trip1234!')).toBe('')
    expect(validatePassword('a1!a1!a1')).toBe('')
  })

  it.each([
    ['', '빈 값'],
    ['Trip12!', '8자 미만'],
    ['Trip1234!Trip1234!abc', '20자 초과'],
    ['12345678!', '영문 없음'],
    ['TripTrip!', '숫자 없음'],
    ['Trip12345', '특수문자 없음'],
    ['Trip 1234!', '공백 포함'],
  ])('rejects the password %j (%s)', (password) => {
    expect(validatePassword(password)).not.toBe('')
  })

  it('reports each password rule separately for the checklist', () => {
    const checks = Object.fromEntries(
      getPasswordChecks('abcdefgh').map((check) => [check.key, check.passed]),
    )

    expect(checks).toEqual({ length: true, letter: true, digit: false, special: false, space: true })
  })

  it('checks the password confirmation', () => {
    expect(validatePasswordConfirm('Trip1234!', 'Trip1234!')).toBe('')
    expect(validatePasswordConfirm('Trip1234!', 'Trip1234?')).not.toBe('')
    expect(validatePasswordConfirm('Trip1234!', '')).not.toBe('')
  })

  it('validates user id, name, and email', () => {
    expect(validateUserId('trip01')).toBe('')
    expect(validateUserId('abc')).not.toBe('')
    expect(validateUserId('Trip01')).not.toBe('')

    expect(validateName('김여행')).toBe('')
    expect(validateName(' 김 ')).not.toBe('')

    expect(validateEmail('trip@example.com')).toBe('')
    expect(validateEmail('trip@example')).not.toBe('')
  })
})
