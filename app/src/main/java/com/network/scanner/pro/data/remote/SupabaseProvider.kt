package com.network.scanner.pro.data.remote

import com.network.scanner.pro.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the single Supabase client. URL and anon key come from BuildConfig
 * (environment variables / local.properties, see app/build.gradle.kts).
 */
@Singleton
class SupabaseProvider @Inject constructor() {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            // Be tolerant with extra server columns (created_at, updated_at).
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            )
            install(Auth)
            install(Postgrest)
        }
    }

    /** False while the placeholder URL/key from build.gradle.kts are still in use. */
    val isConfigured: Boolean
        get() = !BuildConfig.SUPABASE_URL.contains("YOUR-PROJECT") &&
            BuildConfig.SUPABASE_ANON_KEY != "YOUR-ANON-KEY"
}
