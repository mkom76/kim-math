import { readFile, writeFile } from 'node:fs/promises'

// ICO supports embedded PNG entries. Keep the browser fallback in sync with
// the generated PNG favicons instead of renaming a PNG file to .ico.
const [output, ...inputs] = process.argv.slice(2)
if (!output || inputs.length === 0) {
  throw new Error('Usage: node write-ico.mjs output.ico favicon-16.png favicon-32.png')
}
const images = await Promise.all(inputs.map((path) => readFile(path)))
const directory = Buffer.alloc(6 + images.length * 16)
directory.writeUInt16LE(1, 2)
directory.writeUInt16LE(images.length, 4)
let offset = directory.length
for (const [index, png] of images.entries()) {
  const width = png.readUInt32BE(16)
  const height = png.readUInt32BE(20)
  if (
    png.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a' ||
    width !== height ||
    width < 1 ||
    width > 256
  ) {
    throw new Error(`Expected a square PNG up to 256px: ${inputs[index]}`)
  }
  const entry = 6 + index * 16
  directory[entry] = width % 256
  directory[entry + 1] = height % 256
  directory.writeUInt16LE(1, entry + 4)
  directory.writeUInt16LE(32, entry + 6)
  directory.writeUInt32LE(png.length, entry + 8)
  directory.writeUInt32LE(offset, entry + 12)
  offset += png.length
}
await writeFile(output, Buffer.concat([directory, ...images]))
