@props([
    'datos',
    'height' => '520px',
])

{{--
    Mapa general de todas las rutas (dashboard de Rutas de planta). Dibuja el camino de cada
    camioncito como una línea y, alrededor de la ruta elegida, su rango de cobertura: una franja
    de N metros a cada lado del camino. Así planta ve qué proveedores quedan cerca del caminito
    (el popup del marcador indica a cuántos metros están). Las paradas de la ruta elegida se
    muestran numeradas en su orden de recojo.

    - Se actualiza con el evento de navegador "mapa-rutas-general" {datos}; la ruta elegida
      (datos.rutaSel) la decide el componente Livewire padre.
    - Ver en el camino: el evento de navegador "mapa-enfocar-proveedor" {id} centra el mapa en el
      proveedor, abre su popup y une su finca con el punto más cercano del camino de la ruta elegida.
    - Ubicar proveedor: si datos.ubicandoId está definido, un clic en el mapa llama a
      $wire.marcarUbicacion(lat, lng) (Esc cancela); arrastrar el marcador de un proveedor y
      soltarlo llama a $wire.moverUbicacionProveedor(id, lat, lng). Si la nueva ubicación queda
      dentro del rango del camino de una ruta, el servidor agrega al proveedor a esa ruta.
    - Rango: cada ruta guarda el suyo; el control lo muestra y al soltarlo llama a
      $wire.guardarRangoRuta(rutaId, rango).
    - Trazar camino: con el cursor en cruz, un clic agrega un punto y mantener presionado y arrastrar
      dibuja el camino a mano alzada; una línea guía sigue al cursor desde el último punto. Los puntos
      se editan: arrastrar un punto lo mueve, arrastrar (o tocar) el "+" entre dos puntos inserta uno
      nuevo y clic derecho sobre un punto lo quita. El primer punto es el inicio (I, verde) y el último
      el fin (F, rojo). Se guarda con $wire.guardarTrazoRuta(rutaId, puntos).
--}}

