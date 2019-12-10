/**
*   
*   File: Telegram_Driver.groovy
*   Platform: Hubitat
*   Modification History:
*       Date       Who              What
*       2019-12-10 Matthew Bafford  Initial implementation
*
*  Copyright 2019 Matthew Bafford
*
*  To use:
*     In Hubitat management website:
*         Go to "Drivers Code", and add a new driver. Save the contents of this file there. 
*         On "Devices" tab, add a new virtual device and change the type to Telegram.
*         Enter your bot API key and chat ID in the preferences.
*
*  Telegram instructions:
*     Create a bot by communicating with the @BotFather on Telegram. He'll walk you through the steps.
*     @BotFather will give you your bot API key in the form of #####:text
*
*     For the chat ID, you'll need to message your new bot with your normal Telegram account. Once you have done that,
*     there will be a pending update for your bot. You can fetch that update and get the chat ID from the JSON. Call:
*
*     curl 'https://api.telegram.org/bot<BOTID>/getUpdates'
*
*     Parse chat ID from the response JSON:
*
*     {
*         "ok":true,
*         "result":[{
*             "update_id": ####UPDATEID####,
*             "message": {
*                  "message_id":5,
*                  "from":{"id": ####YOURUSERID####,
*                  "is_bot":false, (or are you?)
*                  "first_name": ...,
*                  "username": ...,,
*                  "language_code":"en"
*             },
*             "chat":{
*                  "id": ####CHATID#####,
*                  "first_name": ...,
*                  "username": ...,
*                  "type":"private"
*             },
*             "date":1575988451,
*             "text":"test"}}]
*      }
*
*      In this example, ####CHATID#### is the value you need for the preferences for the device.
* 
*  Inspired by https://github.com/ogiewon/Hubitat/blob/master/Drivers/pushover-notifications.src/pushover-notifications.groovy
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
def version() {"v1.0.20191210"}

preferences {
    log.debug "preferences"

    input("apiKey", "text", title: "API Key:", description: "Telegram Bot ID")
    input("chatID", "text", title: "Chat ID:", description: "Telegram Chat ID")
    
    attribute "botName", "String"
}

metadata {
    definition (name: "Telegram", namespace: "mbafford", author: "Matthew Bafford") {
        capability "Notification"
        capability "Actuator"
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()   
}

def initialize() {
    state.version = version()
    validateAPI()
}

def validateAPI() {
    log.debug "Validating Telegram API Key (bot key)..."
    
    def validated = false
    
    def params = [
        uri: "https://api.telegram.org/bot" + apiKey + "/getMe",
        contentType: "application/json",
        requestContentType: "application/x-www-form-urlencoded",
    ]
    
    if ( !( apiKey =~ /^[0-9]+:[_A-Za-z0-9]+$/ ) ) {
        log.error "Invalid API key format. Must be #######:text. Do not include 'bot' on the front."
        return false;
    }

    try {
        httpPost(params){response ->
            if(response.status != 200) {
                log.error "Telegram Bot initialization. Received HTTP error ${response.status}. Check your API Key / Bot Key!"
            } else {
                log.info response.data
                
                state.botName = response.data.result.username
                state.botID   = response.data.result.id
                state.is_bot  = response.data.result.is_bot
            }
        }
    } catch (Exception e) {
        log.error "An invalid key was probably entered. Telegram Server Returned: ${e}"
        return false;
    } 
    
    sent = sendMessage(chatID, "Initializing Telegram Hubitat bot|||disable_notification=true")
    return sent

    return true;
}

def sendMessage(chatID, message) {
    try {
        message_parts = message.split("[|][|][|]");
        message       = message_parts[0]
        extra_params  = message_parts[1].split("&")
        
        msg_params = [
            uri: "https://api.telegram.org/bot" + apiKey + "/sendMessage",
            contentType: "application/json",
            requestContentType: "application/x-www-form-urlencoded",
            body: [
                "chat_id": chatID,
                "text":    message,
            ]
        ]
        
        for ( extra in extra_params ) {
            kv = extra.split("=")
            msg_params.body[kv[0]] = kv[1]
        }
        
        log.debug "Calling Telegram sendMessage with ${msg_params.body}"
        
        httpPost(msg_params){response ->
            if(response.status != 200) {
                log.error "Received HTTP error ${response.status}. Check your keys!"
            } else {
                log.info response.data
                
                state.lastMessage = response.data.result.date
                log.info "Message response: ${response.data}"
            }
        }
        
        return true        
        
    } catch ( Exception ex ) {
        log.error "Error sending message [${message}] to chat [${chatID}]: ${ex}"
        return false
    }
}

def deviceNotification(message) {
    sendMessage(chatID, message)
}
