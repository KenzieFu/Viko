package com.example.viko.utils

/**
 * How long a ViewModel keeps its `stateIn` flow warm after the last collector
 * goes away. Long enough to survive a rotation, short enough that a backgrounded
 * screen stops querying Room.
 */
const val UI_STATE_TIMEOUT_MS = 5_000L
