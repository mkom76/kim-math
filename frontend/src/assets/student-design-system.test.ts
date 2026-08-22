/// <reference types="node" />

import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const rawHexColor = /#[0-9a-f]{3}(?:[0-9a-f]{3}(?:[0-9a-f]{2})?)?\b/i

function vueFiles(relativeDirectory: string) {
  const directory = resolve(process.cwd(), relativeDirectory)
  return readdirSync(directory)
    .filter((file) => file.endsWith('.vue'))
    .map((file) => `${directory}/${file}`)
}

describe('student design system', () => {
  it('keeps raw palette values out of student V2 screens and components', () => {
    const files = [
      ...vueFiles('src/views/student-v2/'),
      ...vueFiles('src/components/student-v2/'),
      resolve(process.cwd(), 'src/components/StudentBottomNavV2.vue'),
      resolve(process.cwd(), 'src/views/StudentNotificationsView.vue'),
    ]

    const violations = files.flatMap((file) => {
      const matches = readFileSync(file, 'utf8').match(rawHexColor)
      return matches ? [`${file}: ${matches[0]}`] : []
    })

    expect(violations).toEqual([])
  })
})
