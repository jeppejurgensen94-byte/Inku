package com.inku.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import java.security.MessageDigest
import java.util.Locale

internal enum class InkuRuntimeStatus {
    Available,
    Downloading,
    Installing,
    Installed,
    UpdateAvailable,
    Loaded,
    Disabled,
    IncompatibleApi,
    MissingHostDependency,
    SignatureConflict,
    FailedToLoad
}

internal data class InkuRuntimeSource(
    val packageName: String,
    val extensionName: String,
    val kind: InkuRepositoryKind,
    val id: Long,
    val name: String,
    val lang: String,
    val source: Any
) {
    val stableId: String = "${kind.name}:$packageName:$id"
}

internal data class InkuRuntimeExtension(
    val packageName: String,
    val displayName: String,
    val kind: InkuRepositoryKind,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double?,
    val metadataClass: String,
    val nsfw: Boolean,
    val signatureSha256: String,
    val repositoryName: String,
    val status: InkuRuntimeStatus,
    val statusText: String,
    val error: String = "",
    val sources: List<InkuRuntimeSource> = emptyList()
)

internal data class InkuRuntimeSnapshot(
    val extensions: List<InkuRuntimeExtension>,
    val loadedAt: Long = System.currentTimeMillis()
) {
    fun extensionFor(packageName: String): InkuRuntimeExtension? =
        extensions.firstOrNull { it.packageName == packageName }

    fun sources(kind: InkuRepositoryKind): List<InkuRuntimeSource> =
        extensions.flatMap { it.sources }.filter { it.kind == kind }
}

internal object InkuExtensionRuntime {
    private const val TAG = "InkuExtensionLoader"
    private const val MANGA_FEATURE = "tachiyomi.extension"
    private const val ANIME_FEATURE = "tachiyomi.animeextension"
    private const val MANGA_CLASS = "tachiyomi.extension.class"
    private const val MANGA_FACTORY = "tachiyomi.extension.factory"
    private const val MANGA_NSFW = "tachiyomi.extension.nsfw"
    private const val ANIME_CLASS = "tachiyomi.animeextension.class"
    private const val ANIME_FACTORY = "tachiyomi.animeextension.factory"
    private const val ANIME_NSFW = "tachiyomi.animeextension.nsfw"

    @Volatile
    private var snapshot: InkuRuntimeSnapshot = InkuRuntimeSnapshot(emptyList(), 0L)

