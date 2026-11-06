import { LCLS_SYSTM_ROWS } from '@/services/lclsSystm'
import { createCacheKey } from '@/services/locationMapCache'
import { ALL_CATEGORY, getContentTypeId, tourRequest } from '@/services/tourApi'

const CODE_PAGE_SIZE = 100

const codeRequests = new Map()
const classificationTrees = new Map()

/**
 * 코드 목록은 거의 바뀌지 않으므로 한 번 받은 결과를 계속 재사용합니다.
 *
 * @returns {Promise<{ code: string, name: string }[]>}
 */
function requestCodes(operation, params) {
  const key = createCacheKey(operation, params)

  if (!codeRequests.has(key)) {
    const request = tourRequest(operation, { ...params, numOfRows: CODE_PAGE_SIZE, pageNo: 1 })
      .then(({ items }) => items.map((item) => ({ code: String(item.code), name: item.name })))
      .catch((error) => {
        codeRequests.delete(key)
        throw error
      })

    codeRequests.set(key, request)
  }

  return codeRequests.get(key)
}

export function getSidoCodes() {
  return requestCodes('ldongCode2', { lDongListYn: 'N' })
}

export async function getSigunguCodes(sidoCode) {
  if (!sidoCode) return []

  return requestCodes('ldongCode2', { lDongListYn: 'N', lDongRegnCd: sidoCode })
}

export function clearTourCodeCache() {
  codeRequests.clear()
}

function findNode(nodes, code) {
  return code ? nodes?.find((node) => node.code === code) : undefined
}

function addNode(nodes, code, name) {
  let node = findNode(nodes, code)

  if (!node) {
    node = { code, name, children: [] }
    nodes.push(node)
  }

  return node
}

/**
 * 카테고리(관광타입)에 속한 분류 트리이며, 전체 카테고리에서는 명세의 모든 분류를 포함합니다.
 * 연결과 순서는 모두 명세 표(LCLS_SYSTM_ROWS)를 그대로 따릅니다.
 */
function getClassificationTree(category) {
  const contentTypeId = getContentTypeId(category)
  const includeAllCategories = category === ALL_CATEGORY

  if (!contentTypeId && !includeAllCategories) return []

  const treeKey = includeAllCategories ? ALL_CATEGORY : contentTypeId

  if (!classificationTrees.has(treeKey)) {
    const tree = []

    LCLS_SYSTM_ROWS.filter((row) => includeAllCategories || row[6] === contentTypeId).forEach(
      ([code1, name1, code2, name2, code3, name3]) => {
        const level1 = addNode(tree, code1, name1)
        const level2 = addNode(level1.children, code2, name2)

        addNode(level2.children, code3, name3)
      },
    )

    classificationTrees.set(treeKey, tree)
  }

  return classificationTrees.get(treeKey)
}

/**
 * 카테고리에서 고를 수 있는 분류체계의 다음 depth 코드 목록입니다.
 * lclsSystm1이 없으면 대분류, 있으면 중분류, lclsSystm2까지 있으면 소분류입니다.
 *
 * @param {{ category?: string, lclsSystm1?: string, lclsSystm2?: string }} options
 * @returns {{ code: string, name: string }[]}
 */
export function getClassificationCodes({ category = '', lclsSystm1 = '', lclsSystm2 = '' } = {}) {
  let nodes = getClassificationTree(category)

  if (lclsSystm1) nodes = findNode(nodes, lclsSystm1)?.children
  if (lclsSystm1 && lclsSystm2) nodes = findNode(nodes, lclsSystm2)?.children

  return (nodes ?? []).map(({ code, name }) => ({ code, name }))
}

/**
 * 카테고리에 속하지 않거나 상위 코드와 이어지지 않는 분류체계 코드를 비웁니다.
 *
 * @param {string} category
 * @param {{ lclsSystm1?: string, lclsSystm2?: string, lclsSystm3?: string }} classification
 */
export function normalizeClassification(category, classification = {}) {
  const level1 = findNode(getClassificationTree(category), classification.lclsSystm1)
  const level2 = findNode(level1?.children, classification.lclsSystm2)
  const level3 = findNode(level2?.children, classification.lclsSystm3)

  return {
    lclsSystm1: level1?.code ?? '',
    lclsSystm2: level2?.code ?? '',
    lclsSystm3: level3?.code ?? '',
  }
}
