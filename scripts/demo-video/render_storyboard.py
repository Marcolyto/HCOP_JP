#!/usr/bin/env python3
"""Renderiza cuadros PNG de un storyboard de HCOP_JP con Pillow."""

from __future__ import annotations

import argparse
import json
import math
import shutil
import sys
import tempfile
import textwrap
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:  # pragma: no cover - mensaje operativo
    raise SystemExit(
        "Falta Pillow. Instálelo con: py -m pip install Pillow"
    ) from exc


@dataclass(frozen=True)
class Canvas:
    width: int
    height: int
    fps: int
    background: str


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description=(
            "Genera una secuencia PNG 1920x1080 desde capturas y un storyboard. "
            "El MP4 se ensambla luego con render-video.ps1."
        )
    )
    parser.add_argument(
        "--storyboard",
        type=Path,
        default=script_dir / "storyboard.json",
        help="Archivo storyboard.json.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=script_dir.parent.parent
        / "docs"
        / "media"
        / "demo-flujo-7-pasos"
        / ".frames",
        help="Directorio para frame_000001.png y manifest.json.",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Valida el storyboard sin generar cuadros.",
    )
    parser.add_argument(
        "--allow-missing",
        action="store_true",
        help="En validación, informa capturas faltantes sin devolver error.",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Ejecuta un fixture temporal y verifica el renderizador.",
    )
    parser.add_argument(
        "--write-srt",
        type=Path,
        help=(
            "Genera un SRT sincronizado desde el campo subtitle de cada escena. "
            "Puede combinarse con --validate-only."
        ),
    )
    return parser.parse_args()


def load_storyboard(path: Path) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
    except FileNotFoundError as exc:
        raise ValueError(f"No existe el storyboard: {path}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(
            f"JSON inválido en {path}, línea {exc.lineno}: {exc.msg}"
        ) from exc
    if not isinstance(data, dict):
        raise ValueError("El storyboard debe ser un objeto JSON.")
    return data


def canvas_from(data: dict[str, Any]) -> Canvas:
    raw = data.get("canvas")
    if not isinstance(raw, dict):
        raise ValueError("Falta el objeto canvas.")
    width = positive_int(raw.get("width"), "canvas.width")
    height = positive_int(raw.get("height"), "canvas.height")
    fps = positive_int(raw.get("fps"), "canvas.fps")
    background = str(raw.get("background", "#EEF5F9"))
    if width != 1920 or height != 1080:
        raise ValueError("El video de demostración debe usar canvas 1920x1080.")
    return Canvas(width, height, fps, background)


