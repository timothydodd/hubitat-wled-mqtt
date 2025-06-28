# WLED MQTT Driver for Hubitat Elevation

**Enhanced Version 2.0.0** - Complete WLED control via MQTT

This is a comprehensive device driver for Hubitat Elevation that provides full control over WLED devices via MQTT. It supports all major WLED features including colors, effects, palettes, presets, segments, and more.

## ✨ Features

### Core Lighting Control
- **Switch Control**: Turn WLED on/off
- **Brightness Control**: Dimming from 0-100% with smooth transitions
- **Color Control**: Full RGB and HSV color management
- **Color Temperature**: White balance control (2700K-6500K)

### Advanced WLED Features
- **Effects**: Access to all WLED effects with speed and intensity control
- **Palettes**: Color palette selection and management
- **Presets**: Save and load WLED presets
- **Segments**: Multi-segment color control
- **Transitions**: Configurable fade timing

### Modern API Support
- **JSON API**: Recommended modern WLED JSON API (v0.13+)
- **Legacy XML**: Backward compatibility with older WLED versions
- **Auto-Discovery**: Automatic detection of effects and palettes
- **HTTP Fallback**: Direct HTTP API calls when needed

### Robust Communication
- **MQTT Authentication**: Username/password support for secure brokers
- **Auto-Reconnection**: Intelligent reconnection with backoff
- **Error Handling**: Comprehensive error detection and recovery
- **State Synchronization**: Real-time state updates via MQTT

## 🚀 Quick Start

> **⚠️ IMPORTANT**: When entering the WLED IP address, use **IP only** (e.g. `192.168.1.50`) - **DO NOT** include `http://` or paths like `/json`. This is the most common configuration error that causes 408 timeout errors!

### 1. Installation
1. Copy the driver code from `wled-mqtt-driver.groovy`
2. In Hubitat: **Drivers Code** → **New Driver** → Paste code → **Save**
3. **Devices** → **Add Device** → **Virtual** → Select "WLED MQTT Light"

### 2. Basic Configuration
```yaml
MQTT Broker: "192.168.1.100:1883"
MQTT Topic: "wled/livingroom"  # Your WLED device topic
Use JSON API: ✅ Enabled (recommended)
WLED IP Address: "192.168.1.50"  # For auto-discovery (IP ONLY!)
```

### 3. WLED Setup
Ensure your WLED device has MQTT enabled:
- **Settings** → **Sync Interfaces** → **MQTT**
- Set broker IP and topic to match Hubitat configuration
- Enable "Send notifications on direct change"

## 📋 Configuration Options

### MQTT Settings
| Setting | Description | Example |
|---------|-------------|---------|
| **MQTT Broker** | Broker address and port | `192.168.1.100:1883` |
| **MQTT Topic** | WLED device topic | `wled/devicename` |
| **Username** | MQTT authentication (optional) | `mqttuser` |
| **Password** | MQTT authentication (optional) | `mqttpass` |

### WLED Settings
| Setting | Description | Example |
|---------|-------------|---------|
| **WLED IP Address** | IP for HTTP API discovery | `192.168.1.50` ← **IP ONLY** |
| **Use JSON API** | Modern API (recommended) | ✅ Enabled |
| **Auto-refresh** | State update interval | 30 seconds |

### Advanced Settings
| Setting | Description | Default |
|---------|-------------|---------|
| **Max Segments** | Maximum LED segments | 16 |
| **Transition Time** | Default fade duration | 700ms |
| **Debug Logging** | Enable detailed logs | ✅ Enabled |

## 🎮 Available Commands

### Basic Control
```groovy
device.on()                          // Turn on
device.off()                         // Turn off
device.setLevel(75)                  // Set brightness to 75%
device.setLevel(50, 3)               // Fade to 50% over 3 seconds
```

### Color Control
```groovy
device.setColor([hue: 120, saturation: 100, level: 80])  // Green
device.setHue(240)                   // Blue hue
device.setSaturation(50)             // 50% saturation
device.setColorTemperature(3000)     // Warm white (3000K)
```

### Effects
```groovy
device.setEffect("Rainbow")          // Set effect by name
device.setEffect(12)                 // Set effect by number
device.setNextEffect()               // Next effect
device.setPreviousEffect()           // Previous effect
device.setEffectSpeed(200)           // Effect speed (0-255)
device.setEffectIntensity(150)       // Effect intensity (0-255)
```

### Palettes & Presets
```groovy
device.setPalette("Rainbow")         // Set palette by name
device.setPalette(5)                 // Set palette by number
device.setPreset(1)                  // Load preset 1
```

