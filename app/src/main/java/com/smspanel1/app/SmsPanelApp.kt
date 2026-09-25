// SmsPanelApp.kt — کلاس Application اپ «کبوتر»
// تنها کارش فعلاً نصب CrashReporter در اولین لحظه‌ی عمر پروسه است تا حتی
// کرش‌های خیلی زودهنگام (قبل از رسیدن به MainActivity) هم گزارش شوند.
// ثبت‌شده در AndroidManifest با android:name=".SmsPanelApp"

package com.smspanel1.app

import android.app.Application

class SmsPanelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
