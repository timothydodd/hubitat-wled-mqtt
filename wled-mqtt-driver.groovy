/*
 * WLED MQTT Light 
 *  Device Driver for Hubitat Elevation hub
 *  Version 1.2.1
 *
 * Allows keeping device status in sync with WLED light (https://github.com/Aircoookie/WLED) and controlling it via MQTT broker.
 *
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
    definition (name: "WLED MQTT Light", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Initialize"
        capability "Actuator"
        capability "Switch"
         capability "SwitchLevel"
       /* capability "Light"
       /*  capability "LightEffects"
       capability "ColorControl"*/
		
        command "on"
        command "off"
     /*   command "setEffect", [[name: "Effect Name *", type: "STRING"]]
        command "setEffectSpeed", [[name: "Effect speed *", type: "NUMBER", description: "Effect speed to set (0 to 255)", constraints:[]]]
        command "setEffectIntensity", [[name: "Effect intensity *", type: "NUMBER", description: "Effect intensity to set (0 to 255)", constraints:[]]]
        command "setPreset", [[name: "Preset number *", type: "NUMBER", description: "Preset to set (0 to 16)", constraints:[]]]
        command "configure", [[name: "WLED IP address *", type: "STRING", description: "Configure effects & presets from WLED device"]]
        command "setPalette", [[name: "Palette Index *", type: "NUMBER"]]
        command "setPalette", [[name: "Palette Name *", type: "STRING"]]*/
        
        attribute "level", "number"
       /*  attribute "effectNumber", "number"
        attribute "effectSpeed", "number"
        attribute "paletteNumber", "number"
        attribute "paletteName", "string"*/
    }
}

