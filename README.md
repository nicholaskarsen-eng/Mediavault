<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Media Vault OSync v2.2.0

Media Vault OSync is a next-generation military-grade secure media management suite designed for AI-driven organization and multi-mirror cloud synchronization. It leverages Google's Gemini AI to automatically categorize, tag, and summarize your media assets across a robust global storage matrix.

## Core Features

- **🧠 AI-Powered Intelligence**:
    - **Smart Cataloging**: Automatically categorize media into standard and custom categories using Gemini 1.5 Pro/Flash.
    - **Smart Document OCR**: Real-time Optical Character Recognition indexed for full-vault semantic search.
    - **Dynamic Rule Learning**: AI-driven organization logic that adapts to your manual data patterns.
- **🛡️ Privacy & Data Sovereignty**:
    - **Privacy Metadata Scrubbing**: Automatically strip EXIF, GPS coordinates, and device identifiers from images on import.
    - **Air-Gapped Zones**: Define "Offline-Only" categories that never touch the cloud, keeping sensitive data strictly local.
    - **Zero-Knowledge Encryption**: AES-256-GCM encryption engine for client-side security.
- **🌐 Matrix Synchronization**:
    - **4-Node Redundancy**: Replicate data across Primary, Backup, Archive, and Disaster Recovery mirrors (AWS, Azure, SMB, SD Card).
    - **Trusted Wi-Fi Geofencing**: Restrict synchronization protocols to secure, verified network SSIDs.
    - **Differential Sync**: Intelligent delta-check protocols to optimize bandwidth and transfer speed.
- **⚡ High-Speed Infrastructure**:
    - **Parallel Deployment**: Multi-threaded sync engine with semaphore-based concurrency control.
    - **Universal Repository Protocol**: Cross-device asset consolidation and configuration mirroring.
- **⚙️ Automation & Maintenance**:
    - **Smart Purge Protocol**: Automatically offload redundant local media to the cloud matrix to save device storage.
    - **Vault Health Diagnostics**: Proactive integrity monitoring and corruption detection across the entire storage matrix.
- **📱 High-Tech UX**:
    - **Desktop Companion Hub**: Secure QR-bridge pairing for local sandbox access from your PC.
    - **Modern Media Lightbox**: Immersive, high-performance previews for all media types.

## Getting Started

### Prerequisites

- [Android Studio Arctic Fox](https://developer.android.com/studio) or newer.
- A valid [Gemini API Key](https://aistudio.google.com/app/apikey).

### Installation

1. **Clone & Open**: Open the project directory in Android Studio.
2. **Environment Setup**: 
   - Update the `.env` file in the root directory or configure via the app UI.
   - Set `GEMINI_API_KEY=YOUR_KEY_HERE`.
3. **Build & Run**: Deploy to an emulator or a physical Android device (API 24+).

## System Architecture

- **Language**: Kotlin 2.0+ (Coroutines & Flows)
- **UI Framework**: Jetpack Compose (Modern Material 3)
- **Database**: Room Persistence Library (Schema v5)
- **Security**: AES-256-GCM Encryption & EXIF Interface
- **AI Integration**: Google Generative AI (Gemini Multi-Agent System)
- **Background Jobs**: WorkManager for robust synchronization

---
*© 2024 AI Studio Hub. Secure Sandbox Architecture • Multi-Node Redundancy • AI-Driven Telemetry*
