@props([
    'lat' => null,
    'lng' => null,
    'editable' => false,
    'latModel' => 'latitud',
    'lngModel' => 'longitud',
    'height' => '260px',
])

<div
    wire:ignore
    x-data="{
        map: null,
        marker: null,
        capaRutas: null,
        editable: {{ $editable ? 'true' : 'false' }},
        {{-- Los objetos de Leaflet no deben volverse reactivos: Alpine los envolvería en un Proxy y
             Leaflet deja de dibujar las líneas o falla al hacer zoom. __v_skip evita ese Proxy. --}}
        crudo(objeto) {
            objeto.__v_skip = true;

            return objeto;
        },
        init() {
            if (typeof L === 'undefined') {
                const esperarLeaflet = setInterval(() => {
                    if (typeof L !== 'undefined') {
                        clearInterval(esperarLeaflet);
                        this.iniciarMapa();
                    }
                }, 50);

                return;
            }

            this.iniciarMapa();
        },
        iniciarMapa() {
            const lat = {{ $lat ?? -15.8202 }};
            const lng = {{ $lng ?? -70.0212 }};

            this.map = this.crudo(L.map(this.$refs.mapa).setView([lat, lng], {{ $lat && $lng ? 16 : 13 }}));

            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap',
                maxZoom: 19,
            }).addTo(this.map);

            if ({{ $lat && $lng ? 'true' : 'false' }}) {
                this.marker = this.crudo(L.marker([lat, lng]).addTo(this.map));
            }

            if (this.editable) {
                this.map.on('click', (e) => {
                    const { lat, lng } = e.latlng;

                    if (this.marker) {
                        this.marker.setLatLng([lat, lng]);
                    } else {
                        this.marker = this.crudo(L.marker([lat, lng]).addTo(this.map));
                    }

                    $wire.set('{{ $latModel }}', Math.round(lat * 1e7) / 1e7);
                    $wire.set('{{ $lngModel }}', Math.round(lng * 1e7) / 1e7);
                });
            }

            this.capaRutas = this.crudo(L.layerGroup().addTo(this.map));

            setTimeout(() => this.map.invalidateSize(), 200);
        },
        {{-- Evento de navegador 'mapa-ubicacion-rutas' {lat, lng, rutas: [{nombre, trazo}]}: pone el
             marcador de la finca y dibuja las rutas del acopiador elegido como referencia. --}}
        mostrarRutas({ lat, lng, rutas }) {
            if (! this.map) {
                setTimeout(() => this.mostrarRutas({ lat, lng, rutas }), 100);

                return;
            }

            this.map.invalidateSize();
            this.capaRutas.clearLayers();

            if (lat !== null && lng !== null) {
                if (this.marker) {
                    this.marker.setLatLng([lat, lng]);
                } else {
                    this.marker = this.crudo(L.marker([lat, lng]).addTo(this.map));
                }
            } else if (this.marker) {
                this.marker.remove();
                this.marker = null;
            }

            const puntos = [];

            (rutas ?? []).forEach((ruta) => {
                if (ruta.trazo.length > 1) {
                    L.polyline(ruta.trazo, { color: '#2563eb', weight: 5, opacity: .8 })
                        .bindTooltip(ruta.nombre, { sticky: true })
                        .addTo(this.capaRutas);
                    puntos.push(...ruta.trazo);
                }
            });

            if (this.marker) {
                puntos.push(this.marker.getLatLng());
            }

            if (puntos.length > 1) {
                this.map.fitBounds(puntos, { padding: [30, 30], maxZoom: 16 });
            } else if (puntos.length === 1) {
                this.map.setView(puntos[0], 16);
            } else {
                {{-- Sin finca ni rutas (usuario nuevo): vuelve a la vista general de Huata. --}}
                this.map.setView([-15.8202, -70.0212], 13);
            }
        },
    }"
    x-on:mapa-ubicacion-rutas.window="mostrarRutas($event.detail)"
>
    <div x-ref="mapa" style="height: {{ $height }}; border-radius: 0.75rem;" class="ring-1 ring-huata-verde-100"></div>

    @if ($editable)
        <p class="mt-1 text-xs text-zinc-500">Toca el mapa para marcar la ubicación exacta de la finca.</p>
    @endif
</div>
