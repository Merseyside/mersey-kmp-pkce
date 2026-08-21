package com.merseyside.pkce

import kotlin.io.encoding.Base64

/**
 * Байты берутся у браузерного `crypto.getRandomValues` и переносятся в Kotlin
 * строкой base64: обмен массивами между Wasm и JS требует лишних типов,
 * а строка проходит через границу как есть.
 */
private fun randomBytesAsBase64(size: Int): String = js(
    """{
        const bytes = new Uint8Array(size);
        crypto.getRandomValues(bytes);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        return btoa(binary);
    }"""
)

private fun nowMillis(): Double = js("Date.now()")

internal actual fun secureRandomBytes(size: Int): ByteArray =
    Base64.Default.decode(randomBytesAsBase64(size))

internal actual fun currentTimeMillis(): Long = nowMillis().toLong()