<div
    wire:ignore
    x-data="{
        map: null,
        capas: null,
        capaGuia: null,
        capaEnfoque: null,
        marcadores: null,
        dibujandoLibre: false,
        arrastrandoPunto: false,
        datos: @js($datos),
        rutaSel: null,
        rango: 500,
        trazo: [],
        trazando: false,
        enRango: 0,
        colores: ['#2563eb', '#dc2626', '#16a34a', '#9333ea', '#ea580c', '#0891b2', '#db2777', '#ca8a04'],
        {{-- Los objetos de Leaflet no deben volverse reactivos: Alpine los envolvería en un Proxy y
             Leaflet deja de dibujar las líneas o falla al hacer zoom. __v_skip evita ese Proxy. --}}
        crudo(objeto) {
            objeto.__v_skip = true;

            return objeto;
        },
        init() {
            this.rutaSel = this.datos?.rutaSel ?? null;
            this.rango = this.ruta()?.rango ?? 500;

            const esperarLeaflet = setInterval(() => {
                if (typeof L !== 'undefined') {
                    clearInterval(esperarLeaflet);
                    this.iniciarMapa();
                }
            }, 50);

            this.$watch('rango', () => this.dibujar(false));
        },
        iniciarMapa() {
            this.map = this.crudo(L.map(this.$refs.mapa).setView([-15.8202, -70.0212], 13));

            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap',
                maxZoom: 19,
            }).addTo(this.map);

            this.capas = this.crudo(L.layerGroup().addTo(this.map));
            this.capaGuia = this.crudo(L.layerGroup().addTo(this.map));
            this.capaEnfoque = this.crudo(L.layerGroup().addTo(this.map));
            this.marcadores = this.crudo(new Map());
            this.map.on('click', (e) => this.alTocarMapa(e.latlng));
            this.map.on('mousedown', (e) => this.alPresionar(e));
            this.map.on('mousemove', (e) => this.alMoverCursor(e));
            this.map.on('mouseup', () => this.alSoltar());
            this.map.on('mouseout', () => {
                this.alSoltar();
                this.capaGuia.clearLayers();
            });

            this.dibujar(true);
            setTimeout(() => this.map.invalidateSize(), 200);
        },
        actualizar(datos) {
            const cambioDeRuta = datos.rutaSel !== this.rutaSel;
            this.datos = datos;
            this.rutaSel = datos.rutaSel;

            {{-- Un refresco por otra acción (ubicar, asignar...) no debe perder el camino que se está trazando. --}}
            if (cambioDeRuta) {
                this.salirDelTrazo();
                this.rango = this.ruta()?.rango ?? 500;
            }

            this.dibujar(cambioDeRuta);
        },
        guardarRango() {
            const ruta = this.ruta();

            if (! ruta) {
                return;
            }

            ruta.rango = this.rango;
            $wire.guardarRangoRuta(ruta.id, this.rango);
        },
        cancelarUbicacion() {
            if (this.datos?.ubicandoId) {
                $wire.ubicarProveedor(this.datos.ubicandoId);
            }
        },
        alTocarMapa({ lat, lng }) {
            if (this.trazando || ! this.datos?.ubicandoId) {
                return;
            }

            const redondear = (valor) => Math.round(valor * 1e7) / 1e7;
            $wire.marcarUbicacion(redondear(lat), redondear(lng), this.rango);
        },
        ruta() {
            return (this.datos?.rutas ?? []).find((ruta) => ruta.id === this.rutaSel) ?? null;
        },
        color(ruta) {
            const indice = (this.datos?.rutas ?? []).findIndex((r) => r.id === ruta.id);

            return this.colores[Math.max(0, indice) % this.colores.length];
        },
        agregarPunto({ lat, lng }) {
            const redondear = (valor) => Math.round(valor * 1e7) / 1e7;
            this.trazo.push([redondear(lat), redondear(lng)]);
        },
        alPresionar(e) {
            if (! this.trazando || e.originalEvent.button !== 0) {
                return;
            }

            this.dibujandoLibre = true;
            this.agregarPunto(e.latlng);
            this.dibujarGuia(e.latlng);
        },
        {{-- Arrastrando se agrega un punto cada ~12 px de recorrido del cursor; sin arrastrar solo se
             mueve la línea guía desde el último punto hasta el cursor. --}}
        alMoverCursor(e) {
            if (! this.trazando || this.arrastrandoPunto) {
                return;
            }

            const ultimo = this.trazo[this.trazo.length - 1];

            if (this.dibujandoLibre && ultimo && this.map.latLngToContainerPoint(ultimo).distanceTo(e.containerPoint) >= 12) {
                this.agregarPunto(e.latlng);
            }

            this.dibujarGuia(e.latlng);
        },
        alSoltar() {
            if (! this.dibujandoLibre) {
                return;
            }

            this.dibujandoLibre = false;
            this.dibujar(false);
        },
        dibujarGuia(cursor) {
            this.capaGuia.clearLayers();

            const ruta = this.ruta();
            const color = ruta ? this.color(ruta) : '#2563eb';

            if (this.dibujandoLibre && this.trazo.length > 1) {
                L.polyline(this.trazo, { color, weight: 6, opacity: .9, interactive: false }).addTo(this.capaGuia);
            }

            if (this.trazo.length) {
                L.polyline([this.trazo[this.trazo.length - 1], cursor], { color, weight: 3, opacity: .7, dashArray: '4 6', interactive: false }).addTo(this.capaGuia);
            }
        },
        empezarTrazo() {
            this.trazo = [...(this.ruta()?.trazo ?? [])];
            this.trazando = true;
            this.map.dragging.disable();
            this.map.doubleClickZoom.disable();
            this.$refs.mapa.style.cursor = 'crosshair';
            this.dibujar(false);
        },
        salirDelTrazo() {
            this.trazando = false;
            this.dibujandoLibre = false;
            this.capaGuia?.clearLayers();

            if (this.map) {
                this.map.dragging.enable();
                this.map.doubleClickZoom.enable();
            }
        },
        deshacerPunto() {
            this.trazo.pop();
            this.capaGuia.clearLayers();
            this.dibujar(false);
        },
        invertirTrazo() {
            this.trazo.reverse();
            this.dibujar(false);
        },
        cancelarTrazo() {
            this.salirDelTrazo();
            this.dibujar(false);
        },
        guardarTrazo() {
            $wire.guardarTrazoRuta(this.rutaSel, this.trazo).then(() => {
                this.salirDelTrazo();
                this.dibujar(false);
            });
        },
        {{-- Proyección local en metros (suficiente para distancias dentro de la zona de Huata). --}}
        aMetros([lat, lng], latBase) {
            return [lng * 111320 * Math.cos(latBase * Math.PI / 180), lat * 110540];
        },
        aLatLng([x, y], latBase) {
            return [y / 110540, x / (111320 * Math.cos(latBase * Math.PI / 180))];
        },
        {{-- Punto del camino más cercano a un punto dado: {punto: [lat, lng], distancia: metros}. --}}
        puntoMasCercano(punto, camino) {
            if (! camino.length) {
                return { punto: null, distancia: Infinity };
            }

            const latBase = punto[0];
            const [px, py] = this.aMetros(punto, latBase);
            const vertices = camino.map((v) => this.aMetros(v, latBase));
            let mejor = { xy: vertices[0], distancia: Math.hypot(px - vertices[0][0], py - vertices[0][1]) };

            for (let i = 0; i < vertices.length - 1; i++) {
                const [ax, ay] = vertices[i];
                const [bx, by] = vertices[i + 1];
                const largo2 = (bx - ax) ** 2 + (by - ay) ** 2;
                const t = largo2 === 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / largo2));
                const xy = [ax + t * (bx - ax), ay + t * (by - ay)];
                const distancia = Math.hypot(px - xy[0], py - xy[1]);

                if (distancia < mejor.distancia) {
                    mejor = { xy, distancia };
                }
            }

            return { punto: this.aLatLng(mejor.xy, latBase), distancia: mejor.distancia };
        },
        distanciaAlCamino(punto, camino) {
            return this.puntoMasCercano(punto, camino).distancia;
        },
        enfocarProveedor(id) {
            const proveedor = (this.datos?.proveedores ?? []).find((p) => p.id === id);
            const marcador = this.marcadores?.get(id);

            if (! proveedor || ! marcador) {
                return;
            }

            this.capaEnfoque.clearLayers();
            this.$refs.mapa.scrollIntoView({ behavior: 'smooth', block: 'center' });

            const punto = [proveedor.lat, proveedor.lng];
            const { punto: enCamino } = this.puntoMasCercano(punto, this.ruta()?.trazo ?? []);

            if (enCamino) {
                L.polyline([punto, enCamino], { color: '#f97316', weight: 4, dashArray: '6 6', interactive: false }).addTo(this.capaEnfoque);
                L.circleMarker(enCamino, { radius: 7, color: '#fff', weight: 3, fillColor: '#f97316', fillOpacity: 1, interactive: false }).addTo(this.capaEnfoque);
                this.map.fitBounds([punto, enCamino], { padding: [80, 80], maxZoom: 17 });
            } else {
                this.map.setView(punto, 17);
            }

            marcador.openPopup();
        },
        {{-- Franja de cobertura: un rectángulo por tramo y un círculo por vértice, todos con la misma
             orientación para que con fillRule 'nonzero' se pinten como una sola zona. --}}
        franja(camino, metros) {
            const latBase = camino[0][0];
            const vertices = camino.map((v) => this.aMetros(v, latBase));
            const anillos = [];
            const orientar = (anillo) => {
                const area = anillo.reduce((suma, [x1, y1], i) => {
                    const [x2, y2] = anillo[(i + 1) % anillo.length];

                    return suma + (x1 * y2 - x2 * y1);
                }, 0);

                return area < 0 ? anillo.reverse() : anillo;
            };

            vertices.forEach(([x, y]) => {
                anillos.push(orientar(Array.from({ length: 24 }, (_, i) => [
                    x + metros * Math.cos(i * Math.PI / 12),
                    y + metros * Math.sin(i * Math.PI / 12),
                ])));
            });

            for (let i = 0; i < vertices.length - 1; i++) {
                const [ax, ay] = vertices[i];
                const [bx, by] = vertices[i + 1];
                const largo = Math.hypot(bx - ax, by - ay);

                if (largo === 0) {
                    continue;
                }

                const nx = -(by - ay) / largo * metros;
                const ny = (bx - ax) / largo * metros;
                anillos.push(orientar([[ax + nx, ay + ny], [bx + nx, by + ny], [bx - nx, by - ny], [ax - nx, ay - ny]]));
            }

            return anillos.map((anillo) => anillo.map((p) => this.aLatLng(p, latBase)));
        },
        icono(texto, color) {
            return L.divIcon({
                className: '',
                html: `<div style='display:flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:9999px;background:${color};color:#fff;font-weight:700;font-size:12px;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4)'>${texto}</div>`,
                iconSize: [28, 28],
                iconAnchor: [14, 14],
                popupAnchor: [0, -14],
            });
        },
        {{-- Marcadores de inicio (I) y fin (F) del recorrido cuando no se está editando. --}}
        dibujarExtremos(camino) {
            L.marker(camino[0], { icon: this.icono('I', '#16a34a'), zIndexOffset: 1000 })
                .bindTooltip('Inicio del recorrido')
                .addTo(this.capas);

            if (camino.length > 1) {
                L.marker(camino[camino.length - 1], { icon: this.icono('F', '#dc2626'), zIndexOffset: 1000 })
                    .bindTooltip('Fin del recorrido')
                    .addTo(this.capas);
            }
        },
        iconoPunto(color, tamano, contenido = '') {
            return L.divIcon({
                className: '',
                html: `<div style='display:flex;align-items:center;justify-content:center;width:${tamano}px;height:${tamano}px;border-radius:9999px;background:${contenido ? color : '#fff'};color:#fff;font-weight:700;font-size:11px;border:3px solid ${contenido ? '#fff' : color};box-shadow:0 1px 3px rgba(0,0,0,.4);cursor:move'>${contenido}</div>`,
                iconSize: [tamano, tamano],
                iconAnchor: [tamano / 2, tamano / 2],
            });
        },
        {{-- Editor del camino: cada punto se puede arrastrar o quitar (clic derecho) y entre cada par
             de puntos hay un "+" que, al arrastrarlo o tocarlo, inserta un punto nuevo. --}}
        dibujarEditor(linea, color) {
            const redondear = (valor) => Math.round(valor * 1e7) / 1e7;
            const ultimo = this.trazo.length - 1;

            this.trazo.forEach((punto, indice) => {
                const esInicio = indice === 0;
                const esFin = indice === ultimo && ultimo > 0;
                const icono = esInicio
                    ? this.iconoPunto('#16a34a', 24, 'I')
                    : (esFin ? this.iconoPunto('#dc2626', 24, 'F') : this.iconoPunto(color, 14));
                const marcador = L.marker(punto, {
                    icon: icono,
                    draggable: true,
                    zIndexOffset: 1000,
                    title: `${esInicio ? 'Inicio · ' : (esFin ? 'Fin · ' : '')}Arrastra para mover · clic derecho para quitar`,
                });

                marcador.on('dragstart', () => {
                    this.arrastrandoPunto = true;
                    this.capaGuia.clearLayers();
                });
                marcador.on('drag', (e) => {
                    const { lat, lng } = e.target.getLatLng();
                    this.trazo[indice] = [redondear(lat), redondear(lng)];
                    linea?.setLatLngs(this.trazo);
                });
                marcador.on('dragend', () => {
                    this.arrastrandoPunto = false;
                    this.dibujar(false);
                });
                marcador.on('contextmenu', (e) => {
                    L.DomEvent.preventDefault(e.originalEvent);
                    this.trazo.splice(indice, 1);
                    this.dibujar(false);
                });
                marcador.addTo(this.capas);
            });

            for (let indice = 0; indice < ultimo; indice++) {
                const [a, b] = [this.trazo[indice], this.trazo[indice + 1]];
                const medio = [redondear((a[0] + b[0]) / 2), redondear((a[1] + b[1]) / 2)];
                const intermedio = L.marker(medio, {
                    icon: L.divIcon({
                        className: '',
                        html: `<div style='display:flex;align-items:center;justify-content:center;width:16px;height:16px;border-radius:9999px;background:rgba(255,255,255,.85);color:${color};font-weight:700;font-size:13px;line-height:1;border:2px dashed ${color};cursor:copy'>+</div>`,
                        iconSize: [16, 16],
                        iconAnchor: [8, 8],
                    }),
                    draggable: true,
                    zIndexOffset: 900,
                    title: 'Arrastra o toca para agregar un punto aquí',
                });

                intermedio.on('dragstart', () => {
                    this.arrastrandoPunto = true;
                    this.capaGuia.clearLayers();
                    this.trazo.splice(indice + 1, 0, medio);
                });
                intermedio.on('drag', (e) => {
                    const { lat, lng } = e.target.getLatLng();
                    this.trazo[indice + 1] = [redondear(lat), redondear(lng)];
                    linea?.setLatLngs(this.trazo);
                });
                intermedio.on('dragend', () => {
                    this.arrastrandoPunto = false;
                    this.dibujar(false);
                });
                intermedio.on('click', () => {
                    this.trazo.splice(indice + 1, 0, medio);
                    this.dibujar(false);
                });
                intermedio.addTo(this.capas);
            }
        },
        popupProveedor(proveedor, ruta, distancia, numero) {
            const contenido = document.createElement('div');
            const titulo = document.createElement('strong');
            titulo.textContent = proveedor.nombre;
            const ubicacion = document.createElement('div');
            ubicacion.textContent = proveedor.ubicacion;
            const rutas = document.createElement('div');
            rutas.textContent = proveedor.rutaIds.length
                ? 'Ruta: ' + this.datos.rutas.filter((r) => proveedor.rutaIds.includes(r.id)).map((r) => r.nombre).join(', ')
                : 'Sin ruta';
            contenido.append(titulo, ubicacion, rutas);

            if (numero > 0) {
                const parada = document.createElement('div');
                parada.textContent = `Parada ${numero} de ${ruta.nombre}`;
                contenido.append(parada);
            }

            if (ruta && Number.isFinite(distancia)) {
                const cerca = document.createElement('div');
                cerca.textContent = `A ${distancia >= 1000 ? (distancia / 1000).toFixed(1) + ' km' : Math.round(distancia) + ' m'} del camino de ${ruta.nombre}`;
                cerca.style.color = '#71717a';
                contenido.append(cerca);
            }

            return contenido;
        },
        iconoProveedor(relleno, borde, grosor, tamano) {
            return L.divIcon({
                className: '',
                html: `<div style='box-sizing:border-box;width:${tamano}px;height:${tamano}px;border-radius:9999px;background:${relleno};border:${grosor}px solid ${borde};box-shadow:0 1px 3px rgba(0,0,0,.4)'></div>`,
                iconSize: [tamano, tamano],
                iconAnchor: [tamano / 2, tamano / 2],
                popupAnchor: [0, -tamano / 2],
            });
        },
        {{-- Mientras se traza o se ubica a un proveedor los marcadores no reciben clics, así el clic
             siempre llega al mapa aunque caiga encima de otro proveedor. --}}
        marcadorProveedor(proveedor, punto, icono, popup) {
            const redondear = (valor) => Math.round(valor * 1e7) / 1e7;
            const interactivo = ! this.trazando && ! this.datos?.ubicandoId;
            const marcador = L.marker(punto, {
                icon: icono,
                interactive: interactivo,
                draggable: interactivo,
                autoPan: true,
                title: `${proveedor.nombre} · arrastra para mover su ubicación`,
            }).bindPopup(popup);

            marcador.on('dragend', (e) => {
                const { lat, lng } = e.target.getLatLng();
                $wire.moverUbicacionProveedor(proveedor.id, redondear(lat), redondear(lng));
            });

            this.marcadores.set(proveedor.id, marcador.addTo(this.capas));
        },
        dibujar(encuadrar) {
            if (! this.map) {
                return;
            }

            this.capas.clearLayers();
            this.capaEnfoque.clearLayers();
            this.marcadores.clear();
            this.$refs.mapa.style.cursor = this.trazando || this.datos?.ubicandoId ? 'crosshair' : '';

            const seleccionada = this.ruta();
            const caminoSel = this.trazando ? this.trazo : (seleccionada?.trazo ?? []);
            const puntos = [];
            let lineaSel = null;

            if (caminoSel.length && this.rango > 0) {
                L.polygon(this.franja(caminoSel, this.rango), {
                    stroke: false,
                    fillColor: seleccionada ? this.color(seleccionada) : '#2563eb',
                    fillOpacity: .15,
                    fillRule: 'nonzero',
                    interactive: false,
                }).addTo(this.capas);
            }

            (this.datos?.rutas ?? []).forEach((ruta) => {
                const esSel = ruta.id === this.rutaSel;
                const camino = esSel ? caminoSel : ruta.trazo;

                if (camino.length > 1 || (esSel && this.trazando)) {
                    const linea = L.polyline(camino, {
                        color: this.color(ruta),
                        weight: esSel ? 6 : 4,
                        opacity: esSel ? .9 : (ruta.activa ? .55 : .3),
                        dashArray: (esSel && this.trazando) || ! ruta.activa ? '8 8' : null,
                        interactive: ! this.trazando,
                    }).bindTooltip(`${ruta.nombre} · ${ruta.acopiador}`, { sticky: true }).addTo(this.capas);

                    if (esSel) {
                        lineaSel = linea;
                    }
                }

                if (esSel) {
                    puntos.push(...camino);
                }
            });

            let enRango = 0;

            (this.datos?.proveedores ?? []).forEach((proveedor) => {
                const punto = [proveedor.lat, proveedor.lng];
                const enSel = seleccionada && proveedor.rutaIds.includes(seleccionada.id);
                const distancia = seleccionada ? this.distanciaAlCamino(punto, caminoSel) : Infinity;
                const cerca = seleccionada && ! enSel && distancia <= this.rango;
                const rutaPropia = this.datos.rutas.find((r) => proveedor.rutaIds.includes(r.id));
                const numero = enSel ? (this.datos.paradas ?? []).indexOf(proveedor.id) + 1 : 0;

                if (cerca) {
                    enRango++;
                }

                const popup = this.popupProveedor(proveedor, seleccionada, distancia, numero);

                if (numero > 0) {
                    this.marcadorProveedor(proveedor, punto, this.icono(numero, this.color(seleccionada)), popup);
                    puntos.push(punto);

                    return;
                }

                const icono = this.iconoProveedor(
                    rutaPropia ? this.color(rutaPropia) : '#71717a',
                    cerca ? '#f59e0b' : '#fff',
                    cerca ? 4 : 2,
                    enSel || cerca ? 20 : 14,
                );

                this.marcadorProveedor(proveedor, punto, icono, popup);

                if (enSel) {
                    puntos.push(punto);
                }
            });

            this.enRango = enRango;

            if (seleccionada && this.trazando) {
                this.dibujarEditor(lineaSel, this.color(seleccionada));
            } else if (caminoSel.length) {
                this.dibujarExtremos(caminoSel);
            }

            if (encuadrar && puntos.length) {
                this.map.fitBounds(puntos, { padding: [40, 40], maxZoom: 16 });
            }
        },
    }"
    x-on:mapa-rutas-general.window="actualizar($event.detail.datos)"
    x-on:mapa-enfocar-proveedor.window="enfocarProveedor($event.detail.id)"
    x-on:keydown.escape.window="cancelarUbicacion()"
    class="flex flex-col gap-3"
