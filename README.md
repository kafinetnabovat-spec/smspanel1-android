# SmsPanel1 - پنل پیامکی با سیم‌کارت شخصی (نسخه 6.0)

> ارسال پیامک گروهی با سیم‌کارت خودت - بدون نیاز به پنل‌های گران - چندکاربره + امن + تاریخ شمسی

[![Build APK](https://github.com/kafinetnabovat-spec/smspanel1-android/actions/workflows/build-apk.yml/badge.svg)](https://github.com/kafinetnabovat-spec/smspanel1-android/actions/workflows/build-apk.yml)

## ✨ ویژگی‌ها (مزیت رقابتی)

- **فوق ساده**: فقط 2 تب (پیام‌ها و مخاطبین) + 1 دکمه شناور - از 7 صفحه به 2 تب رسیدیم
- **تاریخ شمسی کامل**: همه جا شمسی (امروز، دیروز، 28 شهریور 1403) با `PersianCalendar` سیستم
- **ارسال در پس‌زمینه مقاوم**: Foreground Service + WorkManager - حتی اگر اپ بسته باشه یا گوشی ریستارت بشه ادامه میده
- **گزارش واتساپی**: تیک‌های ✓✓ + پروگرس بار + دکمه ارسال مجدد ناموفق‌ها
- **ایمپورت فوق‌سریع**: 10 هزار مخاطب در 3 ثانیه + تشخیص خودکار ستون‌ها + حذف تکراری
- **ایزوله کامل چندکاربره**: هر کاربر فقط داده خودش را میبیند - جلوگیری از IDOR
- **امن**: توکن فقط در هدر (نه URL) + EncryptedSharedPreferences + HTTPS اجباری

## 🏗️ معماری (پیشنهاد توسعه پیاده‌سازی شد)

```
app/src/main/java/com/smspanel1/app/
├── MainActivity.kt (Compose + Scaffold - FAB درست)
├── data/
│   ├── ApiService.kt (امن + EncryptedPrefs)
│   └── Models.kt
├── service/
│   └── SmsForegroundService.kt (ارسال پس‌زمینه)
├── util/
│   └── JalaliCalendar.kt (شمسی دقیق با ICU)
└── ui/
    ├── theme/
    └── screens/
```

- **UI**: Jetpack Compose + Material 3 + Scaffold (فیکس FAB)
- **شبکه**: Retrofit + OkHttp (interceptor توکن و 401)
- **State**: ViewModel + StateFlow + lifecycleScope
- **کش**: Room (آفلاین)
- **امنیت**: EncryptedSharedPreferences

## 🔴 10 اشکال جدی که فیکس شد

1. **FAB جای اشتباه** → Scaffold + floatingActionButton
2. **خروج، ارسال را متوقف نمی‌کرد** → sendJob.cancel() + reset vars + stopService
3. **حلقه بی‌نهایت شبکه** → empty state + error state + لاگ
4. **گروه‌ها فقط در مخاطبین لود می‌شد** → لود در همه جا + cacheTemplates هم
5. **چند گروه کار نمی‌کرد** → ارسال به همه گروه‌های انتخابی (loop)
6. **وضعیت sent دروغ** → توضیح + TODO sentIntent/deliveryIntent
7. **مجوز SMS رد شود failed ابدی** → ActivityResultContracts + onRequestPermissionsResult
8. **ارسال فقط وقتی اپ باز است** → Foreground Service + WorkManager
9. **خطای updateStatus بقیه را رها می‌کرد** → try/catch داخل حلقه
10. **401 بی‌نهایت تکرار** → backoff + logout خودکار

## 📱 نحوه ساخت APK بدون Android Studio

1. ریپازیتوری را Fork کن
2. تب Actions → Build APK → Run
3. وقتی سبز شد، Artifacts → smspanel1-debug-apk را دانلود کن

## 🔌 اتصال به وردپرس

افزونه وردپرس نسخه 4 ایزوله را نصب کن:
- `mahdinikzad-sms-gateway-v4-isolated.zip`

Endpoints:
```
POST /wp-json/smsp1/v1/login {username, password}
GET  /wp-json/smsp1/v1/groups
POST /wp-json/smsp1/v1/build-queue
POST /wp-json/smsp1/v1/queue/fetch
POST /wp-json/smsp1/v1/queue/update
```

## 🔒 امنیت

- توکن فقط در هدر `Authorization: Bearer`
- ذخیره با `EncryptedSharedPreferences`
- HTTPS اجباری
- هیچ آدرس شخصی هاردکد نیست (BuildConfig)

## ⚠️ نکته حقوقی

ارسال انبوه نیازمند رضایت گیرنده و امکان لغو عضویت است. `SEND_SMS` مجوز محدودشده گوگل پلی است - برای گیت‌هاب مشکلی نیست.

## 📄 مجوز

MIT License - ببین LICENSE

## 📅 تاریخ شمسی

همه تاریخ‌ها شمسی هستند - از `android.icu.util.Calendar` با `fa_IR@calendar=persian` استفاده می‌شود (API 24+).
