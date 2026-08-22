export const parseMultipleAnswer = (answer?: string | null): string[] => {
  if (!answer?.trim()) return []
  return [...new Set(answer.split(',').map(value => value.trim()).filter(Boolean))]
    .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }))
}

export const serializeMultipleAnswer = (answers: string[]): string =>
  [...new Set(answers.map(value => value.trim()).filter(Boolean))]
    .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }))
    .join(',')
