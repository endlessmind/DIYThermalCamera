#include <BluetoothSerial.h>

#include <Adafruit_MLX90640.h>
#include <WebSocketsServer.h>
//#include <LiFuelGauge.h>
//#include <ArduinoJson.h>
#include <Wire.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include "camera_wrap.h"
#include <LightChrono.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif


#define I2C_SDA 15
#define I2C_SCL 14
#define I2C2_SDA 2
#define I2C2_SCL 13
#define THERMARR 768
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
int failCount = 0;
uint8_t* jpgBuff = new uint8_t[68123];
size_t   jpgLength = 0;
uint8_t camNo=0;
bool clientConnected = false;
bool isFrameReady = false;
volatile bool isSendingThermal = false;
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
//TwoWire I2CSensors2 = TwoWire(0);
//LiFuelGauge gauge(MAX17043, I2CSensors2);
Adafruit_MLX90640 mlx;
LightChrono LipoChrono;
LightChrono ThermalChrono;
BluetoothSerial SerialBT;

String outputStr;
double batLvl = 0.0;
double batVolt = 0.0;

float frame[THERMARR]; // buffer for full frame of temperatures
byte bytearray[THERMARR];

void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {

  switch(type) {
      case WStype_DISCONNECTED:
          Serial.printf("[%u] Disconnected!\n", num);
          camNo = num;
          clientConnected = false;
          //isSendingThermal = false;
          //isFrameReady = false;
          //vTaskDelete(Task1);
          break;
      case WStype_CONNECTED:
          Serial.printf("[%u] Connected!\n", num);
          clientConnected = true;
          
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

void processBTData() {
  if (SerialBT.available()) {
    char inc = SerialBT.read();
  }
}

void setup(void) {

  Serial.begin(115200);
  Serial.print("\n");
  #ifdef DEBUG
  Serial.setDebugOutput(true);
  #endif
  SerialBT.begin("ThermalESP");
  //disableCore0WDT();
  disableCore1WDT();
  pinMode(LED_BUILT_IN, OUTPUT);
  digitalWrite(LED_BUILT_IN, LOW);

  I2CSensors.begin(I2C_SDA, I2C_SCL, 400000);
  //I2CSensors2.begin(I2C2_SDA, I2C2_SCL, 100000);
  //gauge.reset(); 
  //delay(500);
 
    
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
  String ipAddress = WiFi.localIP().toString();;
  Serial.println("");
  Serial.print("Connected! IP address: ");
  Serial.println(ipAddress);

  webSocket.begin();
  webSocket.onEvent(webSocketEvent);
  UDPServer.begin(UDPPort);
  delay(500);
  xTaskCreatePinnedToCore(
            getThermalFrame, /* Function to implement the task */
            "ThermaTask", /* Name of the task */
            10000,  /* Stack size in words */
            NULL,  /* Task input parameter */
            0,  /* Priority of the task */
            &Task1,  /* Task handle. */
            0); /* Core where the task should run */

}

void getThermalFrame( void * pvParameters ) {
  initThermal();
  while(1){
    if (ThermalChrono.hasPassed(250)) {
      if (clientConnected == true && !isSendingThermal) {
        ThermalChrono.restart(); //Only reset when all criteria is meet, will givet frame faster
        if (mlx.getFrame(frame) != 0) {
          Serial.println("Failed");
          failCount++;
          if (failCount > 2) {
            initThermal(); //Something whent wrong, re-init the device
          }
         } else {
          isFrameReady = true;
          failCount = 0;
        }
      } else {
        //Serial.println("criteria not meet.");
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
  
  if (LipoChrono.hasPassed(5000) && clientConnected == true) {
    LipoChrono.restart();
    //batLvl = gauge.getSOC();
    //batVolt = gauge.getVoltage();
    //DynamicJsonDocument  jsonRoot(128);
    //jsonRoot["charge"] = batLvl;
    //jsonRoot["voltage"] = batVolt;
    //Serial.println(gauge.getSOC());
    //serializeJson(jsonRoot,outputStr);
    //jsonRoot.clear();
    outputStr = "{\"voltage\":" + String(3.67f) + ", \"soc\":" + String(64) + "\"}";
    webSocket.sendTXT(camNo, outputStr);
  }

  if (isFrameReady) {
    isSendingThermal = true;
    for (size_t i{}; i < THERMARR; ++i) { bytearray[i] = static_cast<byte>(frame[i]); }
    webSocket.sendBIN(camNo, bytearray, THERMARR);
    isFrameReady = false;
    isSendingThermal = false;
  }
  
}
