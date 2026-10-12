package com.example.nexshare.security

import java.security.SecureRandom

class PairingManager {
    private val random = SecureRandom()

    fun newCode(): String {
        val code = random.nextInt(1_000_000)
        return String.format("%06d", code)
    }
}
