package com.duduezn.budgettracker.domain.parser

import com.duduezn.budgettracker.domain.model.IncomingMessage
import com.duduezn.budgettracker.domain.model.ParsedMessage

/**
 * Analyse un message Mobile Money brut.
 * Retourne null lorsque le message n'est pas une transaction exploitable
 * (publicité, code OTP, message vide, montant absent…).
 */
interface MessageParser {
    fun parse(message: IncomingMessage): ParsedMessage?
}
