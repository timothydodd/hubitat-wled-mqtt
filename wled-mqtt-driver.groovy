/*
 * WLED MQTT Light Driver - Enhanced Version
 *  Device Driver for Hubitat Elevation hub
 *  Version 2.0.0
 *
 * Based on the original WLED MQTT Light driver by Mikhail Diatchenko
 * Original: https://github.com/muxa/hubitat/blob/master/drivers/wled-light.groovy
 * Enhanced and refactored by Tim Dodd
 *
 * ENHANCEMENTS FROM ORIGINAL:
 * - Complete color control (RGB, HSV, Color Temperature)
 * - All WLED effects with speed and intensity control
 * - Color palettes and presets (fully implemented)
 * - Multi-segment support
 * - Modern JSON API (recommended) with legacy XML support
 * - Auto-discovery of effects and palettes via HTTP API
 * - Robust error handling and reconnection logic
 * - Authentication support for secure MQTT brokers
 * - Input validation and auto-cleanup
 * - Comprehensive state management
 * - Enhanced documentation and troubleshooting
 *
 * WLED Project: https://github.com/Aircoookie/WLED
 * Documentation: https://kno.wled.ge/
 *
 * SETUP:
 * 1. Configure MQTT broker settings
 * 2. Set WLED MQTT topic (typically "wled/[device-name]")
 * 3. Optionally set WLED IP for HTTP API discovery (IP only, no http://)
 * 4. Enable JSON API for full features (recommended)
 * 5. Run "Configure" to auto-discover effects and palettes
 *
 * FEATURES:
 * - Switch: on(), off()
 * - Dimming: setLevel(0-100)
 * - Color: setColor(), setHue(), setSaturation()
 * - Color Temperature: setColorTemperature() (2700K-6500K)
 * - Effects: setEffect(), setNextEffect(), setPreviousEffect()
 * - Effect Control: setEffectSpeed(), setEffectIntensity()
 * - Palettes: setPalette()
 * - Presets: setPreset()
 * - Segments: setSegmentColor()
 * - Transitions: setTransition()
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 */

metadata {
    definition (name: "WLED MQTT Light", namespace: "dodd", author: "Tim Dodd (enhanced from Mikhail Diatchenko original)") {
        capability "Initialize"
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "Light"
        capability "LightEffects"
        capability "ColorControl"
        capability "ColorTemperature"
		
        command "on"
        command "off"
        command "setEffect", [[name: "Effect Name *", type: "STRING"]]
        command "setEffectSpeed", [[name: "Effect speed *", type: "NUMBER", description: "Effect speed to set (0 to 255)", constraints:[]]]
        command "setEffectIntensity", [[name: "Effect intensity *", type: "NUMBER", description: "Effect intensity to set (0 to 255)", constraints:[]]]
        command "setPreset", [[name: "Preset number *", type: "NUMBER", description: "Preset to set (0 to 16)", constraints:[]]]
        command "configure", [[name: "WLED IP address *", type: "STRING", description: "Configure effects & presets from WLED device"]]
        command "setPalette", [[name: "Palette Index *", type: "NUMBER"]]
        command "setPalette", [[name: "Palette Name *", type: "STRING"]]
        command "setSegmentColor", [[name: "Segment *", type: "NUMBER"], [name: "Color *", type: "STRING"]]
        command "setTransition", [[name: "Duration *", type: "NUMBER", description: "Transition time in seconds"]]
        
        attribute "level", "number"
        attribute "effectNumber", "number"
        attribute "effectSpeed", "number"
        attribute "effectIntensity", "number"
        attribute "paletteNumber", "number"
        attribute "paletteName", "string"
        attribute "preset", "number"
        attribute "transition", "number"
        attribute "segments", "string"
        attribute "wledInfo", "string"
    }
}