preferences {
    section("URIs") {
        input "mqttBroker", "string", title: "MQTT Broker Address:Port", required: true
		input "mqttTopic", "string", title: "MQTT Topic", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.info "installed..."
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

def configure(ipAddress) {
    logInfo "Obtaining WLED configuration from ${ipAddress}"
    def getParams = [
		uri: "http://${ipAddress}/json",
		requestContentType: 'application/json',
		contentType: 'application/json'
	]
    asynchttpGet('processApiResponse', getParams, [ip: ipAddress])
}

def processApiResponse(response, data) {
    if (response.getStatus() == 200) {
        def json = new groovy.json.JsonSlurper().parseText(response.data)
        logInfo "Detected WLED v${json.info.ver}"

        state.ver = json.info.ver
        state.ip = data.ip
        
        configureEffects(json.effects)
        configurePalettes(json.palettes)
	} else {
		log.error "WLED API returned error: $response"
	}
}

def configureEffects(effects) {
//    logDebug "Configure effects ${effects}"

//    state.effects = effects
    
//    def effectMap = [:]
 //   effects.eachWithIndex { element, index ->
//        effectMap[(index)] = element
//}
  //  def le = new groovy.json.JsonBuilder(effectMap)
   // sendEvent(name:"lightEffects",value:le)
}

def configurePalettes(palettes) {
 //   logDebug "Configure palettes ${palettes}"

  //  state.palettes = palettes
}

def parse(String description) {
    def mqtt = interfaces.mqtt.parseMessage(description) 
    
    def xml = new XmlSlurper().parseText(mqtt.payload)
    def map = xmlToMap(xml)
    // logDebug map
    
    /* <ac>	0 to 255	Master Brightness
<cl>	3x 0..255	Primary Color RGB
<cs>	3x 0..255	Secondary RGB
<ns>	0 or 1	Notification Sending on
<nr>	0 or 1	Notification Receive on
<nl>	0 or 1	Nightlight active
<nf>	0 or 1	Nightlight Fade on
<nd>	0 to 255	Nightlight delay
<nt>	0 to 255	Nightlight target brightness
<fx>	0 to 73	Effect index
<sx>	0 to 255	Effect speed
<ix>	0 to 255	Effect intensity
<fp>	0 to 43	FastLED palette
<wv>	-1 to 255	Primary White value
<ws>	0 to 255	Secondary White
<md>	0 or 1	RGB or HSB UI mode
<ds>	String 0..32	Server description
<th>	Hex Colors	Current theme (&IT call required)
    */    
   
    // convert 0..255 level to 0..100 level
    level = Math.round(map.ac.toInteger() / 255 * 100)
    if (level > 0) {
        sendEvent(name: "switch", value: "on")
    } else {
        sendEvent(name: "switch", value: "off")
    }
    if (level != device.currentValue("level")) {
        sendEvent(name: "level", value: level, unit: "%", isStateChange: true)
    }
    /*
    def primaryColor = hubitat.helper.ColorUtils.rgbToHSV(map.cl)
    if (primaryColor[0] != device.currentValue("hue")) {
        sendEvent(name: "hue", value: primaryColor[0])
        setGenericName(primaryColor[0])
    }    
    if (primaryColor[1] != device.currentValue("saturation")) {
        sendEvent(name:"saturation", value:primaryColor[1], unit: "%")
    }
    
    def effectIndex = map.fx.toInteger()
    if (effectIndex != device.currentValue("effectNumber")) {
        // effect changed
        def effectName = state.effects[effectIndex]
        def descr = "Effect was set to ${effectName} (${effectIndex})"
        logInfo "${descr}"
        sendEvent(name:"effectNumber", value:effectIndex, descriptionText: descr)
        sendEvent(name:"effectName", value:effectName, descriptionText: descr)
    }
    
    def effectSpeed = map.sx.toInteger()
    if (effectSpeed != device.currentValue("effectSpeed")) {
        // effect speed changed
        sendEvent(name:"effectSpeed", value:effectSpeed)
    }
    
    def effectIntensity = map.ix.toInteger()
    if (effectIntensity != device.currentValue("effectIntensity")) {
        // effect intensity changed
        sendEvent(name:"effectIntensity", value:effectIntensity)
    }

    def paletteIndex = map.fp.toInteger()
    if (paletteIndex != device.currentValue("paletteNumber")) {
        // palette changed
        def paletteName = state.palettes[paletteIndex]
        def descr = "Palette was set to ${paletteName} (${paletteIndex})"
        logInfo "${descr}"
        sendEvent(name:"paletteNumber", value:paletteIndex, descriptionText: descr)
        sendEvent(name:"paletteName", value:paletteName, descriptionText: descr)
    }
*/
}

def on() {
    logInfo "on"
    publishCommand "T=1"
}

def off() {
    logInfo "off"
    publishCommand "T=0"
}

def setEffect(String effect){
    logInfo "setEffect $effect"
    def effectIndex = state.effects.indexOf(effect)
    if (effectIndex >= 0) setEffect(effectIndex)
}

def setEffect(id){
    logInfo "setEffect $id"
    publishCommand "FX=${id}"
} 

def setEffectSpeed(speed){
    logInfo "setEffectSpeed $speed"
    publishCommand "SX=${speed}"
} 

def setEffectIntensity(indensity){
    logInfo "setEffectIntensity $indensity"
    publishCommand "IX=${indensity}"
} 

def setNextEffect(){
    def currentEffectId = device.currentValue("effectNumber") ?: 0
    currentEffectId++
    if (currentEffectId >= state.effects.size()) 
        currentEffectId = 0
    setEffect(currentEffectId)
}

def setPreviousEffect(){
    def currentEffectId = device.currentValue("effectNumber") ?: 1
    currentEffectId--
    if (currentEffectId < 0) 
        currentEffectId = state.effects.size() - 1
    setEffect(currentEffectId)
}

def setPreset(preset){
    logInfo "setPreset $preset"
    publishCommand "PL=${preset}"
} 

def setColor(value) {
    logInfo "setColor $value"
    def rgb = hubitat.helper.ColorUtils.hsvToRGB([value.hue, value.saturation, value.level])
    publishCommand "R=${rgb[0]}&G=${rgb[1]}&B=${rgb[2]}"
}

def setHue(value) {
    setColor([hue: value, saturation: device.currentValue("saturation"), level: device.currentValue("level")])
}

def setSaturation(value) {
    setColor([hue: device.currentValue("hue"), saturation: value, level: device.currentValue("level")])
}

def setLevel(value) {
    logInfo "setLevel $value"
    publishCommand "A=${Math.round(value * 2.55)}"
}
def setLevel(value,value2) {
    logInfo "setLevel $value"
    publishCommand "A=${Math.round(value * 2.55)}"
}
def setPalette(String palette){
    logInfo "setPalette $palette"
    def paletteIndex = state.palettes.indexOf(palette)
    if (paletteIndex >= 0) setPalette(paletteIndex)
}

def setPalette(id){
    logInfo "setPalette $id"
    publishCommand "FP=${id}"
} 

def publishCommand(command) {
    logDebug "Publish ${settings.mqttTopic}/api&${command}"
    interfaces.mqtt.publish(settings.mqttTopic + "/api", command)
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

def refresh()
{
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
        interfaces.mqtt.unsubscribe(settings.mqttTopic + "/v")
        interfaces.mqtt.disconnect()
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
        // open connection
        log.info "Connecting to ${settings.mqttBroker}"
        interfaces.mqtt.connect("tcp://" + settings.mqttBroker, "hubitat_wled_${device.id}", null, null)
        // subscribe once received connection succeeded status update below        
    } catch(e) {
        log.error "MQTT Connect error: ${e.message}."
        delayedConnect()
    }
}

def subscribe() {
    interfaces.mqtt.subscribe(settings.mqttTopic + "/v") // Contains XML API response (same as HTTP API)
    logDebug "Subscribed to topic ${settings.mqttTopic}/v"
    state.connected = true
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