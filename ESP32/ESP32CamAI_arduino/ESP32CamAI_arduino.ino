#include <Adafruit_MLX90640.h>
#include <WebSocketsServer.h>
#include <BluetoothSerial.h>
//#include <LiFuelGauge.h>
#include <ArduinoJson.h>
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
#define LED_BUILT_IN 4
#define THERMARR 768
// #define DEBUG
// #define SAVE_IMG


const char* ssid = "Spanbil #71";    // <<< change this as yours
const char* password = "3RedApples"; // <<< change this as yours

//THERMAL
int failCount = 0;
TaskHandle_t Task1;
float frame[THERMARR];
byte bytearray[THERMARR];
volatile bool isFrameReady = false;
volatile bool isSendingThermal = false;

//MAX17043
String outputStr;
double batLvl = 0.0;
double batVolt = 0.0;


//BLUETOOTH
String btInc;

//WEBCONNECTION
uint8_t camNo=0;
String strPackage;
unsigned int portRemote;
const int RECVLENGTH = 16;
unsigned int UDPPort = 6868;
unsigned long currentMillis;
bool clientConnected = false;
byte packetBuffer[RECVLENGTH];
unsigned long previousMillis = 0;
const unsigned long interval = 10;


//OV2640 CAMERA
uint8_t* jpgBuff = new uint8_t[68123];
int cameraInitState = -1;
size_t   jpgLength = 0;

//CLASSES
WebSocketsServer webSocket = WebSocketsServer(86);
//LiFuelGauge gauge(MAX17043, I2CSensors2);
//TwoWire I2CSensors2 = TwoWire(0);
TwoWire I2CSensors = TwoWire(0);
LightChrono ThermalChrono;
BluetoothSerial SerialBT;
LightChrono LipoChrono;
Adafruit_MLX90640 mlx;
IPAddress addrRemote;
WiFiUDP UDPServer;





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
      }
      memset(packetBuffer, 0, RECVLENGTH);
  }

}



void processBTData() {
  if (SerialBT.available()) {
    DynamicJsonDocument doc(1024);
    deserializeJson(doc, SerialBT);
    int action = doc["action"];
    if (action == 1) { //Wifi connect
      String ss = doc["ssid"];
      String pw = doc["pass"];
      Serial.println("Got wifi credentials");
      //connectToWifi(ss, pw);
    }
    /*
     *  
    if (action == 1) { //Wifi connect
      connectToWifi(doc["ssid"], doc["pass"]);
    } else if (action == 2) { //Set LED on/off
        int state = doc["state"];
        if (state == 0) {
          digitalWrite(LED_BUILT_IN, LOW);
        } else if (state == 1) {
          digitalWrite(LED_BUILT_IN, HIGH);
        }
    } else if (action == 4) { //Thermal sensor on/off
      
    }
    */
  }
}



void setup(void) {

  Serial.begin(115200);
  Serial.print("\n");
  #ifdef DEBUG
  Serial.setDebugOutput(true);
  #endif
  //SerialBT.begin("ThermalESP");
  //disableCore0WDT();
  disableCore1WDT();
  pinMode(LED_BUILT_IN, OUTPUT);
  digitalWrite(LED_BUILT_IN, LOW);

  I2CSensors.begin(I2C_SDA, I2C_SCL, 400000);
  //I2CSensors2.begin(I2C2_SDA, I2C2_SCL, 100000);
  //gauge.reset(); 
  //delay(500);
 delay(1500);
    
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
            if (failCount > 2) { initThermal(); } /*Something whent wrong, re-init the device */
         } else {
            isFrameReady = true;
            failCount = 0;
        }
      }
    }
  }
}

void loop(void) {
  webSocket.loop();
  //processBTData();
  if(clientConnected == true){
    grabImage(jpgLength, jpgBuff);
    webSocket.sendBIN(camNo, jpgBuff, jpgLength);
  }

  currentMillis = millis();
  if (currentMillis - previousMillis >= interval) {
    previousMillis = currentMillis;
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
