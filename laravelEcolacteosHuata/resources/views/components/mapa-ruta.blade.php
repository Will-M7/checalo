@props([
    'datos',
    'editable' => false,
    'height' => '520px',
])

{{--
    Mapa de una ruta de recojo: dibuja el camino designado (línea) y las paradas numeradas de los
    proveedores. Se actualiza con el evento de navegador "mapa-ruta" que envía el componente
    Livewire padre con los datos de Ruta::datosMapa().

    - editable: planta/administrador trazan el camino tocando el mapa y marcan la ubicación de
      los proveedores (llama a $wire.guardarTrazo(puntos) y $wire.marcarUbicacion(lat, lng)).
    - no editable: el acopiador ve el camino, sus paradas y su posición actual (GPS). "Cómo llegar"
      (desde el popup o con el evento de navegador "como-llegar" {id}) dibuja en este mismo mapa el
      recorrido por caminos desde su posición hasta la parada, calculado con OSRM.
--}}

<div
    wire:ignore
    x-data="{
        map: null,
        capas: null,
        miPosicion: null,
        seguimientoId: null,
        capaIndicaciones: null,
        indicacion: null,
        buscandoIndicacion: false,
        editable: {{ $editable ? 'true' : 'false' }},
        datos: @js($datos),
        trazo: [],
        trazando: false,
        rutaEncuadrada: null,
        {{-- Los objetos de Leaflet no deben volverse reactivos: Alpine los envolvería en un Proxy y
             Leaflet deja de dibujar las líneas o falla al hacer zoom. __v_skip evita ese Proxy. --}}
        crudo(objeto) {
            objeto.__v_skip = true;

            return objeto;
        },
        init() {
            const esperarLeaflet = setInterval(() => {
                if (typeof L !== 'undefined') {
                    clearInterval(esperarLeaflet);
                    this.iniciarMapa();
                }
            }, 50);
        },
        destroy() {
            if (this.seguimientoId !== null) {
                navigator.geolocation.clearWatch(this.seguimientoId);
            }
        },
        iniciarMapa() {
            this.map = this.crudo(L.map(this.$refs.mapa).setView([-15.8202, -70.0212], 13));

            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap',
                maxZoom: 19,
            }).addTo(this.map);

            this.capas = this.crudo(L.layerGroup().addTo(this.map));
            this.capaIndicaciones = this.crudo(L.layerGroup().addTo(this.map));

            if (this.editable) {
                this.map.on('click', (e) => this.alTocarMapa(e.latlng));
            }

            this.dibujar(this.datos);
            setTimeout(() => this.map.invalidateSize(), 200);
        },
        actualizar(datos) {
            this.datos = datos;
            this.trazando = false;
            this.capaIndicaciones?.clearLayers();
            this.indicacion = null;
            this.dibujar(datos);
        },
        alTocarMapa({ lat, lng }) {
            const redondear = (valor) => Math.round(valor * 1e7) / 1e7;

            if (this.datos?.ubicandoId) {
                $wire.marcarUbicacion(redondear(lat), redondear(lng));

                return;
            }

            if (this.trazando) {
                this.trazo.push([redondear(lat), redondear(lng)]);
                this.dibujar(this.datos, false);
            }
        },
        empezarTrazo() {
            this.trazo = [...(this.datos?.trazo ?? [])];
            this.trazando = true;
        },
        deshacerPunto() {
            this.trazo.pop();
            this.dibujar(this.datos, false);
        },
        cancelarTrazo() {
            this.trazando = false;
            this.dibujar(this.datos, false);
        },
        guardarTrazo() {
            $wire.guardarTrazo(this.trazo);
        },
        icono(texto, color) {
            return L.divIcon({
                className: '',
                html: `<div style='display:flex;align-items:center;justify-content:center;width:30px;height:30px;border-radius:9999px;background:${color};color:#fff;font-weight:700;font-size:13px;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)'>${texto}</div>`,
                iconSize: [30, 30],
                iconAnchor: [15, 15],
                popupAnchor: [0, -15],
            });
        },
        popup(parada) {
            const contenido = document.createElement('div');
            const titulo = document.createElement('strong');
            titulo.textContent = `${parada.numero}. ${parada.nombre}`;
            const ubicacion = document.createElement('div');
            ubicacion.textContent = parada.ubicacion;
            contenido.append(titulo, ubicacion);

            if (! this.editable) {
                const estado = document.createElement('div');
                estado.textContent = parada.recogido ? 'Recogido hoy' : 'Pendiente de recojo';
                estado.style.color = parada.recogido ? '#16a34a' : '#dc2626';
                const llegar = document.createElement('button');
                llegar.type = 'button';
                llegar.textContent = 'Cómo llegar';
                llegar.style.cssText = 'margin-top:4px;color:#2563eb;font-weight:600;text-decoration:underline;';
                llegar.addEventListener('click', () => this.comoLlegar(parada.id));
                contenido.append(estado, llegar);
            }

            return contenido;
        },
        dibujar(datos, encuadrar = true) {
            if (! this.map) {
                return;
            }

            this.capas.clearLayers();

            const puntos = [];
            const camino = this.trazando ? this.trazo : (datos?.trazo ?? []);

            if (camino.length > 1) {
                L.polyline(camino, { color: '#2563eb', weight: 5, opacity: .8, dashArray: this.trazando ? '8 8' : null }).addTo(this.capas);
            }

            camino.forEach((punto) => {
                puntos.push(punto);

                if (this.trazando) {
                    L.circleMarker(punto, { radius: 4, color: '#2563eb', fillOpacity: 1 }).addTo(this.capas);
                }
            });

            (datos?.paradas ?? []).forEach((parada) => {
                if (parada.lat === null || parada.lng === null) {
                    return;
                }

                const color = this.editable ? '#15803d' : (parada.recogido ? '#16a34a' : '#dc2626');

                L.marker([parada.lat, parada.lng], { icon: this.icono(parada.numero, color) })
                    .bindPopup(this.popup(parada))
                    .addTo(this.capas);

                puntos.push([parada.lat, parada.lng]);
            });

            if (encuadrar && puntos.length && this.rutaEncuadrada !== datos?.rutaId) {
                this.map.fitBounds(puntos, { padding: [40, 40], maxZoom: 16 });
                this.rutaEncuadrada = datos?.rutaId;
            }
        },
        miUbicacionActual() {
            if (this.miPosicion) {
                const { lat, lng } = this.miPosicion.getLatLng();

                return Promise.resolve([lat, lng]);
            }

            return new Promise((resolver, rechazar) => {
                if (! navigator.geolocation) {
                    rechazar();

                    return;
                }

                navigator.geolocation.getCurrentPosition(
                    ({ coords }) => resolver([coords.latitude, coords.longitude]),
                    rechazar,
                    { enableHighAccuracy: true, timeout: 15000 },
                );
            });
        },
        async comoLlegar(paradaId) {
            const parada = (this.datos?.paradas ?? []).find((p) => p.id === paradaId);

            if (! parada || parada.lat === null || parada.lng === null) {
                return;
            }

            this.map.closePopup();
            this.$refs.mapa.scrollIntoView({ behavior: 'smooth', block: 'center' });
            this.buscandoIndicacion = true;

            let origen;

            try {
                origen = await this.miUbicacionActual();
            } catch {
                this.buscandoIndicacion = false;
                alert('No se pudo obtener tu ubicación. Activa el GPS y da permiso de ubicación.');

                return;
            }

            if (this.seguimientoId === null) {
                this.seguirMiPosicion(false);
            }

            this.capaIndicaciones.clearLayers();
            const destino = [parada.lat, parada.lng];
            let recorrido = [origen, destino];
            let aproximado = true;
            let distancia = this.map.distance(origen, destino);
            let duracion = null;

            try {
                const url = `https://router.project-osrm.org/route/v1/driving/${origen[1]},${origen[0]};${destino[1]},${destino[0]}?overview=full&geometries=geojson`;
                const respuesta = await fetch(url);
                const resultado = await respuesta.json();
                const ruta = resultado.routes?.[0];

                if (ruta) {
                    recorrido = ruta.geometry.coordinates.map(([lng, lat]) => [lat, lng]);
                    distancia = ruta.distance;
                    duracion = ruta.duration;
                    aproximado = false;
                }
            } catch {
                // Sin conexión con el servicio de rutas: se muestra la línea recta como referencia.
            }

            L.polyline(recorrido, { color: '#f97316', weight: 6, opacity: .9, dashArray: aproximado ? '10 8' : null }).addTo(this.capaIndicaciones);
            this.map.fitBounds(recorrido, { padding: [50, 50], maxZoom: 17 });

            this.indicacion = {
                nombre: `${parada.numero}. ${parada.nombre}`,
                distancia: distancia >= 1000 ? `${(distancia / 1000).toFixed(1)} km` : `${Math.round(distancia)} m`,
                duracion: duracion === null ? null : `${Math.max(1, Math.round(duracion / 60))} min`,
                aproximado,
            };
            this.buscandoIndicacion = false;
        },
        quitarIndicacion() {
            this.capaIndicaciones.clearLayers();
            this.indicacion = null;
        },
        seguirMiPosicion(centrar = true) {
            if (! navigator.geolocation) {
                alert('Tu dispositivo no permite obtener la ubicación.');

                return;
            }

            if (this.seguimientoId !== null) {
                if (centrar && this.miPosicion) {
                    this.map.setView(this.miPosicion.getLatLng(), 17);
                }

                return;
            }

            this.seguimientoId = navigator.geolocation.watchPosition(({ coords }) => {
                const punto = [coords.latitude, coords.longitude];

                if (this.miPosicion) {
                    this.miPosicion.setLatLng(punto);
                } else {
                    this.miPosicion = this.crudo(L.circleMarker(punto, { radius: 9, color: '#fff', weight: 3, fillColor: '#2563eb', fillOpacity: 1 })
                        .bindTooltip('Tú estás aquí')
                        .addTo(this.map));

                    if (centrar) {
                        this.map.setView(punto, 17);
                    }
                }
            }, () => alert('No se pudo obtener tu ubicación. Revisa los permisos del GPS.'), { enableHighAccuracy: true });
        },
    }"
    x-on:mapa-ruta.window="actualizar($event.detail.datos)"
    x-on:como-llegar.window="comoLlegar($event.detail.id)"
    class="flex flex-col gap-2"
