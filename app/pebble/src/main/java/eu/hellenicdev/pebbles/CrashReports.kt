package eu.hellenicdev.pebbles

import android.content.Context
import io.sentry.IpAddressUtils
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryException
import io.sentry.protocol.User

/**
 * Crash reporting starts only after the person opts in, and stops when they turn it off.
 * Reports are errors only. Anonymous is the default and does not store an IP address.
 * Include IP sends {{auto}} and nothing else that identifies the person.
 */
object CrashReports {
    private val lock = Any()

    fun start(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            if (Sentry.isEnabled()) return
            if (BuildConfig.SENTRY_DSN.isBlank()) return
            SentryAndroid.init(app) { options -> configure(options, app) }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (Sentry.isEnabled()) Sentry.close()
        }
    }

    /** Sends one debug event. Returns false when reporting is off, so nothing is sent. */
    fun sendTestError(): Boolean {
        if (!Sentry.isEnabled()) return false
        Sentry.captureException(IllegalStateException("Pebbles debug test error")) { scope ->
            scope.setTag("source", "debug-test")
        }
        return true
    }

    private fun configure(options: SentryAndroidOptions, context: Context) {
        options.dsn = BuildConfig.SENTRY_DSN
        options.release = "pebble@${BuildConfig.VERSION_NAME}"
        options.environment = if (BuildConfig.DEBUG) "debug" else "release"
        // Leave dataCollection unset. Setting any of its fields ignores sendDefaultPii.
        @Suppress("DEPRECATION")
        options.isSendDefaultPii = false
        options.isEnableAutoSessionTracking = false
        options.tracesSampleRate = 0.0
        options.profilesSampleRate = 0.0
        options.profileSessionSampleRate = 0.0
        options.sessionReplay.sessionSampleRate = 0.0
        options.sessionReplay.onErrorSampleRate = 0.0
        options.sessionReplay.setMaskAllText(true)
        options.sessionReplay.setMaskAllImages(true)
        options.logs.isEnabled = false
        options.isAttachScreenshot = false
        options.isAttachViewHierarchy = false
        options.isEnableUserInteractionBreadcrumbs = false
        options.isEnableUserInteractionTracing = false
        options.isEnableSystemEventBreadcrumbsExtras = false
        options.isTombstoneEnabled = false
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
            if (!CrashReportScrubber.breadcrumbAllowed(breadcrumb.category, breadcrumb.type)) {
                null
            } else {
                breadcrumb.message = breadcrumb.message?.let(CrashReportScrubber::redact)
                CrashReportScrubber.scrubMap(breadcrumb.data)
                breadcrumb
            }
        }
        options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
            val store = SettingsStore(context)
            if (!store.crashReportsEnabled()) {
                null
            } else {
                prepareCrashReport(event, store.crashReportsIncludeIp())
            }
        }
        options.beforeSendTransaction = SentryOptions.BeforeSendTransactionCallback { _, _ -> null }
    }

    /**
     * Scrubs a report and sets the IP mode. Anonymous sends no IP marker and tells Relay
     * `infer_ip: never`, so the connection address is not stored while the project setting
     * stays off. Include IP sends `{{auto}}` and still drops every other identifier.
     */
    internal fun prepareCrashReport(event: io.sentry.SentryEvent, includeIp: Boolean): io.sentry.SentryEvent? {
        return try {
            event.request = null
            event.serverName = null
            event.contexts.remove("user")
            event.contexts.device?.let { device ->
                device.id = null
                device.name = null
            }
            scrubExtras(event.extras)
            scrubTags(event)
            event.message?.let { message ->
                message.message = message.message?.let(CrashReportScrubber::redact)
                message.formatted = message.formatted?.let(CrashReportScrubber::redact)
                message.params = message.params?.map(CrashReportScrubber::redact)
            }
            event.exceptions?.forEach { exception -> redactException(exception) }
            val kept = event.breadcrumbs?.mapNotNull { breadcrumb ->
                if (!CrashReportScrubber.breadcrumbAllowed(breadcrumb.category, breadcrumb.type)) {
                    null
                } else {
                    breadcrumb.message = breadcrumb.message?.let(CrashReportScrubber::redact)
                    CrashReportScrubber.scrubMap(breadcrumb.data)
                    breadcrumb
                }
            }
            event.breadcrumbs = kept
            applyIpChoice(event, includeIp)
            event
        } catch (_: Throwable) {
            null
        }
    }

    private fun applyIpChoice(event: io.sentry.SentryEvent, includeIp: Boolean) {
        val user = User()
        user.ipAddress = if (includeIp) IpAddressUtils.DEFAULT_IP_ADDRESS else null
        event.user = user
        val current = event.sdk
        val sdk = SdkVersion(
            current?.name ?: "sentry.java.android",
            current?.version ?: "8.65.0",
        )
        sdk.unknown = mapOf(
            "settings" to mapOf("infer_ip" to if (includeIp) "auto" else "never"),
        )
        event.sdk = sdk
    }

    private fun redactException(exception: SentryException) {
        exception.value = exception.value?.let(CrashReportScrubber::redact)
    }

    private fun scrubExtras(extras: MutableMap<String, Any>?) {
        CrashReportScrubber.scrubMap(extras)
    }

    private fun scrubTags(event: io.sentry.SentryEvent) {
        val tags = event.tags ?: return
        for (key in tags.keys.toList()) {
            val value = tags[key] ?: continue
            if (CrashReportScrubber.keyIsSensitive(key)) {
                event.removeTag(key)
            } else {
                val redacted = CrashReportScrubber.redact(value)
                if (redacted != value) event.setTag(key, redacted)
            }
        }
    }
}
