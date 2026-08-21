package com.merseyside.pkce

import kotlin.io.encoding.Base64

/**
 * Пара «верификатор — вызов» из RFC 7636.
 *
 * Верификатор остаётся в приложении, наружу уходит только его хеш. Поэтому
 * перехваченный код авторизации бесполезен: обменять его на токен без
 * верификатора невозможно.
 */
data class PkceCodes(
    val verifier: String,
    val challenge: String,
    val method: String = METHOD_S256
) {
    companion object {
        const val METHOD_S256 = "S256"

        /** 32 байта энтропии дают строку в 43 символа — минимум по RFC 7636. */
        private const val VERIFIER_BYTES = 64

        internal val base64Url: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun generate(): PkceCodes {
            val verifier = base64Url.encode(secureRandomBytes(VERIFIER_BYTES))
            return PkceCodes(verifier = verifier, challenge = challengeOf(verifier))
        }

        fun challengeOf(verifier: String): String =
            base64Url.encode(Sha256.digest(verifier.encodeToByteArray()))
    }
}
