import { describe, expect, it } from 'vitest'
import {
  formatHomeworkQuestionType,
  getHomeworkQuestionType,
  homeworkQuestionTypes,
  homeworkTypeCatalogVersion,
  homeworkTypeOptions,
  matchesHomeworkTypeSearch,
} from './homeworkQuestionTypes'

describe('PDF 숙제 문항의 유형 기준', () => {
  it('검토한 529개 유형의 고유 ID와 네 단계 경로를 보존한다', () => {
    expect(homeworkQuestionTypes).toHaveLength(529)
    expect(new Set(homeworkQuestionTypes.map(type => type.id)).size).toBe(529)
    expect(new Set(homeworkQuestionTypes.map(type => type.code)).size).toBe(529)
    expect(homeworkQuestionTypes.every(type => type.path.length === 4)).toBe(true)
    expect(homeworkTypeCatalogVersion).toMatch(/^2026-11-13-[a-f0-9]{16}$/)
    expect(homeworkTypeOptions.map(option => option.label)).toEqual(['수학(상)', '수학(하)', '대수', '미적분1'])
  })

  it('과목·단원 ID, 원문 THM 번호와 임의의 AI 유형을 세부유형으로 허용하지 않는다', () => {
    for (const id of [null, undefined, '', 'ALG', 'ALG-01-01', 'THM01', '판별식', 'invented-ai-type']) {
      expect(getHomeworkQuestionType(id)).toBeUndefined()
      expect(formatHomeworkQuestionType(id)).toBe('미분류')
    }
  })

  it('유형 이름에 들어 있는 분수·슬래시를 경로 구분자로 해석하지 않는다', () => {
    const type = homeworkQuestionTypes.find(item => item.path[3] === '0/0 꼴 극한값의 계산')!
    expect(type).toBeDefined()
    expect(getHomeworkQuestionType(type.id)?.path).toEqual(['미적분1', '함수의 극한과 연속', '함수의 극한', '0/0 꼴 극한값의 계산'])
    expect(formatHomeworkQuestionType(type.id)).toBe('미적분1 › 함수의 극한과 연속 › 함수의 극한 › 0/0 꼴 극한값의 계산')
  })

  it('원문에서 확인한 유형과 본문 분류안·표제 교정안을 구분한다', () => {
    expect(homeworkQuestionTypes.filter(type => type.proposed)).toHaveLength(19)
    expect(homeworkQuestionTypes.filter(type => type.corrected)).toHaveLength(2)
    expect(homeworkQuestionTypes.filter(type => type.proposed).every(type =>
      ['원의 방정식', '도형의 이동'].includes(type.path[2]!),
    )).toBe(true)
  })

  it('옛 과목명으로 검색해도 대수·미적분1의 유형을 찾는다', () => {
    expect(matchesHomeworkTypeSearch(['대수', '지수함수와 로그함수', '지수', '거듭제곱근의 계산'], '수학1 거듭제곱근')).toBe(true)
    expect(matchesHomeworkTypeSearch(['미적분1', '미분', '접선의 방정식'], '수학 II 접선')).toBe(true)
    expect(matchesHomeworkTypeSearch(['대수', '지수'], '수학2 지수')).toBe(false)
  })
})
