# Pantallas Android de Huata

Para nuevas pantallas Kotlin, incluidos roles dinámicos:

- Reutilizar los componentes de `shared/src/commonMain/kotlin/com/example/ecolacteoshuata/core/ui`: `KpiCard`, `SectionCard`, `HeroMetricCard`, `TituloSeccion`, `AppTextField`, `AppDropdown`, `EstadoChip` y equivalentes existentes.
- Mantener `LocalTemaRol`, las tipografías, colores y espaciados de Huata. Consultar Proveedor, Vendedor, Acopiador y Producción como referencia.
- Conservar `RootScaffold`, la cabecera y la navegación inferior. Un rol dinámico puede reutilizar el panel genérico; no crear otra apariencia por rol.
- Adaptar el contenido a ancho reducido y tamaño de texto del sistema.
- Reutilizar estados de carga, error y vacío; terminar la carga ante errores, explicar la causa y ofrecer reintento. Una descarga fallida no demuestra que falte un perfil.
- No cambiar otras pantallas, estilos globales, reglas operativas o financieras para acomodar una pantalla nueva.
