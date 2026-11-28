#!/usr/bin/env python3
"""Minimal ISO/IEC 18004 Byte-Mode QR Code (V1-V6, EC Level L) SVG generator.

Zero external dependencies (Python 3.9+ standard library only).
"""

from typing import List, Tuple

# (version, size, total_data_cw, ec_cw_per_block, num_blocks, align_coords)
_VERSIONS = [
    (1, 21, 19, 7, 1, []),
    (2, 25, 34, 10, 1, [6, 18]),
    (3, 29, 55, 15, 1, [6, 22]),
    (4, 33, 80, 20, 1, [6, 26]),
    (5, 37, 108, 26, 1, [6, 30]),
    (6, 41, 136, 18, 2, [6, 34]),
]

# GF(256) log/exp tables for primitive polynomial 0x11D
_EXP = [0] * 512
_LOG = [0] * 256
_x = 1
for _i in range(255):
    _EXP[_i] = _x
    _LOG[_x] = _i
    _x <<= 1
    if _x & 0x100:
        _x ^= 0x11D
for _i in range(255, 512):
    _EXP[_i] = _EXP[_i - 255]


def _gf_mul(a: int, b: int) -> int:
    if a == 0 or b == 0:
        return 0
    return _EXP[_LOG[a] + _LOG[b]]


def _rs_generator(degree: int) -> List[int]:
    poly = [1]
    for i in range(degree):
        factor = [1, _EXP[i]]
        nxt = [0] * (len(poly) + 1)
        for j, a in enumerate(poly):
            for k, b in enumerate(factor):
                nxt[j + k] ^= _gf_mul(a, b)
        poly = nxt
    return poly


def _rs_encode(data: List[int], ec_len: int) -> List[int]:
    gen = _rs_generator(ec_len)
    buf = list(data) + [0] * ec_len
    for i in range(len(data)):
        coef = buf[i]
        if coef != 0:
            for j in range(1, len(gen)):
                buf[i + j] ^= _gf_mul(gen[j], coef)
    return buf[len(data) :]


def _format_bits(mask: int) -> int:
    # EC Level L = 0b01
    data = (0b01 << 3) | (mask & 0b111)
    rem = data << 10
    for shift in range(14, 9, -1):
        if rem & (1 << shift):
            rem ^= 0x537 << (shift - 10)
    return ((data << 10) | rem) ^ 0x5412


def _encode_data(payload: bytes) -> Tuple[int, int, List[int], List[int]]:
    n = len(payload)
    for ver, size, data_cw, ec_cw, blocks, align in _VERSIONS:
        # Byte mode header: 4-bit mode (0100) + 8-bit length + 8*n data + 4-bit terminator
        if n <= data_cw - 2:
            bits: List[int] = []

            def push(val: int, count: int) -> None:
                for shift in range(count - 1, -1, -1):
                    bits.append((val >> shift) & 1)

            push(0b0100, 4)
            push(n, 8)
            for b in payload:
                push(b, 8)
            # Terminator (up to 4 zero bits)
            term = min(4, data_cw * 8 - len(bits))
            push(0, term)
            # Pad to byte boundary
            while len(bits) % 8 != 0:
                bits.append(0)
            cw = []
            for i in range(0, len(bits), 8):
                byte = 0
                for bit in bits[i : i + 8]:
                    byte = (byte << 1) | bit
                cw.append(byte)
            # Pad codewords
            pad_bytes = (0xEC, 0x11)
            p_idx = 0
            while len(cw) < data_cw:
                cw.append(pad_bytes[p_idx % 2])
                p_idx += 1

            # Split into blocks and interleave (for V1-V6 EC-L, all blocks have equal size)
            b_size = data_cw // blocks
            data_blocks = [cw[i * b_size : (i + 1) * b_size] for i in range(blocks)]
            ec_blocks = [_rs_encode(blk, ec_cw) for blk in data_blocks]

            interleaved: List[int] = []
            for i in range(b_size):
                for blk in data_blocks:
                    interleaved.append(blk[i])
            for i in range(ec_cw):
                for blk in ec_blocks:
                    interleaved.append(blk[i])
            return ver, size, interleaved, align

    raise ValueError(f"QR payload too long ({n} bytes, max 134)")


