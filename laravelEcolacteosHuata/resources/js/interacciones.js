import { gsap } from 'gsap';

/*
 * Interacciones de escritorio: cursor personalizado discreto, botones magnéticos e inclinación 3D de las
 * tarjetas. Se desactivan con puntero táctil o con "reducir movimiento".
 */

const fino = window.matchMedia('(pointer: fine)').matches;
const reducir = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

if (fino && !reducir) {
    // --- Cursor: un anillo que sigue al puntero con retraso; crece sobre lo interactivo y muestra una indicación
    const anillo = document.createElement('div');
    anillo.className = 'cursor-huata';
    anillo.setAttribute('aria-hidden', 'true');
    const texto = document.createElement('span');
    anillo.appendChild(texto);
    document.body.appendChild(anillo);

    const xTo = gsap.quickTo(anillo, 'x', { duration: 0.35, ease: 'power3' });
    const yTo = gsap.quickTo(anillo, 'y', { duration: 0.35, ease: 'power3' });
    let visible = false;

    window.addEventListener('pointermove', (ev) => {
        if (!visible) { visible = true; anillo.classList.add('visible'); }
        xTo(ev.clientX);
        yTo(ev.clientY);
    }, { passive: true });
    document.addEventListener('pointerleave', () => { visible = false; anillo.classList.remove('visible'); });

    document.addEventListener('pointerover', (ev) => {
        const guia = ev.target.closest('[data-cursor]');
        const accion = ev.target.closest('a, button, [role="tab"], label, input, select');
        if (guia) {
            texto.textContent = guia.dataset.cursor;
            anillo.dataset.modo = 'guia';
        } else if (accion) {
            texto.textContent = '';
            anillo.dataset.modo = 'accion';
        } else {
            texto.textContent = '';
            anillo.dataset.modo = '';
        }
    });
    document.addEventListener('pointerdown', () => anillo.classList.add('presionado'));
    document.addEventListener('pointerup', () => anillo.classList.remove('presionado'));

    // --- Botones magnéticos: se acercan un poco al cursor
    document.querySelectorAll('.boton-magnetico').forEach((boton) => {
        const mover = gsap.quickTo(boton, 'x', { duration: 0.4, ease: 'power3' });
        const moverY = gsap.quickTo(boton, 'y', { duration: 0.4, ease: 'power3' });
        boton.addEventListener('pointermove', (ev) => {
            const r = boton.getBoundingClientRect();
            mover((ev.clientX - (r.left + r.width / 2)) * 0.22);
            moverY((ev.clientY - (r.top + r.height / 2)) * 0.28);
        });
        boton.addEventListener('pointerleave', () => { mover(0); moverY(0); });
    });

    // --- Tarjetas de producto: inclinación 3D suave según el cursor
    document.querySelectorAll('.tarjeta-producto').forEach((tarjeta) => {
        const rotX = gsap.quickTo(tarjeta, 'rotationX', { duration: 0.5, ease: 'power3' });
        const rotY = gsap.quickTo(tarjeta, 'rotationY', { duration: 0.5, ease: 'power3' });
        gsap.set(tarjeta, { transformPerspective: 900 });
        tarjeta.addEventListener('pointermove', (ev) => {
            const r = tarjeta.getBoundingClientRect();
            rotY(((ev.clientX - r.left) / r.width - 0.5) * 7);
            rotX(-((ev.clientY - r.top) / r.height - 0.5) * 7);
        });
        tarjeta.addEventListener('pointerleave', () => { rotX(0); rotY(0); });
    });
}