### Advanced Features
```groovy
device.setSegmentColor(0, "#FF0000") // Set segment 0 to red
device.setSegmentColor(1, "255,128,0") // Set segment 1 to orange
device.setTransition(2)              // Set 2-second transitions
device.configure()                   // Auto-discover effects/palettes
device.refresh()                     // Request state update
```

## 🔧 Setup & Discovery

### Auto-Discovery
Run the `configure()` command to automatically discover:
- Available effects list
- Color palettes
- Device information
- Current state

This populates the device with all available WLED features.

### Manual Configuration
If auto-discovery isn't available, effects and palettes will use numeric IDs:
- Effects: 0-73+ (depends on WLED version)
- Palettes: 0-43+ (depends on WLED version)

## 📊 Hubitat Capabilities

The driver implements these Hubitat capabilities:

| Capability | Purpose |
|------------|---------|
| **Switch** | On/off control |
| **SwitchLevel** | Brightness control |
| **ColorControl** | RGB/HSV color management |
| **ColorTemperature** | White balance control |
| **Light** | Light device identification |
| **LightEffects** | Effect management |
| **Initialize** | Setup and configuration |
| **Actuator** | Device control interface |

## 🔍 Troubleshooting

### ⚠️ Configuration Issues (Most Common)
- **IP Address Format**: Enter IP only (e.g. `192.168.1.50`) - **DO NOT** include `http://` or paths
- **MQTT Topic Format**: Use simple topic (e.g. `wled/livingroom`) - no leading/trailing slashes  
- **408 Timeout Errors**: Usually caused by including URLs like `http://192.168.1.50/json` in IP field
- **Auto-Fix**: Driver now automatically cleans malformed IP addresses

### Connection Issues
- Verify MQTT broker is running and accessible
- Check MQTT topic matches between Hubitat and WLED
- Ensure WLED MQTT is enabled and configured
- Try without authentication first

### Discovery Problems
- Set WLED IP address in device preferences (IP only!)
- Run `configure()` command manually
- Check WLED HTTP API is accessible at `http://[IP]/`
- Verify JSON API is enabled in WLED

### State Sync Issues
- Enable "Send notifications on direct change" in WLED
- Check MQTT topic subscriptions
- Verify JSON vs XML API setting matches WLED
- Monitor Hubitat logs for MQTT messages

### Performance
- Adjust auto-refresh interval (10-300 seconds)
- Use JSON API for better performance
- Reduce transition times if needed
- Check MQTT broker capacity

## 🆕 Version 2.0.0 Changes

### New Features
- Complete color temperature support
- Multi-segment control
- Enhanced effect management
- Modern JSON API support
- Auto-discovery via HTTP API
- MQTT authentication support
- Improved error handling
- Comprehensive state management
- **Auto-cleanup of malformed IP addresses**

### Breaking Changes
- JSON API is now default (can be disabled)
- MQTT topics changed for JSON mode
- Some attribute names updated
- Enhanced configuration options

### Migration from v1.x
1. Update device preferences
2. Enable JSON API (recommended)
3. Run `configure()` for auto-discovery
4. Test all functionality

## 📝 Requirements

- **Hubitat Elevation** Hub (any version)
- **WLED** v0.13.0+ (recommended for JSON API)
- **MQTT Broker** (Mosquitto, Home Assistant, etc.)
- Network connectivity between all components

## 🤝 Contributing

This driver is based on the original WLED MQTT Light driver by **Mikhail Diatchenko** from his [hubitat repository](https://github.com/muxa/hubitat/blob/master/drivers/wled-light.groovy). 

**Enhanced and refactored by Tim Dodd** with significant improvements including:
- Modern WLED JSON API support  
- Complete color temperature implementation
- Full effects and palettes functionality (was commented out in original)
- Multi-segment support
- Auto-discovery via HTTP API
- Robust error handling and reconnection logic
- MQTT authentication support
- Input validation and auto-cleanup
- Comprehensive documentation and troubleshooting
- Enhanced state management

This represents a substantial refactoring and enhancement of the original driver while maintaining compatibility with the core MQTT communication approach.

Feel free to submit issues, feature requests, or improvements!

## 📄 License

Licensed under the Apache License, Version 2.0. See the LICENSE file for details.

## 🔗 Links

- **WLED Project**: https://github.com/Aircoookie/WLED
- **WLED Documentation**: https://kno.wled.ge/
- **Hubitat Documentation**: https://docs.hubitat.com/
- **Original Driver**: https://github.com/muxa/hubitat/blob/master/drivers/wled-light.groovy

---

**Version**: 2.0.0  
**Original Author**: Mikhail Diatchenko (muxa)  
**Enhanced by**: Tim Dodd (dodd)  
**Last Updated**: December 2024