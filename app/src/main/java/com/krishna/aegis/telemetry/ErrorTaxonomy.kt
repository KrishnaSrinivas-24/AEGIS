package com.krishna.aegis.telemetry

sealed class AegisError(val message: String, val isRecoverable: Boolean) {
    class TransientErr(message: String) : AegisError(message, isRecoverable = true)
    class LogicErr(message: String) : AegisError(message, isRecoverable = false)
    class AuthErr(message: String) : AegisError(message, isRecoverable = false)
    class FatalErr(message: String, val cause: Throwable? = null) : AegisError(message, isRecoverable = false)
}
