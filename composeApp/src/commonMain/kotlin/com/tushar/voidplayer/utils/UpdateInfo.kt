package com.tushar.voidplayer.utils

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val releasePageUrl: String,
    val isFdroidInstall: Boolean
)
