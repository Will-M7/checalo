package com.example.ecolacteoshuata.di

import org.koin.core.module.Module

/**
 * Lo que aporta cada plataforma: motor de Ktor (OkHttp / Darwin), driver de SQLite, almacén
 * seguro del token (Keystore / Keychain), monitor de red y exportador de PDF.
 */
expect val platformModule: Module
