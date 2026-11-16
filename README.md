# NetGuard Pro (com.network.scanner.pro)

تطبيق أندرويد بوحدتين: **ماسح الشبكة والأمان** + **متتبّع التركيبات (خريطة الفنيين)**.
Kotlin • Jetpack Compose • Hilt • Room + SQLCipher • Supabase • OSMDroid • WorkManager.

## 1) إعداد Supabase (مرة واحدة)
1. أنشئ مشروعاً على supabase.com.
2. افتح **SQL Editor** والصق محتوى `supabase_schema.sql` ثم **Run**.
3. من **Project Settings > API** انسخ `Project URL` و `anon public key` (لا تستخدم `service_role` أبداً).

## 2) البناء على GitHub (بدون Android Studio)
1. ارفع كل الملفات إلى مستودع جديد (مع المجلد المخفي `.github`).
2. في المستودع: **Settings > Secrets and variables > Actions > New repository secret**
   - `SUPABASE_URL` = رابط المشروع
   - `SUPABASE_ANON_KEY` = مفتاح anon
3. تبويب **Actions > Build Debug APK** (يعمل تلقائياً عند push أو يدوياً Run workflow).
4. نزّل **NetGuardPro-debug-apk** من قسم Artifacts وثبّت `app-debug.apk`.

## 3) البناء محلياً
ضع في `local.properties`: `SUPABASE_URL=...` و `SUPABASE_ANON_KEY=...` ثم:
`gradle :app:assembleDebug` (Gradle 8.9 + JDK 17).

## 4) تغيير الأيقونة
استبدل `res/drawable/ic_launcher_foreground.xml` (أو استخدم Android Studio > New > Image Asset).

## ملاحظات
- العربية تعمل تلقائياً (RTL) عند لغة جهاز عربية؛ الإنجليزية افتراضية.
- أندرويد 10+ يمنع قراءة جدول ARP، لذلك عناوين MAC للأجهزة الأخرى تظهر غالباً على أجهزة Root فقط.
- الحظر/الإيقاف يعمل عبر VPN محلي على هذا الهاتف فقط ولا يفصل الجهاز عن الراوتر.
