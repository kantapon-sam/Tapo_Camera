# Tapo RTSP Viewer (Java)

แอป Java สำหรับดูกล้อง Tapo ผ่าน RTSP หลายจอพร้อมกัน และดูย้อนหลังจากไฟล์ที่บันทึกไว้ด้วย `ffmpeg`

## สิ่งที่ต้องติดตั้ง

1. Java 8+ JDK
2. Maven
3. VLC 64-bit สำหรับ Windows
4. ffmpeg ถ้าต้องการกด `Record All` เพื่อเก็บไฟล์ย้อนหลัง

## ตั้งค่ากล้อง Tapo

ในแอป Tapo ให้สร้าง **Camera Account** ก่อน ไม่ใช่บัญชี TP-Link Cloud:

`Camera Settings -> Advanced Settings -> Camera Account`

จากเอกสาร Tapo, RTSP live URL ใช้รูปแบบนี้:

```text
rtsp://username:password@IP_ADDRESS:554/stream1
rtsp://username:password@IP_ADDRESS:554/stream2
```

`stream1` คือคุณภาพสูง และ `stream2` คือคุณภาพมาตรฐาน

## ตั้งค่าโปรเจกต์

หลังโคลนโปรเจกต์ ให้คัดลอกไฟล์ตัวอย่างก่อน (ทำครั้งแรกเท่านั้น):

```powershell
Copy-Item config/cameras.example.properties config/cameras.properties
Copy-Item config/rtsp-urls.example.txt config/rtsp-urls.txt
```

ไฟล์ตั้งค่าจริงและ URL กล้องจะไม่ถูกอัปโหลดขึ้น Git เพราะอาจมีชื่อผู้ใช้และรหัสผ่าน ส่วน `dist/`, `target/` และ `recordings/` เป็นไฟล์ที่สร้างบนเครื่องและไม่รวมใน repository

เพิ่ม RTSP URL ทีละบรรทัดใน `config/rtsp-urls.txt` หรือแก้ค่ากล้องและตั้ง `camera.1.enabled=true` ในไฟล์:

```text
config/cameras.properties
```

ตัวอย่าง:

```properties
camera.1.enabled=true
camera.1.id=front
camera.1.name=Front Camera
camera.1.host=192.168.1.10
camera.1.port=554
camera.1.username=YOUR_CAMERA_ACCOUNT_USER
camera.1.password=YOUR_CAMERA_ACCOUNT_PASSWORD
camera.1.stream=stream1
```

เพิ่มกล้องตัวที่ 2, 3, 4 ได้ด้วย `camera.2.*`, `camera.3.*`

## รันแบบง่ายบน Windows

```powershell
.\run.ps1
```

ถ้าเครื่องยังไม่มี Maven ใน PATH สคริปต์จะดาวน์โหลด Maven มาไว้ใน `.tools/` ของโปรเจกต์นี้เท่านั้น

## รันด้วย Maven เอง

```powershell
mvn exec:java
```

หรือระบุไฟล์ config เอง:

```powershell
mvn exec:java "-Dexec.args=C:\path\to\cameras.properties"
```

## Live หลายจอ

เพิ่มกล้องจากปุ่ม `+ Add Camera` ได้โดยกรอก IP และเลือกใช้บัญชีจากกล้องเดิม หรือกรอกบัญชีใหม่ กล้องจะปรากฏทันทีและบันทึกในไฟล์ตั้งค่าของโปรแกรมชุดที่กำลังเปิดอยู่ เปิดครั้งถัดไปยังมีกล้องที่เพิ่มไว้ หากกำลังบันทึกอยู่ให้กด `Stop Record` ก่อนเพิ่มกล้อง

แท็บ `Live` จะแสดงกล้องทั้งหมดที่ `enabled=true` ตามจำนวนคอลัมน์ใน config:

```properties
grid.columns=2
live.autoStart=true
```

## ดูย้อนหลัง

RTSP ของ Tapo เป็น live stream เป็นหลัก การดูย้อนหลังในโปรเจกต์นี้จึงใช้วิธีบันทึก stream ลงเครื่องก่อน:

1. กด `Record All`
2. ไฟล์จะถูกเก็บที่ `recordings/<camera-id>/`
3. ไปที่แท็บ `Playback`
4. เลือกกล้องและวันที่ แล้วกด `Refresh`
5. เลือกไฟล์เพื่อเล่นย้อนหลัง

กำหนดความยาวไฟล์แต่ละช่วง:

```properties
recording.segmentSeconds=300
```

ค่า `300` คือแบ่งไฟล์ทุก 5 นาที

## หมายเหตุสำคัญ

- ต้องใช้ Camera Account ที่สร้างในแอป Tapo
- ถ้ารหัสผ่านมีอักขระพิเศษ โค้ดจะ encode ให้เมื่อใช้ host/user/password ใน config
- ถ้าใส่ URL เต็มเองผ่าน `camera.N.rtsp` ต้อง encode username/password เองถ้ามีอักขระพิเศษ
- บางรุ่นมีข้อจำกัดจำนวน stream พร้อมกัน ถ้า live/record เปิดพร้อมกันมากเกินไปอาจ connect ไม่ติด
- ถ้า VLCJ หา VLC ไม่เจอ ให้ติดตั้ง VLC 64-bit ใน path มาตรฐาน เช่น `C:\Program Files\VideoLAN\VLC`
