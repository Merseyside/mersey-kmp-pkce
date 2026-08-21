package com.merseyside.pkce

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun emptyInputMatchesReferenceVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hashOf("")
        )
    }

    @Test
    fun shortInputMatchesReferenceVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hashOf("abc")
        )
    }

    /** 56 байт: длина, при которой дополнение занимает целый лишний блок. */
    @Test
    fun blockBoundaryInputMatchesReferenceVector() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hashOf("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq")
        )
    }

    @Test
    fun longInputMatchesReferenceVector() {
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            hashOf("a".repeat(1_000_000))
        )
    }

    private fun hashOf(input: String): String =
        Sha256.digest(input.encodeToByteArray()).joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}
