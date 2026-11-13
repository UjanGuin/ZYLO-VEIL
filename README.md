# ZYLO VEIL (Android)

> **Advanced Cryptographic Steganography with Multi-Carrier Shamir Secret Sharing, Anti-Forensics, and Auto JPG-to-PNG Carrier Conversion**

ZYLO VEIL for Android is a native Kotlin & Jetpack Compose implementation of the research-grade cryptographic steganography system originally created by Swapnanil Guin. It treats carrier images as a distributed, adaptive cryptographic substrate: payloads are compressed, AES-256-GCM encrypted, and split using Shamir's Secret Sharing (K-of-N) across multiple carrier images.

---

## Key Features

### 1. Auto JPG to PNG Lossless Conversion (NEW)
When uploading or selecting carrier images (JPG, JPEG, WEBP), ZYLO VEIL automatically decodes them into uncompressed ARGB_8888 bitmaps and converts them to lossless PNGs. This guarantees that lossy compression artifacts never corrupt or destroy sensitive LSB bit-planes.

### 2. Multi-Carrier Split Payload (True Shamir K-of-N)
* Payload is encrypted first, then split using **Shamir's Secret Sharing (GF(256))**.
* Each carrier image embeds **only one share**.
* A single image is mathematically and cryptographically useless without collecting at least **K** valid carrier shares.
* Supports **K = 1** for classic single-image steganography.

### 3. Adaptive Noise & Semantic-Aware Embedding
* Computes embedding capacity dynamically per pixel based on luminance cross-difference edge detection, texture variance, and perceptual masking heuristics.
* Smooth regions receive fewer bits; textured regions receive more.

### 4. Anti-Forensic Stego Poisoning
* Injects camera-sensor-like Gaussian noise into unused color channels to disrupt statistical steganalysis tools (chi-squared and sample pair analysis).

### 5. Stateless Zero-Header Mode
* Strips all magic bytes and explicit headers.
* Extraction is driven by deterministic, keyed implicit markers derived from the passphrase, carrier fingerprint, and algorithm version.

### 6. Bit-Plane Migration & Temporal Fragility
* Embedding bit-planes migrate deterministically across 6-hour time epochs.
* Delayed exfiltration copies decay and fail to decrypt.

### 7. Real Self-Destruct
* Configurable maximum decryption attempt counter.
* Exceeding the threshold triggers permanent, irreversible poisoning of the carrier image's embedded bits.

### 8. Stego Analyzer & Visual Bit-Plane Inspector
* Interactive inspector that calculates Red, Green, and Blue LSB Shannon entropy.
* Visualizes the raw LSB bit-plane as a high-contrast binary map to detect artificial noise and anomalies.
* Verify-only mode checks carrier decoding, authenticated metadata, format/KDF compatibility,
  capacity, and Shamir share-set membership without extracting secret plaintext or mutating carriers.
* Benchmark & Steganalysis Reports provide per-carrier and aggregate capacity, repeated verification
  reliability, LSB entropy, and detectability indicators. Changed-channel ratio and pixel error are
  explicitly reported as unavailable unless an original carrier is supplied; indicators do not claim
  immunity from specialized steganalysis.

---

## Technical Architecture

* **Runtime:** Android 14+ (API 34/36)
* **UI Toolkit:** Jetpack Compose + Material Design 3 (Glassmorphism dark theme)
* **Cryptography:** AES-256-GCM (javax.crypto), PBKDF2WithHmacSHA512 KDF, SHA-256 / SHA-512
* **Secret Sharing:** Pure Galois Field GF(256) Shamir Polynomial Thresholding
* **Compression:** Deflate / XZ high-compression stream format

### Format compatibility

New standard and stateless carriers use format version 4. Standard shares authenticate
their serialized header as AES-GCM associated data, and the header records the KDF
identifier and iteration count. Existing version 3 standard carriers remain readable
using the legacy unauthenticated-header path; newly generated carriers should be used
when header tamper detection is required.

---

## Usage

1. **Embed (Veil):**
   * Enter secret text or select any file.
   * Add carrier images (JPG or PNG — JPGs are automatically converted to lossless PNG).
   * Enter a passphrase (min 8 characters).
   * Configure threshold **K** and adversarial flags.
   * Tap **Embed & Protect (Veil)** to generate stego PNG files.

2. **Extract (Unveil):**
   * Select the stego carrier PNG images.
   * Enter the passphrase.
   * Tap **Extract Secret (Unveil)** to reconstruct and view/export the secret.

3. **Inspect:**
   * Pick any image to calculate entropy and visualize the LSB plane.
