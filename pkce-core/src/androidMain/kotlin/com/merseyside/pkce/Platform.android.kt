package com.merseyside.pkce

import java.security.SecureRandom

private val secureRandom by lazy { SecureRandom() }

internal actual fun secureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also(secureRandom::nextBytes)

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