>
    @if ($editable)
        <div class="flex flex-wrap items-center gap-2">
            <template x-if="! trazando">
                <flux:button size="sm" variant="primary" icon="pencil-square" x-on:click="empezarTrazo()">Trazar camino</flux:button>
            </template>
            <template x-if="trazando">
                <div class="flex flex-wrap items-center gap-2">
                    <flux:button size="sm" variant="primary" icon="check" x-on:click="guardarTrazo()">Guardar camino</flux:button>
                    <flux:button size="sm" variant="filled" icon="arrow-uturn-left" x-on:click="deshacerPunto()">Deshacer punto</flux:button>
                    <flux:button size="sm" variant="ghost" icon="x-mark" x-on:click="cancelarTrazo()">Cancelar</flux:button>
                    <span class="text-xs text-zinc-500">Toca el mapa para agregar puntos del camino (<span x-text="trazo.length"></span>).</span>
                </div>
            </template>
        </div>
    @else
        <div class="flex flex-wrap items-center gap-2">
            <flux:button size="sm" variant="primary" icon="map-pin" x-on:click="seguirMiPosicion()">Mi ubicación</flux:button>
            <span class="flex items-center gap-1 text-xs text-zinc-500"><span class="inline-block size-3 rounded-full bg-red-600"></span> Pendiente</span>
            <span class="flex items-center gap-1 text-xs text-zinc-500"><span class="inline-block size-3 rounded-full bg-green-600"></span> Recogido</span>
            <span class="flex items-center gap-1 text-xs text-zinc-500"><span class="inline-block h-1 w-5 rounded bg-blue-600"></span> Camino designado</span>
            <span class="flex items-center gap-1 text-xs text-zinc-500"><span class="inline-block h-1 w-5 rounded bg-orange-500"></span> Cómo llegar</span>
        </div>

        <div x-show="buscandoIndicacion" x-cloak class="text-sm text-zinc-500">Calculando cómo llegar...</div>

        <div x-show="indicacion" x-cloak class="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-orange-300 bg-orange-50 p-3 dark:border-orange-800 dark:bg-orange-950/30">
            <div class="text-sm">
                <p class="font-semibold text-zinc-900 dark:text-white">Cómo llegar a <span x-text="indicacion?.nombre"></span></p>
                <p class="text-zinc-600 dark:text-zinc-300">
                    <span x-text="indicacion?.distancia"></span>
                    <template x-if="indicacion?.duracion"><span> · aprox. <span x-text="indicacion?.duracion"></span></span></template>
                    <template x-if="indicacion?.aproximado"><span> · en línea recta (sin conexión con el servicio de rutas)</span></template>
                </p>
            </div>
            <flux:button size="sm" variant="ghost" icon="x-mark" x-on:click="quitarIndicacion()">Quitar</flux:button>
        </div>
    @endif

    <div x-ref="mapa" style="height: {{ $height }}; border-radius: 0.75rem;" class="z-0 ring-1 ring-huata-verde-100"></div>
</div>
