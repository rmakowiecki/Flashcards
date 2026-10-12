package com.rossomak.flashcards.core.domain.repository

import com.rossomak.flashcards.core.domain.model.AppVersion
import com.rossomak.flashcards.core.domain.model.DeviceInfo

/**
 * Facts about this installation that never change while the process lives. Both reads are cheap and
 * never fail, so implementations do no dispatching: a caller that loads them in `init` has the value
 * before the first frame.
 */
interface InstallationInfoRepository {

    suspend fun getAppVersion(): AppVersion

    suspend fun getDeviceInfo(): DeviceInfo
}
