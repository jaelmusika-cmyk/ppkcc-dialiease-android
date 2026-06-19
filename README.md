# DialiEase: Patient Companion Mobile Application

This repository contains the native Android application designed for **Patients** within the DialiEase Dialysis Monitoring and Session Management Ecosystem. The application functions alongside the centralized DialiEase web platform, communicating directly with its underlying core APIs to ensure real-time clinical visibility for patients.

---

## 📱 Key Features

* **Patient Registration & Onboarding:** Collects core contact details, medical histories, and maps patient identifiers securely to user profiles.
* **Real-Time Vitals Tracking:** Pulls verified, intra-dialytic vitals data logged by clinical staff, displaying trends over time right inside the application UI.
* **Dynamic Schedule Request Desk:** Allows patients to view active dialysis schedules and submit structured requests for session swaps or informed absences.

---

## ⚙️ Local Backend Connection Configuration

To facilitate local evaluation and testing without active cloud hosting dependencies, route the mobile traffic using the following configuration steps:

### 1. Identify the Workstation Local IP
Because the Android emulator operates inside an isolated virtual network layer, setting the API destination URL string to localhost or 127.0.0.1 will cause a connection failure. It must point to the development machine's local network IP address:
1. Open a command line interface (Terminal or CMD) on the workstation.
2. Run the network configuration command:
   * Windows: ipconfig (Locate the IPv4 Address under the active Wi-Fi or Ethernet adapter—e.g., 192.168.1.X).
   * macOS/Linux: ifconfig

### 2. Configure Networking Constants
Open the configuration file "utils/Constants.java" and update the base URL string, ensuring it points to the correct path of your local web project's /api/ folder:

package com.example.dialiease2.utils;

public class Constants {
    public static final String BASE_URL = "http://192.168.1.X/dialiease/api/";
}

### 3. Cleartext Network Security Setup
Android blocks unencrypted HTTP traffic by default. Since local testing environments utilize http:// rather than https://, cleartext access must be explicitly permitted for local network testing:

1. Create a network security configuration file at "res/xml/network_security_config.xml":
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.1.X</domain>
    </domain-config>
</network-security-config>

2. Open the "AndroidManifest.xml" file and link this network configuration inside the <application> tag:
<application
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:networkSecurityConfig="@xml/network_security_config"
    android:theme="@style/Theme.Dialiease2">
    ...
</application>

---

## 🛠️ Build and Execution Requirements

* **Language:** Kotlin
* **Development Environment:** Android Studio (Ladybug or newer)
* **Minimum Android SDK:** API Level 26 (Android 8.0 Oreo)
* **Target Android SDK:** API Level 34
* **Core Networking Engine:** OkHttp3 Client

---

## 🏎️ Running the Application

1. Open Android Studio and select Open Project, then choose the root project directory.
2. Let the Gradle lifecycle sync and resolve all internal dependencies.
3. Launch a virtual device via the Device Manager (Pixel emulator running API 31+ recommended) or connect a physical Android device with USB Debugging enabled.
4. Click the Run 'app' button (the green play arrow) in Android Studio's top toolbar to build and deploy the application.

### Testing Account
Log into the application using the pre-seeded patient account from the database import:
* **Email:** john.doe@email.com
* **Password:** password123
