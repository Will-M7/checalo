import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { gsap } from 'gsap';
import { crearVasoLeche, crearQueso, crearYogur, crearMantequilla, liberar } from './modelos3d.js';

/*
 * Vitrina 3D: leche, queso, yogurt y mantequilla en un mismo escenario. Al elegir otro producto la cámara
 * orbita, el actual se va al fondo girando, el nuevo llega desde el costado y el fondo cambia de tono.
 * El producto se puede arrastrar para verlo desde otros ángulos.
 */

const contenedor = document.getElementById('vitrina3d');
const seccion = document.getElementById('vitrina');

if (contenedor && seccion) {
    try {
        iniciar(contenedor, seccion);
    } catch (error) {
        console.warn('Vitrina 3D desactivada:', error);
        contenedor.dataset.estado = 'error';
        seccion.classList.add('hidden');
    }
}

function iniciar(contenedor, seccion) {
    const reducirMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const movil = window.matchMedia('(max-width: 767px), (pointer: coarse)').matches;

    const renderer = new THREE.WebGLRenderer({ antialias: !movil, alpha: true, powerPreference: 'high-performance' });
    renderer.setClearColor(0x000000, 0);
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.05;
    renderer.domElement.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;display:block;outline:none';
    renderer.domElement.setAttribute('aria-hidden', 'true');
    contenedor.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(32, 1, 0.1, 60);
    const RADIO_CAMARA = 9;
    const pmrem = new THREE.PMREMGenerator(renderer);
    scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;

    const key = new THREE.DirectionalLight(0xffffff, 2.4);
    key.position.set(-3, 5, 6);
    scene.add(key);
    const rim = new THREE.DirectionalLight(0xaecbff, 2.6);
    rim.position.set(5, 2, -4);
    scene.add(rim);
    const calido = new THREE.PointLight(0xf5c400, 12, 14, 2);
    calido.position.set(-4, -1, -2);
    scene.add(calido);

    // --- Productos
    const definiciones = [
        { id: 'leche', crear: crearVasoLeche, escala: 1.15 },
        { id: 'queso', crear: crearQueso, escala: 1.05 },
        { id: 'yogur', crear: crearYogur, escala: 1.05 },
        { id: 'mantequilla', crear: crearMantequilla, escala: 1.0 },
    ];
    const items = definiciones.map((d, i) => {
        const modelo = d.crear({ movil });
        const envoltura = new THREE.Group();
        envoltura.add(modelo.grupo);
        envoltura.visible = i === 0;
        scene.add(envoltura);
        // `estado` es lo que anima GSAP; cada fotograma se copia al objeto 3D
        return { id: d.id, envoltura, modelo, escalaBase: d.escala, estado: { x: 0, y: 0, z: 0, giro: 0, esc: i === 0 ? 1 : 0.6 } };
    });

    // sombra de contacto
    const sombraLienzo = document.createElement('canvas');
    sombraLienzo.width = sombraLienzo.height = 128;
    const sc = sombraLienzo.getContext('2d');
    const grad = sc.createRadialGradient(64, 64, 4, 64, 64, 62);
    grad.addColorStop(0, 'rgba(2,10,35,0.55)');
    grad.addColorStop(1, 'rgba(2,10,35,0)');
    sc.fillStyle = grad;
    sc.fillRect(0, 0, 128, 128);
    const sombra = new THREE.Mesh(new THREE.PlaneGeometry(4, 4), new THREE.MeshBasicMaterial({ map: new THREE.CanvasTexture(sombraLienzo), transparent: true, depthWrite: false }));
    sombra.rotation.x = -Math.PI / 2;
    sombra.position.y = -1.85;
    scene.add(sombra);

    // partículas de fondo
    const nParticulas = movil ? 40 : 100;
    const pos = new Float32Array(nParticulas * 3);
    for (let i = 0; i < nParticulas; i++) {
        pos[i * 3] = (Math.random() - 0.5) * 12;
        pos[i * 3 + 1] = (Math.random() - 0.5) * 7;
        pos[i * 3 + 2] = -5 + Math.random() * 8;
    }
    const geoPart = new THREE.BufferGeometry();
    geoPart.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    const particulas = new THREE.Points(geoPart, new THREE.PointsMaterial({ color: 0xffffff, size: 0.04, transparent: true, opacity: 0.5, depthWrite: false }));
    scene.add(particulas);

    // --- Estado de interacción
    const cam = { ang: 0 };
    const e = { yaw: 0, vel: 0, arrastrando: false, ultimoX: 0, inactivo: 99, ritmo: 1, mx: 0, my: 0, offYaw: 0, offPitch: 0 };
    let activo = 0;
    let animando = false;

    function disponer() {
        const ancho = contenedor.clientWidth || 1;
        const alto = contenedor.clientHeight || 1;
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, movil ? 1.5 : 2));
        renderer.setSize(ancho, alto, false);
        camera.aspect = ancho / alto;
        camera.updateProjectionMatrix();
    }
    disponer();
    new ResizeObserver(() => { disponer(); pedirFotograma(); }).observe(contenedor);

    contenedor.addEventListener('pointermove', (ev) => {
        const r = contenedor.getBoundingClientRect();
        e.mx = ((ev.clientX - r.left) / r.width - 0.5) * 2;
        e.my = ((ev.clientY - r.top) / r.height - 0.5) * 2;
        if (e.arrastrando) {
            const dx = ev.clientX - e.ultimoX;
            e.ultimoX = ev.clientX;
            e.yaw += dx * 0.011;
            e.vel = Math.max(-6, Math.min(6, dx * 0.011 * 60));
            e.inactivo = 0;
        }
        pedirFotograma();
    });
    contenedor.addEventListener('pointerleave', () => { e.mx = 0; e.my = 0; });
    contenedor.addEventListener('pointerdown', (ev) => {
        e.arrastrando = true;
        e.ultimoX = ev.clientX;
        e.inactivo = 0;
        contenedor.classList.add('cursor-grabbing');
        contenedor.setPointerCapture?.(ev.pointerId);
    });
    const soltar = () => { e.arrastrando = false; contenedor.classList.remove('cursor-grabbing'); };
    contenedor.addEventListener('pointerup', soltar);
    contenedor.addEventListener('pointercancel', soltar);

    // --- Datos reales: precio y producto del catálogo que coincide con cada tipo
    const catalogo = (window.HUATA && window.HUATA.productos) || [];
    const palabras = { leche: /leche/i, queso: /queso/i, yogur: /yogur/i, mantequilla: /mantequilla/i };
    const textos = {
        leche: ['Leche fresca', 'Leche fresca de las familias productoras de Huata, acopiada cada día.', '#dbe9f7'],
        queso: ['Queso fresco', 'Queso fresco artesanal, elaborado en nuestra planta con recetas propias.', '#fbefc9'],
        yogur: ['Yogurt', 'Yogurt natural hecho con la leche fresca de Huata.', '#f9dde1'],
        mantequilla: ['Mantequilla', 'Mantequilla elaborada con la leche de nuestras familias productoras.', '#fdf1bd'],
    };
    const nombreEl = document.getElementById('vitrina-nombre');
    const descEl = document.getElementById('vitrina-desc');
    const precioEl = document.getElementById('vitrina-precio');
    const botonEl = document.getElementById('vitrina-agregar');
    const infoEl = document.getElementById('vitrina-info');
    const fondoEl = seccion.querySelector('.vitrina-fondo');
    const pestanas = Array.from(seccion.querySelectorAll('[data-vitrina]'));
    const soles = (n) => 'S/ ' + n.toFixed(2);

    function pintarTextos(id) {
        const [nombre, desc] = textos[id];
        nombreEl.textContent = nombre;
        descEl.textContent = desc;
        const coincidencias = catalogo.filter((p) => palabras[id].test(p.nombre));
        if (coincidencias.length) {
            const mejor = coincidencias.reduce((a, b) => (a.precio <= b.precio ? a : b));
            precioEl.textContent = (coincidencias.length > 1 ? 'Desde ' : '') + soles(mejor.precio);
            precioEl.classList.remove('hidden');
            botonEl.dataset.producto = mejor.id;
            botonEl.firstElementChild.textContent = 'Agregar al carrito';
        } else {
            precioEl.classList.add('hidden');
            delete botonEl.dataset.producto;
            botonEl.firstElementChild.textContent = 'Ver productos';
        }
        pestanas.forEach((b) => {
            const sel = b.dataset.vitrina === id;
            b.setAttribute('aria-selected', sel ? 'true' : 'false');
            b.tabIndex = sel ? 0 : -1;
        });
    }
    pintarTextos(items[0].id);
    fondoEl.style.backgroundColor = textos.leche[2];

    botonEl.addEventListener('click', () => {
        if (botonEl.dataset.producto) {
            document.dispatchEvent(new CustomEvent('huata:agregar', { detail: { id: botonEl.dataset.producto } }));
        } else {
            document.getElementById('productos')?.scrollIntoView({ behavior: 'smooth' });
        }
    });

    // --- Transición entre productos: orbita de cámara, salida al fondo, entrada lateral, cambio de fondo y de texto
    function ir(indice) {
        if (indice === activo || animando) return;
        const sale = items[activo];
        const entra = items[indice];
        const dir = indice > activo ? 1 : -1;
        const nuevoId = entra.id;

        if (reducirMovimiento) {
            sale.envoltura.visible = false;
            entra.envoltura.visible = true;
            Object.assign(entra.estado, { x: 0, y: 0, z: 0, esc: 1 });
            activo = indice;
            pintarTextos(nuevoId);
            fondoEl.style.backgroundColor = textos[nuevoId][2];
            pedirFotograma();
            return;
        }

        animando = true;
        entra.envoltura.visible = true;
        Object.assign(entra.estado, { x: dir * 3.4, y: 0, z: -4.5, esc: 0.55, giro: -dir * Math.PI });
        const tl = gsap.timeline({ onComplete: () => { sale.envoltura.visible = false; activo = indice; animando = false; } });
        tl.to(cam, { ang: dir * 0.75, duration: 0.7, ease: 'power2.in' }, 0)
            .to(cam, { ang: 0, duration: 1, ease: 'power3.out' }, 0.7)
            .to(sale.estado, { x: -dir * 3.4, z: -4.5, esc: 0.55, giro: sale.estado.giro + dir * Math.PI * 1.4, duration: 0.9, ease: 'power2.in' }, 0)
            .to(entra.estado, { x: 0, z: 0, esc: 1, giro: 0, duration: 1.15, ease: 'power3.out' }, 0.55)
            .to(fondoEl, { backgroundColor: textos[nuevoId][2], duration: 1.1, ease: 'power1.inOut' }, 0.15)
            .to(infoEl, { opacity: 0, y: -18, duration: 0.25, ease: 'power1.in' }, 0)
            .add(() => pintarTextos(nuevoId), 0.3)
            .fromTo(infoEl, { opacity: 0, y: 28 }, { opacity: 1, y: 0, duration: 0.6, ease: 'power3.out' }, 0.6);
    }
    pestanas.forEach((b, i) => {
        b.addEventListener('click', () => ir(i));
        b.addEventListener('keydown', (ev) => {
            if (ev.key === 'ArrowRight' || ev.key === 'ArrowLeft') {
                const sig = (i + (ev.key === 'ArrowRight' ? 1 : -1) + pestanas.length) % pestanas.length;
                pestanas[sig].focus();
                ir(sig);
            }
        });
    });

    // --- Render
    const reloj = new THREE.Clock();
    let visible = false;
    let marco = 0;
    let pendiente = false;

    function actualizar(dt, t) {
        e.inactivo += dt;
        if (!e.arrastrando) {
            e.yaw += e.vel * dt;
            e.vel *= Math.pow(0.02, dt);
            e.ritmo += ((e.inactivo > 1.2 ? 1 : 0) - e.ritmo) * (1 - Math.exp(-1.6 * dt));
            if (!reducirMovimiento) e.yaw += 0.5 * dt * e.ritmo;
        }
        const k = 1 - Math.exp(-4.5 * dt);
        e.offYaw += (e.mx * 0.4 - e.offYaw) * k;
        e.offPitch += (e.my * 0.18 - e.offPitch) * k;

        camera.position.set(Math.sin(cam.ang) * RADIO_CAMARA, 0.2, Math.cos(cam.ang) * RADIO_CAMARA);
        camera.lookAt(0, 0, 0);

        const flota = reducirMovimiento ? 0 : Math.sin(t * 0.9) * 0.1;
        items.forEach((it) => {
            if (!it.envoltura.visible) return;
            const s = it.estado;
            it.envoltura.position.set(s.x, s.y + flota, s.z);
            it.envoltura.scale.setScalar(it.escalaBase * s.esc);
            it.envoltura.rotation.y = s.giro + e.yaw + e.offYaw;
            it.envoltura.rotation.x = e.offPitch * 0.8;
            if (it.modelo.grupoLeche) {
                it.modelo.grupoLeche.rotation.z += ((-e.mx * 0.08 + Math.sin(t * 1.7) * 0.012) - it.modelo.grupoLeche.rotation.z) * (1 - Math.exp(-3 * dt));
            }
        });
        sombra.material.opacity = 0.9 - flota * 1.2;

        if (!reducirMovimiento) {
            const p = geoPart.attributes.position;
            for (let i = 0; i < nParticulas; i++) {
                let y = p.getY(i) + dt * (0.07 + (i % 5) * 0.02);
                if (y > 3.8) y = -3.8;
                p.setY(i, y);
            }
            p.needsUpdate = true;
        }
        contenedor.dataset.activo = items[activo].id;
        contenedor.dataset.giro = (e.yaw + e.offYaw).toFixed(3);
    }

    function dibujar() {
        pendiente = false;
        const dt = Math.min(reloj.getDelta(), 0.05);
        actualizar(dt, reloj.elapsedTime);
        renderer.render(scene, camera);
        if (!reducirMovimiento && visible && !document.hidden) marco = requestAnimationFrame(dibujar);
    }
    function pedirFotograma() {
        if (pendiente || (!reducirMovimiento && visible)) return;
        pendiente = true;
        requestAnimationFrame(dibujar);
    }

    const observador = new IntersectionObserver(([entrada]) => {
        const antes = visible;
        visible = entrada.isIntersecting;
        if (visible && !antes) { reloj.getDelta(); cancelAnimationFrame(marco); marco = requestAnimationFrame(dibujar); }
    }, { rootMargin: '120px' });
    observador.observe(seccion);
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && visible && !reducirMovimiento) { cancelAnimationFrame(marco); reloj.getDelta(); marco = requestAnimationFrame(dibujar); }
    });

    document.documentElement.classList.add('vitrina3d-ok');
    contenedor.dataset.estado = 'listo';
    pedirFotograma();

    window.addEventListener('pagehide', () => {
        cancelAnimationFrame(marco);
        observador.disconnect();
        items.forEach((it) => liberar(it.envoltura));
        [sombra, particulas].forEach((m) => { m.geometry.dispose(); m.material.dispose(); });
        renderer.dispose();
        pmrem.dispose();
    });
}
