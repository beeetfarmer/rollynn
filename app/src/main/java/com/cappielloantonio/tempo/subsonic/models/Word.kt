package com.cappielloantonio.tempo.subsonic.models

import androidx.annotation.Keep

@Keep
class Word {
    var start: Int = 0
    var end: Int = 0
    lateinit var text: String
}
