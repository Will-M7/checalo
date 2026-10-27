import * as THREE from 'three';
import { gsap } from 'gsap';

/*
 * Intro 3D de Ecolácteos Huata (una vez por visita):
 * 1) Partículas en un espacio 3D mientras la cámara avanza entre ellas y aparece el logotipo.
 * 2) Una corriente de leche (shader) sube y llena la pantalla; luego baja y deja ver la portada.
 * Si algo falla, o con "reducir movimiento", se salta directo a la portada.
 */

const intro = document.getElementById('intro');
const LLAVE = 'huataIntroVista';

function llegar() {
    document.body.classList.add('llegado');
}

function terminar() {
    try { sessionStorage.setItem(LLAVE, '1'); } catch (e) { /* sin almacenamiento */ }
    document.documentElement.style.overflow = '';
    llegar();
    intro?.remove();
}

if (intro) {
    let visto = false;
    try { visto = sessionStorage.getItem(LLAVE) === '1'; } catch (e) { /* sin almacenamiento */ }
    const reducir = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (visto || reducir) {
        terminar();
    } else {
        try {
            iniciar(intro);
        } catch (error) {
            console.warn('Intro 3D desactivada:', error);
            terminar();
        }
    }
} else {
    llegar();
}

function iniciar(intro) {
    const movil = window.matchMedia('(max-width: 767px), (pointer: coarse)').matches;
    const lienzo = intro.querySelector('#intro-canvas');
    const renderer = new THREE.WebGLRenderer({ canvas: lienzo, antialias: !movil, alpha: false, powerPreference: 'high-performance' });
    renderer.setClearColor(0x12305d, 1);
    renderer.autoClear = false;

    intro.classList.remove('hidden');
    document.documentElement.style.overflow = 'hidden';

    // --- Escena 1: partículas, cámara y logotipo
    const escena = new THREE.Scene();
    const camara = new THREE.PerspectiveCamera(60, 1, 0.1, 80);
    camara.position.z = 14;

    const N = movil ? 260 : 650;
    const posiciones = new Float32Array(N * 3);
    const datos = [];
    for (let i = 0; i < N; i++) {
        const radio = 0.6 + Math.pow(Math.random(), 0.7) * 11;
        const ang = Math.random() * Math.PI * 2;
        const z = -34 + Math.random() * 40;
        datos.push({ radio, ang, z, v: 0.4 + Math.random() * 1.2 });
        posiciones[i * 3] = Math.cos(ang) * radio;
        posiciones[i * 3 + 1] = Math.sin(ang) * radio;
        posiciones[i * 3 + 2] = z;
    }
    const geometria = new THREE.BufferGeometry();
    geometria.setAttribute('position', new THREE.BufferAttribute(posiciones, 3));
    const punto = document.createElement('canvas');
    punto.width = punto.height = 64;
    const pc = punto.getContext('2d');
    const g = pc.createRadialGradient(32, 32, 0, 32, 32, 32);
    g.addColorStop(0, 'rgba(255,255,255,1)');
    g.addColorStop(0.35, 'rgba(255,255,255,0.7)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    pc.fillStyle = g;
    pc.fillRect(0, 0, 64, 64);
    const particulas = new THREE.Points(geometria, new THREE.PointsMaterial({
        map: new THREE.CanvasTexture(punto), size: 0.32, transparent: true, opacity: 0.9, depthWrite: false, sizeAttenuation: true, blending: THREE.AdditiveBlending,
    }));
    escena.add(particulas);

    // Logotipo en una insignia circular (el JPG del logo tiene fondo blanco)
    const logoLienzo = document.createElement('canvas');
    logoLienzo.width = logoLienzo.height = 512;
    const logoTextura = new THREE.CanvasTexture(logoLienzo);
    logoTextura.colorSpace = THREE.SRGBColorSpace;
    const logo = new THREE.Mesh(
        new THREE.PlaneGeometry(2.7, 2.7),
        new THREE.MeshBasicMaterial({ map: logoTextura, transparent: true, depthWrite: false })
    );
    logo.position.set(0, 0.85, -8);
    logo.scale.setScalar(0.1);
    logo.material.opacity = 0;
    escena.add(logo);

    const imagenLogo = new Image();
    imagenLogo.onload = () => {
        const c = logoLienzo.getContext('2d');
        c.clearRect(0, 0, 512, 512);
        c.fillStyle = '#ffffff';
        c.beginPath(); c.arc(256, 256, 250, 0, Math.PI * 2); c.fill();
        c.save();
        c.beginPath(); c.arc(256, 256, 236, 0, Math.PI * 2); c.clip();
        const h = 440, w = (imagenLogo.width / imagenLogo.height) * h;
        c.drawImage(imagenLogo, 256 - w / 2, 256 - h / 2, w, h);
        c.restore();
        logoTextura.needsUpdate = true;
    };
    imagenLogo.src = intro.dataset.logo;

    // --- Escena 2: corriente de leche en un shader a pantalla completa
    const escenaLeche = new THREE.Scene();
    const camaraLeche = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
    const uniformes = { uNivel: { value: -0.3 }, uTiempo: { value: 0 }, uAspecto: { value: 1 } };
    const leche = new THREE.Mesh(
        new THREE.PlaneGeometry(2, 2),
        new THREE.ShaderMaterial({
            uniforms: uniformes,
            transparent: true,
            depthTest: false,
            depthWrite: false,
            vertexShader: 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }',
            fragmentShader: `
                precision highp float;
                varying vec2 vUv;
                uniform float uNivel, uTiempo, uAspecto;
                float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
                float ruido(vec2 p){
                    vec2 i = floor(p), f = fract(p);
                    f = f * f * (3.0 - 2.0 * f);
                    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
                }
                void main(){
                    float x = vUv.x * uAspecto;
                    float ola = 0.05 * sin(x * 4.2 + uTiempo * 1.7) + 0.032 * sin(x * 9.5 - uTiempo * 2.5) + 0.07 * (ruido(vec2(x * 2.2, uTiempo * 0.55)) - 0.5);
                    float d = uNivel + ola - vUv.y;
                    if (d <= 0.0) { gl_FragColor = vec4(0.0); return; }
                    float profundo = clamp(d * 2.4, 0.0, 1.0);
                    vec3 color = mix(vec3(1.0, 0.996, 0.98), vec3(0.9, 0.87, 0.8), profundo);
                    float vetas = ruido(vec2(x * 3.2, vUv.y * 1.4 + uTiempo * 0.25));
                    color *= 0.955 + 0.07 * vetas;
                    float cresta = smoothstep(0.0, 0.012, d) * (1.0 - smoothstep(0.012, 0.085, d));
                    color += vec3(0.78, 0.88, 1.0) * cresta * 0.5;
                    float brillo = smoothstep(0.0, 0.018, d) * (1.0 - smoothstep(0.018, 0.045, d));
                    color += vec3(1.0) * brillo * 0.28;
                    gl_FragColor = vec4(color, smoothstep(0.0, 0.004, d));
                }
            `,
        })
    );
    escenaLeche.add(leche);

    function disponer() {
        const ancho = window.innerWidth, alto = window.innerHeight;
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, movil ? 1.5 : 1.75));
        renderer.setSize(ancho, alto, false);
        camara.aspect = ancho / alto;
        camara.updateProjectionMatrix();
        uniformes.uAspecto.value = ancho / alto;
    }
    disponer();
    window.addEventListener('resize', disponer);

    // --- Texto "ECOLÁCTEOS HUATA" con letras 3D
    const titulo = intro.querySelector('#intro-titulo');
    const texto = titulo.dataset.texto;
    titulo.setAttribute('aria-label', texto);
    const letras = [...texto].map((ch) => {
        const s = document.createElement('span');
        s.textContent = ch === ' ' ? ' ' : ch;
        s.setAttribute('aria-hidden', 'true');
        s.style.display = 'inline-block';
        s.style.opacity = '0';
        titulo.appendChild(s);
        return s;
    });
    const lema = intro.querySelector('#intro-lema');

    // --- Línea de tiempo
    const e = { camara: 0, nivel: -0.3, empuje: 0 };
    let terminado = false;
    let particulasVisibles = true;

    const tl = gsap.timeline({ paused: true, onComplete: cerrar });
    tl.to(e, { camara: 1, duration: 3.8, ease: 'power1.inOut' }, 0)
        .to(logo.material, { opacity: 1, duration: 0.9, ease: 'power2.out' }, 0.7)
        .to(logo.scale, { x: 1, y: 1, z: 1, duration: 1.7, ease: 'expo.out' }, 0.7)
        .to(logo.position, { z: 0, duration: 1.7, ease: 'expo.out' }, 0.7)
        .fromTo(logo.rotation, { y: -1.1 }, { y: 0, duration: 1.9, ease: 'power3.out' }, 0.7)
        .fromTo(letras, { opacity: 0, rotationX: -95, y: 36, z: -160, transformOrigin: '50% 100%' }, { opacity: 1, rotationX: 0, y: 0, z: 0, duration: 0.9, ease: 'back.out(1.6)', stagger: 0.045 }, 1.6)
        .fromTo(lema, { opacity: 0, y: 14 }, { opacity: 1, y: 0, duration: 0.7, ease: 'power2.out' }, 2.5)
        // la cámara atraviesa el logotipo mientras la leche sube
        .to(e, { empuje: 1, duration: 1.7, ease: 'power2.in' }, 3.5)
        .to(e, { nivel: 1.4, duration: 1.7, ease: 'power2.in' }, 3.5)
        .to([titulo, lema], { opacity: 0, duration: 0.5, ease: 'power1.in' }, 4.4)
        .add(() => { particulasVisibles = false; escena.visible = false; llegar(); }, 5.25)
        .to(e, { nivel: -0.3, duration: 1.55, ease: 'power2.inOut' }, 5.3);

    // Leyendas de las escenas de origen y producción
    const leyenda = intro.querySelector('#intro-leyenda');
    const mostrarLeyenda = (texto, t) => {
        tl.add(() => { leyenda.textContent = texto; }, t)
            .fromTo(leyenda, { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: 0.5, ease: 'power2.out' }, t)
            .to(leyenda, { opacity: 0, duration: 0.4, ease: 'power1.in' }, t + 1.3);
    };
    mostrarLeyenda('EL ORIGEN · HUATA, PUNO', 0.3);
    mostrarLeyenda('LA PRODUCCIÓN · HECHA CON CUIDADO', 2.4);

    // Control de reproducción
    const botonPausa = intro.querySelector('#intro-pausa');
    botonPausa.addEventListener('click', () => {
        const pausada = !tl.paused();
        tl.paused(pausada);
        botonPausa.setAttribute('aria-pressed', pausada ? 'true' : 'false');
        botonPausa.querySelector('.ico-pausa').classList.toggle('hidden', pausada);
        botonPausa.querySelector('.ico-play').classList.toggle('hidden', !pausada);
        botonPausa.querySelector('.txt').textContent = pausada ? 'Reanudar' : 'Pausar';
    });

    function dibujar(tiempo, dt) {
        if (terminado) return;
        const z = 14 - 10.5 * e.camara - 5 * e.empuje;
        camara.position.z = z;
        camara.position.x = Math.sin(tiempo * 0.35) * 0.4 * (1 - e.empuje);
        camara.position.y = Math.cos(tiempo * 0.3) * 0.25 * (1 - e.empuje);
        camara.lookAt(0, 0, 0);
        logo.position.x = 0;
        logo.position.y = 0.85 + 0.25 * e.empuje;
        if (particulasVisibles) {
            const p = geometria.attributes.position;
            for (let i = 0; i < N; i++) {
                const d = datos[i];
                d.radio *= 1 - 0.035 * dt;
                d.ang += 0.04 * dt * d.v;
                d.z += d.v * dt * 2.2;
                if (d.z > camara.position.z - 0.5) { d.z = -34; d.radio = 0.8 + Math.random() * 11; }
                p.setXYZ(i, Math.cos(d.ang) * d.radio, Math.sin(d.ang) * d.radio, d.z);
            }
            p.needsUpdate = true;
        }
        uniformes.uNivel.value = e.nivel;
        uniformes.uTiempo.value = tiempo;

        renderer.clear();
        renderer.render(escena, camara);
        renderer.clearDepth();
        renderer.render(escenaLeche, camaraLeche);
    }

    const reloj = new THREE.Clock();
    const paso = () => dibujar(reloj.elapsedTime, Math.min(reloj.getDelta(), 0.05));
    gsap.ticker.add(paso);

    function cerrar() {
        if (terminado) return;
        terminado = true;
        gsap.ticker.remove(paso);
        window.removeEventListener('resize', disponer);
        tl.kill();
        geometria.dispose();
        particulas.material.map.dispose();
        particulas.material.dispose();
        logo.geometry.dispose();
        logoTextura.dispose();
        logo.material.dispose();
        leche.geometry.dispose();
        leche.material.dispose();
        renderer.dispose();
        terminar();
    }

    function saltar() {
        if (terminado) return;
        terminado = true;
        gsap.ticker.remove(paso);
        tl.kill();
        llegar();
        gsap.to(intro, { opacity: 0, duration: 0.45, ease: 'power1.out', onComplete: () => { terminado = false; cerrar(); } });
    }
    intro.querySelector('#intro-saltar').addEventListener('click', saltar);
    document.addEventListener('keydown', (ev) => { if (ev.key === 'Escape') saltar(); });

    // arranca cuando el logotipo y las tipografías ya están listos (máximo 2,5 s de espera)
    Promise.race([
        Promise.all([new Promise((ok) => { if (imagenLogo.complete) ok(); else { imagenLogo.addEventListener('load', ok); imagenLogo.addEventListener('error', ok); } }), document.fonts ? document.fonts.ready : Promise.resolve()]),
        new Promise((ok) => setTimeout(ok, 2500)),
    ]).then(() => { reloj.start(); tl.play(0); });
}
