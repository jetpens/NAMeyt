
/*
 * Copyright 2021-2022 KasukuSakura Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/KasukuSakura/mirai-login-solver-sakura/blob/main/LICENSE
 */

package com.kasukusakura.mlss.resolver

import kotlinx.coroutines.*
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.net.URI
import java.util.*
import javax.swing.*
import kotlin.coroutines.CoroutineContext


internal sealed class WindowResult {
    abstract val cancelled: Boolean
    abstract val valueAsString: String?
    abstract val value: Any?