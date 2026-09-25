=== MahdiNikzad SMS Gateway ===
Requires PHP: 8.0
Requires at least: 6.0
Version: 4.7.0

بک‌اند اپ SmsPanel: مدیریت لایسنس کاربران، گروه‌بندی، مخاطبین و صف ارسال پیامک.

== نصب ==
1. پوشه mahdinikzad-sms-gateway را zip کن و از وردپرس → افزونه‌ها → افزودن → آپلود افزونه، نصبش کن.
   (یا مستقیم در wp-content/plugins/ آپلود کن)
2. فعالش کن.
3. برو به منوی «پنل پیامکی» در سایدبار ادمین وردپرس.
4. برای هر کاربری که باید از اپ استفاده کند: تیک «لایسنس فعال باشد» را بزن، تاریخ انقضا (اختیاری) را ست کن، ذخیره کن.
   کاربر باید از قبل به‌عنوان یک کاربر عادی وردپرس ساخته شده باشد (وردپرس → کاربران → افزودن کاربر).

== نکته امنیتی مهم ==
- این افزونه اجباراً روی HTTPS کار می‌کند (توکن از طریق هدر Authorization فرستاده می‌شود).
  مطمئن شو سایتت گواهی SSL معتبر دارد.
- توکن API هر بار با ورود دوباره از حالت قبلی باطل می‌شود؛ اگر کاربری را غیرفعال کنی توکنش بلافاصله باطل می‌شود.

== API (namespace: smsp1/v1) ==
POST /login              {username, password}                → {user_id, api_token}
GET  /groups              (Authorization: Bearer token)        → [{id, name}]
POST /groups               {name}
GET  /contacts             ?group_id=N
POST /contacts              {group_id, contacts:[{name,mobile}]}  یا  {group_id, name, mobile}
GET  /queue                 → لیست کمپین‌ها
GET  /queue/counts          → {pending, sent}
POST /build-queue            {group_id, manual_body}          → {campaign_id, queued}
POST /queue/fetch             {limit}                          → [{id, receiver, body}]
POST /queue/update             {id, status: sent|failed}
POST /crash-report            {exception_class, message, stack_trace, ...}  → گزارش خودکار کرش/خطای اپ
