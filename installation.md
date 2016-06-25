# Installation Guide

## Preparing the set

### Configure the gateway
1. Click on '**Apps**'->'**Settings**'->'**Display**', keep the screen turned on in '**Screen timeout**.'
2. Click on '**Apps**'->'**Settings**'->'**Lock screen**', disable screen lock by selecting '**None**' in '**Select screen lock**.'
3. Click on '**Apps**'->'**Settings**'->'**Wi-Fi**', tap the '**Menu**' button to the right of the '**Home**' button and select '**Advanced Wi-Fi**'. Uncheck '**Battery saving for Wi-Fi**.'

### Root the gateway
1. Downlaod [TowelRoot](http://bit.ly/1RRWWAk).
2. Install the application and ignore the warning. Finish the installation and open the application.
3. Tap the <span style="color:red">**RED**</span> ***"V3"*** on the title for *3* times. A text field will show up and change the numbers `0 1 0` to `0 0 0`. Then click on the button.
4. If the text suggests the phone is rooted, download [SuperSU](http://bit.ly/1ph6RGA), open and upgrade the application after installaton. Please **DON'T** restart the phone.
5. Seletct the 'Setting' tag.
	+ Uncheck '**Re-authentication**'
	+ Select and change '**default access**' to **Grant**
	+ Uncheck '**Show notifications**'
6. Open '**Downloads**' in '**Apps**' (bottom right of the home screen) and click '**Delete**' on the upper right corner. Select and remove all .apk files.


### Install/Upgrade the SilverLink Gateway Application
1. Download [SilverLinkHelper.apk](http://bit.ly/1RU7uM6) and install the application.
2. Download [SilverLinkC.apk](http://bit.ly/1LS4vrq) and install the application.
3. Open the application after installation.


### Change Local Timezone on the Gateway
1. Download [ClockSync.apk](http://bit.ly/28TWyk1) and install the application.
2. Download [TimezoneDB.apk](http://bit.ly/297ps0A) and install.
3. Open **ClockSync** application and click on '**Menu**'->'**Settings**'.
4. Scroll down and select '**Use offline database**', then you can select the corresponding timezone.

<!-- * [SilverlinkC_3min.apk](https://raw.github.com/HongyiZhu/MetaCTest/master/app/SilverLinkC_3min.apk) -->

### Read gateway logs on the gateway
1. Download and install [ES File Explorer](http://bit.ly/1LS7gZH).
2. Go to Directory `/sdcard/Android/data/com.example.hongyi.foregroundtest/files/logs/`, all the log files are in the folder.

### Download log files to computer
1. Connect the gateway to a computer
2. Reboot the gateway and select 'MTP' mode on the gateway
3. Find the log files on the device under folder: <br/>`LGL34C/Internal Storage/Android/data/com.example.hongyi.foregroundtest/files/logs/`.

### Update sensor firmwear
1. Open '**Apps**'->'**Metawear**', select the sensor to be updated.
2. Connect to the sensor and click on 'Update Firmwear'.
3. After update, disconnect from the sensor.