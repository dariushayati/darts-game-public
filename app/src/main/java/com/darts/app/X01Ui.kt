package com.darts.app

import com.darts.core.X01InRule
import com.darts.core.X01OutRule
import com.darts.core.X01Rules

internal val X01InRule.label: String
    get() = when (this) {
        X01InRule.STRAIGHT -> "STRAIGHT"
        X01InRule.DOUBLE -> "DOUBLE"
        X01InRule.MASTER -> "MASTER"
    }

internal val X01OutRule.label: String
    get() = when (this) {
        X01OutRule.STRAIGHT -> "STRAIGHT"
        X01OutRule.DOUBLE -> "DOUBLE"
        X01OutRule.MASTER -> "MASTER"
    }

internal val X01InRule.description: String
    get() = when (this) {
        X01InRule.STRAIGHT -> "Any dart starts the score."
        X01InRule.DOUBLE -> "The first scoring dart must be a double."
        X01InRule.MASTER -> "The first scoring dart must be a double or treble."
    }

internal val X01OutRule.description: String
    get() = when (this) {
        X01OutRule.STRAIGHT -> "Any dart can finish."
        X01OutRule.DOUBLE -> "The final dart must be a double; the inner bull counts."
        X01OutRule.MASTER -> "The final dart must be a double or treble; the inner bull counts."
    }

internal fun X01Rules.legsLabel(): String =
    if (legsToWin == 1) "1 LEG" else "$legsToWin LEGS"
