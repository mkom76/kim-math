import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data("\(message)\n".utf8))
    exit(1)
}

let paths = Array(CommandLine.arguments.dropFirst())
guard !paths.isEmpty else {
    fail("Usage: swift ensure-png-alpha.swift <png> [png ...]")
}

guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else {
    fail("Unable to create the sRGB color space")
}

for path in paths {
    let url = URL(fileURLWithPath: path)
    guard
        let source = CGImageSourceCreateWithURL(url as CFURL, nil),
        let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
    else {
        fail("Unable to read PNG: \(path)")
    }

    guard let context = CGContext(
        data: nil,
        width: image.width,
        height: image.height,
        bitsPerComponent: 8,
        bytesPerRow: image.width * 4,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            | CGBitmapInfo.byteOrder32Big.rawValue
    ) else {
        fail("Unable to create RGBA context for: \(path)")
    }

    context.interpolationQuality = .high
    context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))

    guard let rgbaImage = context.makeImage() else {
        fail("Unable to create RGBA image for: \(path)")
    }

    let output = NSMutableData()
    guard let destination = CGImageDestinationCreateWithData(
        output,
        UTType.png.identifier as CFString,
        1,
        nil
    ) else {
        fail("Unable to create PNG destination for: \(path)")
    }

    CGImageDestinationAddImage(destination, rgbaImage, nil)
    guard CGImageDestinationFinalize(destination) else {
        fail("Unable to encode PNG: \(path)")
    }

    do {
        try (output as Data).write(to: url, options: .atomic)
    } catch {
        fail("Unable to replace PNG at \(path): \(error)")
    }
}
