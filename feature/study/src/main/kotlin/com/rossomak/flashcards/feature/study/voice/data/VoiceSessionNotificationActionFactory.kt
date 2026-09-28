package com.rossomak.flashcards.feature.study.voice.data

import android.app.PendingIntent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.CustomCommandPendingIntentBuilder
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.PlaybackPendingIntentBuilder
import androidx.media3.session.SessionCommand

/**
 * Builds the media notification's actions when [StudySessionVoiceService] posts that notification
 * itself. Media3's own factory is package-private, so this mirrors it on Media3's public pending
 * intent builders: every action is a media-button or custom-command intent delivered to [service],
 * which Media3's `onStartCommand` routes to the session exactly as it does for its own notification.
 */
@UnstableApi
internal class VoiceSessionNotificationActionFactory(
    private val service: MediaSessionService,
) : MediaNotification.ActionFactory {

    override fun createMediaAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        command: Int,
    ): NotificationCompat.Action = NotificationCompat.Action(icon, title, createMediaActionPendingIntent(mediaSession, command))

    override fun createCustomAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        customAction: String,
        extras: Bundle,
    ): NotificationCompat.Action =
        NotificationCompat.Action(icon, title, customCommandPendingIntent(mediaSession, SessionCommand(customAction, extras)))

    override fun createCustomActionFromCustomCommandButton(
        mediaSession: MediaSession,
        customCommandButton: CommandButton,
    ): NotificationCompat.Action {
        val customCommand = requireNotNull(customCommandButton.sessionCommand) { "A custom command button needs a session command" }
        return NotificationCompat.Action(
            IconCompat.createWithResource(service, customCommandButton.iconResId),
            customCommandButton.displayName,
            customCommandPendingIntent(mediaSession, customCommand),
        )
    }

    override fun createMediaActionPendingIntent(mediaSession: MediaSession, command: Int): PendingIntent =
        PlaybackPendingIntentBuilder(service, command, service.javaClass)
            // Play from a paused session must be able to bring the service back to the foreground.
            .setStartAsForegroundService(!mediaSession.player.playWhenReady)
            .setSessionId(mediaSession.id)
            .build()

    override fun createNotificationDismissalIntent(mediaSession: MediaSession): PendingIntent =
        PlaybackPendingIntentBuilder(service, Player.COMMAND_STOP, service.javaClass)
            .setSessionId(mediaSession.id)
            .setExtras(Bundle().apply { putBoolean(MediaNotification.NOTIFICATION_DISMISSED_EVENT_KEY, true) })
            .build()

    private fun customCommandPendingIntent(mediaSession: MediaSession, customCommand: SessionCommand): PendingIntent =
        CustomCommandPendingIntentBuilder(service, service.javaClass, customCommand)
            .setSessionId(mediaSession.id)
            .build()
}
