/**
   * Initial pass at using the EcoSmart Zigbee remotes, proxied through zigbee2mqtt in Hubitat.
   *
   *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
   *  in compliance with the License. You may obtain a copy of the License at:
   *
   *      http://www.apache.org/licenses/LICENSE-2.0
   *
   *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
   *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
   *  for the specific language governing permissions and limitations under the License.
   */

metadata {
  definition (name: "zigbee2mqtt EcoSmart Remote ZBT-CCTSwitch-D0001", namespace: "mbafford", author: "Matthew Bafford", importURL: "https://raw.githubusercontent.com/mbafford/hubitat-integration-code/master/Drivers/EcoSmart_Remote_Zigbee2MQTT/ecosmart_remote_from_zigbee2mqtt.groovy") {
    capability "Initialize"
    capability "PushableButton"
        
    attribute "numberOfButtons", "number"
    attribute "pushed", "number"
  }

  preferences {
    input name: "MQTTBroker", type: "text", title: "MQTT Broker Address:", required: true, displayDuringSetup: true
    input name: "username", type: "text", title: "MQTT Username:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "password", type: "password", title: "MQTT Password:", description: "(blank if none)", required: false, displayDuringSetup: true
    input name: "topicSub", type: "text", title: "Topic to Subscribe:", description: "Example Topic (topic/device/#)", required: false, displayDuringSetup: true
    input name: "QOS", type: "text", title: "QOS Value:", required: false, defaultValue: "1", displayDuringSetup: true
    input name: "retained", type: "bool", title: "Retain message:", required: false, defaultValue: false, displayDuringSetup: true
    input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: true)
  }
}

def installed() {
    sendEvent([name:'numberOfButtons', value:4, displayed:false])
}

// Parse incoming device messages to generate events
def parse(String mqttMsg) {
  msg = interfaces.mqtt.parseMessage(mqttMsg)
  if (logEnable) log.debug "MQTTStatus- incoming message: ${msg}"

  payload = parseJson( msg.get('payload') )
  
  // handle multiple versions of the zigbee2mqtt converter
  // from action:button1 to action:button_1 to click:power
  click  = payload["click"]
  if ( click == null ) {
    click = payload["action"]
  }
  def button = null;
  switch ( click ) {
      case "power":
      case "button1":
      case "button_1":
          button = 1;
          break;
      case "brightness":
      case "button2":
      case "button_2":
          button = 2; 
          break;
      case "colortemp":
      case "button3":
      case "button_3":
          button = 3;
          break;
      case "memory":
      case "button4":
      case "button_4":
          button = 4; 
          break;
      default:
          log.error "Unknown action on EcoSmart topic from MQTT: ${action}";
          return
  }
  desc = "$device.displayName button $button was pushed"
  if (logEnable) log.debug desc
  sendEvent(name: "pushed", value: button, descriptionText: desc, isStateChange: true);
}

def updated() {
  initialize()
}

def uninstalled() {
  if (logEnable) log.info "Uninstalled. Disconnecting from mqtt"
  interfaces.mqtt.disconnect()
}

def initialize() {
    try {
        def mqttInt = interfaces.mqtt
        def server   = "tcp://" + settings?.MQTTBroker + ":1883"
        def clientID = "hubitat-" + device.deviceNetworkId
        if ( logEnable ) log.debug "MQTTStatus- Connecting to ${server} as client ${clientID}"
        mqttInt.connect(server, clientID, settings?.username,settings?.password)
        //give it a chance to start
        pauseExecution(1000)
        if (logEnable) log.debug "MQTTStatus- Connection established"
        mqttInt.subscribe(settings?.topicSub)
		if (logEnable) log.debug "MQTTStatus- Subscribed to: ${settings?.topicSub}"        
    } catch(e) {
        if (logEnable) log.debug "MQTTStatus- Initialize error: ${e.message}"
    }
}

def mqttClientStatus(String status){
  if (logEnable) log.debug "MQTTStatus- status: ${status}"
    if ( !interfaces.mqtt.isConnected() ) {
        log.warn "Connection lost. Attempting reconnection."
        // initialize()
    }
}