preferences {
    section("MQTT Configuration") {
        input "mqttBroker", "string", title: "MQTT Broker Address:Port", required: true, description: "e.g. 192.168.1.100:1883"
        input "mqttTopic", "string", title: "MQTT Topic", required: true, description: "e.g. wled/livingroom (no leading or trailing slashes)"
        input "mqttUsername", "string", title: "MQTT Username (optional)", required: false
        input "mqttPassword", "password", title: "MQTT Password (optional)", required: false
    }
    section("WLED Configuration") {
        input "wledIP", "string", title: "WLED IP Address", required: false, description: "Enter IP address only (e.g. 192.168.1.50) - do NOT include http:// or paths"
        input "useJsonApi", "bool", title: "Use JSON API (recommended)", defaultValue: true, description: "Use modern JSON API instead of legacy XML"
        input "autoRefresh", "number", title: "Auto-refresh interval (seconds)", defaultValue: 30, range: "10..300"
    }
    section("Advanced Settings") {
        input "maxSegments", "number", title: "Maximum segments", defaultValue: 16, range: "1..32"
        input "transitionTime", "number", title: "Default transition time (ms)", defaultValue: 700, range: "0..10000"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.info "WLED MQTT Driver v2.0.0 installed"
    
    // Set default values
    device.updateSetting("useJsonApi", true)
    device.updateSetting("autoRefresh", 30)
    device.updateSetting("maxSegments", 16)
    device.updateSetting("transitionTime", 700)
    device.updateSetting("logEnable", true)
    
    // Initialize device state
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "level", value: 0)
    sendEvent(name: "hue", value: 0)
    sendEvent(name: "saturation", value: 100)
    sendEvent(name: "colorTemperature", value: 3000)
    sendEvent(name: "effectNumber", value: 0)
    sendEvent(name: "effectSpeed", value: 128)
    sendEvent(name: "effectIntensity", value: 128)
    sendEvent(name: "paletteNumber", value: 0)
    sendEvent(name: "preset", value: 0)
    sendEvent(name: "transition", value: 700)
    
    log.info "Please configure MQTT broker settings and run initialize()"
}

def xmlToMap(xml) {
    def map = [ cl: [], cs: [] ]
    xml[0].children().each {
        switch (it.name()) {
            case "cl":
            case "cs":
                map[it.name()] += it.text().toInteger()
                break
            default:
                map[it.name()] = it.text()
        }
    }
    return map
}

