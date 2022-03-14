# DIYThermalCamera
 Fully working software to build a working thermal camera
![alt tag](https://raw.githubusercontent.com/endlessmind/DIYThermalCamera/master/ThermalCamera_Screenshot.jpg)
## Status
Working:
- OV2640
- MLX90640
- Wifi (Now in AP mode)
- Websocket
- Temperature color auto-rangeing in Android app
- MAX17043
- Render battery information on Android output
- Render current temperature range

Still todo:
- Touch to get reading of specific area. (This is mostly implemented, but i think i will re-write most of the code and the structure was not ment for this kind of functionallity.)
- Show selection on the warmest point.
- Render temperature color map (I just skip this one, is it really needed?)
- Maybe more settings like emissivity?

Canceled:
- BT support has been removed. The ESP32 was running out of memory when trying to use WiFi, BT and Camera at the same time, as well as running tasks on both threads.
It might be possible to get this to work, but as this is only for personal use then it doesn't really matter.
I've switched to AP mode for the ESP32 and just connect to it with my phone, should have done that from the begning..