    private val packageFlags: Int
        @Suppress("DEPRECATION")
        get() = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                0
            }

    fun initialize(context: Context) {
        val app = context.applicationContext as Application
        Injekt.addSingleton(Application::class.java, app)
        Injekt.addSingleton(Context::class.java, app)
        Injekt.addSingleton(NetworkHelper::class.java, NetworkHelper(app))
        Injekt.addSingleton(
            Json::class.java,
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            }
        )
    }

    fun currentSnapshot(): InkuRuntimeSnapshot = snapshot

    suspend fun reload(context: Context): InkuRuntimeSnapshot = withContext(Dispatchers.IO) {
        initialize(context)
        val loaded = discoverPackages(context).map { loadPackage(context, it) }
        InkuRuntimeSnapshot(loaded).also { snapshot = it }
    }

    private fun discoverPackages(context: Context): List<PackageInfo> {
        val manager = context.packageManager
        val packages = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                manager.getInstalledPackages(PackageManager.PackageInfoFlags.of(packageFlags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                manager.getInstalledPackages(packageFlags)
            }
        }.getOrElse { error ->
            Log.w(TAG, "Package discovery failed", error)
            emptyList()
        }
        return packages.filter { info ->
            val features = info.reqFeatures.orEmpty().mapNotNull { it.name }.toSet()
            features.contains(MANGA_FEATURE) || features.contains(ANIME_FEATURE) ||
                info.applicationInfo?.metaData?.containsKey(MANGA_CLASS) == true ||
                info.applicationInfo?.metaData?.containsKey(ANIME_CLASS) == true
        }.also { discovered ->
            Log.i(TAG, "Discovered ${discovered.size} installed extension package(s)")
        }
    }

    private fun loadPackage(context: Context, info: PackageInfo): InkuRuntimeExtension {
        val appInfo = info.applicationInfo
        val meta = appInfo?.metaData
        val features = info.reqFeatures.orEmpty().mapNotNull { it.name }.toSet()
        val kind = when {
            features.contains(ANIME_FEATURE) || meta?.containsKey(ANIME_CLASS) == true -> InkuRepositoryKind.Anime
            features.contains(MANGA_FEATURE) || meta?.containsKey(MANGA_CLASS) == true -> InkuRepositoryKind.Manga
            else -> InkuRepositoryKind.Manga
        }
        val packageName = info.packageName.orEmpty()
        val label = runCatching { context.packageManager.getApplicationLabel(appInfo!!).toString() }
            .getOrDefault(packageName)
        val displayName = label
            .removePrefix("Tachiyomi:")
            .removePrefix("Aniyomi:")
            .trim()
            .ifBlank { label }
        val versionName = info.versionName.orEmpty()
        val versionCode = info.longVersionCodeCompat()
        val libVersion = versionName.substringBeforeLast('.', missingDelimiterValue = versionName).toDoubleOrNull()
        val classMetadata = when (kind) {
            InkuRepositoryKind.Anime -> meta?.getString(ANIME_CLASS) ?: meta?.getString(ANIME_FACTORY)
            InkuRepositoryKind.Manga -> meta?.getString(MANGA_CLASS) ?: meta?.getString(MANGA_FACTORY)
        }.orEmpty()
        Log.i(TAG, "Metadata read for $packageName kind=$kind class=${classMetadata.ifBlank { "<missing>" }}")
        val isNsfw = when (kind) {
            InkuRepositoryKind.Anime -> meta?.getInt(ANIME_NSFW, 0) == 1
            InkuRepositoryKind.Manga -> meta?.getInt(MANGA_NSFW, 0) == 1
        }
        val signature = signatureHashes(info).lastOrNull().orEmpty()
        val repository = ExtensionRepositoryStore.cachedExtensions(context, kind)
            .firstOrNull { it.packageName == packageName }

        fun failed(status: InkuRuntimeStatus, message: String): InkuRuntimeExtension =
            InkuRuntimeExtension(
                packageName = packageName,
                displayName = displayName,
                kind = kind,
                versionName = versionName,
                versionCode = versionCode,
                libVersion = libVersion,
                metadataClass = classMetadata,
                nsfw = isNsfw,
                signatureSha256 = signature,
                repositoryName = repository?.repositoryName.orEmpty(),
                status = status,
                statusText = status.humanText(),
                error = message
            )

        if (!isSupportedLibVersion(kind, libVersion)) {
            return failed(
                InkuRuntimeStatus.IncompatibleApi,
                "Extension lib version ${libVersion ?: "unknown"} is outside Inku's supported ${supportedRange(kind)} range."
            )
        }
        if (classMetadata.isBlank()) {
            return failed(InkuRuntimeStatus.FailedToLoad, "Manifest metadata did not declare an extension class.")
        }
        if (appInfo?.sourceDir.isNullOrBlank()) {
            return failed(InkuRuntimeStatus.FailedToLoad, "Android did not expose an APK source path for this package.")
        }

        return runCatching {
            val classLoader = InkuChildFirstPathClassLoader(appInfo!!.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
            Log.i(TAG, "Class loader created for $packageName")
            val sources = classMetadata.split(';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .flatMap { className ->
                    instantiateDeclaredSource(classLoader, packageName, className, kind)
                }
                .onEach { source ->
                    InkuSourcePreferenceBridge.bootstrap(context, source)
                }
            if (sources.isEmpty()) {
                failed(InkuRuntimeStatus.FailedToLoad, "The declared extension class did not expose any sources.")
            } else {
                InkuRuntimeExtension(
                    packageName = packageName,
                    displayName = displayName,
                    kind = kind,
                    versionName = versionName,
                    versionCode = versionCode,
                    libVersion = libVersion,
                    metadataClass = classMetadata,
                    nsfw = isNsfw,
                    signatureSha256 = signature,
                    repositoryName = repository?.repositoryName.orEmpty(),
                    status = InkuRuntimeStatus.Loaded,
                    statusText = "Installed - Ready",
                    sources = sources.map { source ->
                        when (source) {
                            is Source -> InkuRuntimeSource(
                                packageName = packageName,
                                extensionName = displayName,
                                kind = InkuRepositoryKind.Manga,
                                id = source.id,
                                name = source.name,
                                lang = source.lang,
                                source = source
                            )
                            is AnimeSource -> InkuRuntimeSource(
                                packageName = packageName,
                                extensionName = displayName,
                                kind = InkuRepositoryKind.Anime,
                                id = source.id,
                                name = source.name,
                                lang = source.lang,
                                source = source
                            )
                            else -> error("Unknown source instance ${source.javaClass.name}")
                        }
                    }
                )
            }
        }.getOrElse { error ->
            val status = when (error) {
                is NoClassDefFoundError, is ClassNotFoundException -> InkuRuntimeStatus.MissingHostDependency
                is LinkageError -> InkuRuntimeStatus.IncompatibleApi
                else -> InkuRuntimeStatus.FailedToLoad
            }
            Log.w(TAG, "Extension load failed for $packageName", error)
            failed(status, error.message?.take(260) ?: error.javaClass.name)
        }
    }

    private fun instantiateDeclaredSource(
        classLoader: ClassLoader,
        packageName: String,
        rawClassName: String,
        kind: InkuRepositoryKind
    ): List<Any> {
        val className = if (rawClassName.startsWith(".")) packageName + rawClassName else rawClassName
        Log.i(TAG, "Instantiating declared source class $className")
        val instance = Class.forName(className, false, classLoader)
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance()
        return when (kind) {
            InkuRepositoryKind.Manga -> when (instance) {
                is Source -> listOf(instance)
                is SourceFactory -> instance.createSources()
                else -> error("Declared class $className is not a Tachiyomi Source or SourceFactory.")
            }
            InkuRepositoryKind.Anime -> when (instance) {
                is AnimeSource -> listOf(instance)
                is AnimeSourceFactory -> instance.createSources()
                else -> error("Declared class $className is not an Aniyomi AnimeSource or AnimeSourceFactory.")
            }
        }
    }

    private fun isSupportedLibVersion(kind: InkuRepositoryKind, version: Double?): Boolean =
        when (kind) {
            InkuRepositoryKind.Manga -> version != null && version >= 1.4 && version <= 1.6
            InkuRepositoryKind.Anime -> version != null && version >= 12.0 && version <= 16.0
        }

    private fun supportedRange(kind: InkuRepositoryKind): String =
        if (kind == InkuRepositoryKind.Anime) "12-16" else "1.4-1.6"

    private fun signatureHashes(info: PackageInfo): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptyList()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString(":") { "%02X".format(Locale.ROOT, it) }
        }
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }

    private fun InkuRuntimeStatus.humanText(): String =
        when (this) {
            InkuRuntimeStatus.Available -> "Available"
            InkuRuntimeStatus.Downloading -> "Downloading"
            InkuRuntimeStatus.Installing -> "Installing"
            InkuRuntimeStatus.Installed -> "Installed"
            InkuRuntimeStatus.UpdateAvailable -> "Update available"
            InkuRuntimeStatus.Loaded -> "Installed - Ready"
            InkuRuntimeStatus.Disabled -> "Disabled"
            InkuRuntimeStatus.IncompatibleApi -> "Installed - incompatible API"
            InkuRuntimeStatus.MissingHostDependency -> "Installed - missing host dependency"
            InkuRuntimeStatus.SignatureConflict -> "Signature conflict"
            InkuRuntimeStatus.FailedToLoad -> "Failed to load"
        }
}

private class InkuChildFirstPathClassLoader(
    dexPath: String,
    librarySearchPath: String?,
    parent: ClassLoader
) : PathClassLoader(dexPath, librarySearchPath, parent) {
    private val parentFirstPrefixes = listOf(
        "android.",
        "androidx.",
        "com.inku.",
        "eu.kanade.tachiyomi.",
        "kotlin.",
        "kotlinx.",
        "okhttp3.",
        "okio.",
        "org.jsoup.",
        "rx.",
        "uy.kohesive.injekt."
    )

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(this) {
            findLoadedClass(name)?.let { return it }
            if (parentFirstPrefixes.any { name.startsWith(it) }) {
                return super.loadClass(name, resolve)
            }
            return try {
                findClass(name).also { if (resolve) resolveClass(it) }
            } catch (_: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
    }
}