def configure(ipAddress = null) {
    def ip = ipAddress ?: settings.wledIP
    if (!ip) {
        log.error "No WLED IP address provided. Please set in device preferences or provide as parameter."
        return
    }
    
    // Clean up IP address - remove any http:// prefixes or paths
    ip = ip.replaceAll(/^https?:\/\//, "")  // Remove http:// or https://
    ip = ip.replaceAll(/\/.*$/, "")         // Remove any paths after the IP
    ip = ip.trim()                          // Remove whitespace
    
    if (!ip.matches(/^\d+\.\d+\.\d+\.\d+(:\d+)?$/)) {
        log.error "Invalid IP address format: ${ip}. Please enter IP address only (e.g. 192.168.1.50)"
        return
    }
    
    logInfo "Obtaining WLED configuration from ${ip}"
    
    try {
        // Get device info and state
        def stateParams = [
            uri: "http://${ip}/json/state",
            requestContentType: 'application/json',
            contentType: 'application/json',
            timeout: 10
        ]
        asynchttpGet('processStateResponse', stateParams, [ip: ip])
        
        // Get effects list
        def effectsParams = [
            uri: "http://${ip}/json/effects",
            requestContentType: 'application/json',
            contentType: 'application/json',
            timeout: 10
        ]
        asynchttpGet('processEffectsResponse', effectsParams, [ip: ip])
        
        // Get palettes list
        def palettesParams = [
            uri: "http://${ip}/json/palettes",
            requestContentType: 'application/json',
            contentType: 'application/json',
            timeout: 10
        ]
        asynchttpGet('processPalettesResponse', palettesParams, [ip: ip])
        
        // Get device info
        def infoParams = [
            uri: "http://${ip}/json/info",
            requestContentType: 'application/json',
            contentType: 'application/json',
            timeout: 10
        ]
        asynchttpGet('processInfoResponse', infoParams, [ip: ip])
        
    } catch(e) {
        log.error "Error configuring WLED device: ${e.message}"
    }
}

def processStateResponse(response, data) {
    if (response.getStatus() == 200) {
        try {
            def json = new groovy.json.JsonSlurper().parseText(response.data)
            parseJsonResponse([state: json])
            logInfo "State retrieved from WLED at ${data.ip}"
        } catch(e) {
            log.error "Error parsing state response: ${e.message}"
        }
    } else {
        log.error "WLED state API returned error: ${response.getStatus()}"
    }
}

def processEffectsResponse(response, data) {
    if (response.getStatus() == 200) {
        try {
            def effects = new groovy.json.JsonSlurper().parseText(response.data)
            configureEffects(effects)
            logInfo "Effects list retrieved from WLED at ${data.ip}"
        } catch(e) {
            log.error "Error parsing effects response: ${e.message}"
        }
    } else {
        log.error "WLED effects API returned error: ${response.getStatus()}"
    }
}

def processPalettesResponse(response, data) {
    if (response.getStatus() == 200) {
        try {
            def palettes = new groovy.json.JsonSlurper().parseText(response.data)
            configurePalettes(palettes)
            logInfo "Palettes list retrieved from WLED at ${data.ip}"
        } catch(e) {
            log.error "Error parsing palettes response: ${e.message}"
        }
    } else {
        log.error "WLED palettes API returned error: ${response.getStatus()}"
    }
}

def processInfoResponse(response, data) {
    if (response.getStatus() == 200) {
        try {
            def info = new groovy.json.JsonSlurper().parseText(response.data)
            state.ver = info.ver
            state.name = info.name
            state.ip = data.ip
            sendEvent(name: "wledInfo", value: new groovy.json.JsonBuilder(info).toString())
            logInfo "Detected WLED v${info.ver} '${info.name}' at ${data.ip}"
        } catch(e) {
            log.error "Error parsing info response: ${e.message}"
        }
    } else {
        log.error "WLED info API returned error: ${response.getStatus()}"
    }
}

def configureEffects(effects) {
    logDebug "Configuring ${effects.size()} effects"
    
    state.effects = effects
    
    def effectMap = [:]
    effects.eachWithIndex { element, index ->
        effectMap[(index)] = element
    }
    
    def lightEffectsJson = new groovy.json.JsonBuilder(effectMap)
    sendEvent(name: "lightEffects", value: lightEffectsJson.toString())
    
    logInfo "Configured ${effects.size()} effects"
}

def configurePalettes(palettes) {
    logDebug "Configuring ${palettes.size()} palettes"
    
    state.palettes = palettes
    
    logInfo "Configured ${palettes.size()} palettes"
}

def parse(String description) {
    def mqtt = interfaces.mqtt.parseMessage(description) 
    logDebug "Received MQTT: ${mqtt.topic} = ${mqtt.payload}"
    
    def data
    try {
        if (settings.useJsonApi) {
            data = new groovy.json.JsonSlurper().parseText(mqtt.payload)
            parseJsonResponse(data)
        } else {
            def xml = new XmlSlurper().parseText(mqtt.payload)
            data = xmlToMap(xml)
            parseXmlResponse(data)
        }
    } catch (Exception e) {
        log.error "Failed to parse MQTT payload: ${e.message}"
        logDebug "Payload was: ${mqtt.payload}"
    }
}

def parseJsonResponse(data) {
    logDebug "Parsing JSON response: ${data}"
    
    // Handle WLED JSON state format
    if (data.state) {
        def state = data.state
        
        // Update switch and brightness
        def isOn = state.on ?: false
        sendEvent(name: "switch", value: isOn ? "on" : "off")
        
        if (state.bri != null) {
            def level = Math.round(state.bri / 255 * 100)
            if (level != device.currentValue("level")) {
                sendEvent(name: "level", value: level, unit: "%", isStateChange: true)
            }
        }
        
        // Handle segments (first segment for basic color control)
        if (state.seg && state.seg.size() > 0) {
            def seg = state.seg[0]
            
            // Update colors
            if (seg.col && seg.col.size() > 0 && seg.col[0].size() >= 3) {
                def rgb = seg.col[0]
                def hsv = hubitat.helper.ColorUtils.rgbToHSV([rgb[0], rgb[1], rgb[2]])
                
                if (hsv[0] != device.currentValue("hue")) {
                    sendEvent(name: "hue", value: hsv[0])
                    setGenericName(hsv[0])
                }
                if (hsv[1] != device.currentValue("saturation")) {
                    sendEvent(name: "saturation", value: hsv[1], unit: "%")
                }
                
                def colorMap = [hue: hsv[0], saturation: hsv[1], level: hsv[2]]
                sendEvent(name: "color", value: colorMap)
            }
            
            // Update effect
            if (seg.fx != null && seg.fx != device.currentValue("effectNumber")) {
                def effectName = state.effects ? state.effects[seg.fx] : "Effect ${seg.fx}"
                sendEvent(name: "effectNumber", value: seg.fx)
                sendEvent(name: "effectName", value: effectName)
            }
            
            // Update effect speed and intensity
            if (seg.sx != null && seg.sx != device.currentValue("effectSpeed")) {
                sendEvent(name: "effectSpeed", value: seg.sx)
            }
            if (seg.ix != null && seg.ix != device.currentValue("effectIntensity")) {
                sendEvent(name: "effectIntensity", value: seg.ix)
            }
            
            // Update palette
            if (seg.pal != null && seg.pal != device.currentValue("paletteNumber")) {
                def paletteName = state.palettes ? state.palettes[seg.pal] : "Palette ${seg.pal}"
                sendEvent(name: "paletteNumber", value: seg.pal)
                sendEvent(name: "paletteName", value: paletteName)
            }
        }
        
        // Update preset
        if (state.ps != null && state.ps >= 0) {
            sendEvent(name: "preset", value: state.ps)
        }
        
        // Update transition time
        if (state.transition != null) {
            sendEvent(name: "transition", value: state.transition)
        }
    }
    
    // Store segments info
    if (data.state?.seg) {
        sendEvent(name: "segments", value: new groovy.json.JsonBuilder(data.state.seg).toString())
    }
    
    // Store device info
    if (data.info) {
        sendEvent(name: "wledInfo", value: new groovy.json.JsonBuilder(data.info).toString())
        state.ver = data.info.ver
        state.name = data.info.name
    }
}

def parseXmlResponse(map) {
    logDebug "Parsing XML response: ${map}"
    
    // Legacy XML parsing (original code, uncommented and fixed)
    def level = Math.round(map.ac.toInteger() / 255 * 100)
    if (level > 0) {
        sendEvent(name: "switch", value: "on")
    } else {
        sendEvent(name: "switch", value: "off")
    }
    if (level != device.currentValue("level")) {
        sendEvent(name: "level", value: level, unit: "%", isStateChange: true)
    }
    
    // Update colors from XML
    if (map.cl && map.cl.size() >= 3) {
        def primaryColor = hubitat.helper.ColorUtils.rgbToHSV(map.cl)
        if (primaryColor[0] != device.currentValue("hue")) {
            sendEvent(name: "hue", value: primaryColor[0])
            setGenericName(primaryColor[0])
        }    
        if (primaryColor[1] != device.currentValue("saturation")) {
            sendEvent(name:"saturation", value:primaryColor[1], unit: "%")
        }
        
        def colorMap = [hue: primaryColor[0], saturation: primaryColor[1], level: primaryColor[2]]
        sendEvent(name: "color", value: colorMap)
    }
    
    // Update effect from XML
    if (map.fx != null) {
        def effectIndex = map.fx.toInteger()
        if (effectIndex != device.currentValue("effectNumber")) {
            def effectName = state.effects ? state.effects[effectIndex] : "Effect ${effectIndex}"
            sendEvent(name:"effectNumber", value:effectIndex)
            sendEvent(name:"effectName", value:effectName)
        }
    }
    
    // Update effect speed and intensity from XML
    if (map.sx != null) {
        def effectSpeed = map.sx.toInteger()
        if (effectSpeed != device.currentValue("effectSpeed")) {
            sendEvent(name:"effectSpeed", value:effectSpeed)
        }
    }
    
    if (map.ix != null) {
        def effectIntensity = map.ix.toInteger()
        if (effectIntensity != device.currentValue("effectIntensity")) {
            sendEvent(name:"effectIntensity", value:effectIntensity)
        }
    }

    // Update palette from XML
    if (map.fp != null) {
        def paletteIndex = map.fp.toInteger()
        if (paletteIndex != device.currentValue("paletteNumber")) {
            def paletteName = state.palettes ? state.palettes[paletteIndex] : "Palette ${paletteIndex}"
            sendEvent(name:"paletteNumber", value:paletteIndex)
            sendEvent(name:"paletteName", value:paletteName)
        }
    }
}

def on() {
    logInfo "on"
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([on: true])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "T=1"
    }
}

