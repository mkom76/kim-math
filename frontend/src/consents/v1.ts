/**
 * 개인정보 수집·이용 동의서 v1
 *
 * PIPA 감사 대응: 본문은 git history가 곧 audit log. 정책 개정 시 `v2.ts`를
 * 새로 만들고 백엔드 `app.consent.version`을 업데이트한다. 기존 동의자의
 * 버전 문자열은 그대로 보존된다.
 */
export const CONSENT_VERSION = 'v1';

export interface ConsentSection {
  heading: string;
  rows: { label: string; value: string }[];
}

export interface ConsentDocument {
  version: string;
  title: string;
  intro: string;
  sections: ConsentSection[];
  /** Single checkbox label that the guardian must check. */
  acknowledgementLabel: string;
}

export const CONSENT_V1: ConsentDocument = {
  version: CONSENT_VERSION,
  title: '개인정보 수집·이용 동의서',
  intro:
    '본 학원은 학생 관리 및 학부모 안내를 위해 아래와 같이 개인정보를 수집·이용합니다. ' +
    '내용을 확인하시고 동의 여부를 결정해 주세요.',
  sections: [
    {
      heading: '1. 학생 정보',
      rows: [
        { label: '수집 항목', value: '이름, 학년, 학교, 학생 연락처(선택)' },
        { label: '이용 목적', value: '수업·시험·숙제 관리, 학습 이력 기록, 본인 식별' },
        { label: '보유 기간', value: '재원 기간 + 졸업·퇴원 후 3년 (회계·민원 대응)' },
      ],
    },
    {
      heading: '2. 보호자 정보',
      rows: [
        { label: '수집 항목', value: '보호자 성명, 보호자 연락처' },
        { label: '이용 목적', value: '학생 상태 안내, 결제·회계 연락, 비상 시 연락' },
        { label: '보유 기간', value: '재원 기간 + 졸업·퇴원 후 3년' },
      ],
    },
    {
      heading: '3. 동의 거부 권리',
      rows: [
        {
          label: '안내',
          value:
            '동의를 거부할 권리가 있으며, 거부 시 학원 등록·이용이 제한될 수 있습니다. ' +
            '동의 이후에도 언제든지 학원에 요청하여 동의 철회·정보 삭제가 가능합니다.',
        },
      ],
    },
  ],
  acknowledgementLabel:
    '위 내용을 모두 확인하였으며, 학생 및 보호자 개인정보의 수집·이용에 동의합니다.',
};
