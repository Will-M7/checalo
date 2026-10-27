package com.example.ecolacteoshuata.core.util

import kotlin.random.Random

object IdGenerator {
    private val hexChars = "0123456789abcdef"

    fun newId(): String {
        val sb = StringBuilder(36)
        val groups = intArrayOf(8, 4, 4, 4, 12)
        for ((index, groupLen) in groups.withIndex()) {
            if (index != 0) sb.append('-')
            repeat(groupLen) { sb.append(hexChars[Random.nextInt(16)]) }
        }
        return sb.toString()
    }
}
