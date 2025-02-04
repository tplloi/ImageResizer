/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2024 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package ru.tech.imageresizershrinker.feature.root.presentation.screenLogic

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.t8rin.dynamic.theme.ColorTuple
import com.t8rin.dynamic.theme.extractPrimaryColor
import com.t8rin.logger.makeLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import ru.tech.imageresizershrinker.core.domain.APP_RELEASES
import ru.tech.imageresizershrinker.core.domain.dispatchers.DispatchersHolder
import ru.tech.imageresizershrinker.core.domain.image.ImageGetter
import ru.tech.imageresizershrinker.core.domain.model.PerformanceClass
import ru.tech.imageresizershrinker.core.domain.saving.FileController
import ru.tech.imageresizershrinker.core.resources.BuildConfig
import ru.tech.imageresizershrinker.core.settings.domain.SettingsInteractor
import ru.tech.imageresizershrinker.core.settings.domain.SettingsManager
import ru.tech.imageresizershrinker.core.settings.domain.model.SettingsState
import ru.tech.imageresizershrinker.core.ui.utils.BaseComponent
import ru.tech.imageresizershrinker.core.ui.utils.navigation.Screen
import ru.tech.imageresizershrinker.core.ui.utils.state.update
import ru.tech.imageresizershrinker.core.ui.widget.other.ToastHostState
import ru.tech.imageresizershrinker.feature.root.presentation.components.navigation.ChildProvider
import ru.tech.imageresizershrinker.feature.root.presentation.components.navigation.NavigationChild
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class RootComponent @AssistedInject internal constructor(
    @Assisted componentContext: ComponentContext,
    val imageLoader: ImageLoader,
    private val imageGetter: ImageGetter<Bitmap, ExifInterface>,
    private val settingsManager: SettingsManager,
    private val childProvider: ChildProvider,
    fileController: FileController,
    dispatchersHolder: DispatchersHolder,
) : BaseComponent(dispatchersHolder, componentContext) {

    private val _settingsState = mutableStateOf(SettingsState.Default)
    val settingsState: SettingsState by _settingsState

    private val navController = StackNavigation<Screen>()

    val childStack: Value<ChildStack<Screen, NavigationChild>> =
        childStack(
            source = navController,
            initialConfiguration = Screen.Main,
            serializer = Screen.serializer(),
            handleBackButton = true,
            childFactory = childProvider::createChild,
        )

    private val _uris = mutableStateOf<List<Uri>?>(null)
    val uris by _uris

    private val _extraImageType = mutableStateOf<String?>(null)
    val extraImageType by _extraImageType

    private val _showSelectDialog = mutableStateOf(false)
    val showSelectDialog by _showSelectDialog

    private val _showUpdateDialog = mutableStateOf(false)
    val showUpdateDialog by _showUpdateDialog

    private val _isUpdateAvailable = mutableStateOf(false)
    val isUpdateAvailable by _isUpdateAvailable

    private val _isUpdateCancelled = mutableStateOf(false)

    private val _shouldShowExitDialog = mutableStateOf(true)
    val shouldShowDialog by _shouldShowExitDialog

    private val _showGithubReviewDialog = mutableStateOf(false)
    val showGithubReviewDialog by _showGithubReviewDialog

    private val _showTelegramGroupDialog = mutableStateOf(false)
    val showTelegramGroupDialog by _showTelegramGroupDialog

    private val _tag = mutableStateOf("")
    val tag by _tag

    private val _changelog = mutableStateOf("")
    val changelog by _changelog

    val toastHostState = ToastHostState()

    init {
        if (settingsState.clearCacheOnLaunch) fileController.clearCache()

        runBlocking {
            settingsManager.registerAppOpen()
            _settingsState.value = settingsManager.getSettingsState()
        }
        settingsManager
            .getSettingsStateFlow()
            .onEach {
                _settingsState.value = it
            }
            .launchIn(componentScope)

        settingsManager
            .getNeedToShowTelegramGroupDialog()
            .onEach { value ->
                _showTelegramGroupDialog.update { value }
            }
            .launchIn(componentScope)
    }

    fun toggleShowUpdateDialog() {
        componentScope.launch {
            settingsManager.toggleShowUpdateDialogOnStartup()
        }
    }

    fun setPresets(newPresets: List<Int>) {
        componentScope.launch {
            settingsManager.setPresets(
                newPresets.joinToString("*")
            )
        }
    }

    fun cancelledUpdate(showAgain: Boolean = false) {
        if (!showAgain) _isUpdateCancelled.value = true
        _showUpdateDialog.value = false
    }

    fun tryGetUpdate(
        isNewRequest: Boolean = false,
        isInstalledFromMarket: Boolean,
        onNoUpdates: () -> Unit = {}
    ) {
        if (settingsState.appOpenCount < 2 && !isNewRequest) return

        val showDialog = settingsState.showUpdateDialogOnStartup
        if (isInstalledFromMarket) {
            if (showDialog) {
                _showUpdateDialog.value = isNewRequest
            }
        } else {
            if (!_isUpdateCancelled.value || isNewRequest) {
                componentScope.launch {
                    checkForUpdates(showDialog, onNoUpdates)
                }
            }
        }
    }

    private suspend fun checkForUpdates(
        showDialog: Boolean,
        onNoUpdates: () -> Unit
    ) = withContext(defaultDispatcher) {
        runCatching {
            val nodes =
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                    URL("$APP_RELEASES.atom").openConnection().getInputStream()
                )?.getElementsByTagName("feed")

            if (nodes != null) {
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    val title = element.getElementsByTagName("entry")
                    val line = (title.item(0) as Element)
                    _tag.value = (line.getElementsByTagName("title")
                        .item(0) as Element).textContent
                    _changelog.value = (line.getElementsByTagName("content")
                        .item(0) as Element).textContent
                }
            }

            if (
                isNeedUpdate(
                    currentName = BuildConfig.VERSION_NAME,
                    updateName = tag
                )
            ) {
                _isUpdateAvailable.value = true
                if (showDialog) {
                    _showUpdateDialog.value = true
                }
            } else {
                onNoUpdates()
            }
        }
    }

    @Suppress("unused")
    private fun isNeedUpdateTest() {
        if (BuildConfig.DEBUG) {
            val updateVersions = listOf(
                "2.6.0",
                "2.7.0",
                "2.6.0-rc1",
                "2.6.0-rc01",
                "3.0.0",
                "2.6.0-beta02",
                BuildConfig.VERSION_NAME,
                "2.6.1",
                "2.6.1-alpha01",
                "2.6.0-rc02",
                "2.5.1"
            )
            val currentVersions = listOf(
                BuildConfig.VERSION_NAME,
                "2.6.0-beta03",
                "2.5.0",
                "2.5.1",
                "2.6.0",
                "2.6.2"
            )
            val allowBetas = listOf(false, true)

            allowBetas.forEach { betas ->
                currentVersions.forEach { current ->
                    updateVersions.forEach { update ->
                        val needUpdate = isNeedUpdate(
                            currentName = current,
                            updateName = update,
                            allowBetas = betas
                        )
                        "$current -> $update = $needUpdate, for betaAllowed = $betas".makeLog("Test_Updates")
                    }
                }
            }
        }
    }

    private fun isNeedUpdate(
        currentName: String,
        updateName: String,
        allowBetas: Boolean = settingsState.allowBetas
    ): Boolean {

        val betaList = listOf(
            "alpha", "beta", "rc"
        )

        fun String.toVersionCodeString(): String {
            return replace(
                regex = Regex("0\\d"),
                transform = {
                    it.value.replace("0", "")
                }
            ).replace("-", "")
                .replace(".", "")
                .replace("_", "")
                .let { version ->
                    if (betaList.any { it in version }) version
                    else version + "4"
                }
                .replace("alpha", "1")
                .replace("beta", "2")
                .replace("rc", "3")
                .replace("foss", "")
                .replace("jxl", "")
        }

        val currentVersionCodeString = currentName.toVersionCodeString()
        val updateVersionCodeString = updateName.toVersionCodeString()

        val maxLength = maxOf(currentVersionCodeString.length, updateVersionCodeString.length)

        val currentVersionCode = currentVersionCodeString.padEnd(maxLength, '0').toIntOrNull() ?: -1
        val updateVersionCode = updateVersionCodeString.padEnd(maxLength, '0').toIntOrNull() ?: -1

        return if (!updateName.startsWith(currentName)) {
            if (betaList.all { it !in updateName }) {
                updateVersionCode > currentVersionCode
            } else {
                if (allowBetas || betaList.any { it in currentName }) {
                    updateVersionCode > currentVersionCode
                } else false
            }
        } else false
    }

    fun hideSelectDialog() {
        _showSelectDialog.value = false
    }

    fun updateUris(uris: List<Uri>?) {
        _uris.value = null
        _uris.value = uris

        if (!uris.isNullOrEmpty() || (uris.isNullOrEmpty() && extraImageType != null)) {
            _showSelectDialog.value = true
        }
    }

    fun updateExtraImageType(type: String?) {
        _extraImageType.update { null }
        _extraImageType.update { type }
    }

    fun showToast(
        message: String,
        icon: ImageVector? = null,
    ) {
        componentScope.launch {
            toastHostState.showToast(
                message = message,
                icon = icon
            )
        }
    }

    fun cancelShowingExitDialog() {
        _shouldShowExitDialog.update { false }
    }

    fun toggleAllowBetas(installedFromMarket: Boolean) {
        componentScope.launch {
            settingsManager.toggleAllowBetas()
            tryGetUpdate(
                isNewRequest = true,
                isInstalledFromMarket = installedFromMarket
            )
        }
    }

    suspend fun getColorTupleFromEmoji(
        emojiUri: String
    ): ColorTuple? = imageGetter
        .getImage(data = emojiUri)
        ?.extractPrimaryColor()
        ?.let { ColorTuple(it) }

    fun onWantGithubReview() {
        _showGithubReviewDialog.update { true }
    }

    fun hideReviewDialog() {
        _showGithubReviewDialog.update { false }
    }

    fun hideTelegramGroupDialog() {
        _showTelegramGroupDialog.update { false }
    }

    fun getSettingsInteractor(): SettingsInteractor = settingsManager

    fun adjustPerformance(performanceClass: PerformanceClass) {
        componentScope.launch {
            settingsManager.adjustPerformance(performanceClass)
        }
    }

    fun registerDonateDialogOpen() {
        componentScope.launch {
            settingsManager.registerDonateDialogOpen()
        }
    }

    fun notShowDonateDialogAgain() {
        componentScope.launch {
            settingsManager.setNotShowDonateDialogAgain()
        }
    }

    fun toggleFavoriteScreen(screen: Screen) {
        componentScope.launch {
            settingsManager.toggleFavoriteScreen(screen.id)
        }
    }

    fun registerTelegramGroupOpen() {
        componentScope.launch {
            settingsManager.registerTelegramGroupOpen()
        }
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateTo(screen: Screen) {
        navController.push(screen)
    }

    fun navigateToNew(screen: Screen) {
        navController.pushNew(screen)
    }

    fun navigateBack() {
        navController.pop()
    }

    @AssistedFactory
    fun interface Factory {
        operator fun invoke(
            componentContext: ComponentContext
        ): RootComponent
    }

}