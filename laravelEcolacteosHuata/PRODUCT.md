# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Staff and partners of Ecolácteos Huata, a dairy business. Six roles, each with its own panel and no single dominant one: administrador, planta, acopiador (milk collectors on route), proveedor (milk suppliers), producción, and vendedor. Interface language is Spanish.

## Product Purpose
Trace milk from supplier and collection, through laboratory results and production, to sales, and settle payments to suppliers. Success is that every liter and every product can be followed across those stages and each role completes its own job in its own panel.

## Positioning
A custom system for Ecolácteos Huata that connects the whole chain (acopio, laboratorio, producción, ventas, pagos a proveedores) in one record, instead of separate tools per stage.

## Operating Context
- Acopiadores record pickups (recojo), routes/map, deliveries to the plant, and inventory; a mobile sync endpoint (`/mobile/sync`) supports a companion app.
- Planta handles deliveries, laboratory results (PDF), routes, supplier payments, and announcements (comunicados).
- Producción manages inputs (insumos), units, recipes, production records, products, purchases, and inventory.
- Vendedores record sales (boleta PDF), clients, history, and inventory.
- Proveedores view their deliveries, lab results, and announcements.
- Attendance (asistencia) with justifications is also tracked.

## Capabilities and Constraints
- Stack: Laravel 13, Livewire 4, Flux UI, Tailwind 4, Vite; teams (multi-tenant style routes under `{current_team}`), passkeys, PDF generation (dompdf).
- A public welcome page presents the company (Acopio, Producción y Ventas).
- A separate Kotlin Multiplatform project (`../EcolacteosHuata`, with androidApp and iosApp) is the native companion; the user says the product is web plus a native Android app. This record covers the Laravel web app only.
- Open: whether multiple companies will use the product (not confirmed; today it is single-company, `Empresa::actual()`).

## Brand Commitments
Name: Ecolácteos Huata. Existing green primary on the welcome page (`#1a8049`) with Epilogue and Be Vietnam Pro; treated as incumbent, not yet confirmed as binding.

## Evidence on Hand
Real workflows and data models in the codebase. No testimonials, customer counts, or metrics were provided; do not fabricate them.

## Product Principles
1. Traceability first: every screen should keep the milk-to-sale chain legible.
2. Role-fit: each role sees only the tasks and data of its job.
3. Field-ready: collectors and suppliers often work on phones in the field; core flows must survive small screens and weak connectivity.
4. Spanish, plain language, correct units and currency; no jargon from other domains.
5. Records are trusted: totals, payments, and lab results must be unambiguous and printable.
