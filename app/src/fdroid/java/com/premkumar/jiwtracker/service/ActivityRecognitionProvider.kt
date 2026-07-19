package com.premkumar.jiwtracker.service

object ActivityRecognitionProvider {
    fun create(): IActivityRecognitionManager = NoOpActivityRecognitionManager()
}
