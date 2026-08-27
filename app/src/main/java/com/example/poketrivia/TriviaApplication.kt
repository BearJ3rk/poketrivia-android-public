package com.example.poketrivia

import android.app.Application

class TriviaApplication : Application() {
    val repository by lazy { TriviaRepository(this) }
}
