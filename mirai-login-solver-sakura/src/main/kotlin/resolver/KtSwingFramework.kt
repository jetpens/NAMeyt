
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

    internal object Cancelled : WindowResult() {
        override val valueAsString: String? get() = null
        override val value: Any? get() = null
        override val cancelled: Boolean get() = true

        override fun toString(): String {
            return "WindowResult.Cancelled"
        }
    }

    internal object WindowClosed : WindowResult() {
        override val valueAsString: String? get() = null
        override val value: Any? get() = null
        override val cancelled: Boolean get() = true

        override fun toString(): String {
            return "WindowResult.WindowClosed"
        }
    }

    internal object SelectedOK : WindowResult() {
        override val valueAsString: String get() = "true"
        override val value: Any get() = true
        override val cancelled: Boolean get() = false

        override fun toString(): String {
            return "WindowResult.SelectedOK"
        }
    }

    internal class Confirmed(private val data: String) : WindowResult() {
        override fun toString(): String {
            return "WindowResult.Confirmed($data)"
        }

        override val valueAsString: String get() = data
        override val value: Any get() = data
        override val cancelled: Boolean get() = false
    }

    internal class ConfirmedAnything(override val value: Any? = null) : WindowResult() {
        override val valueAsString: String?
            get() = value?.toString()

        override val cancelled: Boolean get() = false

        override fun toString(): String {
            return "WindowResult.ConfirmedAnything(value=$value)@${hashCode()}"
        }
    }
}

@Suppress("PropertyName")
internal class WindowsOptions(
    val layout: MigLayout,
    val contentPane: Container,
    val optionPane: JOptionPane,
    val response: CompletableDeferred<WindowResult>,
    val parentWindow: Window,
    val subCoroutineScope: CoroutineScope,
    val swingActionsScope: CoroutineScope,
) {
    var width: Int = 3

    fun appendFillX(sub: Component) {
        contentPane.add(sub, "spanx $width,growx,wrap")
    }

    fun appendFillWithLabel(name: String, comp: Component): JLabel {
        val label = JLabel(name)
        contentPane.add(label)
        contentPane.add(comp.also { label.labelFor = it }, "spanx ${width - 1},growx,wrap")
        return label
    }

    fun filledTextField(name: String, value: String): JTextField {
        val field = JTextField(value)
        if (name.isEmpty()) {
            appendFillX(field)
        } else {
            appendFillWithLabel(name, field)
        }
        return field
    }

    private companion object {
        @JvmStatic
        private fun getMnemonic(key: String, l: Locale): Int {
            val value = UIManager.get(key, l) as String? ?: return 0
            try {
                return value.toInt()
            } catch (_: NumberFormatException) {
            }
            return 0
        }
    }

    private val l get() = optionPane.locale
    val BTN_YES by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.yesButtonText", l),
            getMnemonic("OptionPane.yesButtonMnemonic", l),
            null, -1
        )
    }
    val BTN_NO by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.noButtonText", l),
            getMnemonic("OptionPane.noButtonMnemonic", l),
            null, -1
        )
    }
    val BTN_OK by lazy {
        ButtonFactory(
            UIManager.getString("OptionPane.okButtonText", l),