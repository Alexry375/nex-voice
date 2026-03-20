package com.nex.voice

import android.service.voice.VoiceInteractionService

/**
 * System entry point. Android binds to this service when Nex is selected
 * as the default digital assistant. Can be empty — the real logic is in the Session.
 */
class NexVoiceInteractionService : VoiceInteractionService()
