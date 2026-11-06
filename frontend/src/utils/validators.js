export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 20

const USER_ID_PATTERN = /^[a-z0-9]{4,20}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
const SPECIAL_CHARACTER_PATTERN = /[^A-Za-z0-9\s]/

// 화면의 비밀번호 조건 체크리스트와 검증이 같은 규칙을 쓰도록 한 곳에 둡니다.
export function getPasswordChecks(password = '') {
  const value = String(password)

  return [
    {
      key: 'length',
      label: `${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자`,
      passed: value.length >= PASSWORD_MIN_LENGTH && value.length <= PASSWORD_MAX_LENGTH,
    },
    { key: 'letter', label: '영문 포함', passed: /[A-Za-z]/.test(value) },
    { key: 'digit', label: '숫자 포함', passed: /\d/.test(value) },
    { key: 'special', label: '특수문자 포함', passed: SPECIAL_CHARACTER_PATTERN.test(value) },
    { key: 'space', label: '공백 없음', passed: value.length > 0 && !/\s/.test(value) },
  ]
}

// 검증 함수는 문제가 없으면 빈 문자열, 있으면 안내 문구를 돌려줍니다.
export function validateUserId(userId = '') {
  if (!userId) return '아이디를 입력해 주세요.'
  if (!USER_ID_PATTERN.test(userId)) return '아이디는 영문 소문자와 숫자 4~20자로 입력해 주세요.'

  return ''
}

export function validatePassword(password = '') {
  if (!password) return '비밀번호를 입력해 주세요.'
  if (getPasswordChecks(password).some((check) => !check.passed)) {
    return `비밀번호는 영문, 숫자, 특수문자를 모두 포함해 ${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자로 입력해 주세요.`
  }

  return ''
}

export function validatePasswordConfirm(password = '', passwordConfirm = '') {
  if (!passwordConfirm) return '비밀번호를 한 번 더 입력해 주세요.'
  if (password !== passwordConfirm) return '비밀번호가 일치하지 않습니다.'

  return ''
}

export function validateName(name = '') {
  const value = name.trim()

  if (!value) return '이름을 입력해 주세요.'
  if (value.length < 2 || value.length > 20) return '이름은 2~20자로 입력해 주세요.'

  return ''
}

export function validateEmail(email = '') {
  const value = email.trim()

  if (!value) return '이메일을 입력해 주세요.'
  if (!EMAIL_PATTERN.test(value)) return '이메일 형식이 올바르지 않습니다.'

  return ''
}
