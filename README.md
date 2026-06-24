<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Media Vault OSync v2.1.0

Media Vault OSync is a next-generation secure sandbox application designed for AI-driven media organization and multi-mirror cloud synchronization. It leverages Google's Gemini AI to automatically categorize, tag, and summarize your media assets across multiple storage nodes.

## Core Features

- **🧠 AI-Powered Organization**: Automatically catalog media into categories (Memes, Finance, Work, etc.) using Gemini 1.5 Flash or Pro models.
- **🛡️ 4-Node Redundancy**: Secure your data across a storage matrix including Primary, Backup, Archive, and Disaster Recovery mirrors.
- **⚡ Parallel Deployment**: Multi-threaded synchronization engine for lightning-fast cloud uploads and replication.
- **🌐 Universal Repository Protocol**: Seamlessly consolidate assets and system configurations across all your devices.
- **🎨 Modern Media Previews**: High-resolution previews for images, videos, and generated waveforms for audio files.
- **⚙️ Comprehensive Configuration**: Fine-tune AI personas, storage limits, sync rules, and security preferences from a centralized hub.
- **📁 Advanced Registry Detection**: Automatically detects and labels media sources from WhatsApp, Telegram, Camera, and even external SD Cards.

## Getting Started

### Prerequisites

- [Android Studio Arctic Fox](https://developer.android.com/studio) or newer.
- A valid [Gemini API Key](https://aistudio.google.com/app/apikey).

### Installation

1. **Clone & Open**: Open the project directory in Android Studio.
2. **Environment Setup**: 
   - Update the `.env` file in the root directory.
   - Set `GEMINI_API_KEY=YOUR_KEY_HERE`.
   - *Alternatively*, you can configure the API key directly in the app's **Config** tab.
3. **Build & Run**: Deploy to an emulator or a physical Android device (API 24+).

## System Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose
- **Database**: Room Persistence Library (Schema v2)
- **Networking**: OkHttp3 & Retrofit
- **AI Integration**: Google Generative AI (Gemini)
- **Local Persistence**: SharedPreferences via SettingsManager

---
*© 2024 AI Studio Hub. Secure Sandbox Architecture • AES-256 Encryption*