def off() {
    logInfo "off"
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([on: false])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "T=0"
    }
}

def setEffect(String effect){
    logInfo "setEffect $effect"
    def effectIndex = state.effects?.indexOf(effect)
    if (effectIndex != null && effectIndex >= 0) {
        setEffect(effectIndex)
    } else {
        log.warn "Effect '${effect}' not found in effects list"
    }
}

def setEffect(id){
    logInfo "setEffect $id"
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([
            seg: [[
                fx: id.toInteger()
            ]]
        ])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "FX=${id}"
    }
    sendEvent(name: "effectNumber", value: id.toInteger())
} 

def setEffectSpeed(speed){
    logInfo "setEffectSpeed $speed"
    def speedValue = Math.max(0, Math.min(255, speed.toInteger()))
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([
            seg: [[
                sx: speedValue
            ]]
        ])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "SX=${speedValue}"
    }
    sendEvent(name: "effectSpeed", value: speedValue)
} 

def setEffectIntensity(intensity){
    logInfo "setEffectIntensity $intensity"
    def intensityValue = Math.max(0, Math.min(255, intensity.toInteger()))
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([
            seg: [[
                ix: intensityValue
            ]]
        ])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "IX=${intensityValue}"
    }
    sendEvent(name: "effectIntensity", value: intensityValue)
} 

