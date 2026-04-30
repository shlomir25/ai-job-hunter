package com.jobhunter.processing.dto

data class ParsedFields(
    val title: String?,
    val company: String?,
    val location: String?,
    val isRemote: Boolean?,
    val language: String?,           // "he" or "en"
    val description: String?,
    val requirements: String?,
    val salaryText: String?,
    val applyUrl: String?,
    val contactEmail: String?,       // LLM-extracted candidate; verified by EmailExtractor before save
)
