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

package ru.tech.imageresizershrinker.core.filters.presentation.model

import ru.tech.imageresizershrinker.core.filters.domain.model.Filter
import ru.tech.imageresizershrinker.core.resources.R


class UiSmoothToonFilter(
    override val value: Triple<Float, Float, Float> = Triple(0.5f, 0.2f, 10f)
) : UiFilter<Triple<Float, Float, Float>>(
    title = R.string.smooth_toon,
    value = value,
    paramsInfo = listOf(
        R.string.blur_size paramTo 0f..100f,
        R.string.threshold paramTo 0f..5f,
        R.string.quantizationLevels paramTo 0f..100f
    )
), Filter.SmoothToon