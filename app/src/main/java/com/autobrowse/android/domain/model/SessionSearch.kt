package com.autobrowse.android.domain.model

data class SessionListItem(
    val session: Session,
    val matchSnippet: String? = null,
    val matchInTitle: Boolean = false,
    val matchInMessages: Boolean = false,
)