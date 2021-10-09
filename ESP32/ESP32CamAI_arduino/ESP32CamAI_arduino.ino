#include <Adafruit_MLX90640.h>
#include <WebSocketsServer.h>
#include <LiFuelGauge.h>
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
#define THERMARR 768

//Multi-use
const int LED_BUILT_IN = 4;
TwoWire I2CSensors = TwoWire(0);
unsigned long previousMillisServo = 0;
const unsigned long intervalServo = 10;

//WIFI
uint8_t camNo=0;
WiFiUDP UDPServer;
String strPackage;
IPAddress addrRemote;
unsigned int portRemote;
const int RECVLENGTH = 16;
unsigned int UDPPort = 6868;
unsigned long currentMillis;
bool clientConnected = false;
byte packetBuffer[RECVLENGTH];
const char* ssid = "ThermalServer";// "Spanbil #71";
const char* password = "123456789" ;// "3RedApples";
WebSocketsServer webSocket = WebSocketsServer(86);

//MAX17043
String outputStr;
double batLvl = 0.0;
double batVolt = 0.0;
LightChrono LipoChrono;
bool newBattStatus = false;
LiFuelGauge gauge(MAX17043, I2CSensors);

//MLX90640
int failCount = 0;
TaskHandle_t Task1;
Adafruit_MLX90640 mlx;
float frame[THERMARR];
byte bytearray[THERMARR];
bool isFrameReady = false;
LightChrono ThermalChrono;
volatile bool isSendingThermal = false;


//OV2640
size_t   jpgLength = 0;
int cameraInitState = -1;
uint8_t* jpgBuff = new uint8_t[68123];




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
  mlx.setResolution(MLX90640_ADC_16BIT);
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
  gauge.reset(); 
  delay(200);

  
  cameraInitState = initCamera();
  Serial.printf("camera init state %d\n", cameraInitState);
  if(cameraInitState != 0){
    return;
  }
  sensor_t * s = esp_camera_sensor_get();
  s->set_vflip(s, 1);
  
  //WIFI INIT
  /*
  Serial.printf("Connecting to %s\n", ssid);
  if (String(WiFi.SSID()) != String(ssid)) {
    WiFi.mode(WIFI_AP);
    WiFi.begin(ssid, password);
  }

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  */
  WiFi.softAP(ssid, password);
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
            6144,  /* Stack size in words */
            NULL,  /* Task input parameter */
            0,  /* Priority of the task */
            &Task1,  /* Task handle. */
            0); /* Core where the task should run */
  Serial.println(ESP.getFreeHeap());
}

void getThermalFrame( void * pvParameters ) {
  initThermal();
  while(1){
    if (ThermalChrono.hasPassed(125)) {
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
       if (LipoChrono.hasPassed(5000)) {
          LipoChrono.restart();
          batLvl = gauge.getSOC();
          batVolt = gauge.getVoltage();
          outputStr = "{\"voltage\":" + String(batVolt) + ", \"soc\":" + String(batLvl) + "}";
          newBattStatus = true;
          
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
  
  if (newBattStatus && clientConnected == true) {
    webSocket.sendTXT(camNo, outputStr);
    newBattStatus = false;
  }

  if (isFrameReady) {
    isSendingThermal = true;
    for (size_t i{}; i < THERMARR; ++i) { bytearray[i] = static_cast<byte>(frame[i]); }
    webSocket.sendBIN(camNo, bytearray, THERMARR);
    isFrameReady = false;
    isSendingThermal = false;
  }
  
}
