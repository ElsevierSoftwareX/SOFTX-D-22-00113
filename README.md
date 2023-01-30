# Read Me

## Android Development with USB-Connected Sensors

Debugging is at the core of an efficient developement cycle. In order to debug an Android program, the device must be connected through USB to the computer you are developing with. However, how can you that if the external sensor you are trying to test also needs to be connected via USB?

Enter [wireless debugging][wireless-debugging]. In essence, when enabled you will be able to launch your debugging session unthetered. In order to to so, follow these steps:

1. Conect your Android smartphone to the same Wi-Fi network your computer is connected to.

2. Get the smartphone's IP address. If possible, permanently assign it to the smartphone, so it's always the same in that Wi-Fi network. It will ease your workflow. Let's say that IP is **192.168.1.123**.

3. Connecter your smartphone to the computer via USB.

4. Get Android's SDK location in your computer. In macOS it is in the folder **~/Library/Android/sdk/**.

5. Open a terminal session.

6. Run `adb devices` to make sure the device is connected:

    ```shell
    ~/Library/Android/sdk/platform-tools/adb devices
    ```

7. Besides the device's IP, you'll need to assign a port to the smartphone. To use port **5555** you have to execute `adb tcpip 5555`:

    ```shell
    ~/Library/Android/sdk/platform-tools/adb tcpip 5555
    ```

8. Lastly, connect the debugging bridge to the smartphone executing `adb connect 192.168.1.123`:

    ```shell
    ~/Library/Android/sdk/platform-tools/adb connect 192.168.1.123
    ```

And that's all! From this moment on, you could unplug the smartphone from the computer, plug the external sensor and debug your program.

[wireless-debugging]: https://medium.com/android-news/wireless-debugging-through-adb-in-android-using-wifi-965f7edd163a "Wireless Debugging through ADB in Android using WiFi"
