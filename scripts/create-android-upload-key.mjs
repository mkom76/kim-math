import { randomBytes } from 'node:crypto'
import { chmodSync, existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const projectDir = resolve(scriptDir, '..')
const androidDir = resolve(projectDir, 'frontend/android')
const keystoreDir = resolve(androidDir, 'keystores')
const keystorePath = resolve(keystoreDir, 'kim-math-upload.jks')
const propertiesPath = resolve(androidDir, 'keystore.properties')
const keyAlias = 'kim-math-upload'

if (existsSync(keystorePath) || existsSync(propertiesPath)) {
  console.error('Upload-key files already exist; refusing to overwrite them.')
  process.exit(1)
}

mkdirSync(keystoreDir, { recursive: true, mode: 0o700 })
const password = randomBytes(36).toString('base64url')

const result = spawnSync('keytool', [
  '-genkeypair',
  '-v',
  '-keystore', keystorePath,
  '-storetype', 'JKS',
  '-storepass', password,
  '-keypass', password,
  '-alias', keyAlias,
  '-keyalg', 'RSA',
  '-keysize', '4096',
  '-validity', '10000',
  '-dname', 'CN=Kim Math, O=Kim Math, C=KR',
], {
  encoding: 'utf8',
  stdio: ['ignore', 'pipe', 'pipe'],
})

if (result.status !== 0) {
  console.error(result.stderr || result.stdout || 'keytool failed')
  process.exit(result.status ?? 1)
}

const properties = [
  `storeFile=${keystorePath}`,
  `storePassword=${password}`,
  `keyAlias=${keyAlias}`,
  `keyPassword=${password}`,
  '',
].join('\n')

writeFileSync(propertiesPath, properties, { encoding: 'utf8', mode: 0o600 })
chmodSync(keystorePath, 0o600)

console.log(`Created upload keystore: ${keystorePath}`)
console.log(`Created signing properties: ${propertiesPath}`)
console.log('Back up both files in a secure, separate location before publishing.')
