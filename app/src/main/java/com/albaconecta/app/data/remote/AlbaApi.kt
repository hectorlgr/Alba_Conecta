package com.albaconecta.app.data.remote

import com.albaconecta.app.BuildConfig
import com.albaconecta.app.data.local.SessionDataStore
import com.albaconecta.app.data.local.SesionLocal
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicReference

/**
 * Contrato HTTP con el backend. La baseUrl sale de BuildConfig.API_BASE_URL
 * (debug: gateway de desarrollo en http://127.0.0.1:8080/, con adb reverse).
 */
interface AlbaApi {

    /**
     * Crea una solicitud de colaboración. `Response<T>` (en vez de devolver el DTO directo)
     * para poder leer el cuerpo de los errores: el backend responde RFC 7807 y Retrofit
     * lanzaría HttpException perdiéndolo.
     *
     * `Idempotency-Key` es obligatoria: reintentar con la misma clave no duplica la solicitud.
     */
    @POST("api/v1/vinculacion/solicitudes")
    suspend fun crearSolicitud(
        @Header("Idempotency-Key") clave: String,
        @Body solicitud: SolicitudDto
    ): Response<SolicitudCreadaDto>

    // --- Vinculación: tipos de colaboración (público) ---

    @GET("api/v1/vinculacion/tipos-colaboracion")
    suspend fun tiposColaboracion(
        @Query("solo_activos") soloActivos: Boolean = true
    ): Response<List<TipoColaboracionDto>>

    // --- Contenidos (lectura pública, sin token) ---

    @GET("api/v1/contenidos/programas")
    suspend fun programas(
        @Query("page") pagina: Int = 1,
        @Query("limit") limite: Int = 50
    ): Response<PaginaDto<ProgramaDto>>

    @GET("api/v1/contenidos/indicadores")
    suspend fun indicadores(
        @Query("page") pagina: Int = 1,
        @Query("limit") limite: Int = 50
    ): Response<PaginaDto<IndicadorDto>>

    @GET("api/v1/contenidos/historias")
    suspend fun historias(
        @Query("page") pagina: Int = 1,
        @Query("limit") limite: Int = 50
    ): Response<PaginaDto<HistoriaDto>>

    // --- Talento (público) ---

    @GET("api/v1/talento/candidatos")
    suspend fun candidatos(
        @Query("page") pagina: Int = 1,
        @Query("limit") limite: Int = 50
    ): Response<PaginaDto<CandidatoDto>>

    @GET("api/v1/talento/apoyos")
    suspend fun apoyos(): Response<List<ApoyoDto>>

    // --- Identidad ---

    @POST("api/v1/identidad/auth/login")
    suspend fun login(@Body datos: LoginDto): Response<TokenDto>

    @POST("api/v1/identidad/auth/register")
    suspend fun registrar(@Body datos: RegistroDto): Response<UsuarioDto>

    @POST("api/v1/identidad/auth/refresh")
    suspend fun refrescar(@Body datos: RefreshDto): Response<TokenDto>

    @POST("api/v1/identidad/auth/logout")
    suspend fun logout(
        @Header("Authorization") bearer: String,
        @Body datos: RefreshDto
    ): Response<Unit>
}

private class AuthorizationInterceptor(
    private val sesiones: SessionDataStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val path = original.url.encodedPath
        if (
            path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/refresh")
        ) {
            return chain.proceed(original)
        }

        val accessToken = runBlocking { sesiones.sesion.first() }?.accessToken
        val request = if (accessToken.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        }
        return chain.proceed(request)
    }
}

/**
 * Authenticator que reintenta UNA vez con refresh token en 401.
 * Usa la MISMA instancia de AlbaApi (compartida) para no duplicar cliente ni lógica.
 * La función de refresh se inyecta tras crear el Api (referencia atómica).
 */
private class RefreshAuthenticator(
    private val sesiones: SessionDataStore,
    private val refreshFn: AtomicReference<((String) -> TokenDto?)?>
) : Authenticator {
    private val refreshLock = Any()

    override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? {
        val path = response.request.url.encodedPath
        if (
            path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/refresh")
        ) {
            return null
        }
        // Replay detectado: el servidor ya rechazó este access; cerrar sesión local
        if (response.priorResponse != null) {
            runBlocking { sesiones.cerrarSesion() }
            return null
        }

        return synchronized(refreshLock) {
            val sesion = runBlocking { sesiones.sesion.first() } ?: return@synchronized null
            val previousAccess = sesion.accessToken
            val requestAccess = response.request.header("Authorization")
                ?.removePrefix("Bearer ")

            // Otro hilo ya renovó el token; reusar el nuevo access
            if (requestAccess != null && requestAccess != previousAccess) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer $previousAccess")
                    .build()
            }

            val fn = refreshFn.get() ?: return@synchronized null
            val refreshed = fn(sesion.refreshToken) ?: run {
                runBlocking { sesiones.cerrarSesion() }
                null
            } ?: return@synchronized null

            runBlocking { sesiones.actualizarTokens(refreshed.accessToken, refreshed.refreshToken) }
            response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshed.accessToken}")
                .build()
        }
    }
}

/** Instancia única de Retrofit; no hay DI en el proyecto, así que vive como singleton. */
object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true // el backend puede agregar campos sin romper el cliente
        explicitNulls = false    // los campos nulos se omiten del JSON, no viajan como null
        encodeDefaults = true
    }

    private var sesiones: SessionDataStore? = null

    fun initialize(context: Context) {
        sesiones = SessionDataStore(context)
    }

    /** Crea el cliente HTTP, la API y conecta el authenticator con la función de refresh real. */
    val api: AlbaApi by lazy {
        check(BuildConfig.API_BASE_URL.isNotBlank()) {
            "API_BASE_URL está vacía en este build type; configura el host del backend."
        }
        val sessionStore = sesiones
            ?: error("ApiClient.initialize(context) debe llamarse antes de usar la API.")

        // Referencia atómica a la función de refresh; se setea tras crear la API.
        val refreshFn = AtomicReference<((String) -> TokenDto?)?>()

        // Cliente base SIN authenticator (se añade después para evitar ciclo)
        val baseClient = OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor(sessionStore))
            .build()

        // Retrofit inicial para obtener la instancia de AlbaApi
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(baseClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(AlbaApi::class.java)

        // Ahora sí: función de refresh que usa LA MISMA api (sin crear cliente nuevo)
        refreshFn.set { refreshToken ->
            runBlocking {
                try {
                    val resp = api.refrescar(RefreshDto(refreshToken))
                    if (resp.isSuccessful) resp.body() else null
                } catch (_: Exception) {
                    null
                }
            }
        }

        // Cliente final con authenticator conectado a la función compartida
        val finalClient = baseClient.newBuilder()
            .authenticator(RefreshAuthenticator(sessionStore, refreshFn))
            .build()

        // Retrofit definitivo con el cliente completo (connection pooling compartido)
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(finalClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AlbaApi::class.java)
    }
}
