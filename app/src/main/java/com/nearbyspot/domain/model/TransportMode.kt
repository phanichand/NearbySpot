package com.nearbyspot.domain.model

enum class TransportMode(val label: String, val routesApiValue: String) {
    WALK("Walk", "WALK"),
    DRIVE("Drive", "DRIVE"),
    BICYCLE("Bicycle", "BICYCLE"),
}
