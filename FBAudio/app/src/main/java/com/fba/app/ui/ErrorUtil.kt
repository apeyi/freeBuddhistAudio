package com.fba.app.ui

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun friendlyError(e: Exception): String = when {
    e is UnknownHostException || e is ConnectException || e is SocketTimeoutException -> "Connection error"
    e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "Connection error"
    e.message?.contains("failed to connect", ignoreCase = true) == true -> "Connection error"
    e.message?.contains("timeout", ignoreCase = true) == true -> "Connection error"
    else -> e.message ?: "Something went wrong"
}
