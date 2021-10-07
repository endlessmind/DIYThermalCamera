# DIYThermalCamera
 Fully working software to build a working thermal camera

## Status
Working:
- OV2640
- MLX90640
- Wifi
- Websocket
- Temperature color auto-rangeing in Android app

Kindof working:
- MAX17043 (Was working, but removed library to find cause of heap error)
After investigating this more, it looks like it might be an issue with the i2c bus or some device conflict..
Althought it can be as simple as a cross-threading issue.
Core 1 is collecting the data from the MLX90640 over i2c, and Core 0 wants to collect data from the MAX17043
over the same i2c interface. The MLX90640 is read every 250ms, and the MAX17043 is read every 5000ms.
Naturally, there should be some offset. Some drift. Clocks are never perfectly in sync. But at some point, 
it might be that both cores are trying to make calls to the same bus.
Needs to be investigated more..

Still todo:
- BT  -for wifi configuration and automatic device-detection from the Android app
- Render battery information on Android output
- Render temperature color map
- Render current temperature range
- Maybe more settings like emissivity?