def _build_matrix(text: str) -> List[List[bool]]:
    payload = text.encode("utf-8")
    _, size, codewords, align = _encode_data(payload)

    mat: List[List[bool]] = [[False] * size for _ in range(size)]
    func: List[List[bool]] = [[False] * size for _ in range(size)]

    def set_func(r: int, c: int, val: bool) -> None:
        if 0 <= r < size and 0 <= c < size:
            mat[r][c] = val
            func[r][c] = True

    def draw_finder(r0: int, c0: int) -> None:
        for dr in range(-1, 8):
            for dc in range(-1, 8):
                r, c = r0 + dr, c0 + dc
                if 0 <= r < size and 0 <= c < size:
                    in_outer = 0 <= dr <= 6 and 0 <= dc <= 6
                    on_border = dr in (0, 6) or dc in (0, 6)
                    in_center = 2 <= dr <= 4 and 2 <= dc <= 4
                    set_func(r, c, in_outer and (on_border or in_center))

    draw_finder(0, 0)
    draw_finder(0, size - 7)
    draw_finder(size - 7, 0)

    # Timing patterns
    for i in range(8, size - 8):
        set_func(6, i, i % 2 == 0)
        set_func(i, 6, i % 2 == 0)

    # Alignment patterns
    for ar in align:
        for ac in align:
            if (ar == 6 and ac == 6) or (ar == 6 and ac == size - 7) or (ar == size - 7 and ac == 6):
                continue
            for dr in range(-2, 3):
                for dc in range(-2, 3):
                    val = max(abs(dr), abs(dc)) != 1
                    set_func(ar + dr, ac + dc, val)

    # Reserve format info strips + dark module
    for i in range(9):
        if i != 6:
            set_func(8, i, False)
            set_func(i, 8, False)
    for i in range(size - 8, size):
        set_func(8, i, False)
        set_func(i, 8, False)
    set_func(size - 8, 8, True)

    # Place data bits in zigzag columns
    bits: List[bool] = []
    for byte in codewords:
        for shift in range(7, -1, -1):
            bits.append(bool((byte >> shift) & 1))

    bit_idx = 0
    upward = True
    col = size - 1
    while col > 0:
        if col == 6:
            col -= 1
        rows = range(size - 1, -1, -1) if upward else range(size)
        for row in rows:
            for dc in (0, -1):
                c = col + dc
                if not func[row][c]:
                    val = bits[bit_idx] if bit_idx < len(bits) else False
                    mat[row][c] = val
                    bit_idx += 1
        upward = not upward
        col -= 2

    def apply_mask(mask: int) -> List[List[bool]]:
        out = [row[:] for row in mat]
        for r in range(size):
            for c in range(size):
                if func[r][c]:
                    continue
                if mask == 0:
                    inv = (r + c) % 2 == 0
                elif mask == 1:
                    inv = r % 2 == 0
                elif mask == 2:
                    inv = c % 3 == 0
                elif mask == 3:
                    inv = (r + c) % 3 == 0
                elif mask == 4:
                    inv = ((r // 2) + (c // 3)) % 2 == 0
                elif mask == 5:
                    inv = ((r * c) % 2 + (r * c) % 3) == 0
                elif mask == 6:
                    inv = (((r * c) % 2 + (r * c) % 3) % 2) == 0
                else:
                    inv = (((r + c) % 2 + (r * c) % 3) % 2) == 0
                if inv:
                    out[r][c] = not out[r][c]

        fmt = _format_bits(mask)
        # Top-left vertical (bits 0..5 -> rows 0..5, bit 6 -> row 7, bit 7 -> row 8)
        # Bottom-left vertical (bits 8..14 -> rows size-7..size-1)
        # Top-right horizontal (bits 0..7 -> cols size-1..size-8)
        # Top-left horizontal (bits 14..9 -> cols 0..5, bit 8 -> col 7)
        for i in range(6):
            out[8][i] = bool((fmt >> (14 - i)) & 1)
        out[8][7] = bool((fmt >> 8) & 1)
        out[8][8] = bool((fmt >> 7) & 1)
        out[7][8] = bool((fmt >> 6) & 1)
        for i in range(6):
            out[5 - i][8] = bool((fmt >> (5 - i)) & 1)

        for i in range(8):
            out[8][size - 1 - i] = bool((fmt >> i) & 1)
        for i in range(7):
            out[size - 1 - i][8] = bool((fmt >> (14 - i)) & 1)
        out[size - 8][8] = True
        return out

    def penalty(grid: List[List[bool]]) -> int:
        score = 0
        # Rule 1: adjacent runs >= 5
        for r in range(size):
            run = 1
            for c in range(1, size):
                if grid[r][c] == grid[r][c - 1]:
                    run += 1
                    if run == 5:
                        score += 3
                    elif run > 5:
                        score += 1
                else:
                    run = 1
        for c in range(size):
            run = 1
            for r in range(1, size):
                if grid[r][c] == grid[r - 1][c]:
                    run += 1
                    if run == 5:
                        score += 3
                    elif run > 5:
                        score += 1
                else:
                    run = 1
        # Rule 2: 2x2 blocks
        for r in range(size - 1):
            for c in range(size - 1):
                v = grid[r][c]
                if v == grid[r][c + 1] == grid[r + 1][c] == grid[r + 1][c + 1]:
                    score += 3
        # Rule 4: balance
        dark = sum(1 for r in grid for v in r if v)
        ratio = abs((dark * 100 // (size * size)) - 50) // 5
        score += ratio * 10
        return score

    best_grid = None
    best_pen = 10**9
    for m in range(8):
        candidate = apply_mask(m)
        p = penalty(candidate)
        if p < best_pen:
            best_pen = p
            best_grid = candidate

    assert best_grid is not None
    return best_grid


def render_svg(text: str, quiet: int = 4, fg: str = "#091018", bg: str = "#ffffff") -> str:
    grid = _build_matrix(text)
    size = len(grid)
    total = size + quiet * 2
    rects = []
    for r, row in enumerate(grid):
        c = 0
        while c < size:
            if row[c]:
                start = c
                while c < size and row[c]:
                    c += 1
                rects.append(f'<rect x="{start + quiet}" y="{r + quiet}" width="{c - start}" height="1"/>')
            else:
                c += 1
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {total} {total}" '
        f'shape-rendering="crispEdges">'
        f'<rect width="{total}" height="{total}" fill="{bg}"/>'
        f'<g fill="{fg}">{"".join(rects)}</g>'
        f"</svg>"
    )
