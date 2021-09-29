#include <ArduinoJson.h>

#include <Chrono.h>
#include <LightChrono.h>

#include <SparkFun_MAX1704x_Fuel_Gauge_Arduino_Library.h>

#include <Wire.h>

/*
BSD 2-Clause License

Copyright (c) 2020, ANM-P4F
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
#include <Adafruit_MLX90640.h>
#include <WebSocketsServer.h>
#include <LiFuelGauge.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include "camera_wrap.h"

#define I2C_SDA 15
#define I2C_SCL 14
// #define DEBUG
// #define SAVE_IMG

enum TRACK{
  TRACK_NONE = 0,
  TRACK_FW,
  TRACK_LEFT,
  TRACK_RIGHT,
  TRACK_STOP
};


const char* ssid = "Spanbil #71";    // <<< change this as yours
const char* password = "3RedApples"; // <<< change this as yours
//holds the current upload
int cameraInitState = -1;
uint8_t* jpgBuff = new uint8_t[68123];
size_t   jpgLength = 0;
uint8_t camNo=0;
bool clientConnected = false;
bool isFrameReady = false;
TaskHandle_t Task1;
//Creating UDP Listener Object. 
WiFiUDP UDPServer;
IPAddress addrRemote;
unsigned int portRemote;
unsigned int UDPPort = 6868;
unsigned long currentMillis;
const int RECVLENGTH = 16;
byte packetBuffer[RECVLENGTH];

WebSocketsServer webSocket = WebSocketsServer(86);
String strPackage;

const int LED_BUILT_IN        = 4;
unsigned long previousMillisServo = 0;
const unsigned long intervalServo = 10;

TwoWire I2CSensors = TwoWire(0);

LiFuelGauge gauge(MAX17043, I2CSensors);
Adafruit_MLX90640 mlx;
Chrono LipoChrono;
Chrono ThermalChrono;

String outputStr;
double batLvl = 0.0;
double batVolt = 0.0;

float frame[32*24]; // buffer for full frame of temperatures
byte bytearray[32*24];

void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {

  switch(type) {
      case WStype_DISCONNECTED:
          Serial.printf("[%u] Disconnected!\n", num);
          camNo = num;
          clientConnected = false;
          vTaskDelete(Task1);
          break;
      case WStype_CONNECTED:
          Serial.printf("[%u] Connected!\n", num);
          clientConnected = true;
          xTaskCreatePinnedToCore(
            getThermalFrame, /* Function to implement the task */
            "ThermaTask", /* Name of the task */
            16384,  /* Stack size in words */
            NULL,  /* Task input parameter */
            0,  /* Priority of the task */
            &Task1,  /* Task handle. */
            0); /* Core where the task should run */
          break;
      case WStype_TEXT:
      case WStype_BIN:
      case WStype_ERROR:
      case WStype_FRAGMENT_TEXT_START:
      case WStype_FRAGMENT_BIN_START:
      case WStype_FRAGMENT:
      case WStype_FRAGMENT_FIN:
          Serial.println(type);
          break;
  }
}

void initThermal() {
  if (!mlx.begin(MLX90640_I2CADDR_DEFAULT, &I2CSensors)) {
    Serial.println("MLX90640 not found!");
  }
  Serial.println("Found MLX90640");
  mlx.setMode(MLX90640_CHESS);
  mlx.setResolution(MLX90640_ADC_18BIT);
  mlx.setRefreshRate(MLX90640_4_HZ);
}


void processUDPData(){
  int cb = UDPServer.parsePacket();

  if (cb) {
      UDPServer.read(packetBuffer, RECVLENGTH);
      addrRemote = UDPServer.remoteIP();
      portRemote = UDPServer.remotePort();

      strPackage = String((const char*)packetBuffer);
  #ifdef DEBUG
      Serial.print("receive: ");
      // for (int y = 0; y < RECVLENGTH; y++){
      //   Serial.print(packetBuffer[y]);
      //   Serial.print("\n");
      // }
      Serial.print(strPackage);
      Serial.print(" from: ");
      Serial.print(addrRemote);
      Serial.print(":");
      Serial.println(portRemote);
  #endif
      if(strPackage.equals("whoami")){
          UDPServer.beginPacket(addrRemote, portRemote-1);
          String res = "ESP32-CAM";
          UDPServer.write((const uint8_t*)res.c_str(),res.length());
          UDPServer.endPacket();
          Serial.println("response");
      }else if(strPackage.equals("ledon")){
        digitalWrite(LED_BUILT_IN, HIGH);
      }else if(strPackage.equals("ledoff")){
        digitalWrite(LED_BUILT_IN, LOW);
      }

      memset(packetBuffer, 0, RECVLENGTH);
  }

}

void setup(void) {

  Serial.begin(115200);
  Serial.print("\n");
  #ifdef DEBUG
  Serial.setDebugOutput(true);
  #endif
  disableCore0WDT();
  disableCore1WDT();
  pinMode(LED_BUILT_IN, OUTPUT);
  digitalWrite(LED_BUILT_IN, LOW);

  I2CSensors.begin(I2C_SDA, I2C_SCL, 400000);
  gauge.reset();  // Resets MAX17043
  delay(200);  // Waits for the initial measurements to be made
  gauge.setAlertThreshold(10);
    
  cameraInitState = initCamera();
  Serial.printf("camera init state %d\n", cameraInitState);
  if(cameraInitState != 0){
    return;
  }
  sensor_t * s = esp_camera_sensor_get();
  s->set_vflip(s, 1);
  
  //WIFI INIT
  Serial.printf("Connecting to %s\n", ssid);
  if (String(WiFi.SSID()) != String(ssid)) {
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, password);
  }

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.print("Connected! IP address: ");
  String ipAddress = WiFi.localIP().toString();;
  Serial.println(ipAddress);

  webSocket.begin();
  webSocket.onEvent(webSocketEvent);
  UDPServer.begin(UDPPort);


}

void getThermalFrame( void * pvParameters ) {
  initThermal();
  for(;;){
    if (ThermalChrono.hasPassed(250) && clientConnected == true) {
      ThermalChrono.restart();
      if (mlx.getFrame(frame) != 0) {
        Serial.println("Failed");
      } else {
        isFrameReady = true;
      }
    }
  }
}

void loop(void) {
  webSocket.loop();
  if(clientConnected == true){
    grabImage(jpgLength, jpgBuff);
    webSocket.sendBIN(camNo, jpgBuff, jpgLength);
  }

  currentMillis = millis();
  if (currentMillis - previousMillisServo >= intervalServo) {
    previousMillisServo = currentMillis;
    processUDPData();
  }
  
  if (LipoChrono.hasPassed(15000) && clientConnected == true) {
    LipoChrono.restart();
    batLvl = gauge.getSOC();
    batVolt = gauge.getVoltage();
    DynamicJsonDocument  jsonRoot(128);
    jsonRoot["charge"] = batLvl;
    jsonRoot["voltage"] = batVolt;

    outputStr = "";
    serializeJson(jsonRoot,outputStr);
    jsonRoot.clear();
    webSocket.sendTXT(camNo, outputStr);
  }

  if (isFrameReady) {
    for (uint8_t i = 0; i < 32*24; ++i) { bytearray[i] = static_cast<byte>(frame[i]); }
    webSocket.sendBIN(camNo, bytearray, 32*24);
    isFrameReady = false;
  }
  
}
