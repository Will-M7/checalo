import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js';

/*
 * Modelos 3D de los productos de Ecolácteos Huata, construidos con geometría en código (no hay archivos
 * GLB). Cada función devuelve un THREE.Group centrado en el origen, con una altura aproximada de 2.6.
 */

const AZUL = '#2864b4';
const AZUL_OSCURO = '#173d72';
const AMARILLO = '#f2c230';
const ROJO = '#c93445';
const VERDE = '#23865b';

/** Textura de etiqueta dibujada en un lienzo (se redibuja cuando cargan las tipografías). */
function etiquetaTextura({ ancho = 2048, alto = 512, fondo = AZUL, linea1, linea2, linea3, colorLinea1 = '#ffffff', colorLinea2 = AMARILLO, bandas = true, repetir = false, relleno = null }) {
    const lienzo = document.createElement('canvas');
    lienzo.width = ancho;
    lienzo.height = alto;
    const textura = new THREE.CanvasTexture(lienzo);
    textura.colorSpace = THREE.SRGBColorSpace;
    textura.anisotropy = 4;

    const dibujar = () => {
        const c = lienzo.getContext('2d');
        c.clearRect(0, 0, ancho, alto);
        if (relleno) { c.fillStyle = relleno; c.fillRect(0, 0, ancho, alto); }
        const x = 40, y = 40, w = repetir ? ancho - 80 : 880, h = alto - 80, r = 44;
        c.fillStyle = fondo;
        c.beginPath();
        c.moveTo(x + r, y);
        c.arcTo(x + w, y, x + w, y + h, r);
        c.arcTo(x + w, y + h, x, y + h, r);
        c.arcTo(x, y + h, x, y, r);
        c.arcTo(x, y, x + w, y, r);
        c.closePath();
        c.fill();
        if (bandas) {
            c.fillStyle = ROJO; c.fillRect(x, y + h - 54, w * 0.34, 54);
            c.fillStyle = AMARILLO; c.fillRect(x + w * 0.34, y + h - 54, w * 0.33, 54);
            c.fillStyle = VERDE; c.fillRect(x + w * 0.67, y + h - 54, w * 0.33, 54);
        }
        c.textAlign = 'center';
        c.fillStyle = colorLinea1;
        c.font = '800 112px "Bricolage Grotesque", "Arial Black", sans-serif';
        c.fillText(linea1, x + w / 2, y + 190);
        c.fillStyle = colorLinea2;
        c.fillText(linea2, x + w / 2, y + 310);
        if (linea3) {
            c.fillStyle = colorLinea1 === '#ffffff' ? 'rgba(255,255,255,0.85)' : 'rgba(6,27,63,0.75)';
            c.font = '600 38px "Figtree", Arial, sans-serif';
            c.fillText(linea3, x + w / 2, y + 372);
        }
        textura.needsUpdate = true;
    };
    dibujar();
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(dibujar);
    return textura;
}

/** Textura de poros para el queso. */
function texturaQueso() {
    const lienzo = document.createElement('canvas');
    lienzo.width = lienzo.height = 256;
    const c = lienzo.getContext('2d');
    c.fillStyle = '#f2de93';
    c.fillRect(0, 0, 256, 256);
    for (let i = 0; i < 90; i++) {
        const x = Math.random() * 256, y = Math.random() * 256, r = 2 + Math.random() * 6;
        c.fillStyle = 'rgba(190,150,60,0.28)';
        c.beginPath(); c.arc(x, y, r, 0, Math.PI * 2); c.fill();
        c.fillStyle = 'rgba(255,248,210,0.5)';
        c.beginPath(); c.arc(x - r * 0.3, y - r * 0.3, r * 0.5, 0, Math.PI * 2); c.fill();
    }
    const t = new THREE.CanvasTexture(lienzo);
    t.colorSpace = THREE.SRGBColorSpace;
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    return t;
}

