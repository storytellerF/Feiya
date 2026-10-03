#!/usr/bin/env python3
"""Render Feiya's VectorDrawable brand source. Requires CairoSVG 2.9.1."""
from pathlib import Path
import xml.etree.ElementTree as ET
import cairosvg

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'app/src/main/res'
A = '{http://schemas.android.com/apk/res/android}'
BACKGROUND = ET.parse(RES / 'values/ic_launcher_background.xml').find('color').text


def paths(name):
    vector = ET.parse(RES / f'drawable/{name}.xml').getroot()
    return ''.join(f'<path fill="{p.attrib[A + "fillColor"]}" d="{p.attrib[A + "pathData"]}"/>'
                   for p in vector.findall('path'))


def svg(content, size=108):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}">{content}</svg>'


mark = paths('ic_launcher_foreground')
for density, pixels in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    for suffix, background in [('', f'<rect width="108" height="108" rx="24" fill="{BACKGROUND}"/>'),
                               ('_round', f'<circle cx="54" cy="54" r="54" fill="{BACKGROUND}"/>')]:
        # Legacy launchers do not apply the adaptive layer inset.
        art = svg(background + f'<g transform="translate(-13.5 -13.5) scale(1.25)">{mark}</g>')
        cairosvg.svg2png(bytestring=art.encode(), write_to=str(RES / f'mipmap-{density}/ic_launcher{suffix}.png'),
                        output_width=pixels, output_height=pixels)

logo = svg(f'<rect width="108" height="108" rx="24" fill="{BACKGROUND}"/>' + mark)
cairosvg.svg2png(bytestring=logo.encode(), write_to=str(RES / 'drawable-nodpi/feiya_logo.png'),
                output_width=512, output_height=512)
