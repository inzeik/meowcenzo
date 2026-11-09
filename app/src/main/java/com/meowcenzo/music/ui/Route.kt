package com.meowcenzo.music.ui

sealed interface Route {
    data class Album(val id: Long, val name: String) : Route
    data class Artist(val name: String) : Route
    data class PlaylistRoute(val id: String) : Route
    data class FolderRoute(val path: String) : Route
    data object MostPlayed : Route
    data object Equalizer : Route
    data object Settings : Route
    data object About : Route
}