/** Vaso de leche de cristal con etiqueta. Devuelve también el grupo de la leche para animar el oleaje. */
export function crearVasoLeche({ movil = false } = {}) {
    const ALTO = 2.6;
    const radioInterior = (y) => 0.6 + ((y - 0.26) / (ALTO - 0.26)) * 0.2;
    const radioExterior = (y) => 0.66 + ((y - 0.1) / (ALTO - 0.1)) * 0.22;
    const segmentos = movil ? 48 : 96;

    const perfil = [new THREE.Vector2(0, 0), new THREE.Vector2(0.56, 0), new THREE.Vector2(0.63, 0.03), new THREE.Vector2(0.66, 0.1)];
    for (let i = 1; i <= 14; i++) {
        const y = 0.1 + ((ALTO - 0.1) * i) / 14;
        perfil.push(new THREE.Vector2(radioExterior(y), y));
    }
    perfil.push(new THREE.Vector2(0.885, ALTO + 0.012), new THREE.Vector2(0.84, ALTO + 0.018), new THREE.Vector2(0.805, ALTO + 0.005));
    for (let i = 14; i >= 0; i--) {
        const y = 0.26 + ((ALTO - 0.26) * i) / 14;
        perfil.push(new THREE.Vector2(radioInterior(y), y));
    }
    perfil.push(new THREE.Vector2(0.56, 0.235), new THREE.Vector2(0.45, 0.22), new THREE.Vector2(0, 0.22));

    const cristal = new THREE.Mesh(
        new THREE.LatheGeometry(perfil, segmentos),
        new THREE.MeshPhysicalMaterial({
            color: 0xbfd8f3, metalness: 0, roughness: 0.04, transparent: true, opacity: 0.3, envMapIntensity: 2.4,
            clearcoat: 1, clearcoatRoughness: 0.05, specularIntensity: 1, depthWrite: false, side: THREE.DoubleSide,
        })
    );
    cristal.renderOrder = 3;

    const nivel = 2.08;
    const radioLeche = (y) => radioInterior(y) - 0.014;
    const perfilLeche = [new THREE.Vector2(0, 0.235), new THREE.Vector2(0.5, 0.235), new THREE.Vector2(radioLeche(0.3), 0.3)];
    for (let i = 1; i <= 10; i++) {
        const y = 0.3 + ((nivel - 0.3) * i) / 10;
        perfilLeche.push(new THREE.Vector2(radioLeche(y), y));
    }
    perfilLeche.push(new THREE.Vector2(radioLeche(nivel) - 0.03, nivel + 0.012), new THREE.Vector2(0, nivel + 0.015));
    const leche = new THREE.Mesh(
        new THREE.LatheGeometry(perfilLeche, segmentos),
        new THREE.MeshPhysicalMaterial({
            color: 0xf7f2e7, roughness: 0.34, metalness: 0, clearcoat: 0.45, clearcoatRoughness: 0.25, sheen: 1,
            sheenColor: new THREE.Color(0xffffff), emissive: 0xfff1d6, emissiveIntensity: 0.07, envMapIntensity: 0.8,
        })
    );
    const grupoLeche = new THREE.Group();
    grupoLeche.add(leche);

    const yEt = [0.55, 1.75];
    const etiqueta = new THREE.Mesh(
        new THREE.CylinderGeometry(radioExterior(yEt[1]) + 0.006, radioExterior(yEt[0]) + 0.006, yEt[1] - yEt[0], segmentos, 1, true),
        new THREE.MeshStandardMaterial({
            map: etiquetaTextura({ linea1: 'ECOLÁCTEOS', linea2: 'HUATA', linea3: 'LECHE FRESCA · PUNO · PERÚ' }),
            transparent: true, roughness: 0.42, metalness: 0.05, envMapIntensity: 0.9,
        })
    );
    etiqueta.position.y = (yEt[0] + yEt[1]) / 2;
    etiqueta.renderOrder = 2;

    const grupo = new THREE.Group();
    const interior = new THREE.Group();
    interior.add(grupoLeche, cristal, etiqueta);
    interior.position.y = -ALTO / 2;
    grupo.add(interior);
    return { grupo, grupoLeche };
}

/** Rueda de queso fresco con una cuña cortada al frente. */
export function crearQueso({ movil = false } = {}) {
    const segmentos = movil ? 48 : 96;
    const R = 1.12, H = 0.78, corte = 0.62;
    const mapa = texturaQueso();
    const corteta = new THREE.MeshStandardMaterial({ color: 0xfff0b8, roughness: 0.6, map: mapa, envMapIntensity: 0.5 });
    const costra = new THREE.MeshStandardMaterial({ color: 0xe7c35f, roughness: 0.52, envMapIntensity: 0.7 });
    const tapa = new THREE.MeshStandardMaterial({ color: 0xf6e29a, roughness: 0.5, map: mapa, envMapIntensity: 0.6 });

    const grupo = new THREE.Group();

    // Cuerpo: la rueda sin la cuña. El perfil redondea el canto.
    const cuerpo = new THREE.Mesh(new THREE.CylinderGeometry(R, R, H, segmentos, 1, false, corte, Math.PI * 2 - corte), [costra, tapa, tapa]);
    grupo.add(cuerpo);

    // Caras del corte (interior pálido y poroso).
    for (const ang of [corte, Math.PI * 2]) {
        const cara = new THREE.Mesh(new THREE.PlaneGeometry(R, H), corteta);
        cara.position.set((Math.sin(ang) * R) / 2, 0, (Math.cos(ang) * R) / 2);
        cara.rotation.y = ang - Math.PI / 2 + (ang === corte ? Math.PI : 0);
        grupo.add(cara);
    }

    // La cuña cortada, apoyada al frente
    const cuna = new THREE.Group();
    const trozo = new THREE.Mesh(new THREE.CylinderGeometry(R, R, H, 32, 1, false, 0, corte), [costra, tapa, tapa]);
    cuna.add(trozo);
    for (const ang of [0, corte]) {
        const cara = new THREE.Mesh(new THREE.PlaneGeometry(R, H), corteta);
        cara.position.set((Math.sin(ang) * R) / 2, 0, (Math.cos(ang) * R) / 2);
        cara.rotation.y = ang - Math.PI / 2 + (ang === 0 ? 0 : Math.PI);
        cuna.add(cara);
    }
    cuna.position.set(0.55, 0.0, 1.0);
    cuna.rotation.y = -0.35 - corte / 2;
    grupo.add(cuna);

    grupo.rotation.x = 0.28;
    grupo.scale.setScalar(1.05);
    const envoltura = new THREE.Group();
    envoltura.add(grupo);
    return { grupo: envoltura };
}