def setNextEffect(){
    logInfo "setNextEffect"
    def currentEffectId = device.currentValue("effectNumber") ?: 0
    def maxEffects = state.effects?.size() ?: 100
    currentEffectId++
    if (currentEffectId >= maxEffects) 
        currentEffectId = 0
    setEffect(currentEffectId)
}

def setPreviousEffect(){
    logInfo "setPreviousEffect"
    def currentEffectId = device.currentValue("effectNumber") ?: 1
    def maxEffects = state.effects?.size() ?: 100
    currentEffectId--
    if (currentEffectId < 0) 
        currentEffectId = maxEffects - 1
    setEffect(currentEffectId)
}

def setPreset(preset){
    logInfo "setPreset $preset"
    def presetNum = preset.toInteger()
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([ps: presetNum])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "PL=${presetNum}"
    }
    sendEvent(name: "preset", value: presetNum)
} 

def setColor(value) {
    logInfo "setColor $value"
    def hue = value.hue ?: device.currentValue("hue") ?: 0
    def saturation = value.saturation ?: device.currentValue("saturation") ?: 100
    def level = value.level ?: device.currentValue("level") ?: 100
    
    def rgb = hubitat.helper.ColorUtils.hsvToRGB([hue, saturation, level])
    
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([
            seg: [[
                col: [[rgb[0], rgb[1], rgb[2]]]
            ]]
        ])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "R=${rgb[0]}&G=${rgb[1]}&B=${rgb[2]}"
    }
    
    // Update device attributes
    sendEvent(name: "hue", value: hue)
    sendEvent(name: "saturation", value: saturation, unit: "%")
    sendEvent(name: "color", value: [hue: hue, saturation: saturation, level: level])
    setGenericName(hue)
}

