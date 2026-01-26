package app.campfire.audioplayer.impl

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.campfire.settings.api.PlaybackSettings

/**
 * A [ForwardingPlayer] that intercepts next/previous media item commands and
 * redirects them based on the [PlaybackSettings.remoteNextPrevSkipsChapters] setting.
 *
 * When [remoteNextPrevSkipsChapters] is true (default), next/prev commands skip chapters.
 * When false, they seek forward/backward by the configured time instead.
 */
@OptIn(UnstableApi::class)
class RemoteControlForwardingPlayer(
  player: Player,
  private val settings: PlaybackSettings,
) : ForwardingPlayer(player) {

  override fun seekToNextMediaItem() {
    if (settings.remoteNextPrevSkipsChapters) {
      super.seekToNextMediaItem()
    } else {
      seekForward()
    }
  }

  override fun seekToPreviousMediaItem() {
    if (settings.remoteNextPrevSkipsChapters) {
      super.seekToPreviousMediaItem()
    } else {
      seekBack()
    }
  }

  override fun seekToNext() {
    if (settings.remoteNextPrevSkipsChapters) {
      super.seekToNext()
    } else {
      seekForward()
    }
  }

  override fun seekToPrevious() {
    if (settings.remoteNextPrevSkipsChapters) {
      super.seekToPrevious()
    } else {
      seekBack()
    }
  }
}