>
    <div class="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div class="flex flex-wrap items-end gap-3">
            <label class="flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
                <span>Rango de la ruta: <span x-text="rango >= 1000 ? (rango / 1000).toFixed(1) + ' km' : rango + ' m'"></span></span>
                <input type="range" min="100" max="5000" step="100" x-model.number="rango" x-on:change="guardarRango()" x-bind:disabled="! rutaSel" class="w-56 accent-huata-verde-600" />
            </label>
        </div>

        <div class="flex flex-wrap items-center gap-2" x-show="rutaSel">
            <template x-if="! trazando">
                <flux:button size="sm" variant="primary" icon="pencil-square" x-on:click="empezarTrazo()">Trazar camino</flux:button>
            </template>
            <template x-if="trazando">
                <div class="flex flex-wrap items-center gap-2">
                    <flux:button size="sm" variant="primary" icon="check" x-on:click="guardarTrazo()">Guardar camino</flux:button>
                    <flux:button size="sm" variant="filled" icon="arrow-uturn-left" x-on:click="deshacerPunto()">Deshacer punto</flux:button>
                    <flux:button size="sm" variant="filled" icon="arrows-right-left" x-on:click="invertirTrazo()" title="Intercambia el inicio y el fin del recorrido">Invertir sentido</flux:button>
                    <flux:button size="sm" variant="ghost" icon="x-mark" x-on:click="cancelarTrazo()">Cancelar</flux:button>
                </div>
            </template>
        </div>
    </div>

    <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-zinc-500">
        <span x-show="trazando" x-cloak>Haz clic para agregar puntos al final o mantén presionado y arrastra para dibujar (<span x-text="trazo.length"></span> puntos). Arrastra un punto para moverlo, arrastra o toca un "+" para insertar uno entre dos puntos y haz clic derecho sobre un punto para quitarlo. Usa la rueda del mouse para acercar o alejar.</span>
        <span x-show="! trazando && ! ruta()?.trazo?.length" x-cloak>Esta ruta todavía no tiene camino. Usa "Trazar camino".</span>
        <span class="flex items-center gap-1"><span class="inline-flex size-4 items-center justify-center rounded-full bg-green-600 text-[9px] font-bold text-white">I</span> Inicio</span>
        <span class="flex items-center gap-1"><span class="inline-flex size-4 items-center justify-center rounded-full bg-red-600 text-[9px] font-bold text-white">F</span> Fin</span>
        <span class="flex items-center gap-1"><span class="inline-block h-3 w-5 rounded bg-blue-600/20"></span> Rango de cobertura</span>
        <span class="flex items-center gap-1"><span class="inline-block size-3 rounded-full border-2 border-amber-500 bg-zinc-500"></span> Dentro del rango, fuera de la ruta (<span x-text="enRango"></span>)</span>
        <span class="flex items-center gap-1"><span class="inline-block size-3 rounded-full bg-blue-600 ring-2 ring-white"></span> Parada numerada de la ruta</span>
        <span class="flex items-center gap-1"><span class="inline-block size-3 rounded-full bg-zinc-500"></span> Sin ruta</span>
    </div>

    <div x-ref="mapa" style="height: {{ $height }}; border-radius: 0.75rem;" class="z-0 ring-1 ring-huata-verde-100"></div>
</div>
