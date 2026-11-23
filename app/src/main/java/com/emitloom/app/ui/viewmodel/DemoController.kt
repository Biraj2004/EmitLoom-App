// SPDX-License-Identifier: AGPL-3.0-only

package com.emitloom.app.ui.viewmodel

import com.emitloom.app.model.ColorRgb
import com.emitloom.app.model.ControllerDevice
import com.emitloom.app.model.LightMode
import com.emitloom.app.model.LightState
import com.emitloom.app.protocol.ControllerProfile

internal fun demoController() = DeviceControls(
    device = ControllerDevice("emitloom-demo", "SP621E", ControllerProfile.A0),
    outputs = listOf(
        LightState(
            power = true,
            brightness = 192,
            color = ColorRgb(95, 255, 179),
            mode = LightMode.COLOR,
            effect = ControllerProfile.A0.solidEffect,
            speed = 5,
            sensitivity = 8,
            length = 24
        )
    ),
    demoName = "SP621E demo"
)
