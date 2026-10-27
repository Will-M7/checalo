import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { gsap } from 'gsap';
import { crearVasoLeche, liberar } from './modelos3d.js';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

/*
 * Hero 3D de Ecolácteos Huata: un vaso de leche de cristal que flota, gira solo, sigue al mouse,
 * se puede arrastrar y reacciona al scroll. WebGL real con Three.js (sin React: la página es Blade).
 * Si el navegador no puede dibujar 3D, el hero conserva la esfera SVG de respaldo.
 */

const escena = document.getElementById('hero3d');
const hero = document.getElementById('inicio');

if (escena && hero) {
    try {
        iniciar(escena, hero);
    } catch (error) {
        console.warn('Hero 3D desactivado:', error);
        escena.dataset.estado = 'error';
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
    camera.position.set(0, 0, 9);

    // Reflejos de estudio sin descargar nada: el cristal necesita algo que reflejar.
    const pmrem = new THREE.PMREMGenerator(renderer);
    scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;

    const key = new THREE.DirectionalLight(0xffffff, 2.2);
    key.position.set(-3, 5, 6);
    scene.add(key);
    const rimAzul = new THREE.DirectionalLight(0x4c85e3, 3.2);
    rimAzul.position.set(5, 2, -4);
    scene.add(rimAzul);
    const rimAmarillo = new THREE.PointLight(0xf5c400, 14, 14, 2);
    rimAmarillo.position.set(-4, -1, -2);
    scene.add(rimAmarillo);

    const raiz = new THREE.Group(); // posición, escala y flotación
    const giro = new THREE.Group(); // rotación sobre su eje y reacción al mouse
    raiz.add(giro);
    scene.add(raiz);

    const ALTO = 2.6;
    const modeloVaso = crearVasoLeche({ movil });
    const grupoLeche = modeloVaso.grupoLeche;
    giro.add(modeloVaso.grupo);

    // --- Sombra suave en el piso (se achica cuando el vaso sube)
    const sombraLienzo = document.createElement('canvas');
    sombraLienzo.width = sombraLienzo.height = 128;
    const sc = sombraLienzo.getContext('2d');
    const grad = sc.createRadialGradient(64, 64, 4, 64, 64, 62);
    grad.addColorStop(0, 'rgba(2,10,35,0.62)');
    grad.addColorStop(1, 'rgba(2,10,35,0)');
    sc.fillStyle = grad;
    sc.fillRect(0, 0, 128, 128);
    const sombra = new THREE.Mesh(
        new THREE.PlaneGeometry(3.4, 3.4),
        new THREE.MeshBasicMaterial({ map: new THREE.CanvasTexture(sombraLienzo), transparent: true, depthWrite: false })
    );
    sombra.rotation.x = -Math.PI / 2;
    sombra.position.y = -ALTO / 2 - 0.55;
    raiz.add(sombra);

    // --- Gotas de leche que orbitan
    const orbita = new THREE.Group();
    raiz.add(orbita);
    const materialGota = grupoLeche.children[0].material.clone();
    materialGota.envMapIntensity = 1.2;
    const gotas = [];
    const cantidadGotas = movil ? 6 : 10;
    for (let i = 0; i < cantidadGotas; i++) {
        const tam = 0.06 + ((i * 37) % 10) / 10 * 0.14;
        const gota = new THREE.Mesh(new THREE.SphereGeometry(tam, 24, 16), materialGota);
        const ang = (i / cantidadGotas) * Math.PI * 2;
        const radio = 1.7 + ((i * 13) % 7) * 0.16;
        gota.userData = { ang, radio, altura: -1 + ((i * 29) % 10) * 0.26, fase: i * 1.7, vel: 0.6 + (i % 4) * 0.12 };
        orbita.add(gota);
        gotas.push(gota);
    }

    // --- Partículas de polvo con profundidad
    const cantidadParticulas = movil ? 70 : 170;
    const posiciones = new Float32Array(cantidadParticulas * 3);
    for (let i = 0; i < cantidadParticulas; i++) {
        posiciones[i * 3] = (Math.random() - 0.5) * 13;
        posiciones[i * 3 + 1] = (Math.random() - 0.5) * 8;
        posiciones[i * 3 + 2] = -5 + Math.random() * 9;
    }
    const geoParticulas = new THREE.BufferGeometry();
    geoParticulas.setAttribute('position', new THREE.BufferAttribute(posiciones, 3));
    const particulas = new THREE.Points(
        geoParticulas,
        new THREE.PointsMaterial({ color: 0xffffff, size: 0.045, transparent: true, opacity: 0.55, depthWrite: false, sizeAttenuation: true })
    );
    scene.add(particulas);

    // --- Diseño según el tamaño de pantalla
    const base = { x: 0, y: 0, s: 1 };
    function disponer() {
        const ancho = contenedor.clientWidth || 1;
        const alto = contenedor.clientHeight || 1;
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, movil ? 1.5 : 2));
        renderer.setSize(ancho, alto, false);
        camera.aspect = ancho / alto;
        camera.updateProjectionMatrix();
        const anchoVisible = 2 * camera.position.z * Math.tan((camera.fov * Math.PI) / 360) * camera.aspect;
        if (camera.aspect > 1.3) {
            base.x = Math.min(2.7, anchoVisible * 0.27);
            base.y = -0.15;
            base.s = Math.min(1.45, 0.75 + camera.aspect * 0.42);
        } else {
            base.x = 0;
            base.y = -1.45;
            base.s = 0.8;
        }
    }
    disponer();
    new ResizeObserver(() => { disponer(); pedirFotograma(); }).observe(contenedor);

    // --- Estado de la interacción
    const e = { yaw: 0, velYaw: 0, arrastrando: false, ultimoX: 0, inactivo: 99, mx: 0, my: 0, offYaw: 0, offPitch: 0, ritmo: 1, p: 0, pSuave: 0 };

    window.addEventListener('pointermove', (ev) => {
        const r = seccion.getBoundingClientRect();
        e.mx = ((ev.clientX - r.left) / r.width - 0.5) * 2;
        e.my = ((ev.clientY - r.top) / r.height - 0.5) * 2;
        pedirFotograma();
    }, { passive: true });
    window.addEventListener('pointerleave', () => { e.mx = 0; e.my = 0; });

    contenedor.addEventListener('pointerdown', (ev) => {
        e.arrastrando = true;
        e.ultimoX = ev.clientX;
        e.inactivo = 0;
        contenedor.classList.add('cursor-grabbing');
        contenedor.setPointerCapture?.(ev.pointerId);
    });
    contenedor.addEventListener('pointermove', (ev) => {
        if (!e.arrastrando) return;
        const dx = ev.clientX - e.ultimoX;
        e.ultimoX = ev.clientX;
        e.yaw += dx * 0.011;
        e.velYaw = Math.max(-6, Math.min(6, dx * 0.011 * 60));
        e.inactivo = 0;
        pedirFotograma();
    });
    const soltar = () => {
        e.arrastrando = false;
        contenedor.classList.remove('cursor-grabbing');
    };
    contenedor.addEventListener('pointerup', soltar);
    contenedor.addEventListener('pointercancel', soltar);

    // --- Scroll: ScrollTrigger mueve la cámara y el vaso; el texto entra y sale
    let disparadores = [];
    const escrollActivo = !reducirMovimiento;
    if (escrollActivo) {
        gsap.registerPlugin(ScrollTrigger);
        disparadores.push(
            ScrollTrigger.create({
                trigger: seccion,
                start: 'top top',
                end: 'bottom bottom',
                onUpdate: (self) => { e.p = self.progress; pedirFotograma(); },
            })
        );
        const linea = gsap.timeline({ scrollTrigger: { trigger: seccion, start: 'top top', end: 'bottom bottom', scrub: 0.8 } });
        linea
            .to(seccion.querySelector('.hero-copy'), { opacity: 0, x: -70, filter: 'blur(8px)', ease: 'power2.in', duration: 0.38 }, 0)
            .to(seccion.querySelector('.hero-hint'), { opacity: 0, duration: 0.2 }, 0)
            .to(seccion.querySelector('.hero-velo'), { opacity: 0.45, ease: 'none', duration: 1 }, 0)
            .fromTo(seccion.querySelector('.hero-caption'), { opacity: 0, y: 50 }, { opacity: 1, y: 0, ease: 'power2.out', duration: 0.3 }, 0.5)
            .to(seccion.querySelector('.hero-caption'), { opacity: 0, y: -30, ease: 'power1.in', duration: 0.18 }, 0.92);
        disparadores.push(linea.scrollTrigger);
    }

    // --- Bucle de render
    const reloj = new THREE.Clock();
    let visible = true;
    let marco = 0;
    let pendiente = false;

    const suave = (t) => t * t * (3 - 2 * t);

    function actualizar(dt, t) {
        e.inactivo += dt;
        if (!e.arrastrando) {
            // inercia tras soltar y regreso suave al giro automático
            e.yaw += e.velYaw * dt;
            e.velYaw *= Math.pow(0.02, dt);
            const objetivoRitmo = e.inactivo > 1.2 ? 1 : 0;
            e.ritmo += (objetivoRitmo - e.ritmo) * (1 - Math.exp(-1.6 * dt));
            if (!reducirMovimiento) e.yaw += 0.42 * dt * e.ritmo;
        }

        const objYaw = e.mx * 0.55;
        const objPitch = e.my * 0.22;
        const k = 1 - Math.exp(-4.5 * dt);
        e.offYaw += (objYaw - e.offYaw) * k;
        e.offPitch += (objPitch - e.offPitch) * k;
        e.pSuave += (e.p - e.pSuave) * (1 - Math.exp(-5 * dt));
        const p = suave(Math.min(1, Math.max(0, e.pSuave)));

        giro.rotation.y = e.yaw + e.offYaw + p * (Math.PI / 2);
        giro.rotation.x = e.offPitch * 0.9;
        giro.rotation.z = reducirMovimiento ? 0 : Math.sin(t * 0.6) * 0.03 - e.offYaw * 0.05;

        // la leche se queda un poco atrás del vaso, como un líquido
        grupoLeche.rotation.z += ((-(objYaw - e.offYaw) * 0.22 + Math.sin(t * 1.7) * 0.012) - grupoLeche.rotation.z) * (1 - Math.exp(-3 * dt));
        grupoLeche.rotation.x += ((e.offPitch * 0.12 + Math.cos(t * 1.3) * 0.01) - grupoLeche.rotation.x) * (1 - Math.exp(-3 * dt));

        const flota = reducirMovimiento ? 0 : Math.sin(t * 0.9) * 0.12;
        raiz.position.set(base.x * (1 - p), base.y + flota - p * 0.1, 0);
        const escala = base.s * (1 + 0.1 * p);
        raiz.scale.setScalar(escala);
        camera.position.z = 9 - 1.1 * p;
        camera.position.x = Math.sin(p * Math.PI) * 0.6;
        camera.lookAt(0, 0, 0);

        // estado visible para pruebas y depuración (giro total, escala y avance del scroll)
        contenedor.dataset.giro = giro.rotation.y.toFixed(3);
        contenedor.dataset.escala = escala.toFixed(3);
        contenedor.dataset.scroll = p.toFixed(3);

        sombra.material.opacity = 0.9 - flota * 1.2;
        sombra.scale.setScalar(1 - flota * 0.8);

        if (!reducirMovimiento) {
            orbita.rotation.y = t * 0.16;
            gotas.forEach((g) => {
                const d = g.userData;
                const a = d.ang + t * 0.08 * d.vel;
                g.position.set(Math.cos(a) * d.radio, d.altura + Math.sin(t * d.vel + d.fase) * 0.22, Math.sin(a) * d.radio * 0.7);
            });
            const pos = geoParticulas.attributes.position;
            for (let i = 0; i < cantidadParticulas; i++) {
                let y = pos.getY(i) + dt * (0.08 + (i % 5) * 0.025);
                if (y > 4.2) y = -4.2;
                pos.setY(i, y);
                pos.setX(i, pos.getX(i) + Math.sin(t * 0.3 + i) * dt * 0.05);
            }
            pos.needsUpdate = true;
            particulas.rotation.y = Math.sin(t * 0.05) * 0.1;
        } else if (!gotas[0].userData.colocada) {
            gotas.forEach((g) => {
                const d = g.userData;
                g.position.set(Math.cos(d.ang) * d.radio, d.altura, Math.sin(d.ang) * d.radio * 0.7);
                d.colocada = true;
            });
        }
    }

    function dibujar() {
        pendiente = false;
        const dt = Math.min(reloj.getDelta(), 0.05);
        actualizar(dt, reloj.elapsedTime);
        renderer.render(scene, camera);
        if (!reducirMovimiento && visible && !document.hidden) marco = requestAnimationFrame(dibujar);
    }

    function pedirFotograma() {
        if (!reducirMovimiento || pendiente) return;
        pendiente = true;
        requestAnimationFrame(dibujar);
    }

    // el bucle solo corre mientras el hero se ve (ahorra batería)
    const observador = new IntersectionObserver(([entrada]) => {
        const antes = visible;
        visible = entrada.isIntersecting;
        if (visible && !antes && !reducirMovimiento) { reloj.getDelta(); marco = requestAnimationFrame(dibujar); }
    }, { threshold: 0 });
    observador.observe(seccion);
    const alCambiarVisibilidad = () => {
        if (!document.hidden && visible && !reducirMovimiento) { cancelAnimationFrame(marco); reloj.getDelta(); marco = requestAnimationFrame(dibujar); }
    };
    document.addEventListener('visibilitychange', alCambiarVisibilidad);

    // el hero 3D ya está listo: se oculta la alternativa y se activa el recorrido por scroll
    document.documentElement.classList.add('hero3d-ok');
    if (escrollActivo) document.documentElement.classList.add('hero3d-scroll');
    contenedor.dataset.estado = 'listo';
    if (escrollActivo) ScrollTrigger.refresh();
    marco = requestAnimationFrame(dibujar);

    window.addEventListener('pagehide', () => {
        cancelAnimationFrame(marco);
        observador.disconnect();
        disparadores.forEach((d) => d && d.kill());
        document.removeEventListener('visibilitychange', alCambiarVisibilidad);
        renderer.dispose();
        pmrem.dispose();
        liberar(modeloVaso.grupo);
        [sombra, particulas].forEach((m) => { m.geometry.dispose(); m.material.dispose(); });
        gotas.forEach((g) => g.geometry.dispose());
    });
}
