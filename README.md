# Price List Staff App

A modern Android application designed for staff to manage and view product price lists, calculate total costs with taxes and discounts, and stay updated with real-time stock changes.

## Features

### Product Catalog
- **Smart Search:** Quickly find items by name or code.
- **Detailed Item Cards:** View product names, codes, units, and images at a glance.
- **Image Support:** High-quality product images with zoom functionality for better inspection.

### Pricing & Calculations
- **Automatic Price Breakdown:** Calculates total price including GST and applying discounts automatically.
- **Unit/Bulk Calculator:** Built-in tool to calculate individual unit prices from bulk amounts or vice versa.
- **Precise Formatting:** All amounts are formatted to two decimal places for accuracy.

### Content Management
- **Cloud Sync:** Seamlessly synchronize product data from the cloud to the local database.
- **Brochure Downloads:** Access and download PDF brochures directly within the app.
- **In-App Updates:** Check for and install the latest app versions via GitHub releases.

### Notifications
- **Stock Alerts:** Receive visual indicators and notifications for new stock arrivals or changes.

## Technical Stack

- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) for a modern, reactive user interface.
- **Design System:** [Material Design 3](https://m3.material.io/) for a consistent and clean look.
- **Architecture:** MVVM (Model-View-ViewModel) for clean separation of concerns.
- **Local Database:** [Room](https://developer.android.com/training/data-storage/room) for fast, offline access to product data.
- **Cloud Integration:** [Firebase](https://firebase.google.com/) for data synchronization, storage, and analytics.
- **Image Loading:** [Coil](https://coil-kt.github.io/coil/) for efficient image fetching and caching.
- **Networking:** Kotlin Coroutines for asynchronous operations.

## Project Structure

- `app/src/main/java/com/example/pricelist/ui`: Contains all Composable UI screens and themes.
- `app/src/main/java/com/example/pricelist/viewmodel`: Logic for handling UI state and data flow.
- `app/src/main/java/com/example/pricelist/data`: Room database entities, DAOs, and repository.
- `app/src/main/java/com/example/pricelist/util`: Utility classes for updates, notifications, and analytics.