def setHue(value) {
    logInfo "setHue $value"
    def saturation = device.currentValue("saturation") ?: 100
    def level = device.currentValue("level") ?: 100
    setColor([hue: value, saturation: saturation, level: level])
}

def setSaturation(value) {
    logInfo "setSaturation $value"
    def hue = device.currentValue("hue") ?: 0
    def level = device.currentValue("level") ?: 100
    setColor([hue: hue, saturation: value, level: level])
}

def setColorTemperature(colortemperature, level = null, transitionTime = null) {
    logInfo "setColorTemperature $colortemperature K, level: $level, transition: $transitionTime"
    
    // Constrain color temperature to WLED range (typically 2700-6500K)
    def ct = Math.max(2700, Math.min(6500, colortemperature.toInteger()))
    
    // Convert Kelvin to WLED color temperature (WLED uses mireds * 50)
    def mireds = Math.round(1000000 / ct)
    def wledCt = Math.round(mireds / 50)
    
    if (settings.useJsonApi) {
        def jsonData = [seg: [[cct: wledCt]]]
        
        if (level != null) {
            jsonData.bri = Math.round(level * 2.55)
        }
        if (transitionTime != null) {
            jsonData.transition = Math.round(transitionTime * 10)
        }
        
        def json = new groovy.json.JsonBuilder(jsonData)
        publishJsonCommand(json.toString())
    } else {
        def command = "CT=${wledCt}"
        if (level != null) {
            command += "&A=${Math.round(level * 2.55)}"
        }
        if (transitionTime != null) {
            command += "&TT=${Math.round(transitionTime * 10)}"
        }
        publishXmlCommand(command)
    }
    
    sendEvent(name: "colorTemperature", value: ct)
    sendEvent(name: "colorName", value: getColorTempName(ct))
}

def getColorTempName(temp) {
    if (temp < 3000) return "Warm White"
    else if (temp < 4000) return "Incandescent"
    else if (temp < 5000) return "White"
    else if (temp < 6000) return "Daylight"
    else return "Cool White"
}

def setLevel(value) {
    logInfo "setLevel $value"
    def brightness = Math.round(value * 2.55)
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([bri: brightness])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "A=${brightness}"
    }
}

def setLevel(value, duration) {
    logInfo "setLevel $value over ${duration}s"
    def brightness = Math.round(value * 2.55)
    def transitionTime = Math.round((duration ?: settings.transitionTime ?: 700) / 100)
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([bri: brightness, transition: transitionTime])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "A=${brightness}&TT=${transitionTime}"
    }
}
def setPalette(String palette){
    logInfo "setPalette $palette"
    def paletteIndex = state.palettes?.indexOf(palette)
    if (paletteIndex != null && paletteIndex >= 0) {
        setPalette(paletteIndex)
    } else {
        log.warn "Palette '${palette}' not found in palettes list"
    }
}

def setPalette(id){
    logInfo "setPalette $id"
    def paletteId = id.toInteger()
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([
            seg: [[
                pal: paletteId
            ]]
        ])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "FP=${paletteId}"
    }
    sendEvent(name: "paletteNumber", value: paletteId)
}

