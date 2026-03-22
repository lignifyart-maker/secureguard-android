package com.secureguard.app.domain.model

data class SecuritySuggestion(
    val title: String,
    val detail: String,
    val categoryLabel: String,
    val priorityLabel: String
)
