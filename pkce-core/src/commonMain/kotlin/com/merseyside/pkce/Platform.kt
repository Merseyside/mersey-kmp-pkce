package com.merseyside.pkce

/**
 * Криптостойкие случайные байты. RFC 7636 требует именно их: обычный Random
 * предсказуем, а по верификатору восстанавливается весь смысл защиты.
 */
internal expect fun secureRandomBytes(size: Int): ByteArray

/** Текущее время в миллисекундах эпохи — им считается срок жизни токена. */
internal expect fun currentTimeMillis(): Long
