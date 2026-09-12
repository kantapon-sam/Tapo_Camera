Tapo RTSP Viewer portable package

How to run:
1. Edit config\rtsp-urls.txt and put one RTSP URL per line.
2. Double-click run.bat.

Adding cameras:
- Click + Add Camera, enter the IP address, and select stream1 or stream2.
- Select an existing camera account, or clear the checkbox and enter a new account.
- The camera appears immediately and is saved in the active config/cameras.properties.
- Stop recording before adding a camera. Record All includes newly added cameras.

Viewing camera accounts:
- Click User / Pass below a camera, then select Show password to reveal the password.
- Add Camera also shows the selected existing account and includes Show password.

Removing cameras:
- Click Delete below a camera and confirm. Stop recording first.
- The camera is removed from its source configuration or RTSP URL file and stays removed after restart.
- Recorded clips are kept. Use Playback > Open File to open clips from a removed camera.
- Use + Add Camera to add a removed camera again.

Folders:
- app\ contains the application jar and Java dependencies.
- config\ contains cameras.properties and rtsp-urls.txt.
- recordings\ is where Record All writes video clips.
- runtime\ is optional. Put a Java 8+ runtime here if the target PC has no Java.
- vlc\ is optional. Put VLC 64-bit files here if the target PC has no installed VLC.
- tools\ffmpeg\bin\ffmpeg.exe is used by Record All when bundled.

Notes:
- If using local VLC, libvlc.dll should be directly inside vlc\ and plugins should be inside vlc\plugins\.
- ffmpeg is only required for Record All. Install ffmpeg, set ffmpeg.path in config\cameras.properties, or put ffmpeg.exe in tools\ffmpeg\bin\.
- To rebuild this package from source, run build-portable.ps1 in the project folder.