def setSegmentColor(segment, color) {
    logInfo "setSegmentColor segment:$segment color:$color"
    def segId = segment.toInteger()
    
    def rgb
    if (color.startsWith("#")) {
        // Hex color
        def hex = color.substring(1)
        rgb = [
            Integer.parseInt(hex.substring(0,2), 16),
            Integer.parseInt(hex.substring(2,4), 16),
            Integer.parseInt(hex.substring(4,6), 16)
        ]
    } else {
        // Assume RGB format like "255,128,0"
        rgb = color.split(",").collect { it.toInteger() }
    }
    
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([
            seg: [
                [id: segId, col: [[rgb[0], rgb[1], rgb[2]]]]
            ]
        ])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "SM=${segId}&R=${rgb[0]}&G=${rgb[1]}&B=${rgb[2]}"
    }
}

def setTransition(duration) {
    logInfo "setTransition $duration seconds"
    def transMs = Math.round(duration.toInteger() * 1000)
    if (settings.useJsonApi) {
        def json = new groovy.json.JsonBuilder([transition: transMs])
        publishJsonCommand(json.toString())
    } else {
        publishXmlCommand "TT=${transMs}"
    }
    sendEvent(name: "transition", value: transMs)
} 

def publishCommand(command) {
    if (settings.useJsonApi) {
        publishJsonCommand(command)
    } else {
        publishXmlCommand(command)
    }
}

def publishJsonCommand(jsonData) {
    def topic = settings.mqttTopic
    logDebug "Publishing JSON to ${topic}: ${jsonData}"
    interfaces.mqtt.publish(topic, jsonData)
}

def publishXmlCommand(command) {
    def topic = settings.mqttTopic + "/api"
    logDebug "Publishing XML to ${topic}: ${command}"
    interfaces.mqtt.publish(topic, command)
}

def setGenericName(hue){
    def colorName
    hue = hue.toInteger()
    if (!hiRezHue) hue = (hue * 3.6)
    switch (hue.toInteger()){
        case 0..15: colorName = "Red"
            break
        case 16..45: colorName = "Orange"
            break
        case 46..75: colorName = "Yellow"
            break
        case 76..105: colorName = "Chartreuse"
            break
        case 106..135: colorName = "Green"
            break
        case 136..165: colorName = "Spring"
            break
        case 166..195: colorName = "Cyan"
            break
        case 196..225: colorName = "Azure"
            break
        case 226..255: colorName = "Blue"
            break
        case 256..285: colorName = "Violet"
            break
        case 286..315: colorName = "Magenta"
            break
        case 316..345: colorName = "Rose"
            break
        case 346..360: colorName = "Red"
            break
    }
    logDebug "${device.getDisplayName()} color is ${colorName}"
    sendEvent(name: "colorName", value: colorName)
}

def refresh() {
    logInfo "Refreshing device state"
    requestUpdate()
}

def requestUpdate() {
    if (!state.connected) {
        logDebug "Not connected, skipping update request"
        return
    }
    
    try {
        if (settings.useJsonApi) {
            // Request current state via MQTT
            def json = new groovy.json.JsonBuilder([state: true])
            publishJsonCommand(json.toString())
        } else {
            // For XML mode, request via HTTP API if IP is configured
            if (settings.wledIP) {
                def getParams = [
                    uri: "http://${settings.wledIP}/json",
                    requestContentType: 'application/json',
                    contentType: 'application/json'
                ]
                asynchttpGet('processRefreshResponse', getParams)
            }
        }
        
        // Schedule next refresh
        if (settings.autoRefresh && settings.autoRefresh > 0) {
            runIn(settings.autoRefresh, requestUpdate)
        }
        
    } catch(e) {
        log.error "Error requesting update: ${e.message}"
    }
}

def processRefreshResponse(response, data) {
    if (response.getStatus() == 200) {
        try {
            def json = new groovy.json.JsonSlurper().parseText(response.data)
            parseJsonResponse(json)
        } catch(e) {
            log.error "Error processing refresh response: ${e.message}"
        }
    } else {
        log.error "HTTP refresh request failed: ${response.getStatus()}"
    }
}

