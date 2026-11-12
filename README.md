# خريطة الأمير (Al Ameer Map)

Native Android app (Kotlin, Jetpack Compose, OSMDroid satellite map, Supabase, Room) for mapping
client network devices (MikroTik, NanoStation, PowerBeam, routers...).

## 1. Supabase
1. Create a project at https://supabase.com
2. SQL Editor -> paste and run `supabase/schema.sql`
3. Authentication -> Providers -> Email: enabled
   (Optional while testing: turn off "Confirm email")
4. Authentication -> URL Configuration -> Redirect URLs: add `alameer://auth`
5. Project Settings -> API: copy the Project URL and the `anon` public key

## 2. Secrets
| Name | Value |
|---|---|
| `SUPABASE_URL` | https://xxxx.supabase.co |
| `SUPABASE_ANON_KEY` | anon public key |
| `ENCRYPTION_PEPPER` | any long random string (32+ chars). **Never change it later**, or stored device passwords can no longer be decrypted. |

* GitHub: repo -> Settings -> Secrets and variables -> Actions -> New repository secret
* Local build: put the same three lines in `local.properties` (`NAME=value`)

## 3. Build
Push to `main`; Actions -> "Build Android APK" -> download the `AlAmeerMap-debug-apk` artifact.
Local: `gradle :app:assembleDebug` (or generate a wrapper once with `gradle wrapper`).