def positive_int(value: Any, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{field} debe ser un número entero positivo.")
    result = int(value)
    if result <= 0 or result != value:
        raise ValueError(f"{field} debe ser un número entero positivo.")
    return result


def positive_number(value: Any, field: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{field} debe ser un número positivo.")
    result = float(value)
    if result <= 0:
        raise ValueError(f"{field} debe ser un número positivo.")
    return result


def bounded_number(
    value: Any, field: str, lower: float, upper: float
) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{field} debe ser un número.")
    result = float(value)
    if not lower <= result <= upper:
        raise ValueError(
            f"{field} debe estar entre {lower:g} y {upper:g}."
        )
    return result


def normalize_rect(
    value: Any, field: str, canvas: Canvas
) -> list[float]:
    if not isinstance(value, list) or len(value) != 4:
        raise ValueError(f"{field} debe ser [x1, y1, x2, y2].")
    x1 = bounded_number(value[0], f"{field}[0]", 0, canvas.width)
    y1 = bounded_number(value[1], f"{field}[1]", 0, canvas.height)
    x2 = bounded_number(value[2], f"{field}[2]", 0, canvas.width)
    y2 = bounded_number(value[3], f"{field}[3]", 0, canvas.height)
    if x2 <= x1 or y2 <= y1:
        raise ValueError(f"{field} debe tener ancho y alto positivos.")
    return [x1, y1, x2, y2]


def normalize_point(
    value: Any, field: str, canvas: Canvas
) -> list[float]:
    if not isinstance(value, list) or len(value) != 2:
        raise ValueError(f"{field} debe ser [x, y].")
    return [
        bounded_number(value[0], f"{field}[0]", 0, canvas.width),
        bounded_number(value[1], f"{field}[1]", 0, canvas.height),
    ]


def normalize_interval(
    item: dict[str, Any], field: str, duration: float
) -> tuple[float, float]:
    start = bounded_number(
        item.get("start", 0), f"{field}.start", 0, duration
    )
    end = bounded_number(
        item.get("end", duration), f"{field}.end", 0, duration
    )
    if end <= start:
        raise ValueError(f"{field}.end debe ser posterior a start.")
    return start, end


def normalize_annotations(
    scene: dict[str, Any], scene_id: str, duration: float, canvas: Canvas
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any] | None]:
    raw_highlights = scene.get("highlights", [])
    if not isinstance(raw_highlights, list):
        raise ValueError(f"{scene_id}.highlights debe ser una lista.")
    highlights: list[dict[str, Any]] = []
    for index, raw in enumerate(raw_highlights):
        field = f"{scene_id}.highlights[{index}]"
        if not isinstance(raw, dict):
            raise ValueError(f"{field} debe ser un objeto.")
        start, end = normalize_interval(raw, field, duration)
        highlights.append(
            {
                **raw,
                "rect": normalize_rect(raw.get("rect"), f"{field}.rect", canvas),
                "start": start,
                "end": end,
                "label": str(raw.get("label", "")).strip(),
                "color": str(raw.get("color", "#006BFF")),
            }
        )

    raw_arrows = scene.get("arrows", [])
    if not isinstance(raw_arrows, list):
        raise ValueError(f"{scene_id}.arrows debe ser una lista.")
    arrows: list[dict[str, Any]] = []
    for index, raw in enumerate(raw_arrows):
        field = f"{scene_id}.arrows[{index}]"
        if not isinstance(raw, dict):
            raise ValueError(f"{field} debe ser un objeto.")
        start, end = normalize_interval(raw, field, duration)
        arrows.append(
            {
                **raw,
                "from": normalize_point(raw.get("from"), f"{field}.from", canvas),
                "to": normalize_point(raw.get("to"), f"{field}.to", canvas),
                "start": start,
                "end": end,
                "label": str(raw.get("label", "")).strip(),
                "color": str(raw.get("color", "#006BFF")),
            }
        )

    raw_panel = scene.get("panel")
    panel: dict[str, Any] | None = None
    if raw_panel is not None:
        field = f"{scene_id}.panel"
        if not isinstance(raw_panel, dict):
            raise ValueError(f"{field} debe ser un objeto.")
        start, end = normalize_interval(raw_panel, field, duration)
        items = raw_panel.get("items", [])
        if not isinstance(items, list) or not items:
            raise ValueError(f"{field}.items requiere al menos un elemento.")
        normalized_items = []
        for index, item in enumerate(items):
            item_field = f"{field}.items[{index}]"
            if isinstance(item, str):
                text = item.strip()
                tone = "blue"
            elif isinstance(item, dict):
                text = str(item.get("text", "")).strip()
                tone = str(item.get("tone", "blue")).strip()
            else:
                raise ValueError(f"{item_field} debe ser texto u objeto.")
            if not text:
                raise ValueError(f"{item_field}.text no puede estar vacío.")
            normalized_items.append({"text": text, "tone": tone})
        panel = {
            **raw_panel,
            "rect": normalize_rect(raw_panel.get("rect"), f"{field}.rect", canvas),
            "start": start,
            "end": end,
            "title": str(raw_panel.get("title", "")).strip(),
            "items": normalized_items,
        }
    return highlights, arrows, panel


def resolved_scene(
    scene: dict[str, Any],
    index: int,
    defaults: dict[str, Any],
    storyboard_dir: Path,
    canvas: Canvas,
) -> dict[str, Any]:
    if not isinstance(scene, dict):
        raise ValueError(f"scenes[{index}] debe ser un objeto.")
    scene_id = str(scene.get("id", "")).strip()
    if not scene_id:
        raise ValueError(f"scenes[{index}].id es obligatorio.")
    capture_value = str(scene.get("capture", "")).strip()
    if not capture_value:
        raise ValueError(f"{scene_id}: capture es obligatorio.")
    capture = Path(capture_value)
    if not capture.is_absolute():
        capture = (storyboard_dir / capture).resolve()
    if capture.suffix.lower() != ".png":
        raise ValueError(f"{scene_id}: la captura debe ser PNG.")
    duration = positive_number(
        scene.get("duration_seconds", defaults.get("duration_seconds", 10)),
        f"{scene_id}.duration_seconds",
    )
    transition = positive_number(
        scene.get(
            "transition_seconds", defaults.get("transition_seconds", 0.45)
        ),
        f"{scene_id}.transition_seconds",
    )
    if transition * 2 >= duration:
        raise ValueError(f"{scene_id}: la transición es demasiado larga.")
    title_duration = positive_number(
        scene.get(
            "title_duration_seconds",
            defaults.get("title_duration_seconds", 3.2),
        ),
        f"{scene_id}.title_duration_seconds",
    )
    cursor_path = scene.get("cursor_path", [])
    if not isinstance(cursor_path, list) or not cursor_path:
        raise ValueError(f"{scene_id}: cursor_path requiere al menos un punto.")
    previous_t = -1.0
    normalized_path: list[dict[str, Any]] = []
    for point_index, point in enumerate(cursor_path):
        if not isinstance(point, dict):
            raise ValueError(
                f"{scene_id}.cursor_path[{point_index}] debe ser un objeto."
            )
        t = float(point.get("t", -1))
        x = float(point.get("x", -1))
        y = float(point.get("y", -1))
        if t < 0 or t > duration or t < previous_t:
            raise ValueError(
                f"{scene_id}: tiempos de cursor fuera de rango o desordenados."
            )
        if not 0 <= x < canvas.width or not 0 <= y < canvas.height:
            raise ValueError(
                f"{scene_id}: cursor ({x}, {y}) fuera del canvas."
            )
        normalized_path.append(
            {"t": t, "x": x, "y": y, "click": bool(point.get("click", False))}
        )
        previous_t = t
    subtitle = str(scene.get("subtitle", scene.get("caption", ""))).strip()
    if not subtitle:
        raise ValueError(f"{scene_id}: subtitle es obligatorio.")
    highlights, arrows, panel = normalize_annotations(
        scene, scene_id, duration, canvas
    )
    return {
        **scene,
        "id": scene_id,
        "capture_path": capture,
        "duration_seconds": duration,
        "transition_seconds": transition,
        "title_duration_seconds": title_duration,
        "cursor_path": normalized_path,
        "subtitle": subtitle,
        "highlights": highlights,
        "arrows": arrows,
        "panel": panel,
    }


def validate_storyboard(
    data: dict[str, Any], storyboard_path: Path, allow_missing: bool = False
) -> tuple[Canvas, dict[str, Any], list[dict[str, Any]], list[Path]]:
    if data.get("version") != 1:
        raise ValueError("La versión admitida del storyboard es 1.")
    canvas = canvas_from(data)
    defaults = data.get("defaults", {})
    if not isinstance(defaults, dict):
        raise ValueError("defaults debe ser un objeto.")
    scenes_raw = data.get("scenes")
    if not isinstance(scenes_raw, list) or not scenes_raw:
        raise ValueError("scenes debe contener al menos una escena.")
    scenes = [
        resolved_scene(scene, index, defaults, storyboard_path.parent, canvas)
        for index, scene in enumerate(scenes_raw)
    ]
    ids = [scene["id"] for scene in scenes]
    if len(ids) != len(set(ids)):
        raise ValueError("Los id de escenas no pueden repetirse.")
    missing = [
        scene["capture_path"]
        for scene in scenes
        if not scene["capture_path"].is_file()
    ]
    if missing and not allow_missing:
        formatted = "\n".join(f"  - {path}" for path in missing)
        raise ValueError(
            "Faltan capturas PNG. Tome las siete capturas antes de renderizar:\n"
            f"{formatted}"
        )
    return canvas, defaults, scenes, missing


def load_font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    names = (
        ["segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"]
        if bold
        else ["segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"]
    )
    roots = [
        Path("C:/Windows/Fonts"),
        Path("/usr/share/fonts/truetype/dejavu"),
    ]
    for root in roots:
        for name in names:
            candidate = root / name
            if candidate.is_file():
                return ImageFont.truetype(str(candidate), size)
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def fit_capture(path: Path, canvas: Canvas) -> Image.Image:
    with Image.open(path) as source:
        image = source.convert("RGB")
    scale = min(canvas.width / image.width, canvas.height / image.height)
    width = max(1, round(image.width * scale))
    height = max(1, round(image.height * scale))
    resampling = getattr(Image, "Resampling", Image).LANCZOS
    image = image.resize((width, height), resampling)
    frame = Image.new("RGB", (canvas.width, canvas.height), canvas.background)
    frame.paste(image, ((canvas.width - width) // 2, (canvas.height - height) // 2))
    return frame


def interpolate_cursor(path: list[dict[str, Any]], time: float) -> tuple[float, float]:
    if time <= path[0]["t"]:
        return path[0]["x"], path[0]["y"]
    if time >= path[-1]["t"]:
        return path[-1]["x"], path[-1]["y"]
    for left, right in zip(path, path[1:]):
        if left["t"] <= time <= right["t"]:
            span = max(0.0001, right["t"] - left["t"])
            progress = (time - left["t"]) / span
            eased = progress * progress * (3 - 2 * progress)
            return (
                left["x"] + (right["x"] - left["x"]) * eased,
                left["y"] + (right["y"] - left["y"]) * eased,
            )
    return path[-1]["x"], path[-1]["y"]


def hex_rgba(value: str, alpha: int) -> tuple[int, int, int, int]:
    color = value.lstrip("#")
    if len(color) != 6:
        return 55, 168, 230, alpha
    return (
        int(color[0:2], 16),
        int(color[2:4], 16),
        int(color[4:6], 16),
        alpha,
    )


def draw_title(
    frame: Image.Image, scene: dict[str, Any], time: float
) -> Image.Image:
    title_duration = scene["title_duration_seconds"]
    if time > title_duration:
        return frame
    fade_out = min(1.0, max(0.0, (title_duration - time) / 0.4))
    fade_in = min(1.0, max(0.0, time / 0.3))
    alpha = round(232 * min(fade_in, fade_out))
    if alpha <= 0:
        return frame
    layer = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(
        (68, 64, 1030, 226),
        radius=22,
        fill=(255, 255, 255, alpha),
        outline=(196, 215, 225, alpha),
        width=2,
    )
    badge = str(scene.get("step", ""))
    draw.ellipse((94, 93, 184, 183), fill=(29, 132, 190, alpha))
    draw.text(
        (139, 137),
        badge,
        fill=(255, 255, 255, alpha),
        font=load_font(42, bold=True),
        anchor="mm",
    )
    draw.text(
        (216, 91),
        str(scene.get("title", "")),
        fill=(25, 54, 69, alpha),
        font=load_font(40, bold=True),
    )
    draw.text(
        (218, 151),
        str(scene.get("caption", "")),
        fill=(63, 85, 97, alpha),
        font=load_font(24),
    )
    return Image.alpha_composite(frame.convert("RGBA"), layer).convert("RGB")


TONE_COLORS = {
    "blue": "#006BFF",
    "green": "#138A53",
    "amber": "#B46900",
    "red": "#C83532",
    "gray": "#5C6B73",
}


def active_during(item: dict[str, Any], time: float) -> bool:
    return float(item["start"]) <= time <= float(item["end"])


def color_for(item: dict[str, Any], default: str = "#006BFF") -> str:
    raw = str(item.get("color", default))
    return TONE_COLORS.get(raw, raw)


def draw_pill(
    draw: ImageDraw.ImageDraw,
    xy: tuple[float, float],
    text: str,
    color: str,
    alpha: int = 245,
) -> None:
    if not text:
        return
    font = load_font(23, bold=True)
    x, y = xy
    box = draw.textbbox((x, y), text, font=font)
    width = box[2] - box[0] + 30
    height = box[3] - box[1] + 18
    draw.rounded_rectangle(
        (x, y, x + width, y + height),
        radius=10,
        fill=hex_rgba(color, alpha),
        outline=(255, 255, 255, min(255, alpha)),
        width=2,
    )
    draw.text(
        (x + 15, y + 7),
        text,
        fill=(255, 255, 255, alpha),
        font=font,
    )


def draw_highlights(
    draw: ImageDraw.ImageDraw,
    highlights: list[dict[str, Any]],
    time: float,
) -> None:
    for item in highlights:
        if not active_during(item, time):
            continue
        x1, y1, x2, y2 = item["rect"]
        color = color_for(item)
        pulse = 0.5 + 0.5 * math.sin(time * math.pi * 2.4)
        width = 5 + round(pulse * 2)
        draw.rounded_rectangle(
            (x1, y1, x2, y2),
            radius=12,
            fill=hex_rgba(color, 24),
            outline=hex_rgba(color, 245),
            width=width,
        )
        label_y = y1 - 45 if y1 >= 54 else y2 + 8
        draw_pill(draw, (x1, label_y), item.get("label", ""), color)


def draw_arrow(
    draw: ImageDraw.ImageDraw, item: dict[str, Any]
) -> None:
    x1, y1 = item["from"]
    x2, y2 = item["to"]
    color = color_for(item)
    rgba = hex_rgba(color, 245)
    draw.line((x1, y1, x2, y2), fill=rgba, width=8)
    angle = math.atan2(y2 - y1, x2 - x1)
    arrow_size = 24
    left = (
        x2 - arrow_size * math.cos(angle - math.pi / 6),
        y2 - arrow_size * math.sin(angle - math.pi / 6),
    )
    right = (
        x2 - arrow_size * math.cos(angle + math.pi / 6),
        y2 - arrow_size * math.sin(angle + math.pi / 6),
    )
    draw.polygon([(x2, y2), left, right], fill=rgba)
    label = item.get("label", "")
    if label:
        mid_x = (x1 + x2) / 2
        mid_y = (y1 + y2) / 2 - 48
        draw_pill(draw, (mid_x, mid_y), label, color)


def draw_info_panel(
    draw: ImageDraw.ImageDraw, panel: dict[str, Any], time: float
) -> None:
    if not active_during(panel, time):
        return
    x1, y1, x2, y2 = panel["rect"]
    draw.rounded_rectangle(
        (x1, y1, x2, y2),
        radius=18,
        fill=(255, 255, 255, 246),
        outline=(177, 199, 212, 255),
        width=3,
    )
    cursor_y = y1 + 24
    title = panel.get("title", "")
    if title:
        draw.text(
            (x1 + 24, cursor_y),
            title,
            fill=(22, 55, 74, 255),
            font=load_font(26, bold=True),
        )
        cursor_y += 50
    body_font = load_font(22, bold=True)
    for item in panel["items"]:
        color = TONE_COLORS.get(item["tone"], TONE_COLORS["blue"])
        lines = textwrap.wrap(item["text"], width=max(18, int((x2 - x1) / 15)))
        row_height = max(44, len(lines) * 29 + 14)
        if cursor_y + row_height > y2 - 16:
            break
        draw.rounded_rectangle(
            (x1 + 18, cursor_y, x2 - 18, cursor_y + row_height),
            radius=10,
            fill=hex_rgba(color, 20),
            outline=hex_rgba(color, 160),
            width=2,
        )
        draw.ellipse(
            (x1 + 30, cursor_y + 15, x1 + 46, cursor_y + 31),
            fill=hex_rgba(color, 255),
        )
        draw.multiline_text(
            (x1 + 57, cursor_y + 9),
            "\n".join(lines),
            fill=(39, 61, 74, 255),
            font=body_font,
            spacing=3,
        )
        cursor_y += row_height + 12


def draw_annotations(
    frame: Image.Image, scene: dict[str, Any], time: float
) -> Image.Image:
    highlights = scene.get("highlights", [])
    arrows = scene.get("arrows", [])
    panel = scene.get("panel")
    if not highlights and not arrows and panel is None:
        return frame
    layer = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw_highlights(draw, highlights, time)
    for item in arrows:
        if active_during(item, time):
            draw_arrow(draw, item)
    if panel is not None:
        draw_info_panel(draw, panel, time)
    return Image.alpha_composite(frame.convert("RGBA"), layer).convert("RGB")


def draw_cursor(
    frame: Image.Image,
    scene: dict[str, Any],
    cursor_defaults: dict[str, Any],
    time: float,
) -> Image.Image:
    x, y = interpolate_cursor(scene["cursor_path"], time)
    size = int(cursor_defaults.get("size", 34))
    halo_radius = int(cursor_defaults.get("halo_radius", 34))
    color = str(cursor_defaults.get("halo_color", "#37A8E6"))
    layer = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    pulse = 0.5 + 0.5 * math.sin(time * math.pi * 3)
    radius = halo_radius + round(pulse * 7)
    draw.ellipse(
        (x - radius, y - radius, x + radius, y + radius),
        fill=hex_rgba(color, 28),
        outline=hex_rgba(color, 115),
        width=3,
    )
    for point in scene["cursor_path"]:
        if point["click"] and abs(time - point["t"]) <= 0.32:
            click_progress = abs(time - point["t"]) / 0.32
            click_radius = round(16 + 38 * click_progress)
            draw.ellipse(
                (
                    point["x"] - click_radius,
                    point["y"] - click_radius,
                    point["x"] + click_radius,
                    point["y"] + click_radius,
                ),
                outline=hex_rgba(color, round(220 * (1 - click_progress))),
                width=5,
            )
    shadow = [
        (x + 4, y + 5),
        (x + size * 0.38, y + size * 1.05),
        (x + size * 0.56, y + size * 0.76),
        (x + size * 0.83, y + size * 0.92),
        (x + size * 0.98, y + size * 0.72),
        (x + size * 0.70, y + size * 0.55),
        (x + size, y + size * 0.43),
    ]
    cursor = [
        (x, y),
        (x + size * 0.38, y + size),
        (x + size * 0.55, y + size * 0.70),
        (x + size * 0.79, y + size * 0.86),
        (x + size * 0.94, y + size * 0.67),
        (x + size * 0.67, y + size * 0.51),
        (x + size * 0.96, y + size * 0.40),
    ]
    draw.polygon(shadow, fill=(0, 0, 0, 95))
    draw.polygon(cursor, fill=(255, 255, 255, 255), outline=(22, 45, 56, 255))
    return Image.alpha_composite(frame.convert("RGBA"), layer).convert("RGB")


def clean_output_directory(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
    for candidate in path.glob("frame_*.png"):
        candidate.unlink()
    manifest = path / "manifest.json"
    if manifest.exists():
        manifest.unlink()


def render(
    canvas: Canvas,
    defaults: dict[str, Any],
    scenes: list[dict[str, Any]],
    output_dir: Path,
) -> dict[str, Any]:
    clean_output_directory(output_dir)
    captures = [fit_capture(scene["capture_path"], canvas) for scene in scenes]
    cursor_defaults = defaults.get("cursor", {})
    if not isinstance(cursor_defaults, dict):
        cursor_defaults = {}
    frame_number = 1
    rendered_scenes = []
    for scene_index, scene in enumerate(scenes):
        frame_count = round(scene["duration_seconds"] * canvas.fps)
        transition = scene["transition_seconds"]
        for local_frame in range(frame_count):
            time = local_frame / canvas.fps
            frame = captures[scene_index].copy()
            if scene_index == 0 and time < transition:
                background = Image.new("RGB", frame.size, canvas.background)
                frame = Image.blend(background, frame, time / transition)
            remaining = scene["duration_seconds"] - time
            if scene_index + 1 < len(scenes) and remaining <= transition:
                alpha = 1 - max(0.0, remaining) / transition
                frame = Image.blend(frame, captures[scene_index + 1], alpha)
            frame = draw_title(frame, scene, time)
            frame = draw_annotations(frame, scene, time)
            frame = draw_cursor(frame, scene, cursor_defaults, time)
            frame.save(
                output_dir / f"frame_{frame_number:06d}.png",
                "PNG",
                compress_level=2,
            )
            frame_number += 1
        rendered_scenes.append(
            {
                "id": scene["id"],
                "frames": frame_count,
                "duration_seconds": scene["duration_seconds"],
            }
        )
    manifest = {
        "width": canvas.width,
        "height": canvas.height,
        "fps": canvas.fps,
        "frame_count": frame_number - 1,
        "duration_seconds": (frame_number - 1) / canvas.fps,
        "scenes": rendered_scenes,
    }
    with (output_dir / "manifest.json").open("w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    return manifest


def srt_timestamp(seconds: float) -> str:
    milliseconds = max(0, round(seconds * 1000))
    hours, remainder = divmod(milliseconds, 3_600_000)
    minutes, remainder = divmod(remainder, 60_000)
    whole_seconds, milliseconds = divmod(remainder, 1000)
    return (
        f"{hours:02d}:{minutes:02d}:{whole_seconds:02d},"
        f"{milliseconds:03d}"
    )


def write_srt(scenes: list[dict[str, Any]], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    current = 0.0
    blocks = []
    for index, scene in enumerate(scenes, start=1):
        duration = float(scene["duration_seconds"])
        start = current + min(0.45, duration * 0.08)
        end = current + duration - min(0.30, duration * 0.06)
        subtitle = str(scene["subtitle"]).replace("\\n", "\n")
        blocks.append(
            f"{index}\n{srt_timestamp(start)} --> {srt_timestamp(end)}\n"
            f"{subtitle}\n"
        )
        current += duration
    output.write_text("\n".join(blocks), encoding="utf-8")


def run_self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="hcop-demo-video-") as raw_tmp:
        temp = Path(raw_tmp)
        fixture_image = Image.new("RGB", (960, 540), "#DDEBF3")
        fixture_draw = ImageDraw.Draw(fixture_image)
        fixture_draw.rectangle((90, 90, 870, 450), fill="#FFFFFF")
        fixture_draw.text(
            (480, 270),
            "HCOP_JP",
            fill="#215C7A",
            font=load_font(54, bold=True),
            anchor="mm",
        )
        fixture_path = temp / "fixture.png"
        fixture_image.save(fixture_path)
        fixture = {
            "version": 1,
            "canvas": {
                "width": 1920,
                "height": 1080,
                "fps": 6,
                "background": "#EEF5F9",
            },
            "defaults": {
                "duration_seconds": 1,
                "transition_seconds": 0.2,
                "title_duration_seconds": 0.8,
            },
            "scenes": [
                {
                    "id": "fixture",
                    "step": 1,
                    "capture": str(fixture_path),
                    "title": "Prueba",
                    "caption": "Fixture temporal",
                    "subtitle": "Prueba del render anotado.",
                    "highlights": [
                        {
                            "rect": [600, 350, 1300, 760],
                            "start": 0.1,
                            "end": 0.9,
                            "label": "Área de prueba",
                        }
                    ],
                    "panel": {
                        "rect": [60, 330, 430, 650],
                        "start": 0.1,
                        "end": 0.9,
                        "title": "Opciones",
                        "items": [
                            {"text": "Camino esperado", "tone": "green"},
                            {"text": "Excepción controlada", "tone": "red"},
                        ],
                    },
                    "cursor_path": [
                        {"t": 0, "x": 800, "y": 500},
                        {"t": 0.8, "x": 1100, "y": 650, "click": True},
                    ],
                }
            ],
        }
        fixture_storyboard = temp / "storyboard.json"
        with fixture_storyboard.open("w", encoding="utf-8") as handle:
            json.dump(fixture, handle)
        canvas, defaults, scenes, _ = validate_storyboard(
            fixture, fixture_storyboard
        )
        output = temp / "frames"
        manifest = render(canvas, defaults, scenes, output)
        srt_path = temp / "fixture.srt"
        write_srt(scenes, srt_path)
        frames = sorted(output.glob("frame_*.png"))
        if manifest["frame_count"] != 6 or len(frames) != 6:
            raise RuntimeError("La prueba no produjo los 6 cuadros esperados.")
        with Image.open(frames[0]) as first:
            if first.size != (1920, 1080):
                raise RuntimeError("La prueba no respetó la resolución 1920x1080.")
        if "Prueba del render anotado." not in srt_path.read_text(
            encoding="utf-8"
        ):
            raise RuntimeError("La prueba no generó el SRT esperado.")
        shutil.rmtree(output)
    print("Self-test OK: 6 cuadros 1920x1080; temporales eliminados.")


def main() -> int:
    args = parse_args()
    if args.self_test:
        run_self_test()
        return 0
    storyboard_path = args.storyboard.resolve()
    try:
        data = load_storyboard(storyboard_path)
        canvas, defaults, scenes, missing = validate_storyboard(
            data,
            storyboard_path,
            allow_missing=args.allow_missing and args.validate_only,
        )
        total_duration = sum(scene["duration_seconds"] for scene in scenes)
        if args.write_srt:
            write_srt(scenes, args.write_srt.resolve())
            print(f"SRT generado: {args.write_srt.resolve()}")
        if args.validate_only:
            print(
                f"Storyboard válido: {len(scenes)} escenas, "
                f"{total_duration:.1f} s, {canvas.width}x{canvas.height}, "
                f"{canvas.fps} fps."
            )
            if missing:
                print(f"Capturas pendientes: {len(missing)}")
                for path in missing:
                    print(f"  - {path}")
            return 0
        manifest = render(canvas, defaults, scenes, args.output_dir.resolve())
        print(
            f"Render completo: {manifest['frame_count']} cuadros en "
            f"{args.output_dir.resolve()}"
        )
        return 0
    except (OSError, ValueError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