/** Vaso de yogurt con tapa de aluminio y etiqueta. */
export function crearYogur({ movil = false } = {}) {
    const segmentos = movil ? 48 : 96;
    const H = 2.2;
    const radio = (y) => 0.58 + (y / H) * 0.32;
    const perfil = [new THREE.Vector2(0, 0), new THREE.Vector2(0.5, 0), new THREE.Vector2(0.58, 0.04)];
    for (let i = 1; i <= 12; i++) {
        const y = (H * i) / 12;
        perfil.push(new THREE.Vector2(radio(y), y));
    }
    perfil.push(new THREE.Vector2(radio(H) + 0.05, H + 0.02), new THREE.Vector2(radio(H) + 0.06, H + 0.06));
    const taza = new THREE.Mesh(
        new THREE.LatheGeometry(perfil, segmentos),
        new THREE.MeshPhysicalMaterial({ color: 0xf4f7ff, roughness: 0.32, metalness: 0, clearcoat: 0.6, clearcoatRoughness: 0.2, envMapIntensity: 1, side: THREE.DoubleSide })
    );
    const tapa = new THREE.Mesh(
        new THREE.CylinderGeometry(radio(H) + 0.075, radio(H) + 0.075, 0.07, segmentos),
        new THREE.MeshStandardMaterial({ color: 0xdfe5ee, roughness: 0.22, metalness: 0.95, envMapIntensity: 1.6 })
    );
    tapa.position.y = H + 0.045;
    const etiqueta = new THREE.Mesh(
        new THREE.CylinderGeometry(radio(1.7) + 0.008, radio(0.35) + 0.008, 1.35, segmentos, 1, true),
        new THREE.MeshStandardMaterial({
            map: etiquetaTextura({ fondo: '#c93445', linea1: 'YOGURT', linea2: 'NATURAL', linea3: 'ECOLÁCTEOS HUATA', colorLinea2: '#ffffff', bandas: false, repetir: false }),
            transparent: true, roughness: 0.4, metalness: 0.05, envMapIntensity: 0.9,
        })
    );
    etiqueta.position.y = 1.025;
    const grupo = new THREE.Group();
    const interior = new THREE.Group();
    interior.add(taza, tapa, etiqueta);
    interior.position.y = -H / 2;
    grupo.add(interior);
    grupo.scale.setScalar(1.12);
    return { grupo };
}

/** Barra de mantequilla con envoltorio de papel a medio abrir. */
export function crearMantequilla() {
    const mantequilla = new THREE.Mesh(
        new RoundedBoxGeometry(2.1, 0.95, 1.1, 5, 0.1),
        new THREE.MeshPhysicalMaterial({ color: 0xf6d466, roughness: 0.38, clearcoat: 0.35, clearcoatRoughness: 0.4, sheen: 0.6, sheenColor: new THREE.Color(0xfff2b0), envMapIntensity: 0.8 })
    );
    const papel = new THREE.Mesh(
        new RoundedBoxGeometry(1.3, 1.0, 1.14, 5, 0.06),
        new THREE.MeshStandardMaterial({
            map: etiquetaTextura({ fondo: '#ffffff', linea1: 'MANTEQUILLA', linea2: 'HUATA', linea3: 'ECOLÁCTEOS · PUNO', colorLinea1: AZUL_OSCURO, colorLinea2: ROJO, bandas: true, repetir: true, ancho: 1024, alto: 512, relleno: '#ffffff' }),
            roughness: 0.55, metalness: 0.05, envMapIntensity: 0.6,
        })
    );
    papel.position.x = -0.38;
    const grupo = new THREE.Group();
    grupo.add(mantequilla, papel);
    grupo.rotation.x = 0.35;
    grupo.rotation.z = -0.1;
    grupo.scale.setScalar(1.15);
    return { grupo };
}

/** Libera la geometría, los materiales y las texturas de un grupo. */
export function liberar(objeto) {
    objeto.traverse((hijo) => {
        if (hijo.geometry) hijo.geometry.dispose();
        const materiales = Array.isArray(hijo.material) ? hijo.material : hijo.material ? [hijo.material] : [];
        materiales.forEach((m) => {
            if (m.map) m.map.dispose();
            m.dispose();
        });
    });
}
