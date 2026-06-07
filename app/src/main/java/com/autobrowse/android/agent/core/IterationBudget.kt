package com.autobrowse.android.agent.core

class IterationBudget(val maxIterations: Int = 12) {
    private var used = 0

    fun consume(): Boolean {
        if (used >= maxIterations) return false
        used++
        return true
    }

    fun remaining(): Int = (maxIterations - used).coerceAtLeast(0)

    fun used(): Int = used

    fun exhausted(): Boolean = used >= maxIterations
}