def updated() {
    log.info "Updated..."
    initialize()
}

def uninstalled() {
    disconnect()
}

def disconnect() {
    if (state.connected) {
        log.info "Disconnecting from MQTT"
        try {
            if (settings.useJsonApi) {
                interfaces.mqtt.unsubscribe(settings.mqttTopic + "/state")
            } else {
                interfaces.mqtt.unsubscribe(settings.mqttTopic + "/v")
            }
            interfaces.mqtt.disconnect()
        } catch(e) {
            logDebug "Error during disconnect: ${e.message}"
        }
        state.connected = false
        unschedule(requestUpdate)
    }
}

def delayedConnect() {
    // increase delay by 5 seconds every time, to max of 1 hour
    if (state.delay < 3600)
        state.delay = (state.delay ?: 0) + 5

    logDebug "Reconnecting in ${state.delay}s"
    runIn(state.delay, connect)
}

def initialize() {
    logDebug "Initialize"

    state.delay = 0
    disconnect()
    connect()
}

def connect() {
    try {
        log.info "Connecting to ${settings.mqttBroker}"
        def clientId = "hubitat_wled_${device.id}_${now()}"
        
        if (settings.mqttUsername && settings.mqttPassword) {
            logDebug "Connecting with authentication"
            interfaces.mqtt.connect("tcp://" + settings.mqttBroker, clientId, settings.mqttUsername, settings.mqttPassword)
        } else {
            logDebug "Connecting without authentication"
            interfaces.mqtt.connect("tcp://" + settings.mqttBroker, clientId, null, null)
        }
        
        state.connectionAttempts = (state.connectionAttempts ?: 0) + 1
        logDebug "Connection attempt #${state.connectionAttempts}"
        
    } catch(e) {
        log.error "MQTT Connect error: ${e.message}"
        state.lastError = e.message
        state.lastErrorTime = now()
        delayedConnect()
    }
}

def subscribe() {
    try {
        if (settings.useJsonApi) {
            // Subscribe to JSON state topic
            interfaces.mqtt.subscribe(settings.mqttTopic + "/state")
            logDebug "Subscribed to JSON topic ${settings.mqttTopic}/state"
        } else {
            // Subscribe to XML topic (legacy)
            interfaces.mqtt.subscribe(settings.mqttTopic + "/v")
            logDebug "Subscribed to XML topic ${settings.mqttTopic}/v"
        }
        
        state.connected = true
        state.lastConnected = now()
        state.connectionAttempts = 0
        state.delay = 0
        
        // Schedule periodic refresh if enabled
        if (settings.autoRefresh && settings.autoRefresh > 0) {
            runIn(settings.autoRefresh, requestUpdate)
        }
        
    } catch(e) {
        log.error "MQTT Subscribe error: ${e.message}"
        state.lastError = e.message
        state.lastErrorTime = now()
        delayedConnect()
    }
}

def mqttClientStatus(String status){
    // This method is called with any status messages from the MQTT client connection (disconnections, errors during connect, etc) 
    // The string that is passed to this method with start with "Error" if an error occurred or "Status" if this is just a status message.
    def parts = status.split(': ')
    switch (parts[0]) {
        case 'Error':
            log.warn status
            switch (parts[1]) {
                case 'Connection lost':
                case 'send error':
                    state.connected = false
                    state.delay = 0
                    delayedConnect()
                    break
            }
            break
        case 'Status':
            log.info "MQTT ${status}"
            switch (parts[1]) {
                case 'Connection succeeded':
                    state.connected = true
                    // without this delay the `parse` method is never called
                    // (it seems that there needs to be some delay after connection to subscribe)
                    runInMillis(1000, subscribe)
                    break
            }
            break
        default:
            logDebug "MQTT ${status}"
            break
    }
}

def logInfo(msg) {
    if (logEnable) log.info msg
}

def logDebug(msg) {
    if (logEnable) log.debug msg
}