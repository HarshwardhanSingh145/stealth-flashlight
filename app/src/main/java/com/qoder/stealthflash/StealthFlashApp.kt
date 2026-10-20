package com.qoder.stealthflash

import android.app.Application
import com.qoder.stealthflash.torch.TorchController
import com.qoder.stealthflash.vault.BiometricAuthenticator
import com.qoder.stealthflash.vault.InstalledAppsRepository
import com.qoder.stealthflash.vault.VaultStore

/**
 * App-scoped singletons. The torch must outlive the ViewModel because the LED has to be
 * released deterministically when the process leaves the foreground, and the vault store owns
 * a lazily-created master key that is expensive to rebuild.
 */
class StealthFlashApp : Application() {

    val torch: TorchController by lazy { TorchController(this) }
    val vaultStore: VaultStore by lazy { VaultStore(this) }
    val installedApps: InstalledAppsRepository by lazy { InstalledAppsRepository(this) }
    val biometrics: BiometricAuthenticator by lazy { BiometricAuthenticator(this) }
}
